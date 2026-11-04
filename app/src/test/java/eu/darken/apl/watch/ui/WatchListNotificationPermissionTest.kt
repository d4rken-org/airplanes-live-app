package eu.darken.apl.watch.ui

import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.WebpageTool
import eu.darken.apl.common.location.LocationManager2
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.upgrade.UpgradeRepo
import eu.darken.apl.watch.core.WatchLimit
import eu.darken.apl.watch.core.WatchRepo
import eu.darken.apl.watch.core.WatchSettings
import eu.darken.apl.watch.core.alerts.WatchMonitor
import eu.darken.apl.watch.core.db.types.BaseWatchEntity
import eu.darken.apl.watch.core.db.types.SquawkWatchEntity
import eu.darken.apl.watch.core.history.WatchCheck
import eu.darken.apl.watch.core.history.WatchHistoryRepo
import eu.darken.apl.watch.core.server.WatchServerMode
import eu.darken.apl.watch.core.types.SquawkWatch
import eu.darken.apl.watch.core.types.Watch
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.coroutine.TestDispatcherProvider

class WatchListNotificationPermissionTest : BaseTest() {

    private val dispatcher = UnconfinedTestDispatcher()
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

    private val watches = MutableStateFlow<List<Watch>>(emptyList())

    @BeforeEach
    fun setup() {
        // viewModelScope is built on the main dispatcher
        Dispatchers.setMain(dispatcher)
        every { watchRepo.isRefreshing } returns MutableStateFlow(false)
        every { watchRepo.watches } returns watches
        every { watchRepo.status } returns MutableStateFlow(emptyList<Watch.Status>())
        every { watchRepo.limit } returns MutableStateFlow(WatchLimit(count = 0, max = null))
        every { historyRepo.firehose } returns emptyFlow<WatchCheck?>()
        every { locationManager2.state } returns MutableStateFlow(LocationManager2.State.Waiting)
        every { accessRepo.state } returns MutableStateFlow<AccessState?>(null)
        every { serverMode.state } returns MutableStateFlow(WatchServerMode.State.OFF)
        coEvery { serverMode.current() } returns WatchServerMode.State.OFF
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = WatchListViewModel(
        dispatcherProvider = TestDispatcherProvider(dispatcher),
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

    private fun TestScope.eventsOf(vm: WatchListViewModel): List<WatchListEvents> {
        val events = mutableListOf<WatchListEvents>()
        backgroundScope.launch { vm.events.toList(events) }
        return events
    }

    private fun squawk(id: String, alerts: Boolean) = SquawkWatch(
        BaseWatchEntity(id = id, watchType = SquawkWatchEntity.TYPE_KEY, notificationEnabled = alerts),
        SquawkWatchEntity(id = id, code = "7700"),
    )

    @Test
    fun `a first load with an alert-enabled watch asks for the notification permission once`() = runTest(dispatcher) {
        watches.value = listOf(squawk("off", alerts = false), squawk("on", alerts = true))
        val vm = createViewModel()
        val events = eventsOf(vm)
        advanceUntilIdle()

        events shouldBe listOf(WatchListEvents.RequestNotificationPermission)
    }

    @Test
    fun `a first load without alert-enabled watches does not ask`() = runTest(dispatcher) {
        watches.value = listOf(squawk("a", alerts = false), squawk("b", alerts = false))
        val vm = createViewModel()
        val events = eventsOf(vm)
        advanceUntilIdle()

        events shouldBe emptyList()
    }

    @Test
    fun `an empty first load does not ask`() = runTest(dispatcher) {
        val vm = createViewModel()
        val events = eventsOf(vm)
        advanceUntilIdle()

        events shouldBe emptyList()
    }

    @Test
    fun `alerts turned on after the first load do not ask`() = runTest(dispatcher) {
        watches.value = listOf(squawk("a", alerts = false))
        val vm = createViewModel()
        val events = eventsOf(vm)
        advanceUntilIdle()

        watches.value = listOf(squawk("a", alerts = true))
        advanceUntilIdle()

        events shouldBe emptyList()
    }
}
