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
import eu.darken.apl.watch.core.WatchRequestGate
import eu.darken.apl.watch.core.WatchSettings
import eu.darken.apl.watch.core.db.WatchDatabase
import eu.darken.apl.watch.core.db.server.WatchServerStateEntity
import eu.darken.apl.watch.core.db.types.AircraftWatchEntity
import eu.darken.apl.watch.core.db.types.BaseWatchEntity
import eu.darken.apl.watch.core.db.types.FlightWatchEntity
import eu.darken.apl.watch.core.db.types.SquawkWatchEntity
import eu.darken.apl.watch.core.history.WatchCheck
import eu.darken.apl.watch.core.history.WatchHistoryRepo
import eu.darken.apl.watch.core.server.WatchListSync
import eu.darken.apl.watch.core.server.WatchListSyncScheduler
import eu.darken.apl.watch.core.server.WatchServerMode
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.FlightWatch
import eu.darken.apl.watch.core.types.SquawkWatch
import eu.darken.apl.watch.core.types.Watch
import eu.darken.apl.watch.core.types.WatchCheckOutcome
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
    private val serverMode = mockk<WatchServerMode>()
    private val watchListSync = mockk<WatchListSync>()
    private val syncScheduler = mockk<WatchListSyncScheduler>(relaxed = true)

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
        // Mirrors the transaction: the revision decides, then the operation, then the previous check
        coEvery { watchDb.applyCheck(any(), any(), any(), any(), any(), any(), any(), any(), any()) } answers {
            val watchId = firstArg<WatchId>()
            val revision = secondArg<Long>()
            val operationId = arg<String?>(6)
            when {
                currentRevision(watchId) != revision -> WatchDatabase.CheckApplication.Stale
                operationId != null && history.any { it.watchId == watchId && it.operationId == operationId } ->
                    WatchDatabase.CheckApplication.Duplicate
                else -> {
                    val previous = history.lastOrNull { it.watchId == watchId }
                    history.add(
                        WatchCheck(
                            watchId = watchId,
                            checkAt = serverClock.now(),
                            aircraftCount = arg(4),
                            seenHexes = arg<Set<String>?>(5) ?: emptySet(),
                        ).withOperation(operationId)
                    )
                    WatchDatabase.CheckApplication.Applied(previous?.aircraftCount)
                }
            }
        }
        coEvery { watchDb.updateLastCheck(any(), any(), any(), any(), any(), any()) } answers {
            currentRevision(firstArg()) == secondArg<Long>()
        }
        coEvery { operationStore.pending(any(), any()) } returns emptyList()
        coEvery { serverMode.current() } returns WatchServerMode.State.OFF
        coEvery { watchListSync.run(any(), any()) } returns WatchListSync.Result.Done(moreWork = false)

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
            gate = WatchRequestGate(),
            serverMode = serverMode,
            watchListSync = watchListSync,
            syncScheduler = syncScheduler,
        )
    }

    /** Server mode where the server holds [held] and nothing else is known about the other watches. */
    private fun serverModeHolding(vararg held: String) {
        coEvery { serverMode.current() } returns WatchServerMode.State(
            access = null,
            isActive = true,
            states = held.associateWith { id ->
                WatchServerStateEntity(
                    watchId = id,
                    installationId = "installation",
                    serverWatchId = "server-$id",
                    uploadState = WatchServerStateEntity.UPLOAD_SYNCED,
                    updatedAt = Instant.EPOCH,
                )
            },
        )
    }

    private fun currentRevision(watchId: WatchId): Long? = watches.value.firstOrNull { it.id == watchId }?.definitionRevision

    private val operationIds = mutableMapOf<WatchCheck, String?>()
    private fun WatchCheck.withOperation(operationId: String?) = also { operationIds[it] = operationId }
    private val WatchCheck.operationId: String? get() = operationIds[this]

    private fun aircraftWatch(id: String, hex: String, notify: Boolean = true, revision: Long = 0) = AircraftWatch(
        BaseWatchEntity(
            id = id,
            watchType = AircraftWatchEntity.TYPE_KEY,
            notificationEnabled = notify,
            definitionRevision = revision,
        ),
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
    fun `a match tells an aircraft watch its registration, other watches none`() {
        runTest {
            watches.value = listOf(aircraftWatch("w1", "3C65A3"), flightWatch("w2", "DLH453"))
            answerWith(
                matched(FakeAircraft(hex = "3C65A3", registration = "D-AIMA")),
                matched(FakeAircraft(hex = "3C6752", registration = "D-AIMB", callsign = "DLH453")),
            )

            monitor.check(WatchMonitor.Trigger.MANUAL)

            coVerify { watchDb.applyCheck("w1", any(), any(), any(), any(), any(), any(), "D-AIMA", any()) }
            coVerify { watchDb.applyCheck("w2", any(), any(), any(), any(), any(), any(), null, any()) }
        }
    }

    @Test
    fun `the daily cleanup backfills watched registrations before the cache is evicted`() {
        runTest {
            every { lastCleanup.flow } returns MutableStateFlow(Instant.EPOCH)
            coEvery { historyRepo.cleanupOldChecks() } just Runs
            every { aircraftRepo.cache } returns flowOf(
                mapOf(
                    "3C65A3" to FakeAircraft(hex = "3C65A3", registration = "D-AIMA"),
                    "3C6752" to FakeAircraft(hex = "3C6752", registration = "D-AIMB", callsign = "DLH453"),
                )
            )
            watches.value = listOf(aircraftWatch("w1", "3c65a3"), flightWatch("w2", "DLH453"))
            answerWith(WatchOutcome.Absent(expiresAt = null), WatchOutcome.Absent(expiresAt = null))

            monitor.check(WatchMonitor.Trigger.MANUAL)

            coVerifyOrder {
                watchDb.backfillRegistrations(mapOf("w1" to "D-AIMA"))
                aircraftDatabase.evict(any(), any())
            }
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
                watchDb.updateLastCheck("w1", 0, any(), WatchCheckOutcome.INCONCLUSIVE, "data_incomplete", any())
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
            coVerify { watchDb.updateLastCheck("w1", 0, any(), WatchCheckOutcome.RESTRICTED, "tier_restricted", any()) }
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
                    ownerRevisions = listOf(0),
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

            coVerify { watchDb.updateLastCheck("w1", 0, any(), WatchCheckOutcome.FAILED, any(), any()) }
            coVerify { watchDb.updateLastCheck("w2", 0, any(), WatchCheckOutcome.FAILED, any(), any()) }
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
                    ownerRevisions = listOf(0),
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

    @Test
    fun `a result for an area the watch was moved away from writes nothing`() {
        runTest {
            watches.value = listOf(aircraftWatch("w1", "3C65A3"))
            coEvery { aircraftRepo.checkWatches(any()) } answers {
                // The edit lands while the request is out
                watches.value = listOf(aircraftWatch("w1", "3C65A3", revision = 1))
                listOf(batch(listOf(matched(FakeAircraft(hex = "3C65A3")))))
            }

            val summary = monitor.check(WatchMonitor.Trigger.PERIODIC)

            history.isEmpty() shouldBe true
            summary.evaluated shouldBe 0
            coVerify(exactly = 0) { notifications.alert(any(), any()) }
        }
    }

    @Test
    fun `a failure for an area the watch was moved away from leaves its status alone`() {
        runTest {
            watches.value = listOf(aircraftWatch("w1", "3C65A3"))
            coEvery { aircraftRepo.checkWatches(any()) } answers {
                watches.value = listOf(aircraftWatch("w1", "3C65A3", revision = 1))
                throw IOException("offline")
            }

            shouldThrow<IOException> { monitor.check(WatchMonitor.Trigger.PERIODIC) }

            // The failure is written for the revision the check was made for, which no longer matches
            coVerify { watchDb.updateLastCheck("w1", 0, any(), WatchCheckOutcome.FAILED, any(), any()) }
            currentRevision("w1") shouldBe 1
        }
    }

    @Test
    fun `a recovered result for a moved watch does not cover it`() {
        runTest {
            watches.value = listOf(aircraftWatch("w1", "3C65A3", revision = 1))
            coEvery { operationStore.pending(any(), any()) } returns listOf(
                OperationStore.PendingOperation(
                    operationId = OPERATION_ID,
                    kind = OperationStore.Kind.WATCH,
                    requestJson = "{}",
                    ownerIds = listOf("w1"),
                    createdAt = serverClock.now(),
                    resultJson = "{}",
                    ownerRevisions = listOf(0),
                )
            )
            coEvery { aircraftRepo.replayWatchOperation(any()) } returns
                    batch(listOf(matched(FakeAircraft(hex = "3C65A3"))))
            answerWith(WatchOutcome.Absent(expiresAt = null), operationId = SECOND_OPERATION_ID)

            monitor.check(WatchMonitor.Trigger.APP_START)

            // The old area's answer is dropped, and the new area is evaluated in the same round
            history.single().aircraftCount shouldBe 0
            coVerify(exactly = 1) { aircraftRepo.checkWatches(any()) }
        }
    }

    @Test
    fun `a pending operation from before revisions is discarded and the watch checked again`() {
        runTest {
            watches.value = listOf(aircraftWatch("w1", "3C65A3"))
            coEvery { operationStore.pending(any(), any()) } returns listOf(
                OperationStore.PendingOperation(
                    operationId = OPERATION_ID,
                    kind = OperationStore.Kind.WATCH,
                    requestJson = "{}",
                    ownerIds = listOf("w1"),
                    createdAt = serverClock.now(),
                    resultJson = "{}",
                    ownerRevisions = null,
                )
            )
            answerWith(WatchOutcome.Absent(expiresAt = null), operationId = SECOND_OPERATION_ID)

            monitor.check(WatchMonitor.Trigger.APP_START)

            coVerify(exactly = 0) { aircraftRepo.replayWatchOperation(any()) }
            coVerify { operationStore.complete(OPERATION_ID) }
            history.single().aircraftCount shouldBe 0
        }
    }

    @Test
    fun `a duplicate answer leaves the status alone`() {
        runTest {
            watches.value = listOf(aircraftWatch("w1", "3C65A3"))
            history.add(WatchCheck("w1", serverClock.now(), aircraftCount = 1).withOperation(OPERATION_ID))
            answerWith(matched(FakeAircraft(hex = "3C65A3")))

            monitor.check(WatchMonitor.Trigger.PERIODIC)

            history.size shouldBe 1
            coVerify(exactly = 0) { notifications.alert(any(), any()) }
        }
    }

    @Test
    fun `each chunk carries the revisions it was built from`() {
        runTest {
            watches.value = listOf(aircraftWatch("w1", "3C65A3", revision = 4), flightWatch("w2", "DLH453"))
            answerWith(WatchOutcome.Absent(expiresAt = null), WatchOutcome.Absent(expiresAt = null))

            monitor.check(WatchMonitor.Trigger.PERIODIC)

            val chunks = slot<List<AircraftRepo.Chunk<WatchDefinition>>>()
            coVerify { aircraftRepo.checkWatches(capture(chunks)) }
            chunks.captured.single().ownerRevisions shouldBe listOf(4L, 0L)
        }
    }

    @Test
    fun `without server mode the watch list is not synced`() {
        runTest {
            watches.value = listOf(aircraftWatch("w1", "3C65A3"))
            answerWith(WatchOutcome.Absent(expiresAt = null))

            monitor.check(WatchMonitor.Trigger.PERIODIC)

            coVerify(exactly = 0) { watchListSync.run(any(), any()) }
            coVerify(exactly = 1) { aircraftRepo.checkWatches(any()) }
        }
    }

    @Test
    fun `in server mode the list is synced first and only the watches the app still covers are checked`() {
        runTest {
            serverModeHolding("w1")
            watches.value = listOf(aircraftWatch("w1", "3C65A3"), flightWatch("w2", "DLH453"))
            answerWith(WatchOutcome.Absent(expiresAt = null))

            monitor.check(WatchMonitor.Trigger.PERIODIC)

            val chunks = slot<List<AircraftRepo.Chunk<WatchDefinition>>>()
            coVerifyOrder {
                watchListSync.run(any(), periodic = true)
                aircraftRepo.checkWatches(capture(chunks))
            }
            chunks.captured.single().ownerIds shouldBe listOf("w2")
            history.none { it.watchId == "w1" } shouldBe true
        }
    }

    @Test
    fun `only a periodic check asks the sync for the periodic refresh`() {
        runTest {
            serverModeHolding("w1")
            watches.value = listOf(aircraftWatch("w1", "3C65A3"))

            monitor.check(WatchMonitor.Trigger.MANUAL)

            coVerify { watchListSync.run(any(), periodic = false) }
            coVerify(exactly = 0) { aircraftRepo.checkWatches(any()) }
        }
    }

    @Test
    fun `what the sync leaves to do is queued`() {
        runTest {
            serverModeHolding()
            val result = WatchListSync.Result.Done(moreWork = true)
            coEvery { watchListSync.run(any(), any()) } returns result

            monitor.check(WatchMonitor.Trigger.PERIODIC)

            coVerify { syncScheduler.afterRun(result) }
        }
    }

    @Test
    fun `a pending operation for watches the server holds is completed without applying it`() {
        runTest {
            serverModeHolding("w1")
            watches.value = listOf(aircraftWatch("w1", "3C65A3"))
            history.add(WatchCheck("w1", serverClock.now(), aircraftCount = 0))
            coEvery { operationStore.pending(any(), any()) } returns listOf(
                OperationStore.PendingOperation(
                    operationId = OPERATION_ID,
                    kind = OperationStore.Kind.WATCH,
                    requestJson = "{}",
                    ownerIds = listOf("w1"),
                    createdAt = serverClock.now(),
                    resultJson = "{}",
                    ownerRevisions = listOf(0),
                )
            )
            coEvery { aircraftRepo.replayWatchOperation(any()) } returns
                    batch(listOf(matched(FakeAircraft(hex = "3C65A3"))))

            monitor.check(WatchMonitor.Trigger.APP_START)

            coVerify { operationStore.complete(OPERATION_ID) }
            coVerify(exactly = 0) { aircraftRepo.replayWatchOperation(any()) }
            coVerify(exactly = 0) { watchDb.applyCheck(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
            coVerify(exactly = 0) { notifications.alert(any(), any()) }
            history.single().aircraftCount shouldBe 0
        }
    }

    @Test
    fun `a pending operation shared with a held watch applies only to the others`() {
        runTest {
            serverModeHolding("w1")
            watches.value = listOf(aircraftWatch("w1", "3C65A3"), flightWatch("w2", "DLH453"))
            coEvery { operationStore.pending(any(), any()) } returns listOf(
                OperationStore.PendingOperation(
                    operationId = OPERATION_ID,
                    kind = OperationStore.Kind.WATCH,
                    requestJson = "{}",
                    ownerIds = listOf("w1", "w2"),
                    createdAt = serverClock.now(),
                    resultJson = null,
                    ownerRevisions = listOf(0, 0),
                )
            )
            coEvery { aircraftRepo.replayWatchOperation(any()) } returns batch(
                listOf(
                    matched(FakeAircraft(hex = "3C65A3")),
                    WatchOutcome.Absent(expiresAt = Instant.ofEpochMilli(NOW_MILLIS).plusSeconds(60)),
                )
            )
            coEvery { aircraftRepo.checkWatches(any()) } returns emptyList()

            monitor.check(WatchMonitor.Trigger.APP_START)

            history.none { it.watchId == "w1" } shouldBe true
            history.single { it.watchId == "w2" }.aircraftCount shouldBe 0
            coVerify { operationStore.complete(OPERATION_ID) }
            coVerify(exactly = 0) { aircraftRepo.checkWatches(any()) }
        }
    }

    @Test
    fun `a failing sync still lets the app check the watches it covers`() {
        runTest {
            serverModeHolding()
            coEvery { watchListSync.run(any(), any()) } throws IOException("offline")
            watches.value = listOf(aircraftWatch("w1", "3C65A3"))
            answerWith(WatchOutcome.Absent(expiresAt = null))

            val summary = monitor.check(WatchMonitor.Trigger.PERIODIC)

            summary.evaluated shouldBe 1
            history.single().watchId shouldBe "w1"
        }
    }

    @Test
    fun `a failure marks only the watches the app checked`() {
        runTest {
            serverModeHolding("w1")
            watches.value = listOf(aircraftWatch("w1", "3C65A3"), flightWatch("w2", "DLH453"))
            coEvery { aircraftRepo.checkWatches(any()) } throws IOException("offline")

            shouldThrow<IOException> { monitor.check(WatchMonitor.Trigger.PERIODIC) }

            coVerify { watchDb.updateLastCheck("w2", 0, any(), WatchCheckOutcome.FAILED, any(), any()) }
            coVerify(exactly = 0) { watchDb.updateLastCheck("w1", any(), any(), any(), any(), any()) }
        }
    }

    @Test
    fun `without the legacy part only the list is synced`() {
        runTest {
            serverModeHolding()
            watches.value = listOf(aircraftWatch("w1", "3C65A3"))

            monitor.check(WatchMonitor.Trigger.FOREGROUND, legacy = false)

            coVerify { watchListSync.run(any(), periodic = false) }
            coVerify(exactly = 0) { aircraftRepo.checkWatches(any()) }
            coVerify(exactly = 0) { operationStore.pending(any(), any()) }
        }
    }

    @Test
    fun diagF3InFlightResultForWatchThatBecameServerHeldIsNotApplied() {
        runTest {
            val unmapped = WatchServerMode.State(access = null, isActive = true, states = emptyMap())
            val held = WatchServerMode.State(
                access = null,
                isActive = true,
                states = mapOf(
                    "w1" to WatchServerStateEntity(
                        watchId = "w1",
                        installationId = "installation",
                        serverWatchId = "server-w1",
                        uploadState = WatchServerStateEntity.UPLOAD_SYNCED,
                        updatedAt = Instant.EPOCH,
                    )
                ),
            )
            var mode = unmapped
            coEvery { serverMode.current() } answers { mode }
            watches.value = listOf(aircraftWatch("w1", "3C65A3"))
            history.add(WatchCheck("w1", serverClock.now(), aircraftCount = 0))
            coEvery { aircraftRepo.checkWatches(any()) } answers {
                // The sync of another trigger handed the watch to the server while this check was out
                mode = held
                listOf(batch(listOf(matched(FakeAircraft(hex = "3C65A3")))))
            }

            monitor.check(WatchMonitor.Trigger.PERIODIC)

            coVerify(exactly = 1) { aircraftRepo.checkWatches(any()) }
            coVerify(exactly = 0) { notifications.alert(any(), any()) }
            coVerify(exactly = 0) { watchDb.applyCheck(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        }
    }

    @Test
    fun diagF3bLaterOwnerHandedToServerMidBatchIsNotApplied() {
        runTest {
            val unmapped = WatchServerMode.State(access = null, isActive = true, states = emptyMap())
            val w2Held = WatchServerMode.State(
                access = null,
                isActive = true,
                states = mapOf(
                    "w2" to WatchServerStateEntity(
                        watchId = "w2",
                        installationId = "installation",
                        serverWatchId = "server-w2",
                        uploadState = WatchServerStateEntity.UPLOAD_SYNCED,
                        updatedAt = Instant.EPOCH,
                    )
                ),
            )
            var mode = unmapped
            coEvery { serverMode.current() } answers { mode }
            watches.value = listOf(aircraftWatch("w1", "3C65A3"), aircraftWatch("w2", "3C6752"))
            history.add(WatchCheck("w1", serverClock.now(), aircraftCount = 0))
            history.add(WatchCheck("w2", serverClock.now(), aircraftCount = 0))
            answerWith(matched(FakeAircraft(hex = "3C65A3")), matched(FakeAircraft(hex = "3C6752")))
            coEvery { notifications.alert(match { it.id == "w1" }, any()) } answers {
                // Processing w1's result suspends here; a sync of another trigger hands w2 to the server
                mode = w2Held
            }

            monitor.check(WatchMonitor.Trigger.PERIODIC)

            coVerify(exactly = 1) { notifications.alert(match { it.id == "w1" }, any()) }
            coVerify(exactly = 0) { notifications.alert(match { it.id == "w2" }, any()) }
            coVerify(exactly = 0) { watchDb.applyCheck("w2", any(), any(), any(), any(), any(), any(), any(), any()) }
        }
    }

    companion object {
        private const val SECOND_OPERATION_ID = "5a5c6b2e-3b0a-4a2e-9a0f-000000000002"
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
