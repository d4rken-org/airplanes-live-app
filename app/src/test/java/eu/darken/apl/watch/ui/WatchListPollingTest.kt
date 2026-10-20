package eu.darken.apl.watch.ui

import eu.darken.apl.common.WebpageTool
import eu.darken.apl.common.location.LocationManager2
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.server.api.Allowance
import eu.darken.apl.server.api.RequestLimits
import eu.darken.apl.server.api.RequestRate
import eu.darken.apl.server.api.TierPolicy
import eu.darken.apl.server.api.Usage
import eu.darken.apl.upgrade.UpgradeRepo
import eu.darken.apl.watch.core.WatchLimit
import eu.darken.apl.watch.core.WatchRepo
import eu.darken.apl.watch.core.WatchSettings
import eu.darken.apl.watch.core.alerts.WatchMonitor
import eu.darken.apl.watch.core.history.WatchCheck
import eu.darken.apl.watch.core.history.WatchHistoryRepo
import eu.darken.apl.watch.core.server.WatchServerMode
import eu.darken.apl.watch.core.types.Watch
import io.kotest.assertions.withClue
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual as shouldBeAtLeast
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.coroutine.TestDispatcherProvider
import java.time.Instant

/**
 * The foreground poll spends one server evaluation per watch out of a shared allowance, so a check
 * that runs longer than the interval must cost ticks, not defer them.
 */
class WatchListPollingTest : BaseTest() {

    private val watchRepo = mockk<WatchRepo>(relaxed = true)
    private val watchMonitor = mockk<WatchMonitor>(relaxed = true)
    private val webpageTool = mockk<WebpageTool>(relaxed = true)
    private val locationManager2 = mockk<LocationManager2>(relaxed = true)
    private val aircraftRepo = mockk<AircraftRepo>(relaxed = true)
    private val historyRepo = mockk<WatchHistoryRepo>(relaxed = true)
    private val watchSettings = mockk<WatchSettings>(relaxed = true)
    private val accessRepo = mockk<AccessRepo>(relaxed = true)
    private val upgradeRepo = mockk<UpgradeRepo>(relaxed = true)
    private val serverMode = mockk<WatchServerMode>()
    private val serverClock = ServerClock(object : MonotonicClock {
        override fun elapsed(): Long = 0L
    })

    private val isRefreshing = MutableStateFlow(false)

    private object ProInfo : UpgradeRepo.Info {
        override val type = UpgradeRepo.Type.FOSS
        override val isPro = true
        override val isSettled = true
        override val source = UpgradeRepo.Source.FEEDER
        override val error: Throwable? = null
    }

    @BeforeEach
    fun setup() {
        // viewModelScope is built on the main dispatcher
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { watchRepo.isRefreshing } returns isRefreshing
        every { watchRepo.watches } returns MutableStateFlow(emptyList<Watch>())
        every { watchRepo.status } returns MutableStateFlow(emptyList<Watch.Status>())
        every { watchRepo.limit } returns MutableStateFlow(WatchLimit(count = 0, max = null))
        every { historyRepo.firehose } returns emptyFlow<WatchCheck?>()
        every { locationManager2.state } returns MutableStateFlow(LocationManager2.State.Waiting)
        every { accessRepo.state } returns MutableStateFlow<AccessState?>(null)
        every { upgradeRepo.upgradeInfo } returns MutableStateFlow<UpgradeRepo.Info>(ProInfo)
        every { serverMode.state } returns MutableStateFlow(WatchServerMode.State.OFF)
        coEvery { serverMode.current() } returns WatchServerMode.State.OFF
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = WatchListViewModel(
        dispatcherProvider = TestDispatcherProvider(),
        watchRepo = watchRepo,
        watchMonitor = watchMonitor,
        webpageTool = webpageTool,
        locationManager2 = locationManager2,
        aircraftRepo = aircraftRepo,
        historyRepo = historyRepo,
        watchSettings = watchSettings,
        accessRepo = accessRepo,
        upgradeRepo = upgradeRepo,
        serverClock = serverClock,
        serverMode = serverMode,
    )

    private fun accessWithWatchAllowance(remaining: Int) = AccessState(
        installationId = "installation",
        fetchedAt = Instant.EPOCH,
        tier = AccessState.Tier.FEEDER,
        restricted = false,
        allowanceScope = "principal",
        limits = TierPolicy(
            viewingIntervalSeconds = 5, viewingBurst = 3, viewingPerDay = 25000, searchesPerDay = 25,
            watchesPerDay = 1000, maxRadiusNm = 25, watchTypes = listOf("hex", "callsign"),
        ),
        usage = Usage(
            resetsAt = 3_600_000,
            viewing = Allowance(25000, 0, 0, 25000),
            search = Allowance(25, 0, 0, 25),
            watch = Allowance(1000, 1000 - remaining, 0, remaining),
        ),
        installationRequests = RequestLimits(
            viewing = RequestRate(perSecond = 0.2, burst = 3),
            search = RequestRate(perSecond = 1.0, burst = 3),
            watch = RequestRate(perSecond = 1.0, burst = 3),
            concurrency = 3,
        ),
    )

    private fun TestScope.pollOnce(viewModel: WatchListViewModel) {
        val polling = launch { viewModel.pollWhileVisible() }
        advanceTimeBy(WatchSettings.PRO_FOREGROUND_CHECK_INTERVAL.toMillis() + 1)
        polling.cancel()
    }

    @Test
    fun `in server mode a used up allowance still syncs the watch list`() = runTest {
        every { accessRepo.state } returns MutableStateFlow<AccessState?>(accessWithWatchAllowance(remaining = 0))
        val active = WatchServerMode.State(access = null, isActive = true)
        every { serverMode.state } returns MutableStateFlow(active)
        coEvery { serverMode.current() } returns active

        pollOnce(createViewModel())

        coVerify(exactly = 1) { watchMonitor.check(WatchMonitor.Trigger.FOREGROUND, legacy = false) }
    }

    @Test
    fun `in server mode an allowance left checks the app's watches as well`() = runTest {
        every { accessRepo.state } returns MutableStateFlow<AccessState?>(accessWithWatchAllowance(remaining = 10))
        val active = WatchServerMode.State(access = null, isActive = true)
        every { serverMode.state } returns MutableStateFlow(active)
        coEvery { serverMode.current() } returns active

        pollOnce(createViewModel())

        coVerify(exactly = 1) { watchMonitor.check(WatchMonitor.Trigger.FOREGROUND, legacy = true) }
    }

    @Test
    fun `without server mode a used up allowance skips the tick`() = runTest {
        every { accessRepo.state } returns MutableStateFlow<AccessState?>(accessWithWatchAllowance(remaining = 0))

        pollOnce(createViewModel())

        coVerify(exactly = 0) { watchMonitor.check(any(), any()) }
    }

    @Test
    fun `a check that outlasts the poll interval must not be followed by catch-up checks`() = runTest {
        val clock = testScheduler
        val interval = WatchSettings.PRO_FOREGROUND_CHECK_INTERVAL.toMillis()
        val window = 10 * interval
        val slowCheck = 5 * interval
        val fastCheck = 1_000L

        val startedAt = mutableListOf<Long>()
        coEvery { watchMonitor.check(WatchMonitor.Trigger.FOREGROUND) } coAnswers {
            startedAt.add(clock.currentTime)
            delay(if (startedAt.size == 1) slowCheck else fastCheck)
            WatchMonitor.CheckSummary()
        }

        val viewModel = createViewModel()
        val polling = launch { viewModel.pollWhileVisible() }
        advanceTimeBy(window)
        polling.cancel()

        withClue("the foreground poll never ran often enough to compare gaps") {
            startedAt.size shouldBeAtLeast 2
        }

        val gaps = startedAt.zipWithNext { previous, next -> next - previous }
        withClue(
            "" + startedAt.size + " foreground checks ran in a " + window + "ms window with a " +
                interval + "ms poll interval.\n" +
                "Checks started at (ms of virtual time): " + startedAt + "\n" +
                "Gaps between consecutive checks (ms): " + gaps + "\n" +
                "The first check ran for " + slowCheck + "ms, so the ticks that fell due while it " +
                "was running must be dropped, not replayed back-to-back once it finishes."
        ) {
            gaps.min() shouldBeGreaterThanOrEqual interval
        }
    }
}
