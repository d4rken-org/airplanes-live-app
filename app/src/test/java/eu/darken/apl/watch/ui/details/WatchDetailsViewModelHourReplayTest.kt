package eu.darken.apl.watch.ui.details

import androidx.lifecycle.viewModelScope
import eu.darken.apl.common.location.LocationManager2
import eu.darken.apl.common.navigation.NavEvent
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.map.core.MapOptions
import eu.darken.apl.map.ui.DestinationMap
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.upgrade.ui.DestinationUpgrade
import eu.darken.apl.watch.core.WatchRepo
import eu.darken.apl.watch.core.db.types.AircraftWatchEntity
import eu.darken.apl.watch.core.db.types.BaseWatchEntity
import eu.darken.apl.watch.core.db.types.LocationWatchEntity
import eu.darken.apl.watch.core.history.WatchActivityGrid
import eu.darken.apl.watch.core.server.WatchServerMode
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.LocationWatch
import eu.darken.apl.watch.core.types.Watch
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelper.coroutine.TestDispatcherProvider
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

// Location watches carry android.location.Location
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class WatchDetailsViewModelHourReplayTest {

    private val watchRepo = mockk<WatchRepo>(relaxed = true)
    private val statuses = MutableStateFlow<List<Watch.Status>>(emptyList())
    private val access = MutableStateFlow<AccessState?>(null)

    @Before
    fun setup() {
        // viewModelScope is built on the main dispatcher
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { watchRepo.status } returns statuses
        statuses.value = listOf(aircraftStatus())
        access.value = accessState(hasHistory = true, offersHistory = true)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private val viewModels = mutableListOf<WatchDetailsViewModel>()

    /** The ViewModel's clocks tick forever, they end with the test. */
    private fun vmTest(body: suspend TestScope.() -> Unit) = runTest {
        try {
            body()
        } finally {
            viewModels.forEach { it.viewModelScope.cancel() }
        }
    }

    private fun createViewModel(watchId: String = AIRCRAFT_ID) = WatchDetailsViewModel(
        dispatcherProvider = TestDispatcherProvider(),
        watchRepo = watchRepo,
        searchRepo = mockk(relaxed = true),
        aircraftRepo = mockk<AircraftRepo> { every { cache } returns flowOf(emptyMap()) },
        locationManager2 = mockk { every { state } returns MutableStateFlow<LocationManager2.State>(LocationManager2.State.Waiting) },
        flightRepo = mockk(relaxed = true),
        historyRepo = mockk(relaxed = true),
        webpageTool = mockk(relaxed = true),
        serverClock = mockk<ServerClock> { every { now() } returns NOW },
        liveCheck = mockk(relaxed = true),
        hourAircraftRepo = mockk(relaxed = true),
        accessRepo = mockk<AccessRepo> { every { state } returns access },
        serverMode = mockk { every { state } returns flowOf(WatchServerMode.State(access = null, isActive = true)) },
    ).apply {
        init(watchId)
        viewModels.add(this)
    }

    /** A tap that did nothing leaves both queues empty, so the next navigation and event are these. */
    private suspend fun WatchDetailsViewModel.shouldHaveDoneNothing(watchId: String = AIRCRAFT_ID) {
        goUpgrade()
        navEvents.first() shouldBe NavEvent.GoTo(DestinationUpgrade)
        removeAlert()
        events.first() shouldBe WatchDetailsEvents.RemovalConfirmation(watchId)
    }

    @Test
    fun `an aircraft watch with history opens its seen hours on the map`() = vmTest {
        createViewModel().state.first().hourReplay shouldBe true
    }

    @Test
    fun `a seen hour opens the aircraft's history at that hour`() = vmTest {
        val vm = createViewModel()

        vm.replayHourSlot(slot(HOUR))

        vm.navEvents.first() shouldBe NavEvent.GoTo(
            DestinationMap(
                mapOptions = MapOptions.focus(HEX).copy(
                    follow = true,
                    replayWindow = MapOptions.ReplayWindow(
                        from = HOUR.toEpochMilli(),
                        until = HOUR.plus(HOUR_STEP).toEpochMilli(),
                    ),
                )
            )
        )
    }

    @Test
    fun `a seen two-hour slot opens both of its hours`() = vmTest {
        val vm = createViewModel()
        val second = HOUR.plus(HOUR_STEP)

        vm.replayHourSlot(slot(HOUR, second))

        vm.navEvents.first() shouldBe NavEvent.GoTo(
            DestinationMap(
                mapOptions = MapOptions.focus(HEX).copy(
                    follow = true,
                    replayWindow = MapOptions.ReplayWindow(
                        from = HOUR.toEpochMilli(),
                        until = second.plus(HOUR_STEP).toEpochMilli(),
                    ),
                )
            )
        )
    }

    @Test
    fun `quiet, unknown and later hours open nothing`() = vmTest {
        val vm = createViewModel()

        vm.replayHourSlot(slot(HOUR, state = WatchActivityGrid.State.INACTIVE))
        vm.replayHourSlot(slot(HOUR, state = WatchActivityGrid.State.UNKNOWN))
        vm.replayHourSlot(slot(HOUR, state = WatchActivityGrid.State.NOT_YET))

        vm.shouldHaveDoneNothing()
    }

    @Test
    fun `without replay in the tier a seen hour offers the upgrade instead`() = vmTest {
        access.value = accessState(hasHistory = true, offersHistory = false)
        val vm = createViewModel()
        vm.state.first().hourReplay shouldBe true

        vm.replayHourSlot(slot(HOUR))

        vm.events.first() shouldBe WatchDetailsEvents.HistoryUpsell
        vm.goUpgrade()
        vm.navEvents.first() shouldBe NavEvent.GoTo(DestinationUpgrade)
    }

    @Test
    fun `without aircraft history hours do not open`() = vmTest {
        access.value = accessState(hasHistory = false, offersHistory = false)
        val vm = createViewModel()
        vm.state.first().hourReplay shouldBe false

        vm.replayHourSlot(slot(HOUR))

        vm.shouldHaveDoneNothing()
    }

    @Test
    fun `without access state hours do not open`() = vmTest {
        access.value = null
        val vm = createViewModel()
        vm.state.first().hourReplay shouldBe false

        vm.replayHourSlot(slot(HOUR))

        vm.shouldHaveDoneNothing()
    }

    @Test
    fun `a location watch does not open hours on the map`() = vmTest {
        statuses.value = listOf(locationStatus())
        val vm = createViewModel(watchId = LOCATION_ID)
        vm.state.first().hourReplay shouldBe false

        vm.replayHourSlot(slot(HOUR))

        vm.shouldHaveDoneNothing(watchId = LOCATION_ID)
    }

    @Test
    fun `upgrading opens the upgrade screen`() = vmTest {
        val vm = createViewModel()

        vm.goUpgrade()

        vm.navEvents.first() shouldBe NavEvent.GoTo(DestinationUpgrade)
    }

    private fun accessState(hasHistory: Boolean, offersHistory: Boolean) = mockk<AccessState> {
        every { offersWatchHourAircraft } returns false
        every { maxLocationWatchRadiusKm } returns 25
        every { hasAircraftHistory } returns hasHistory
        every { offersAircraftHistory } returns offersHistory
    }

    private fun slot(vararg hours: Instant, state: WatchActivityGrid.State = WatchActivityGrid.State.ACTIVE) =
        WatchActivityGrid.Slot(state, 1, hours.toList())

    private fun aircraftStatus() = AircraftWatch.Status(
        watch = AircraftWatch(
            BaseWatchEntity(id = AIRCRAFT_ID, watchType = AircraftWatchEntity.TYPE_KEY),
            AircraftWatchEntity(id = AIRCRAFT_ID, hexCode = HEX),
        ),
        lastHit = null,
    )

    private fun locationStatus() = LocationWatch.Status(
        watch = LocationWatch(
            BaseWatchEntity(
                id = LOCATION_ID,
                watchType = LocationWatchEntity.TYPE_KEY,
                latitude = 52.5,
                longitude = 13.4,
                radius = 25_000f,
            ),
            LocationWatchEntity(id = LOCATION_ID, label = "Berlin"),
        ),
        lastHit = null,
    )

    companion object {
        private const val AIRCRAFT_ID = "aircraft-1"
        private const val LOCATION_ID = "location-1"
        private const val HEX = "3C65A3"
        private val NOW = Instant.parse("2026-11-09T12:20:00Z")
        private val HOUR_STEP = Duration.ofHours(1)
        private val HOUR = NOW.truncatedTo(ChronoUnit.HOURS).minus(Duration.ofHours(3))
    }
}
