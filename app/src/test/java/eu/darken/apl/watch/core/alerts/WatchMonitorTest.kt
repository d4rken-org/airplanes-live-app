package eu.darken.apl.watch.core.alerts

import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.datastore.DataStoreValue
import eu.darken.apl.common.compose.preview.FakeAircraft
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.db.AircraftDatabase
import eu.darken.apl.main.core.query.BatchResult
import eu.darken.apl.main.core.query.WatchOutcome
import eu.darken.apl.main.core.request.OperationFailedException
import eu.darken.apl.main.core.request.OperationStore
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.api.Allowance
import eu.darken.apl.server.api.ServerCodes
import eu.darken.apl.server.api.UsageUpdate
import eu.darken.apl.server.api.WatchDefinition
import eu.darken.apl.watch.core.WatchId
import eu.darken.apl.watch.core.WatchRepo
import eu.darken.apl.watch.core.WatchSettings
import eu.darken.apl.watch.core.db.WatchDatabase
import eu.darken.apl.watch.core.db.types.AircraftWatchEntity
import eu.darken.apl.watch.core.db.types.BaseWatchEntity
import eu.darken.apl.watch.core.db.types.FlightWatchEntity
import eu.darken.apl.watch.core.db.types.SquawkWatchEntity
import eu.darken.apl.watch.core.history.WatchCheck
import eu.darken.apl.watch.core.history.WatchHistoryRepo
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.FlightWatch
import eu.darken.apl.watch.core.types.SquawkWatch
import eu.darken.apl.watch.core.types.Watch
import eu.darken.apl.watch.core.types.WatchCheckOutcome
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class WatchMonitorTest {

    private val watchRepo = mockk<WatchRepo>()
    private val watchDb = mockk<WatchDatabase>(relaxed = true)
    private val historyRepo = mockk<WatchHistoryRepo>()
    private val aircraftRepo = mockk<AircraftRepo>()
    private val aircraftDatabase = mockk<AircraftDatabase>(relaxed = true)
    private val operationStore = mockk<OperationStore>(relaxed = true)
    private val accessRepo = mockk<AccessRepo>(relaxed = true)
    private val notifications = mockk<WatchAlertNotifications>(relaxed = true)

    private val watches = MutableStateFlow<List<Watch>>(emptyList())
    private val serverClock = ServerClock(object : MonotonicClock {
        override fun elapsed(): Long = 0L
    }).apply { noteServerTime(NOW_MILLIS) }

    private val settings = mockk<WatchSettings>()
    private val lastCheck = mockk<DataStoreValue<Instant>>()
    private val lastCleanup = mockk<DataStoreValue<Instant>>()
    private lateinit var monitor: WatchMonitor

    /** List backed history, so the order of read and insert is the real one. */
    private val history = mutableListOf<WatchCheck>()

    @Before
    fun setup() {
        history.clear()

        every { settings.lastCheck } returns lastCheck
        every { lastCheck.flow } returns MutableStateFlow(Instant.EPOCH)
        coEvery { lastCheck.update(any()) } returns DataStoreValue.Updated(Instant.EPOCH, Instant.EPOCH)
        every { settings.lastCleanup } returns lastCleanup
        // A recent cleanup keeps the daily maintenance out of these checks
        every { lastCleanup.flow } returns MutableStateFlow(Instant.now())
        coEvery { lastCleanup.update(any()) } returns DataStoreValue.Updated(Instant.now(), Instant.now())

        every { watchRepo.watches } returns watches
        coEvery { historyRepo.getLastCheck(any()) } answers {
            history.lastOrNull { it.watchId == firstArg<WatchId>() }
        }
        coEvery { historyRepo.addCheck(any(), any(), any(), any()) } answers {
            val watchId = firstArg<WatchId>()
            val operationId = arg<String?>(3)
            if (operationId != null && history.any { it.watchId == watchId && it.operationId == operationId }) {
                false
            } else {
                history.add(
                    WatchCheck(
                        watchId = watchId,
                        checkAt = serverClock.now(),
                        aircraftCount = secondArg(),
                        seenHexes = arg<Set<String>?>(2) ?: emptySet(),
                    ).withOperation(operationId)
                )
                true
            }
        }
        coEvery { operationStore.pending(any(), any()) } returns emptyList()

        monitor = WatchMonitor(
            settings = settings,
            watchRepo = watchRepo,
            watchDb = watchDb,
            historyRepo = historyRepo,
            aircraftRepo = aircraftRepo,
            aircraftDatabase = aircraftDatabase,
            operationStore = operationStore,
            serverClock = serverClock,
            accessRepo = accessRepo,
            notifications = notifications,
        )
    }

    private val operationIds = mutableMapOf<WatchCheck, String?>()
    private fun WatchCheck.withOperation(operationId: String?) = also { operationIds[it] = operationId }
    private val WatchCheck.operationId: String? get() = operationIds[this]

    private fun aircraftWatch(id: String, hex: String, notify: Boolean = true) = AircraftWatch(
        BaseWatchEntity(id = id, watchType = AircraftWatchEntity.TYPE_KEY, notificationEnabled = notify),
        AircraftWatchEntity(id = id, hexCode = hex),
    )

    private fun flightWatch(id: String, callsign: String) = FlightWatch(
        BaseWatchEntity(id = id, watchType = FlightWatchEntity.TYPE_KEY),
        FlightWatchEntity(id = id, callsign = callsign),
    )

    private fun squawkWatch(id: String, code: String) = SquawkWatch(
        BaseWatchEntity(id = id, watchType = SquawkWatchEntity.TYPE_KEY),
        SquawkWatchEntity(id = id, code = code),
    )

    private fun answerWith(vararg outcomes: WatchOutcome, operationId: String = OPERATION_ID) {
        coEvery { aircraftRepo.checkWatches(any()) } returns listOf(batch(outcomes.toList(), operationId))
    }

    private fun batch(outcomes: List<WatchOutcome>, operationId: String = OPERATION_ID) = BatchResult(
        operationId = operationId,
        replayed = false,
        snapshot = null,
        outcomes = outcomes,
        usage = USAGE,
    )

    private fun matched(vararg aircraft: Aircraft) = WatchOutcome.Matched(
        aircraft = aircraft.toList(),
        totalMatching = aircraft.size,
        capped = false,
        expiresAt = null,
    )

    @Test
    fun `outcomes correlate to the submitted order across watch types`() {
        runTest {
            watches.value = listOf(
                aircraftWatch("w1", "3C65A3"),
                flightWatch("w2", "DLH453"),
                squawkWatch("w3", "7700"),
            )
            answerWith(
                matched(FakeAircraft(hex = "3C65A3")),
                WatchOutcome.Absent(expiresAt = null),
                WatchOutcome.Inconclusive("stale_observations"),
            )

            val summary = monitor.check(WatchMonitor.Trigger.MANUAL)

            val definitions = slot<List<AircraftRepo.Chunk<WatchDefinition>>>()
            coVerify { aircraftRepo.checkWatches(capture(definitions)) }
            definitions.captured.single().items shouldBe listOf(
                WatchDefinition(type = "hex", value = "3C65A3"),
                WatchDefinition(type = "callsign", value = "DLH453"),
                WatchDefinition(type = "squawk", value = "7700"),
            )
            definitions.captured.single().ownerIds shouldBe listOf("w1", "w2", "w3")

            history.single { it.watchId == "w1" }.aircraftCount shouldBe 1
            history.single { it.watchId == "w1" }.seenHexes shouldBe setOf("3C65A3")
            history.single { it.watchId == "w2" }.aircraftCount shouldBe 0
            history.none { it.watchId == "w3" } shouldBe true

            summary.evaluated shouldBe 2
            summary.inconclusive shouldBe 1
        }
    }

    @Test
    fun `an inconclusive outcome writes no history row`() {
        runTest {
            watches.value = listOf(aircraftWatch("w1", "3C65A3"))
            history.add(WatchCheck("w1", serverClock.now(), aircraftCount = 3))
            answerWith(WatchOutcome.Inconclusive("data_incomplete"))

            monitor.check(WatchMonitor.Trigger.PERIODIC)

            history.size shouldBe 1
            history.single().aircraftCount shouldBe 3
            coVerify {
                watchDb.updateLastCheck("w1", any(), WatchCheckOutcome.INCONCLUSIVE, "data_incomplete")
            }
        }
    }

    @Test
    fun `a restricted outcome is recorded without history`() {
        runTest {
            watches.value = listOf(squawkWatch("w1", "7700"))
            answerWith(WatchOutcome.Rejected("tier_restricted"))

            val summary = monitor.check(WatchMonitor.Trigger.PERIODIC)

            summary.restricted shouldBe 1
            history.isEmpty() shouldBe true
            coVerify { watchDb.updateLastCheck("w1", any(), WatchCheckOutcome.RESTRICTED, "tier_restricted") }
        }
    }

    @Test
    fun `an alert fires on the edge from zero to some`() {
        runTest {
            watches.value = listOf(aircraftWatch("w1", "3C65A3"))
            history.add(WatchCheck("w1", serverClock.now(), aircraftCount = 0))
            answerWith(matched(FakeAircraft(hex = "3C65A3")))

            monitor.check(WatchMonitor.Trigger.PERIODIC)

            coVerify(exactly = 1) { notifications.alert(any(), any()) }
        }
    }

    @Test
    fun `no alert while the watch was already tracking aircraft`() {
        runTest {
            watches.value = listOf(aircraftWatch("w1", "3C65A3"))
            history.add(WatchCheck("w1", serverClock.now(), aircraftCount = 2))
            answerWith(matched(FakeAircraft(hex = "3C65A3")))

            monitor.check(WatchMonitor.Trigger.PERIODIC)

            coVerify(exactly = 0) { notifications.alert(any(), any()) }
        }
    }

    @Test
    fun `the first ever hit stays quiet`() {
        runTest {
            watches.value = listOf(aircraftWatch("w1", "3C65A3"))
            answerWith(matched(FakeAircraft(hex = "3C65A3")))

            monitor.check(WatchMonitor.Trigger.PERIODIC)

            history.single().aircraftCount shouldBe 1
            coVerify(exactly = 0) { notifications.alert(any(), any()) }
        }
    }

    @Test
    fun `applying the same operation twice writes one row and one alert`() {
        runTest {
            watches.value = listOf(aircraftWatch("w1", "3C65A3"))
            history.add(WatchCheck("w1", serverClock.now(), aircraftCount = 0))
            answerWith(matched(FakeAircraft(hex = "3C65A3")))

            monitor.check(WatchMonitor.Trigger.PERIODIC)

            coEvery { operationStore.pending(any(), any()) } returns listOf(
                OperationStore.PendingOperation(
                    operationId = OPERATION_ID,
                    kind = OperationStore.Kind.WATCH,
                    requestJson = "{}",
                    ownerIds = listOf("w1"),
                    createdAt = serverClock.now(),
                    resultJson = "{}",
                )
            )
            coEvery { aircraftRepo.replayWatchOperation(any()) } returns
                    batch(listOf(matched(FakeAircraft(hex = "3C65A3"))))
            coEvery { aircraftRepo.checkWatches(any()) } returns emptyList()

            monitor.check(WatchMonitor.Trigger.APP_START)

            history.count { it.watchId == "w1" && it.aircraftCount == 1 } shouldBe 1
            coVerify(exactly = 1) { notifications.alert(any(), any()) }
        }
    }

    @Test
    fun `more than one batch worth of watches is split`() {
        runTest {
            watches.value = (1..150).map { aircraftWatch("w$it", "3C65%02X".format(it)) }
            coEvery { aircraftRepo.checkWatches(any()) } answers {
                val chunk = firstArg<List<AircraftRepo.Chunk<WatchDefinition>>>().single()
                listOf(batch(chunk.items.map { WatchOutcome.Absent(expiresAt = null) }))
            }

            monitor.check(WatchMonitor.Trigger.PERIODIC)

            coVerify(exactly = 2) { aircraftRepo.checkWatches(any()) }
        }
    }

    @Test
    fun `a whole request failure marks every watch as failed`() {
        runTest {
            watches.value = listOf(aircraftWatch("w1", "3C65A3"), flightWatch("w2", "DLH453"))
            coEvery { aircraftRepo.checkWatches(any()) } throws IOException("offline")

            shouldThrow<IOException> { monitor.check(WatchMonitor.Trigger.PERIODIC) }

            coVerify { watchDb.updateLastCheck("w1", any(), WatchCheckOutcome.FAILED, any()) }
            coVerify { watchDb.updateLastCheck("w2", any(), WatchCheckOutcome.FAILED, any()) }
            history.isEmpty() shouldBe true
        }
    }

    @Test
    fun `an oversized result is asked for in halves`() {
        runTest {
            watches.value = (1..4).map { aircraftWatch("w$it", "3C65A$it") }
            val sent = mutableListOf<AircraftRepo.Chunk<WatchDefinition>>()
            coEvery { aircraftRepo.checkWatches(any()) } answers {
                val chunk = firstArg<List<AircraftRepo.Chunk<WatchDefinition>>>().single()
                sent.add(chunk)
                if (sent.size == 1) throw OperationFailedException(ServerCodes.RESULT_TOO_LARGE)
                listOf(batch(chunk.items.map { WatchOutcome.Absent(expiresAt = null) }, chunk.operationId!!))
            }

            monitor.check(WatchMonitor.Trigger.PERIODIC)

            sent.size shouldBe 3
            sent[1].ownerIds shouldBe listOf("w1", "w2")
            sent[2].ownerIds shouldBe listOf("w3", "w4")
            sent.mapNotNull { it.operationId }.toSet().size shouldBe 3
        }
    }

    @Test
    fun `a rejected watch refreshes the access policy`() {
        runTest {
            watches.value = listOf(squawkWatch("w1", "7700"), flightWatch("w2", "DLH453"))
            answerWith(
                WatchOutcome.Rejected("tier_restricted"),
                WatchOutcome.Rejected("daily_allowance_exhausted"),
            )

            monitor.check(WatchMonitor.Trigger.PERIODIC)

            coVerify(exactly = 1) { accessRepo.refreshThrottled(any()) }
        }
    }

    @Test
    fun `a pending row for a deleted watch is ignored`() {
        runTest {
            watches.value = listOf(aircraftWatch("w1", "3C65A3"))
            coEvery { operationStore.pending(any(), any()) } returns listOf(
                OperationStore.PendingOperation(
                    operationId = OPERATION_ID,
                    kind = OperationStore.Kind.WATCH,
                    requestJson = "{}",
                    ownerIds = listOf("gone"),
                    createdAt = serverClock.now(),
                    resultJson = "{}",
                )
            )
            coEvery { aircraftRepo.replayWatchOperation(any()) } returns
                    batch(listOf(matched(FakeAircraft(hex = "3C65A3"))))
            answerWith(WatchOutcome.Absent(expiresAt = null), operationId = "5a5c6b2e-3b0a-4a2e-9a0f-000000000002")

            val summary = monitor.check(WatchMonitor.Trigger.APP_START)

            history.none { it.watchId == "gone" } shouldBe true
            history.single().watchId shouldBe "w1"
            summary.evaluated shouldBe 1
            coVerify(exactly = 0) { notifications.alert(any(), any()) }
        }
    }

    @Test
    fun `a changed watch set gets a new operation id`() {
        runTest {
            watches.value = listOf(aircraftWatch("w1", "3C65A3"))
            val operationIds = mutableListOf<String?>()
            coEvery { aircraftRepo.checkWatches(any()) } answers {
                val chunk = firstArg<List<AircraftRepo.Chunk<WatchDefinition>>>().single()
                operationIds.add(chunk.operationId)
                listOf(batch(chunk.items.map { WatchOutcome.Absent(expiresAt = null) }, chunk.operationId!!))
            }

            monitor.check(WatchMonitor.Trigger.PERIODIC)
            watches.value = watches.value + flightWatch("w2", "DLH453")
            monitor.check(WatchMonitor.Trigger.PERIODIC)

            operationIds.size shouldBe 2
            operationIds[0] shouldNotBe operationIds[1]
        }
    }

    companion object {
        private const val NOW_MILLIS = 1_710_000_000_000L
        private const val OPERATION_ID = "5a5c6b2e-3b0a-4a2e-9a0f-000000000001"
        private val USAGE = UsageUpdate(
            scope = "principal",
            bucket = "WATCH",
            resetsAt = 1_710_028_800_000L,
            allowance = Allowance(limit = 1000, used = 1, reserved = 0, remaining = 999),
        )
    }
}
