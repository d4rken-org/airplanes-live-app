package eu.darken.apl.map.ui

import android.content.Context
import android.os.SystemClock
import eu.darken.apl.airport.core.AirportFlightsRepo
import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.datastore.DataStoreValue
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.map.core.AircraftKind
import eu.darken.apl.map.core.MapAircraftProvider
import eu.darken.apl.map.core.MapLabelFields
import eu.darken.apl.map.core.MapPlane
import eu.darken.apl.map.core.MapSettings
import eu.darken.apl.map.core.NativeMapStyle
import eu.darken.apl.map.core.overlay.OverlayRepo
import eu.darken.apl.map.core.overlay.OverlayState
import eu.darken.apl.map.ui.NativeMapViewModel.Selected
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.compat.AppCompatibility
import eu.darken.apl.server.compat.CompatState
import eu.darken.apl.server.session.SessionManager
import eu.darken.apl.server.session.SessionState
import eu.darken.apl.server.status.ServerStatus
import eu.darken.apl.server.status.ServerStatusTracker
import eu.darken.apl.upgrade.UpgradeRepo
import eu.darken.apl.watch.core.WatchLimit
import eu.darken.apl.watch.core.WatchRepo
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
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

class NativeMapViewModelPinTest : BaseTest() {

    private fun plane(hex: String) = MapPlane(
        hex = hex,
        latitude = 51.0,
        longitude = 10.0,
        trackDegrees = null,
        altitudeFt = 30_000,
        groundSpeedKnots = 450,
        onGround = false,
        callsign = null,
        squawk = null,
        aircraftType = null,
        category = null,
        military = false,
        opacity = 1f,
    )

    // On screen, so a list tap outside Multi-select has somewhere to move the camera to
    private val providerFrames = MutableStateFlow(
        MapAircraftProvider.Frame(
            traffic = listOf(plane("AAAAAA"), plane("BBBBBB")), trafficVersion = 1, fades = emptyMap(),
            selectedHex = null, selected = null, selectedDetails = null, selectedMissing = null,
            selectedTrail = emptyList(), selectedTrailHead = null, tracks = null, viewport = null,
            onScreen = listOf(plane("AAAAAA"), plane("BBBBBB")), shownCount = 2, capped = false, totalMatching = null,
        )
    )
    private val provider = mockk<MapAircraftProvider>(relaxed = true) {
        every { frames } returns providerFrames
        every { state } returns MutableStateFlow(null)
    }
    private val watchRepo = mockk<WatchRepo>(relaxed = true) {
        every { watches } returns MutableStateFlow(emptyList())
        every { limit } returns flowOf(WatchLimit(count = 0, max = null))
    }
    private val aircraftRepo = mockk<AircraftRepo>(relaxed = true) {
        every { cache } returns MutableStateFlow(emptyMap())
        every { cachedByHex(any()) } returns flowOf(null)
    }

    private fun <T> setting(value: T) = mockk<DataStoreValue<T>> {
        every { flow } returns MutableStateFlow(value)
    }

    private val mapSettings = mockk<MapSettings>(relaxed = true) {
        every { nativeMapStyle } returns setting(NativeMapStyle.AUTO.key)
        every { isLabelsEnabled } returns setting(true)
        every { labelFields } returns flowOf(MapLabelFields())
        every { isAllTracksEnabled } returns setting(false)
        every { isMilitaryHighlightEnabled } returns setting(true)
        every { isWatchHighlightEnabled } returns setting(true)
        every { enabledOverlays } returns setting(emptySet())
        every { isRestoreLastViewEnabled } returns setting(false)
        every { isMilitaryOnlyEnabled } returns setting(false)
        every { aircraftKinds } returns setting(emptySet<AircraftKind>())
    }

    private val context = mockk<Context>(relaxed = true) {
        every { resources.getStringArray(any()) } returns arrayOf("tagline")
    }

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } returns 0L
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
        unmockkStatic(SystemClock::class)
    }

    private fun createViewModel() = NativeMapViewModel(
        dispatcherProvider = TestDispatcherProvider(),
        context = context,
        sensorManager = mockk(relaxed = true),
        mapSettings = mapSettings,
        webpageTool = mockk(relaxed = true),
        watchRepo = watchRepo,
        aircraftRepo = aircraftRepo,
        flightRepo = mockk(relaxed = true),
        locationManager2 = mockk(relaxed = true),
        provider = provider,
        accessRepo = mockk<AccessRepo>(relaxed = true) {
            every { state } returns MutableStateFlow(null)
        },
        serverClock = ServerClock(object : MonotonicClock {
            override fun elapsed(): Long = 0L
        }),
        mapRequests = MapRequests(),
        locationWatchCreator = mockk(relaxed = true),
        airportRepo = mockk(relaxed = true),
        sessionManager = mockk<SessionManager> { every { state } returns MutableStateFlow(SessionState.Unknown) },
        shapesRepo = mockk(relaxed = true),
        upgradeRepo = mockk<UpgradeRepo> {
            every { upgradeInfo } returns MutableStateFlow(mockk(relaxed = true))
        },
        compatibility = mockk<AppCompatibility> {
            every { state } returns MutableStateFlow(CompatState.Ok)
        },
        overlayRepo = mockk<OverlayRepo> {
            every { state } returns MutableStateFlow(OverlayState())
        },
        serverEndpoint = mockk(relaxed = true),
        airportFlightsRepo = mockk<AirportFlightsRepo> { every { pass } returns MutableStateFlow(null) },
        monotonicClock = object : MonotonicClock {
            override fun elapsed(): Long = 0L
        },
        serverStatusTracker = mockk<ServerStatusTracker> {
            every { status } returns MutableStateFlow(ServerStatus.Ok)
        },
    )

    /** Shown with the list open, the screen collecting its state and the map its frames. */
    private fun TestScope.shownViewModel(): NativeMapViewModel = createViewModel().also { vm ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.frames.collect {} }
        vm.init()
        vm.toggleSidebar()
        vm.isSidebarOpen.value shouldBe true
    }

    private fun TestScope.eventsOf(vm: NativeMapViewModel): List<NativeMapEvents> {
        val events = mutableListOf<NativeMapEvents>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.toList(events) }
        return events
    }

    private val NativeMapViewModel.current: NativeMapViewModel.State
        get() = state.value.shouldNotBeNull()

    @Test
    fun `a long-press pins without selecting, moving the camera or closing the list`() = runTest {
        val viewModel = shownViewModel()
        val events = eventsOf(viewModel)

        viewModel.onSidebarAircraftLongClicked("AAAAAA")

        viewModel.current.pinned shouldBe listOf("AAAAAA")
        viewModel.current.pinnedCount shouldBe 1
        viewModel.current.selection.shouldBeNull()
        viewModel.isSidebarOpen.value shouldBe true
        events.filterIsInstance<NativeMapEvents.MoveCamera>().shouldBeEmpty()
        verify { provider.pin(listOf("AAAAAA")) }
    }

    @Test
    fun `a second long-press unpins`() = runTest {
        val viewModel = shownViewModel()
        viewModel.onSidebarAircraftLongClicked("AAAAAA")

        viewModel.onSidebarAircraftLongClicked("AAAAAA")

        viewModel.current.pinned.shouldBeEmpty()
        verify { provider.pin(emptyList()) }
    }

    @Test
    fun `a long-press on another row keeps the selection`() = runTest {
        val viewModel = shownViewModel()
        viewModel.onAircraftTapped("AAAAAA")

        viewModel.onSidebarAircraftLongClicked("BBBBBB")

        viewModel.current.selection shouldBe Selected.Aircraft("AAAAAA")
        viewModel.current.pinned shouldBe listOf("BBBBBB")
    }

    @Test
    fun `a list tap in Multi-select pins instead of selecting`() = runTest {
        val viewModel = shownViewModel()
        val events = eventsOf(viewModel)
        viewModel.toggleMultiSelect()

        viewModel.onSidebarAircraftClicked("AAAAAA", docked = false)

        viewModel.current.pinned shouldBe listOf("AAAAAA")
        viewModel.current.selection.shouldBeNull()
        viewModel.isSidebarOpen.value shouldBe true
        events.filterIsInstance<NativeMapEvents.MoveCamera>().shouldBeEmpty()
        verify(exactly = 0) { provider.select("AAAAAA") }
    }

    @Test
    fun `a list tap outside Multi-select still selects, closes the list and moves the camera`() = runTest {
        val viewModel = shownViewModel()
        val events = eventsOf(viewModel)

        viewModel.onSidebarAircraftClicked("AAAAAA", docked = false)

        viewModel.current.selection shouldBe Selected.Aircraft("AAAAAA")
        viewModel.current.pinned.shouldBeEmpty()
        viewModel.isSidebarOpen.value shouldBe false
        events.filterIsInstance<NativeMapEvents.MoveCamera>() shouldHaveSize 1
    }

    @Test
    fun `long-presses pin no more than the server takes`() = runTest {
        val viewModel = shownViewModel()
        val hexes = (0..MapAircraftProvider.MAX_PINNED).map { "%06X".format(it) }

        hexes.forEach { viewModel.onSidebarAircraftLongClicked(it) }

        viewModel.current.pinned shouldBe hexes.take(MapAircraftProvider.MAX_PINNED)
    }

    @Test
    fun `unpinning the last aircraft ends isolation when nothing is selected`() = runTest {
        val viewModel = shownViewModel()
        viewModel.onSidebarAircraftLongClicked("AAAAAA")
        viewModel.toggleIsolate()
        viewModel.current.toggles.isolate shouldBe true

        viewModel.onSidebarAircraftLongClicked("AAAAAA")

        viewModel.current.toggles.isolate shouldBe false
    }

    @Test
    fun `unpinning the last aircraft keeps isolating the selection`() = runTest {
        val viewModel = shownViewModel()
        viewModel.onAircraftTapped("AAAAAA")
        viewModel.onSidebarAircraftLongClicked("BBBBBB")
        viewModel.toggleIsolate()

        viewModel.onSidebarAircraftLongClicked("BBBBBB")

        viewModel.current.toggles.isolate shouldBe true
        viewModel.current.isolating shouldBe true
    }

    @Test
    fun `swapping one pin for another reaches the state`() = runTest {
        val viewModel = shownViewModel()
        viewModel.onSidebarAircraftLongClicked("AAAAAA")

        viewModel.onSidebarAircraftLongClicked("AAAAAA")
        viewModel.onSidebarAircraftLongClicked("BBBBBB")

        viewModel.current.pinned shouldBe listOf("BBBBBB")
        viewModel.current.pinnedCount shouldBe 1
    }
}
