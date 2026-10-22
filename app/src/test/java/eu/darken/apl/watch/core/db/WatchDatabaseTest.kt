package eu.darken.apl.watch.core.db

import androidx.test.core.app.ApplicationProvider
import eu.darken.apl.watch.core.LocationWatchChangedException
import eu.darken.apl.watch.core.db.history.WatchHistoryHourEntity
import eu.darken.apl.watch.core.db.history.WatchHistorySyncEntity
import eu.darken.apl.main.core.aircraft.Registration
import eu.darken.apl.watch.core.db.server.WatchNotificationOutboxEntity
import eu.darken.apl.watch.core.db.server.WatchServerEventEntity
import eu.darken.apl.watch.core.db.server.WatchServerRollupEntity
import eu.darken.apl.watch.core.db.server.WatchServerStateEntity
import eu.darken.apl.watch.core.db.server.WatchServerSyncEntity
import eu.darken.apl.watch.core.db.server.WatchServerTombstoneEntity
import eu.darken.apl.watch.core.db.types.AircraftWatchEntity
import eu.darken.apl.watch.core.db.types.BaseWatchEntity
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.LocationWatch
import eu.darken.apl.watch.core.types.WatchCheckOutcome
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class WatchDatabaseTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var db: WatchDatabase

    @Before
    fun setup() {
        context.deleteDatabase("watch")
        db = WatchDatabase(context)
    }

    @After
    fun teardown() {
        context.deleteDatabase("watch")
    }

    private suspend fun location(): LocationWatch =
        db.createLocation(latitude = 50.0, longitude = 8.5, radiusInMeters = 5000f, label = "FRA", note = "")

    private suspend fun revisionOf(id: String) = db.watches.first().single { it.id == id }.definitionRevision

    @Test
    fun `a check is written with its status and reports the previous count`() = runBlocking<Unit> {
        val watch = location()

        val first = db.applyCheck(watch.id, 0, AT, WatchCheckOutcome.ABSENT, aircraftCount = 0, operationId = "op-1")
        val second = db.applyCheck(watch.id, 0, AT.plusSeconds(60), WatchCheckOutcome.MATCHED, 2, setOf("3C65A3"), "op-2")

        first shouldBe WatchDatabase.CheckApplication.Applied(previousCount = null)
        second shouldBe WatchDatabase.CheckApplication.Applied(previousCount = 0)
        val stored = db.watches.first().single()
        stored.lastCheckOutcome shouldBe WatchCheckOutcome.MATCHED
        stored.lastCheckAt shouldBe AT.plusSeconds(60)
    }

    @Test
    fun `the same operation twice writes neither the check nor the status again`() = runBlocking<Unit> {
        val watch = location()
        db.applyCheck(watch.id, 0, AT, WatchCheckOutcome.MATCHED, aircraftCount = 1, operationId = "op-1")
        db.updateLastCheck(watch.id, 0, AT.plusSeconds(30), WatchCheckOutcome.INCONCLUSIVE, "data_incomplete")

        val again = db.applyCheck(watch.id, 0, AT.plusSeconds(60), WatchCheckOutcome.MATCHED, 1, operationId = "op-1")

        again shouldBe WatchDatabase.CheckApplication.Duplicate
        db.checks.count() shouldBe 1
        db.watches.first().single().lastCheckOutcome shouldBe WatchCheckOutcome.INCONCLUSIVE
    }

    @Test
    fun `a matching check remembers the aircraft's registration`() = runBlocking<Unit> {
        val watch = db.createAircraft("3C6752", note = "")

        db.applyCheck(watch.id, 0, AT, WatchCheckOutcome.MATCHED, 1, setOf("3C6752"), "op-1", registration = "D-AIMA")

        (db.watches.first().single() as AircraftWatch).lastRegistration shouldBe "D-AIMA"
    }

    @Test
    fun `a missing registration keeps the remembered one`() = runBlocking<Unit> {
        val watch = db.createAircraft("3C6752", note = "")
        db.applyCheck(watch.id, 0, AT, WatchCheckOutcome.MATCHED, 1, setOf("3C6752"), "op-1", registration = "D-AIMA")

        db.applyCheck(watch.id, 0, AT.plusSeconds(60), WatchCheckOutcome.MATCHED, 1, setOf("3C6752"), "op-2", registration = null)
        db.applyCheck(watch.id, 0, AT.plusSeconds(120), WatchCheckOutcome.MATCHED, 1, setOf("3C6752"), "op-3", registration = " ")
        db.backfillRegistrations(mapOf(watch.id to null))

        (db.watches.first().single() as AircraftWatch).lastRegistration shouldBe "D-AIMA"
    }

    @Test
    fun `the backfill fills a gap but never replaces what a check saw`() = runBlocking<Unit> {
        val checked = db.createAircraft("3C6752", note = "")
        val unchecked = db.createAircraft("4B1805", note = "")
        db.applyCheck(checked.id, 0, AT, WatchCheckOutcome.MATCHED, 1, setOf("3C6752"), "op-1", registration = "D-AIMA")

        db.backfillRegistrations(mapOf(checked.id to "D-OLD", unchecked.id to "HB-CQE"))

        val stored = db.watches.first().filterIsInstance<AircraftWatch>().associate { it.id to it.lastRegistration }
        stored shouldBe mapOf(checked.id to "D-AIMA", unchecked.id to "HB-CQE")
    }

    @Test
    fun `a check's registration and status reach the watch list together`() = runBlocking<Unit> {
        val watch = db.createAircraft("3C6752", note = "")
        val emissions = Channel<AircraftWatch>(Channel.UNLIMITED)
        val collector = launch(Dispatchers.Default) {
            db.watches.collect { emissions.send(it.single() as AircraftWatch) }
        }
        emissions.receive().lastRegistration shouldBe null

        db.applyCheck(watch.id, 0, AT, WatchCheckOutcome.MATCHED, 1, setOf("3C6752"), "op-1", registration = "D-AIMA")

        withTimeout(5_000) {
            while (true) {
                val emitted = emissions.receive()
                // A registration without the check that wrote it would be a torn read
                if (emitted.lastRegistration != null) emitted.lastCheckOutcome shouldBe WatchCheckOutcome.MATCHED
                if (emitted.lastRegistration == "D-AIMA") break
            }
        }
        collector.cancel()
    }

    @Test
    fun `a backfilled registration reaches the watch list`() = runBlocking<Unit> {
        val watch = db.createAircraft("3C6752", note = "")
        val emissions = Channel<Registration?>(Channel.UNLIMITED)
        val collector = launch(Dispatchers.Default) {
            db.watches.collect { emissions.send((it.single() as AircraftWatch).lastRegistration) }
        }
        emissions.receive() shouldBe null

        db.backfillRegistrations(mapOf(watch.id to "D-AIMA"))

        withTimeout(5_000) { while (emissions.receive() != "D-AIMA") Unit }
        collector.cancel()
    }

    @Test
    fun `moving a location watch starts it over`() = runBlocking<Unit> {
        val watch = location()
        db.applyCheck(watch.id, 0, AT, WatchCheckOutcome.MATCHED, aircraftCount = 1, operationId = "op-1")
        db.applyHistory("history-1", AT) {
            writeHours(listOf(hour(watch.id, AT, count = 1, final = true)))
            writeSync(WatchHistorySyncEntity(watch.id, 0, nextEligibleAt = AT, updatedAt = AT))
        }

        db.updateLocation(watch.id, latitude = 51.0, longitude = 8.5, radiusInMeters = 5000f, label = "FRA")

        revisionOf(watch.id) shouldBe 1
        db.checks.count() shouldBe 0
        db.history.getHours(watch.id, Instant.EPOCH, AT.plusSeconds(3600)) shouldBe emptyList()
        db.history.getSync(watch.id) shouldBe null
        val stored = db.watches.first().single()
        stored.lastCheckAt shouldBe null
        stored.lastCheckOutcome shouldBe null
    }

    @Test
    fun `an edit begun before the area changed saves nothing`() = runBlocking<Unit> {
        val watch = location()
        db.updateLocation(watch.id, latitude = 51.0, longitude = 8.5, radiusInMeters = 5000f, label = "FRA")

        shouldThrow<LocationWatchChangedException> {
            db.updateLocation(watch.id, latitude = 52.0, longitude = 8.5, radiusInMeters = 5000f, label = "X", expectedRevision = 0)
        }

        revisionOf(watch.id) shouldBe 1
        (db.watches.first().single() as LocationWatch).apply {
            latitude shouldBe 51.0
            label shouldBe "FRA"
        }
    }

    @Test
    fun `an edit of the current area saves`() = runBlocking<Unit> {
        val watch = location()

        db.updateLocation(watch.id, latitude = 51.0, longitude = 8.5, radiusInMeters = 5000f, label = "FRA", expectedRevision = 0)

        revisionOf(watch.id) shouldBe 1
    }

    @Test
    fun `renaming a location watch keeps what it saw`() = runBlocking<Unit> {
        val watch = location()
        db.applyCheck(watch.id, 0, AT, WatchCheckOutcome.MATCHED, aircraftCount = 1, operationId = "op-1")

        db.updateLocation(watch.id, latitude = 50.0, longitude = 8.5, radiusInMeters = 5000f, label = "Frankfurt")

        revisionOf(watch.id) shouldBe 0
        db.checks.count() shouldBe 1
        (db.watches.first().single() as LocationWatch).label shouldBe "Frankfurt"
    }

    @Test
    fun `an answer for the old area is refused after a move and back`() = runBlocking<Unit> {
        val watch = location()
        db.updateLocation(watch.id, latitude = 51.0, longitude = 8.5, radiusInMeters = 5000f, label = "FRA")
        db.updateLocation(watch.id, latitude = 50.0, longitude = 8.5, radiusInMeters = 5000f, label = "FRA")

        val application = db.applyCheck(watch.id, 0, AT, WatchCheckOutcome.MATCHED, 1, operationId = "op-1")
        val status = db.updateLastCheck(watch.id, 0, AT, WatchCheckOutcome.FAILED, "IOException")

        revisionOf(watch.id) shouldBe 2
        application shouldBe WatchDatabase.CheckApplication.Stale
        status shouldBe false
        db.checks.count() shouldBe 0
        db.watches.first().single().lastCheckOutcome shouldBe null
    }

    @Test
    fun `a legacy-only check is refused for a watch the server holds`() = runBlocking<Unit> {
        val held = db.createAircraft("3C6752", note = "")
        val unmapped = db.createAircraft("3C65A3", note = "")
        db.server.upsertState(state(held.id))

        val application = db.applyCheck(held.id, 0, AT, WatchCheckOutcome.MATCHED, 1, operationId = "op-1", legacyOnly = true)
        val status = db.updateLastCheck(held.id, 0, AT, WatchCheckOutcome.FAILED, "IOException", legacyOnly = true)
        val other = db.applyCheck(unmapped.id, 0, AT, WatchCheckOutcome.ABSENT, 0, operationId = "op-1", legacyOnly = true)

        application shouldBe WatchDatabase.CheckApplication.Stale
        status shouldBe false
        other shouldBe WatchDatabase.CheckApplication.Applied(previousCount = null)
        db.checks.count() shouldBe 1
        db.watches.first().single { it.id == held.id }.lastCheckOutcome shouldBe null

        db.applyCheck(held.id, 0, AT, WatchCheckOutcome.MATCHED, 1, operationId = "op-1") shouldBe
                WatchDatabase.CheckApplication.Applied(previousCount = null)
        db.updateLastCheck(held.id, 0, AT.plusSeconds(60), WatchCheckOutcome.INCONCLUSIVE, "data_incomplete") shouldBe true
        db.checks.count() shouldBe 2
        db.watches.first().single { it.id == held.id }.lastCheckOutcome shouldBe WatchCheckOutcome.INCONCLUSIVE
    }

    @Test
    fun `a history answer is applied once`() = runBlocking<Unit> {
        val watch = location()
        val first = db.applyHistory("history-1", AT) {
            writeHours(listOf(hour(watch.id, AT, count = 1, final = false)))
        }
        val replayed = db.applyHistory("history-1", AT) {
            writeHours(listOf(hour(watch.id, AT, count = 5, final = true)))
        }

        first shouldBe true
        replayed shouldBe false
        db.history.getHours(watch.id, listOf(AT)).single().aircraftCount shouldBe 1
    }

    @Test
    fun `a final hour is not replaced by a provisional answer`() = runBlocking<Unit> {
        val watch = location()
        db.applyHistory("history-1", AT) { writeHours(listOf(hour(watch.id, AT, count = 3, final = true))) }
        db.applyHistory("history-2", AT) {
            writeHours(
                listOf(
                    hour(watch.id, AT, count = 0, final = false),
                    hour(watch.id, AT.plusSeconds(3600), count = 2, final = false),
                )
            )
        }

        val hours = db.history.getHours(watch.id, AT, AT.plusSeconds(7200)).sortedBy { it.hour }
        hours.map { it.aircraftCount to it.isFinal } shouldBe listOf(3 to true, 2 to false)
    }

    @Test
    fun `deleting a watch removes its history`() = runBlocking<Unit> {
        val watch = location()
        db.applyHistory("history-1", AT) {
            writeHours(listOf(hour(watch.id, AT, count = 1, final = true)))
            writeSync(WatchHistorySyncEntity(watch.id, 0, nextEligibleAt = AT, updatedAt = AT))
        }

        db.deleteWatch(watch.id)

        db.history.getHours(watch.id, Instant.EPOCH, AT.plusSeconds(3600)) shouldBe emptyList()
        db.history.getSync(watch.id) shouldBe null
    }

    @Test
    fun `a snapshot pairs watches with their checks`() = runBlocking<Unit> {
        val watch = location()
        db.applyCheck(watch.id, 0, AT, WatchCheckOutcome.ABSENT, aircraftCount = 0, operationId = "op-1")

        val (watches, checks) = db.snapshot()

        watches.single().shouldBeInstanceOf<LocationWatch>()
        checks.single().watchId shouldBe watch.id
    }

    @Test
    fun `an import waits for a running allocation`() = runBlocking<Unit> {
        val holding = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val allocation = launch(Dispatchers.Default) {
            db.withAllocation {
                holding.complete(Unit)
                release.await()
            }
        }
        holding.await()

        val importing = async(Dispatchers.Default) { importAircraft("3C65A3") }

        withTimeoutOrNull(500) { importing.await() } shouldBe null
        importing.isActive shouldBe true
        db.watches.first() shouldBe emptyList()

        release.complete(Unit)
        val imported = withTimeout(5_000) { importing.await() }
        allocation.join()

        db.watches.first().map { it.id } shouldBe listOf(imported)
    }

    @Test
    fun `only a move of the area raises the generation`() = runBlocking<Unit> {
        val watch = location()

        db.updateNote(watch.id, "home")
        db.updateNotification(watch.id, true)
        db.updateLocation(watch.id, latitude = 50.0, longitude = 8.5, radiusInMeters = 5000f, label = "Frankfurt")
        stored(watch.id).localGeneration shouldBe 0

        db.updateLocation(watch.id, latitude = 51.0, longitude = 8.5, radiusInMeters = 5000f, label = "Frankfurt")
        stored(watch.id).localGeneration shouldBe 1
    }

    @Test
    fun `a note for a removed watch is dropped`() = runBlocking<Unit> {
        val watch = location()
        db.deleteWatch(watch.id)

        db.updateNote(watch.id, "home")

        db.watches.first() shouldBe emptyList()
    }

    @Test
    fun `deleting a sent watch leaves a tombstone and removes its server data`() = runBlocking<Unit> {
        val sent = location()
        val other = db.createAircraft("3C6752", note = "")
        db.server.upsertState(state(sent.id, installationId = "installation-old"))
        db.server.upsertState(state(other.id))
        db.server.upsertRollups(listOf(rollup(sent.id), rollup(other.id)))
        db.server.upsertOutbox(listOf(outbox(sent.id, seq = 1), outbox(other.id, seq = 2)))

        db.deleteWatch(sent.id)

        db.server.getTombstones("installation-old").map { it.watchId } shouldBe listOf(sent.id)
        db.server.getTombstones(INSTALLATION) shouldBe emptyList()
        db.server.getState(sent.id) shouldBe null
        db.server.rollupsSince(sent.id, Instant.EPOCH).first() shouldBe emptyList()
        db.server.getOutbox().map { it.watchId } shouldBe listOf(other.id)
        db.server.getState(other.id) shouldNotBe null
        db.server.rollupsSince(other.id, Instant.EPOCH).first().size shouldBe 1
    }

    @Test
    fun `deleting a watch the server never heard of leaves no tombstone`() = runBlocking<Unit> {
        val sent = location()
        val unsent = db.createAircraft("3C6752", note = "")
        val alsoUnsent = db.createSquawk("7700", note = "")
        db.server.upsertState(state(sent.id))

        db.deleteWatch(unsent.id)
        db.server.getTombstones(INSTALLATION) shouldBe emptyList()

        db.deleteBatch(setOf(sent.id, alsoUnsent.id))
        db.server.getTombstones(INSTALLATION).map { it.watchId } shouldBe listOf(sent.id)
        db.server.getAllStates() shouldBe emptyList()
    }

    @Test
    fun `wiping server state keeps watches, checks and history`() = runBlocking<Unit> {
        val watch = location()
        db.applyCheck(watch.id, 0, AT, WatchCheckOutcome.MATCHED, aircraftCount = 1, operationId = "op-1")
        db.applyHistory("history-1", AT) { writeHours(listOf(hour(watch.id, AT, count = 1, final = true))) }
        db.server.upsertState(state(watch.id))
        db.server.insertTombstones(listOf(WatchServerTombstoneEntity("gone", INSTALLATION, AT)))
        db.server.upsertSync(WatchServerSyncEntity(INSTALLATION, phase = WatchServerSyncEntity.PHASE_LIVE, updatedAt = AT))
        db.server.insertEvents(listOf(event(1)))
        db.server.upsertOutbox(listOf(outbox(watch.id, seq = 1)))
        db.server.upsertRollups(listOf(rollup(watch.id)))

        db.wipeServerState()

        db.server.getAllStates() shouldBe emptyList()
        db.server.getTombstones(INSTALLATION) shouldBe emptyList()
        db.server.getSync(INSTALLATION) shouldBe null
        db.server.getEvents() shouldBe emptyList()
        db.server.getOutbox() shouldBe emptyList()
        db.server.rollupsSince(watch.id, Instant.EPOCH).first() shouldBe emptyList()
        db.watches.first().single().id shouldBe watch.id
        db.checks.count() shouldBe 1
        db.history.getHours(watch.id, listOf(AT)).size shouldBe 1
    }

    @Test
    fun `an event stored before is not stored again`() = runBlocking<Unit> {
        db.server.insertEvents(listOf(event(1, kind = "matched")))

        val rowIds = db.server.insertEvents(listOf(event(1, kind = "cleared"), event(2, kind = "cleared")))

        rowIds.first() shouldBe -1L
        db.server.getEvents().map { it.seq to it.kind } shouldBe listOf(1L to "matched", 2L to "cleared")
    }

    @Test
    fun `a rollup replaces the one for the same revision, hour and source`() = runBlocking<Unit> {
        val watch = location()
        db.server.upsertRollups(listOf(rollup(watch.id, count = 1, final = false)))

        db.server.upsertRollups(
            listOf(
                rollup(watch.id, count = 3, final = true),
                rollup(watch.id, count = 5, final = false, source = "other"),
            )
        )

        db.server.rollupsSince(watch.id, Instant.EPOCH).first()
            .map { Triple(it.source, it.aircraftCount, it.isFinal) }
            .sortedBy { it.first } shouldBe listOf(Triple("live", 3, true), Triple("other", 5, false))
    }

    private suspend fun stored(id: String) = db.watches.first().single { it.id == id }

    private suspend fun importAircraft(hex: String): String {
        val base = BaseWatchEntity(watchType = AircraftWatchEntity.TYPE_KEY)
        db.importAircraft(base, AircraftWatchEntity(id = base.id, hexCode = hex))
        return base.id
    }

    private fun hour(watchId: String, at: Instant, count: Int, final: Boolean) = WatchHistoryHourEntity(
        watchId = watchId,
        hour = at,
        aircraftCount = count,
        coverage = "full",
        isFinal = final,
    )

    private fun state(watchId: String, installationId: String = INSTALLATION) = WatchServerStateEntity(
        watchId = watchId,
        installationId = installationId,
        serverWatchId = "server-$watchId",
        uploadState = WatchServerStateEntity.UPLOAD_SYNCED,
        updatedAt = AT,
    )

    private fun rollup(watchId: String, count: Int = 1, final: Boolean = false, source: String = "live") =
        WatchServerRollupEntity(
            serverWatchId = "server-$watchId",
            revision = 1,
            hour = AT,
            source = source,
            watchId = watchId,
            installationId = INSTALLATION,
            seq = 1,
            evaluationBasis = "observed",
            aircraftCount = count,
            coverage = "full",
            isFinal = final,
        )

    private fun outbox(watchId: String, seq: Long) = WatchNotificationOutboxEntity(
        id = WatchNotificationOutboxEntity.individualId(seq),
        installationId = INSTALLATION,
        kind = WatchNotificationOutboxEntity.KIND_INDIVIDUAL,
        watchId = watchId,
        eventSeq = seq,
        createdAt = AT,
    )

    private fun event(seq: Long, kind: String = "matched") = WatchServerEventEntity(
        seq = seq,
        installationId = INSTALLATION,
        kind = kind,
        observedAt = AT,
        receivedAt = AT,
    )

    companion object {
        private val AT = Instant.parse("2026-10-13T10:00:00Z")
        private const val INSTALLATION = "installation-1"
    }
}
