package eu.darken.apl.watch.core.db

import androidx.test.core.app.ApplicationProvider
import eu.darken.apl.watch.core.LocationWatchChangedException
import eu.darken.apl.main.core.aircraft.Registration
import eu.darken.apl.watch.core.db.server.WatchNotificationOutboxEntity
import eu.darken.apl.watch.core.db.server.WatchServerEventEntity
import eu.darken.apl.watch.core.db.server.WatchServerRollupEntity
import eu.darken.apl.watch.core.db.server.WatchServerStateEntity
import eu.darken.apl.watch.core.db.server.WatchServerSyncEntity
import eu.darken.apl.watch.core.db.server.WatchServerTombstoneEntity
import eu.darken.apl.watch.core.db.types.AircraftWatchEntity
import eu.darken.apl.watch.core.db.types.BaseWatchEntity
import eu.darken.apl.watch.core.db.types.LocationWatchEntity
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.LocationWatch
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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

    private suspend fun location(minAltitudeFt: Int? = null, maxAltitudeFt: Int? = null): LocationWatch =
        db.createLocation(
            latitude = 50.0,
            longitude = 8.5,
            radiusInMeters = 5000f,
            minAltitudeFt = minAltitudeFt,
            maxAltitudeFt = maxAltitudeFt,
            label = "FRA",
            note = "",
        )

    private suspend fun revisionOf(id: String) = db.watches.first().single { it.id == id }.definitionRevision

    @Test
    fun `the backfill fills a gap but never replaces a stored registration`() = runBlocking<Unit> {
        val stored = importAircraft("3C6752", registration = "D-AIMA")
        val missing = db.createAircraft("4B1805", note = "")

        db.backfillRegistrations(mapOf(stored to "D-OLD", missing.id to "HB-CQE"))

        val registrations = db.watches.first().filterIsInstance<AircraftWatch>().associate { it.id to it.lastRegistration }
        registrations shouldBe mapOf(stored to "D-AIMA", missing.id to "HB-CQE")
    }

    @Test
    fun `a blank registration fills nothing`() = runBlocking<Unit> {
        val first = db.createAircraft("3C6752", note = "")
        val second = db.createAircraft("4B1805", note = "")

        db.backfillRegistrations(mapOf(first.id to null, second.id to " "))

        db.watches.first().filterIsInstance<AircraftWatch>().map { it.lastRegistration } shouldBe listOf(null, null)
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
    fun `moving a location watch starts a new definition`() = runBlocking<Unit> {
        val watch = location()

        db.updateLocation(
            watch.id,
            latitude = 51.0,
            longitude = 8.5,
            radiusInMeters = 5000f,
            minAltitudeFt = null,
            maxAltitudeFt = null,
            label = "FRA",
        )

        revisionOf(watch.id) shouldBe 1
        (db.watches.first().single() as LocationWatch).latitude shouldBe 51.0
    }

    @Test
    fun `an edit begun before the area changed saves nothing`() = runBlocking<Unit> {
        val watch = location()
        db.updateLocation(
            watch.id,
            latitude = 51.0,
            longitude = 8.5,
            radiusInMeters = 5000f,
            minAltitudeFt = null,
            maxAltitudeFt = null,
            label = "FRA",
        )

        shouldThrow<LocationWatchChangedException> {
            db.updateLocation(
                watch.id,
                latitude = 52.0,
                longitude = 8.5,
                radiusInMeters = 5000f,
                minAltitudeFt = null,
                maxAltitudeFt = null,
                label = "X",
                expectedRevision = 0,
            )
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

        db.updateLocation(
            watch.id,
            latitude = 51.0,
            longitude = 8.5,
            radiusInMeters = 5000f,
            minAltitudeFt = null,
            maxAltitudeFt = null,
            label = "FRA",
            expectedRevision = 0,
        )

        revisionOf(watch.id) shouldBe 1
    }

    @Test
    fun `renaming a location watch keeps its definition`() = runBlocking<Unit> {
        val watch = location()

        db.updateLocation(
            watch.id,
            latitude = 50.0,
            longitude = 8.5,
            radiusInMeters = 5000f,
            minAltitudeFt = null,
            maxAltitudeFt = null,
            label = "Frankfurt",
        )

        revisionOf(watch.id) shouldBe 0
        (db.watches.first().single() as LocationWatch).label shouldBe "Frankfurt"
    }

    @Test
    fun `moving back to the old area is still a new definition`() = runBlocking<Unit> {
        val watch = location()
        db.updateLocation(
            watch.id,
            latitude = 51.0,
            longitude = 8.5,
            radiusInMeters = 5000f,
            minAltitudeFt = null,
            maxAltitudeFt = null,
            label = "FRA",
        )
        db.updateLocation(
            watch.id,
            latitude = 50.0,
            longitude = 8.5,
            radiusInMeters = 5000f,
            minAltitudeFt = null,
            maxAltitudeFt = null,
            label = "FRA",
        )

        revisionOf(watch.id) shouldBe 2
    }

    @Test
    fun `changing only the altitude band starts a new definition`() = runBlocking<Unit> {
        val watch = location(minAltitudeFt = 1_000, maxAltitudeFt = 10_000)

        db.updateLocation(watch.id, 50.0, 8.5, 5000f, minAltitudeFt = 1_000, maxAltitudeFt = 20_000, label = "FRA")

        (stored(watch.id) as LocationWatch).apply {
            definitionRevision shouldBe 1
            localGeneration shouldBe 1
            minAltitudeFt shouldBe 1_000
            maxAltitudeFt shouldBe 20_000
        }

        db.updateLocation(watch.id, 50.0, 8.5, 5000f, minAltitudeFt = null, maxAltitudeFt = 20_000, label = "FRA")

        (stored(watch.id) as LocationWatch).apply {
            definitionRevision shouldBe 2
            localGeneration shouldBe 2
            minAltitudeFt shouldBe null
        }
    }

    @Test
    fun `renaming a location watch keeps its altitude band and definition`() = runBlocking<Unit> {
        val watch = location(minAltitudeFt = 0, maxAltitudeFt = 5_000)

        db.updateLocation(watch.id, 50.0, 8.5, 5000f, minAltitudeFt = 0, maxAltitudeFt = 5_000, label = "Frankfurt")

        (stored(watch.id) as LocationWatch).apply {
            definitionRevision shouldBe 0
            localGeneration shouldBe 0
            label shouldBe "Frankfurt"
            minAltitudeFt shouldBe 0
            maxAltitudeFt shouldBe 5_000
        }
    }

    @Test
    fun `an invalid altitude band is not created`() = runBlocking<Unit> {
        shouldThrow<IllegalArgumentException> { location(minAltitudeFt = 10_000, maxAltitudeFt = 5_000) }
        shouldThrow<IllegalArgumentException> { location(minAltitudeFt = -1) }
        shouldThrow<IllegalArgumentException> { location(maxAltitudeFt = 60_001) }

        db.watches.first() shouldBe emptyList()
    }

    @Test
    fun `an invalid altitude band is not saved over the stored one`() = runBlocking<Unit> {
        val watch = location(minAltitudeFt = 1_000)

        shouldThrow<IllegalArgumentException> {
            db.updateLocation(watch.id, 50.0, 8.5, 5000f, minAltitudeFt = 1_000, maxAltitudeFt = 500, label = "FRA")
        }

        (stored(watch.id) as LocationWatch).apply {
            definitionRevision shouldBe 0
            minAltitudeFt shouldBe 1_000
            maxAltitudeFt shouldBe null
        }
    }

    @Test
    fun `an invalid altitude band is not imported`() = runBlocking<Unit> {
        val base = BaseWatchEntity(
            watchType = LocationWatchEntity.TYPE_KEY,
            latitude = 50.0,
            longitude = 8.5,
            radius = 5000f,
            minAltitudeFt = 70_000,
        )

        shouldThrow<IllegalArgumentException> {
            db.importLocation(base, LocationWatchEntity(id = base.id, label = "FRA"))
        }

        db.watches.first() shouldBe emptyList()
    }

    @Test
    fun `an area with another altitude band is another location watch`() = runBlocking<Unit> {
        location()
        location(minAltitudeFt = 0, maxAltitudeFt = 10_000)

        db.hasLocationWatch(50.0, 8.5, 5000f, null, null) shouldBe true
        db.hasLocationWatch(50.0, 8.5, 5000f, 0, 10_000) shouldBe true
        db.hasLocationWatch(50.0, 8.5, 5000f, null, 10_000) shouldBe false
        db.hasLocationWatch(50.0, 8.5, 5000f, 0, null) shouldBe false
    }

    @Test
    fun `a snapshot holds every watch with its subtype`() = runBlocking<Unit> {
        location()
        db.createAircraft("3C6752", note = "")

        val watches = db.snapshot()

        watches.size shouldBe 2
        watches.filterIsInstance<LocationWatch>().single().label shouldBe "FRA"
        watches.filterIsInstance<AircraftWatch>().single().hex shouldBe "3C6752"
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
        db.updateLocation(
            watch.id,
            latitude = 50.0,
            longitude = 8.5,
            radiusInMeters = 5000f,
            minAltitudeFt = null,
            maxAltitudeFt = null,
            label = "Frankfurt",
        )
        stored(watch.id).localGeneration shouldBe 0

        db.updateLocation(
            watch.id,
            latitude = 51.0,
            longitude = 8.5,
            radiusInMeters = 5000f,
            minAltitudeFt = null,
            maxAltitudeFt = null,
            label = "Frankfurt",
        )
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
    fun `wiping server state keeps the watches`() = runBlocking<Unit> {
        val watch = location()
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

    private suspend fun importAircraft(hex: String, registration: Registration? = null): String {
        val base = BaseWatchEntity(watchType = AircraftWatchEntity.TYPE_KEY)
        db.importAircraft(base, AircraftWatchEntity(id = base.id, hexCode = hex, lastRegistration = registration))
        return base.id
    }

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
