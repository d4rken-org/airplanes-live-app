package eu.darken.apl.watch.ui

import eu.darken.apl.common.WebpageTool
import eu.darken.apl.common.location.LocationManager2
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.upgrade.UpgradeRepo
import eu.darken.apl.watch.core.WatchRepo
import eu.darken.apl.watch.core.WatchSettings
import eu.darken.apl.watch.core.alerts.WatchMonitor
import eu.darken.apl.watch.core.history.WatchCheck
import eu.darken.apl.watch.core.history.WatchHistoryRepo
import eu.darken.apl.watch.core.types.Watch
import io.kotest.assertions.withClue
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual as shouldBeAtLeast
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
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
        every { historyRepo.firehose } returns emptyFlow<WatchCheck?>()
        every { locationManager2.state } returns MutableStateFlow(LocationManager2.State.Waiting)
        every { accessRepo.state } returns MutableStateFlow<AccessState?>(null)
        every { upgradeRepo.upgradeInfo } returns MutableStateFlow<UpgradeRepo.Info>(ProInfo)
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
    )

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
