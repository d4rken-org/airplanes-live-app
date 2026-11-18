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
import eu.darken.apl.watch.core.history.LastSighting
import eu.darken.apl.watch.core.history.WatchActivityTimeline
import eu.darken.apl.watch.core.history.WatchActivityTimeline.Cell
import eu.darken.apl.watch.core.history.WatchHistoryRepo
import eu.darken.apl.watch.core.server.WatchServerMode
import eu.darken.apl.watch.core.types.Watch
import eu.darken.apl.watch.ui.preview.mockAircraftWatchStatus
import eu.darken.apl.watch.ui.preview.mockLocationWatchStatus
import eu.darken.apl.watch.ui.preview.mockSquawkWatchStatus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
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
import java.time.Instant

class WatchListLocationSummaryTest : BaseTest() {

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

    private val now = Instant.parse("2026-11-04T12:20:00Z")

    private val location = mockLocationWatchStatus()
    private val aircraft = mockAircraftWatchStatus()
    private val squawk = mockSquawkWatchStatus()

    private val sighting = LastSighting(at = Instant.parse("2026-11-04T11:40:00Z"), hex = "3C65A3")

    /** The two hours before now's and now's: quiet, 2 and 3. */
    private val timeline = WatchActivityTimeline(
        from = Instant.parse("2026-11-04T10:00:00Z"),
        cells = listOf(Cell.INACTIVE, Cell.ACTIVE, Cell.ACTIVE),
        counts = listOf(0, 2, 3),
    )

    @BeforeEach
    fun setup() {
        // viewModelScope is built on the main dispatcher
        Dispatchers.setMain(dispatcher)
        serverClock.noteServerTime(now.toEpochMilli())
        val all = listOf(location, aircraft, squawk)
        every { watchRepo.isRefreshing } returns MutableStateFlow(false)
        every { watchRepo.watches } returns MutableStateFlow(all.map { it.watch })
        every { watchRepo.status } returns MutableStateFlow<List<Watch.Status>>(all)
        every { watchRepo.limit } returns MutableStateFlow(WatchLimit(count = 0, max = null))
        every { locationManager2.state } returns MutableStateFlow(LocationManager2.State.Waiting)
        every { historyRepo.activityByWatch(any()) } returns flowOf(all.associate { it.id to timeline })
        every { historyRepo.historyLastSeen } returns flowOf(emptyMap())
        every { historyRepo.historyLastSighting } returns flowOf(all.associate { it.id to sighting })
        val sortMode = mockk<DataStoreValue<WatchSortMode>> { every { flow } returns flowOf(WatchSortMode.BY_NOTE) }
        every { watchSettings.watchSortMode } returns sortMode
        every { accessRepo.state } returns MutableStateFlow<AccessState?>(null)
        coEvery { accessRepo.awaitLoaded() } returns Unit
        every { serverMode.state } returns MutableStateFlow(WatchServerMode.State.OFF)
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

    @Test
    fun `only the location watch's item carries the summary`() = vmTest {
        val items = statesOf(createViewModel()).last().items.associateBy { it.status.id }

        items.getValue(location.id).shouldBeInstanceOf<WatchListViewModel.WatchItem.Multi>().locationSummary shouldBe
            WatchListViewModel.LocationSummary(lastSighting = sighting, hourlySightingsInDay = 5)
        items.getValue(squawk.id).shouldBeInstanceOf<WatchListViewModel.WatchItem.Multi>().locationSummary shouldBe null
        items.getValue(aircraft.id).shouldBeInstanceOf<WatchListViewModel.WatchItem.Single>()
    }

    @Test
    fun `a location watch without a sighting or activity has no summary`() = vmTest {
        every { historyRepo.activityByWatch(any()) } returns flowOf(emptyMap())
        every { historyRepo.historyLastSighting } returns flowOf(emptyMap())

        val items = statesOf(createViewModel()).last().items.associateBy { it.status.id }

        items.getValue(location.id).shouldBeInstanceOf<WatchListViewModel.WatchItem.Multi>().locationSummary shouldBe null
    }

    @Test
    fun `the day's count is left out of the summary when the day has no data`() = vmTest {
        every { historyRepo.activityByWatch(any()) } returns flowOf(
            mapOf(location.id to timeline.copy(from = Instant.parse("2026-11-01T10:00:00Z")))
        )

        val items = statesOf(createViewModel()).last().items.associateBy { it.status.id }

        items.getValue(location.id).shouldBeInstanceOf<WatchListViewModel.WatchItem.Multi>().locationSummary shouldBe
            WatchListViewModel.LocationSummary(lastSighting = sighting, hourlySightingsInDay = null)
    }
}
