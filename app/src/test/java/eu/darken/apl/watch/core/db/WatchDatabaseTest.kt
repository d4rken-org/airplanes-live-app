package eu.darken.apl.watch.core.db

import androidx.test.core.app.ApplicationProvider
import eu.darken.apl.watch.core.db.history.WatchHistoryHourEntity
import eu.darken.apl.watch.core.db.history.WatchHistorySyncEntity
import eu.darken.apl.main.core.aircraft.Registration
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.LocationWatch
import eu.darken.apl.watch.core.types.WatchCheckOutcome
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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

    private fun hour(watchId: String, at: Instant, count: Int, final: Boolean) = WatchHistoryHourEntity(
        watchId = watchId,
        hour = at,
        aircraftCount = count,
        coverage = "full",
        isFinal = final,
    )

    companion object {
        private val AT = Instant.parse("2026-10-13T10:00:00Z")
    }
}
