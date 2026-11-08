package eu.darken.apl.watch.ui

import androidx.lifecycle.viewModelScope
import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.datastore.DataStoreValue
import eu.darken.apl.common.location.LocationManager2
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.watch.core.WatchLimit
import eu.darken.apl.watch.core.WatchRepo
import eu.darken.apl.watch.core.WatchSettings
import eu.darken.apl.watch.core.WatchSortMode
import eu.darken.apl.watch.core.db.types.BaseWatchEntity
import eu.darken.apl.watch.core.db.types.SquawkWatchEntity
import eu.darken.apl.watch.core.history.WatchHistoryRepo
import eu.darken.apl.watch.core.server.WatchServerMode
import eu.darken.apl.watch.core.types.SquawkWatch
import eu.darken.apl.watch.core.types.Watch
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.coroutine.TestDispatcherProvider

class WatchListServerUnavailableTest : BaseTest() {

    private val dispatcher = UnconfinedTestDispatcher()
    private val watchRepo = mockk<WatchRepo>(relaxed = true)
    private val locationManager2 = mockk<LocationManager2>(relaxed = true)
    private val historyRepo = mockk<WatchHistoryRepo>(relaxed = true)
    private val watchSettings = mockk<WatchSettings>(relaxed = true)
    private val accessRepo = mockk<AccessRepo>(relaxed = true)
    private val serverMode = mockk<WatchServerMode>()
    private val serverClock = ServerClock(object : MonotonicClock {
        override fun elapsed(): Long = 0L
    })

    private val loaded = CompletableDeferred<Unit>()
    private val statuses = MutableStateFlow<List<Watch.Status>>(emptyList())

    // Before the persisted access is read, server mode is off for want of an access
    private val mode = MutableStateFlow(WatchServerMode.State.OFF)

    @BeforeEach
    fun setup() {
        // viewModelScope is built on the main dispatcher
        Dispatchers.setMain(dispatcher)
        every { watchRepo.isRefreshing } returns MutableStateFlow(false)
        every { watchRepo.watches } returns MutableStateFlow(emptyList<Watch>())
        every { watchRepo.status } returns statuses
        every { watchRepo.limit } returns MutableStateFlow(WatchLimit(count = 0, max = null))
        every { locationManager2.state } returns MutableStateFlow(LocationManager2.State.Waiting)
        every { historyRepo.activityByWatch(any()) } returns flowOf(emptyMap())
        every { historyRepo.historyLastSeen } returns flowOf(emptyMap())
        val sortMode = mockk<DataStoreValue<WatchSortMode>> { every { flow } returns flowOf(WatchSortMode.BY_NOTE) }
        every { watchSettings.watchSortMode } returns sortMode
        every { accessRepo.state } returns MutableStateFlow<AccessState?>(null)
        coEvery { accessRepo.awaitLoaded() } coAnswers { loaded.await() }
        every { serverMode.state } returns mode
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
    }

    private val viewModels = mutableListOf<WatchListViewModel>()

    /** The list's clock ticks forever on the test's scheduler, it ends with the test. */
    private fun vmTest(body: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try {
            body()
        } finally {
            viewModels.forEach { it.viewModelScope.cancel() }
        }
    }

    private fun createViewModel() = WatchListViewModel(
        dispatcherProvider = TestDispatcherProvider(dispatcher),
        watchRepo = watchRepo,
        watchMonitor = mockk(relaxed = true),
        webpageTool = mockk(relaxed = true),
        locationManager2 = locationManager2,
        aircraftRepo = mockk(relaxed = true),
        historyRepo = historyRepo,
        watchSettings = watchSettings,
        accessRepo = accessRepo,
        upgradeRepo = mockk(relaxed = true),
        serverClock = serverClock,
        serverMode = serverMode,
    ).also { viewModels.add(it) }

    private fun TestScope.statesOf(vm: WatchListViewModel): List<WatchListViewModel.State> {
        val states = mutableListOf<WatchListViewModel.State>()
        backgroundScope.launch { vm.state.collect { states.add(it) } }
        return states
    }

    private fun squawk(id: String) = SquawkWatch.Status(
        watch = SquawkWatch(
            BaseWatchEntity(id = id, watchType = SquawkWatchEntity.TYPE_KEY),
            SquawkWatchEntity(id = id, code = "7700"),
        ),
        lastHit = null,
    )

    @Test
    fun `no banner before the persisted access is loaded`() = vmTest {
        statuses.value = listOf(squawk("a"))
        val states = statesOf(createViewModel())

        states.filter { it.showServerUnavailable }.shouldBeEmpty()
    }

    @Test
    fun `the banner shows once loaded while server mode is inactive and there are watches`() = vmTest {
        statuses.value = listOf(squawk("a"))
        val states = statesOf(createViewModel())

        loaded.complete(Unit)

        states.last().showServerUnavailable shouldBe true
    }

    @Test
    fun `no banner in server mode`() = vmTest {
        statuses.value = listOf(squawk("a"))
        mode.value = WatchServerMode.State(access = null, isActive = true)
        val states = statesOf(createViewModel())

        loaded.complete(Unit)

        states.last().showServerUnavailable shouldBe false
    }

    @Test
    fun `no banner without watches`() = vmTest {
        val states = statesOf(createViewModel())

        loaded.complete(Unit)

        states.last().showServerUnavailable shouldBe false
    }
}
