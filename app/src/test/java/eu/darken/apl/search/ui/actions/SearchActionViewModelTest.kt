package eu.darken.apl.search.ui.actions

import android.os.SystemClock
import androidx.lifecycle.viewModelScope
import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.compose.preview.FakeAircraft
import eu.darken.apl.common.flight.FlightRepo
import eu.darken.apl.common.location.LocationManager2
import eu.darken.apl.common.navigation.NavEvent
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.server.ServerClock
import eu.darken.apl.watch.core.WatchLimitReachedException
import eu.darken.apl.watch.core.WatchRepo
import eu.darken.apl.watch.core.db.types.AircraftWatchEntity
import eu.darken.apl.watch.core.db.types.BaseWatchEntity
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.Watch
import eu.darken.apl.watch.ui.DestinationCreateAircraftWatch
import eu.darken.apl.watch.ui.DestinationWatchDetails
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.coroutine.TestDispatcherProvider

class SearchActionViewModelTest : BaseTest() {

    private val storedWatches = MutableStateFlow<List<Watch>>(emptyList())
    private val watchRepo = mockk<WatchRepo>(relaxed = true) {
        every { watches } returns storedWatches
    }
    private val aircraftCache = MutableStateFlow<Map<String, Aircraft>>(emptyMap())
    private val aircraftRepo = mockk<AircraftRepo>(relaxed = true) {
        every { cache } returns aircraftCache
    }
    // Every source of the state combine has to emit, a relaxed mock's flow never does
    private val locationManager2 = mockk<LocationManager2>(relaxed = true) {
        every { state } returns MutableStateFlow<LocationManager2.State>(LocationManager2.State.Waiting)
    }
    private val flightRepo = mockk<FlightRepo>(relaxed = true) {
        every { getByHex(any()) } returns flowOf(null)
    }

    private val viewModels = mutableListOf<SearchActionViewModel>()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } returns 0L
    }

    @AfterEach
    fun teardown() {
        viewModels.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
        unmockkStatic(SystemClock::class)
    }

    private fun createViewModel(hex: String = HEX) = SearchActionViewModel(
        dispatcherProvider = TestDispatcherProvider(),
        aircraftRepo = aircraftRepo,
        watchRepo = watchRepo,
        locationManager2 = locationManager2,
        flightRepo = flightRepo,
        serverClock = ServerClock(object : MonotonicClock {
            override fun elapsed(): Long = 0L
        }),
        webpageTool = mockk(relaxed = true),
    ).apply {
        init(hex)
        viewModels.add(this)
    }

    private fun watch(hex: String = HEX) = AircraftWatch(
        base = BaseWatchEntity(id = "id-$hex", watchType = AircraftWatchEntity.TYPE_KEY),
        specific = AircraftWatchEntity(id = "id-$hex", hexCode = hex),
    )

    @Test
    fun `the eye on an unwatched aircraft adds it and names it by its registration first`() = runTest {
        aircraftCache.value = mapOf(HEX to FakeAircraft(hex = HEX, registration = "D-AIUA", callsign = "DLH4AB"))
        coEvery { watchRepo.addAircraft(HEX) } returns WatchRepo.AircraftAdd.Created(watch())
        val viewModel = createViewModel()

        viewModel.onWatchAction()

        coVerify(exactly = 1) { watchRepo.addAircraft(HEX) }
        viewModel.events.first() shouldBe SearchActionEvents.WatchAdded(watchId = "id-$HEX", label = "D-AIUA")
    }

    @Test
    fun `an added aircraft without registration is named by its callsign, without data by its hex`() = runTest {
        aircraftCache.value = mapOf(HEX to FakeAircraft(hex = HEX, registration = null, callsign = "DLH4AB"))
        coEvery { watchRepo.addAircraft(HEX) } returns WatchRepo.AircraftAdd.Created(watch())
        val viewModel = createViewModel()

        viewModel.onWatchAction()
        viewModel.events.first() shouldBe SearchActionEvents.WatchAdded(watchId = "id-$HEX", label = "DLH4AB")

        aircraftCache.value = emptyMap()
        viewModel.onWatchAction()
        viewModel.events.first() shouldBe SearchActionEvents.WatchAdded(watchId = "id-$HEX", label = HEX)
    }

    @Test
    fun `undo deletes exactly the added watch`() = runTest {
        coEvery { watchRepo.addAircraft(HEX) } returns WatchRepo.AircraftAdd.Created(watch())
        val viewModel = createViewModel()
        viewModel.onWatchAction()
        val added = viewModel.events.first() as SearchActionEvents.WatchAdded

        viewModel.undoWatch(added.watchId)

        coVerify(exactly = 1) { watchRepo.delete("id-$HEX") }
        coVerify(exactly = 1) { watchRepo.delete(any()) }
    }

    @Test
    fun `the eye on a watched aircraft opens its watch`() = runTest {
        coEvery { watchRepo.addAircraft(HEX) } returns WatchRepo.AircraftAdd.Existing(watch())
        val viewModel = createViewModel()

        viewModel.onWatchAction()

        viewModel.navEvents.first() shouldBe NavEvent.GoTo(DestinationWatchDetails(watchId = "id-$HEX"))
    }

    @Test
    fun `the eye at the watch limit says so`() = runTest {
        val error = WatchLimitReachedException(max = 3)
        coEvery { watchRepo.addAircraft(HEX) } throws error
        val viewModel = createViewModel()

        viewModel.onWatchAction()

        viewModel.events.first() shouldBe SearchActionEvents.WatchLimitReached(error)
    }

    @Test
    fun `the add button on an unwatched aircraft opens the create dialog`() = runTest {
        val viewModel = createViewModel()

        viewModel.showWatch()

        viewModel.navEvents.first() shouldBe NavEvent.GoTo(DestinationCreateAircraftWatch(hex = HEX))
        coVerify(exactly = 0) { watchRepo.addAircraft(any()) }
    }

    companion object {
        private const val HEX = "3C6444"
    }
}
