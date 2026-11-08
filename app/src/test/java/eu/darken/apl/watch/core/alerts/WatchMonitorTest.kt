package eu.darken.apl.watch.core.alerts

import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.compose.preview.FakeAircraft
import eu.darken.apl.common.datastore.DataStoreValue
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.main.core.db.AircraftDatabase
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.session.SessionRevokedException
import eu.darken.apl.watch.core.WatchRepo
import eu.darken.apl.watch.core.WatchSettings
import eu.darken.apl.watch.core.db.WatchDatabase
import eu.darken.apl.watch.core.db.types.AircraftWatchEntity
import eu.darken.apl.watch.core.db.types.BaseWatchEntity
import eu.darken.apl.watch.core.db.types.FlightWatchEntity
import eu.darken.apl.watch.core.server.WatchListSync
import eu.darken.apl.watch.core.server.WatchListSyncScheduler
import eu.darken.apl.watch.core.server.WatchServerMode
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.FlightWatch
import eu.darken.apl.watch.core.types.Watch
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CancellationException
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
    private val aircraftRepo = mockk<AircraftRepo>()
    private val aircraftDatabase = mockk<AircraftDatabase>(relaxed = true)
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

    /** Backs [lastCleanup], so a cleanup that ran is seen by the next check. */
    private val cleanedUpAt = MutableStateFlow(Instant.now())
    private lateinit var monitor: WatchMonitor

    @Before
    fun setup() {
        every { settings.lastCheck } returns lastCheck
        every { lastCheck.flow } returns MutableStateFlow(Instant.EPOCH)
        coEvery { lastCheck.update(any()) } returns DataStoreValue.Updated(Instant.EPOCH, Instant.EPOCH)
        every { settings.lastCleanup } returns lastCleanup
        // A recent cleanup keeps the daily maintenance out of the checks that are not about it
        every { lastCleanup.flow } returns cleanedUpAt
        coEvery { lastCleanup.update(any()) } answers {
            val old = cleanedUpAt.value
            cleanedUpAt.value = firstArg<(Instant) -> Instant?>()(old)!!
            DataStoreValue.Updated(old, cleanedUpAt.value)
        }

        every { watchRepo.watches } returns watches
        every { aircraftRepo.cache } returns flowOf(emptyMap())
        coEvery { serverMode.current() } returns WatchServerMode.State.OFF
        coEvery { watchListSync.run(any(), any()) } returns WatchListSync.Result.Done(moreWork = false)

        monitor = WatchMonitor(
            settings = settings,
            watchRepo = watchRepo,
            watchDb = watchDb,
            aircraftRepo = aircraftRepo,
            aircraftDatabase = aircraftDatabase,
            serverClock = serverClock,
            serverMode = serverMode,
            watchListSync = watchListSync,
            syncScheduler = syncScheduler,
        )
    }

    private fun serverModeActive() {
        coEvery { serverMode.current() } returns WatchServerMode.State(access = null, isActive = true)
    }

    private fun cleanupDue() {
        cleanedUpAt.value = Instant.EPOCH
    }

    private fun aircraftWatch(id: String, hex: String) = AircraftWatch(
        BaseWatchEntity(id = id, watchType = AircraftWatchEntity.TYPE_KEY),
        AircraftWatchEntity(id = id, hexCode = hex),
    )

    private fun flightWatch(id: String, callsign: String) = FlightWatch(
        BaseWatchEntity(id = id, watchType = FlightWatchEntity.TYPE_KEY),
        FlightWatchEntity(id = id, callsign = callsign),
    )

    @Test
    fun `without server mode the watch list is not synced`() {
        runTest {
            monitor.check(WatchMonitor.Trigger.PERIODIC)

            coVerify(exactly = 0) { watchListSync.run(any(), any()) }
            verify(exactly = 0) { syncScheduler.afterRun(any()) }
            coVerify(exactly = 0) { lastCheck.update(any()) }
        }
    }

    @Test
    fun `only a periodic check asks the sync for the periodic refresh`() {
        runTest {
            serverModeActive()

            monitor.check(WatchMonitor.Trigger.PERIODIC)
            coVerify(exactly = 1) { watchListSync.run(any(), periodic = true) }

            WatchMonitor.Trigger.entries.filter { it != WatchMonitor.Trigger.PERIODIC }.forEach {
                monitor.check(it)
            }
            coVerify(exactly = 1) { watchListSync.run(any(), periodic = true) }
            coVerify(exactly = WatchMonitor.Trigger.entries.size - 1) { watchListSync.run(any(), periodic = false) }
        }
    }

    @Test
    fun `what the sync leaves to do is queued`() {
        runTest {
            serverModeActive()
            val result = WatchListSync.Result.Done(moreWork = true)
            coEvery { watchListSync.run(any(), any()) } returns result

            monitor.check(WatchMonitor.Trigger.PERIODIC)

            verify(exactly = 1) { syncScheduler.afterRun(result) }
        }
    }

    @Test
    fun `a network failure of the sync is thrown`() {
        runTest {
            serverModeActive()
            coEvery { watchListSync.run(any(), any()) } throws IOException("offline")

            shouldThrow<IOException> { monitor.check(WatchMonitor.Trigger.PERIODIC) }

            verify(exactly = 0) { syncScheduler.afterRun(any()) }
        }
    }

    @Test
    fun `a retryable server error of the sync is thrown`() {
        runTest {
            serverModeActive()
            val error = ServerApiException(code = "operation_in_progress", status = 409, retryAfterSeconds = 1)
            coEvery { watchListSync.run(any(), any()) } throws error

            shouldThrow<ServerApiException> { monitor.check(WatchMonitor.Trigger.PERIODIC) } shouldBe error
        }
    }

    @Test
    fun `a revoked installation is thrown`() {
        runTest {
            serverModeActive()
            coEvery { watchListSync.run(any(), any()) } throws SessionRevokedException()

            shouldThrow<SessionRevokedException> { monitor.check(WatchMonitor.Trigger.PERIODIC) }
        }
    }

    @Test
    fun `cancellation of the sync propagates`() {
        runTest {
            serverModeActive()
            coEvery { watchListSync.run(any(), any()) } throws CancellationException("stopped")

            shouldThrow<CancellationException> { monitor.check(WatchMonitor.Trigger.PERIODIC) }
        }
    }

    @Test
    fun `the last check is recorded after a successful sync`() {
        runTest {
            serverModeActive()

            monitor.check(WatchMonitor.Trigger.MANUAL)

            val written = slot<(Instant) -> Instant?>()
            coVerify(exactly = 1) { lastCheck.update(capture(written)) }
            written.captured(Instant.EPOCH) shouldBe serverClock.now()
        }
    }

    @Test
    fun `the last check is not recorded when the sync fails or does not run`() {
        runTest {
            serverModeActive()
            coEvery { watchListSync.run(any(), any()) } throws IOException("offline")
            shouldThrow<IOException> { monitor.check(WatchMonitor.Trigger.MANUAL) }

            coEvery { watchListSync.run(any(), any()) } returns WatchListSync.Result.Inactive
            monitor.check(WatchMonitor.Trigger.MANUAL)

            coEvery { serverMode.current() } returns WatchServerMode.State.OFF
            monitor.check(WatchMonitor.Trigger.MANUAL)

            coVerify(exactly = 0) { lastCheck.update(any()) }
        }
    }

    @Test
    fun `the daily cleanup backfills watched registrations before the cache is evicted`() {
        runTest {
            cleanupDue()
            every { aircraftRepo.cache } returns flowOf(
                mapOf(
                    "3C65A3" to FakeAircraft(hex = "3C65A3", registration = "D-AIMA"),
                    "3C6752" to FakeAircraft(hex = "3C6752", registration = "D-AIMB", callsign = "DLH453"),
                )
            )
            watches.value = listOf(aircraftWatch("w1", "3c65a3"), flightWatch("w2", "DLH453"))

            monitor.check(WatchMonitor.Trigger.MANUAL)

            coVerifyOrder {
                watchDb.backfillRegistrations(mapOf("w1" to "D-AIMA"))
                aircraftDatabase.evict(any(), any())
            }
        }
    }

    @Test
    fun `the daily cleanup runs once per day`() {
        runTest {
            cleanupDue()

            monitor.check(WatchMonitor.Trigger.PERIODIC)
            monitor.check(WatchMonitor.Trigger.MANUAL)

            coVerify(exactly = 1) { watchDb.backfillRegistrations(any()) }
            coVerify(exactly = 1) { aircraftDatabase.evict(any(), any()) }
        }
    }

    @Test
    fun `the daily cleanup also runs when the sync fails`() {
        runTest {
            cleanupDue()
            serverModeActive()
            coEvery { watchListSync.run(any(), any()) } throws IOException("offline")

            shouldThrow<IOException> { monitor.check(WatchMonitor.Trigger.PERIODIC) }

            coVerify(exactly = 1) { watchDb.backfillRegistrations(any()) }
            coVerify(exactly = 1) { aircraftDatabase.evict(any(), any()) }
        }
    }

    companion object {
        private const val NOW_MILLIS = 1_710_000_000_000L
    }
}
