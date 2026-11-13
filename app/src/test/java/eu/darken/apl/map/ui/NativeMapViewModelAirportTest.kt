package eu.darken.apl.map.ui

import android.content.Context
import android.os.SystemClock
import eu.darken.apl.airport.core.Airport
import eu.darken.apl.airport.core.AirportRef
import eu.darken.apl.airport.core.AirportRepo
import eu.darken.apl.airport.core.AirportSearch
import eu.darken.apl.airport.core.AirportTarget
import eu.darken.apl.airport.core.toAirport
import eu.darken.apl.airport.core.toAirportRef
import eu.darken.apl.airport.ui.AirportDetailsDisplay
import eu.darken.apl.airport.ui.AirportDetailsState
import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.datastore.DataStoreValue
import eu.darken.apl.common.navigation.NavEvent
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.map.core.AircraftHistoryTracks
import eu.darken.apl.map.core.AircraftKind
import eu.darken.apl.map.core.MapAircraftProvider
import eu.darken.apl.map.core.MapLabelFields
import eu.darken.apl.map.core.MapOptions
import eu.darken.apl.map.core.MapSettings
import eu.darken.apl.map.core.MapSidebarData
import eu.darken.apl.map.core.NativeMapStyle
import eu.darken.apl.map.core.overlay.OverlayRepo
import eu.darken.apl.map.core.overlay.OverlayState
import eu.darken.apl.map.ui.NativeMapViewModel.Selected
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.server.api.AirportSearchResultDto
import eu.darken.apl.server.api.AirportSearchResultDto.Companion.STATUS_ANSWERED
import eu.darken.apl.server.api.AirportSummaryDto
import eu.darken.apl.server.api.TrailPoint
import eu.darken.apl.server.compat.AppCompatibility
import eu.darken.apl.server.compat.CompatState
import eu.darken.apl.server.session.SessionManager
import eu.darken.apl.server.session.SessionState
import eu.darken.apl.upgrade.UpgradeRepo
import eu.darken.apl.watch.core.LocationWatchCreator
import eu.darken.apl.watch.core.WatchLimit
import eu.darken.apl.watch.core.WatchRepo
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.Watch
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CompletableDeferred
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
import eu.darken.apl.common.flight.Airport as RouteAirport

class NativeMapViewModelAirportTest : BaseTest() {

    private fun frame(selectedHex: String? = null) = MapAircraftProvider.Frame(
        traffic = emptyList(), trafficVersion = 1, fades = emptyMap(), selectedHex = selectedHex, selected = null,
        selectedDetails = null, selectedMissing = selectedHex, selectedTrail = emptyList(), selectedTrailHead = null,
        tracks = null, viewport = null, onScreen = emptyList(), shownCount = 0, capped = false, totalMatching = null,
    )

    private val providerFrames = MutableStateFlow(frame())
    private val mapRequests = MapRequests()
    private val provider = mockk<MapAircraftProvider>(relaxed = true) {
        every { frames } returns providerFrames
        every { state } returns MutableStateFlow(null)
    }
    private val storedWatches = MutableStateFlow<List<Watch>>(emptyList())
    private val watchRepo = mockk<WatchRepo>(relaxed = true) {
        every { watches } returns storedWatches
        every { limit } returns flowOf(WatchLimit(count = 0, max = null))
    }
    private val aircraftRepo = mockk<AircraftRepo>(relaxed = true) {
        every { cache } returns MutableStateFlow(emptyMap())
        every { cachedByHex(any()) } returns flowOf(null)
    }
    private val access = MutableStateFlow<AccessState?>(null)
    private val accessRepo = mockk<AccessRepo>(relaxed = true) {
        every { state } returns access
    }
    private val creator = mockk<LocationWatchCreator>(relaxed = true) {
        every { isAllowedNow() } returns true
        every { maxRadiusKm } returns flowOf(250)
        coEvery { labelFor(any(), any()) } returns "Aachen, DE"
    }
    private val airportRepo = mockk<AirportRepo>()

    private fun <T> setting(value: T) = mockk<DataStoreValue<T>> {
        every { flow } returns MutableStateFlow(value)
    }

    private val militaryOnly = MutableStateFlow(false)
    private val militaryOnlySetting = mockk<DataStoreValue<Boolean>> { every { flow } returns militaryOnly }
    private val chosenKinds = MutableStateFlow(emptySet<AircraftKind>())
    private val kindsSetting = mockk<DataStoreValue<Set<AircraftKind>>> { every { flow } returns chosenKinds }

    private val mapSettings = mockk<MapSettings>(relaxed = true) {
        every { nativeMapStyle } returns setting(NativeMapStyle.AUTO.key)
        every { isLabelsEnabled } returns setting(true)
        every { labelFields } returns flowOf(MapLabelFields())
        every { isAllTracksEnabled } returns setting(false)
        every { isMilitaryHighlightEnabled } returns setting(true)
        every { isWatchHighlightEnabled } returns setting(true)
        every { enabledOverlays } returns setting(emptySet())
        every { isRestoreLastViewEnabled } returns setting(false)
        every { isMilitaryOnlyEnabled } returns militaryOnlySetting
        every { aircraftKinds } returns kindsSetting
    }

    private val context = mockk<Context>(relaxed = true) {
        every { resources.getStringArray(any()) } returns arrayOf("tagline")
    }

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } returns 0L
        coEvery { airportRepo.search(any()) } returns nothingFound
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
        unmockkStatic(SystemClock::class)
    }

    private fun createViewModel(requests: MapRequests = mapRequests) = NativeMapViewModel(
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
        accessRepo = accessRepo,
        serverClock = ServerClock(object : MonotonicClock {
            override fun elapsed(): Long = 0L
        }),
        mapRequests = requests,
        locationWatchCreator = creator,
        airportRepo = airportRepo,
        sessionManager = mockk<SessionManager> { every { state } returns MutableStateFlow(SessionState.Unknown) },
        shapesRepo = mockk(relaxed = true),
        upgradeRepo = mockk<UpgradeRepo> {
            every { upgradeInfo } returns flowOf(mockk<UpgradeRepo.Info>(relaxed = true))
        },
        compatibility = mockk<AppCompatibility> {
            every { state } returns MutableStateFlow(CompatState.Ok)
        },
        overlayRepo = mockk<OverlayRepo> {
            every { state } returns MutableStateFlow(OverlayState())
        },
        serverEndpoint = mockk(relaxed = true),
    )

    /** Started with the screen collecting its state, as it shows the map. */
    private fun TestScope.shownViewModel(requests: MapRequests = mapRequests): NativeMapViewModel =
        createViewModel(requests).also { vm ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
            vm.init()
        }

    private val NativeMapViewModel.selection: Selected?
        get() = state.value.shouldNotBeNull().selection

    private val frankfurt = AirportTarget.Full(
        Airport(
            id = "oa:2212",
            name = "Frankfurt am Main Airport",
            type = "large",
            ident = "EDDF",
            latitude = 50.0333,
            longitude = 8.5706,
            icao = "EDDF",
            iata = "FRA",
        )
    )
    private val munich = AirportTarget.Full(
        Airport(
            id = "oa:2513",
            name = "Munich Airport",
            type = "large",
            ident = "EDDM",
            latitude = 48.3538,
            longitude = 11.7861,
            icao = "EDDM",
            iata = "MUC",
        )
    )
    private val hamburgFromRoute = AirportTarget.Partial(
        AirportRef.create(icao = "EDDH", iata = "HAM", name = "Hamburg Airport", latitude = 53.6304, longitude = 9.98823)!!
    )

    private val hamburgRoute = RouteAirport(
        icaoCode = "EDDH",
        iataCode = "HAM",
        name = "Hamburg Airport",
        countryName = "Germany",
        latitude = 53.6304,
        longitude = 9.98823,
    )
    private val hamburgRouteTarget = AirportTarget.Partial(hamburgRoute.toAirportRef()!!)

    /** As a route without positions knows it. */
    private val hamburgCodeOnly = AirportTarget.Partial(AirportRef.create(icao = "EDDH", iata = "HAM")!!)

    private val hamburgDto = AirportSummaryDto(
        id = "oa:2245",
        name = "Hamburg Helmut Schmidt Airport",
        type = AirportSummaryDto.TYPE_LARGE,
        ident = "EDDH",
        latitude = 53.630402,
        longitude = 9.988228,
        municipality = "Hamburg",
        icao = "EDDH",
        iata = "HAM",
        matchedCode = "EDDH",
        website = "https://www.hamburg-airport.de/",
    )
    private val hamburg = hamburgDto.toAirport()
    private val hamburgFound = AirportSearch.Answered(
        listOf(AirportSearchResultDto(0, STATUS_ANSWERED, listOf(hamburgDto)))
    )
    private val nothingFound = AirportSearch.Answered(
        listOf(AirportSearchResultDto(0, STATUS_ANSWERED, emptyList()))
    )

    private val before = MapOptions.Camera(lat = 48.1, lon = 11.5, zoom = 8.0)

    private fun showAirport(airport: AirportTarget) = MapOptions(
        camera = MapOptions.Camera(lat = airport.latitude!!, lon = airport.longitude!!, zoom = 12.0),
        airport = airport,
    )

    private fun watch(hex: String) = mockk<AircraftWatch>(relaxed = true) {
        every { this@mockk.hex } returns hex
    }

    private fun lastFilter(): MapAircraftProvider.Filter {
        val filters = mutableListOf<MapAircraftProvider.Filter>()
        verify { provider.setFilter(capture(filters)) }
        return filters.last()
    }

    private fun TestScope.navEventsOf(vm: NativeMapViewModel): List<NavEvent> {
        val events = mutableListOf<NavEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.navEvents.toList(events) }
        return events
    }

    private fun TestScope.camerasOf(vm: NativeMapViewModel): List<MapOptions.Camera> {
        val events = mutableListOf<NativeMapEvents>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.toList(events) }
        return object : AbstractList<MapOptions.Camera>() {
            private val moves get() = events.filterIsInstance<NativeMapEvents.MoveCamera>().map { it.camera }
            override val size: Int get() = moves.size
            override fun get(index: Int) = moves[index]
        }
    }

    private fun pinCalls(): List<List<String>> {
        val pins = mutableListOf<List<String>>()
        verify(atLeast = 0) { provider.pin(capture(pins)) }
        return pins
    }

    private fun TestScope.withHistory(hex: String) {
        access.value = mockk<AccessState>(relaxed = true) {
            every { hasAircraftHistory } returns true
            every { offersAircraftHistory } returns true
        }
        val base = 1_791_972_000_000L
        coEvery { provider.loadHistory(hex) } returns AircraftHistoryTracks.Track(
            points = listOf(0L, 60_000, 120_000, 3_600_000, 3_660_000).map { TrailPoint(50.0, 8.0, 35_000.0, base + it) },
            maxGapMs = 300_000,
            from = base - 3_600_000,
            until = base + 7_200_000,
        )
    }

    // Requests

    @Test
    fun `a plain start selects no airport`() = runTest {
        val viewModel = shownViewModel()

        viewModel.selection.shouldBeNull()
        viewModel.airportSheet.value.shouldBeNull()
        verify(exactly = 0) { provider.select(any()) }
    }

    @Test
    fun `an airport asked for when the map opens is selected there`() = runTest {
        mapRequests.publish(showAirport(frankfurt))

        val viewModel = shownViewModel()

        viewModel.selection.shouldBeInstanceOf<Selected.Airport>().target shouldBe frankfurt
        viewModel.airportSheet.value.shouldNotBeNull().best shouldBe frankfurt
        viewModel.initialCamera.value shouldBe MapOptions.Camera(lat = 50.0333, lon = 8.5706, zoom = 12.0)
    }

    @Test
    fun `an airport asked for while the map runs is selected there`() = runTest {
        val viewModel = shownViewModel()

        mapRequests.publish(showAirport(frankfurt))

        viewModel.selection.shouldBeInstanceOf<Selected.Airport>().target shouldBe frankfurt
        viewModel.initialCamera.value shouldBe MapOptions.Camera(lat = 50.0333, lon = 8.5706, zoom = 12.0)
    }

    @Test
    fun `an airport in a request wins over the aircraft it names`() = runTest {
        val viewModel = shownViewModel()

        mapRequests.publish(showAirport(frankfurt).copy(filter = MapOptions.Filter(selected = setOf("3C65A3"))))

        viewModel.selection.shouldBeInstanceOf<Selected.Airport>().target shouldBe frankfurt
        verify(exactly = 0) { provider.select("3C65A3") }
    }

    @Test
    fun `the next airport replaces the selected one`() = runTest {
        val viewModel = shownViewModel()
        mapRequests.publish(showAirport(frankfurt))

        mapRequests.publish(showAirport(munich))

        viewModel.selection.shouldBeInstanceOf<Selected.Airport>().target shouldBe munich
    }

    @Test
    fun `a request without an airport ends its selection`() = runTest {
        mapRequests.publish(showAirport(frankfurt))
        val viewModel = shownViewModel()

        mapRequests.publish(MapOptions.focus("CCCCCC"))

        viewModel.selection shouldBe Selected.Aircraft("CCCCCC")
        viewModel.airportSheet.value.shouldBeNull()

        mapRequests.publish(showAirport(frankfurt))
        mapRequests.publish(MapOptions(camera = MapOptions.Camera(lat = 50.0, lon = 8.0, zoom = 9.0)))

        viewModel.selection.shouldBeNull()
        viewModel.airportSheet.value.shouldBeNull()
    }

    @Test
    fun `an airport the map opens for clears the list's search and watched filter`() = runTest {
        storedWatches.value = listOf(watch("AAAAAA"))
        val viewModel = createViewModel()
        viewModel.setListQuery("dlh")
        viewModel.toggleListWatchedOnly()
        mapRequests.publish(showAirport(frankfurt))

        viewModel.init()

        viewModel.listControls.value shouldBe MapSidebarData.Controls()
        lastFilter().query shouldBe ""
        lastFilter().watches shouldBe null
    }

    @Test
    fun `an airport asked for while the map runs clears the list's search and watched filter, not its sort`() = runTest {
        storedWatches.value = listOf(watch("AAAAAA"))
        val viewModel = createViewModel().apply { init() }
        viewModel.setListQuery("dlh")
        viewModel.toggleListWatchedOnly()
        viewModel.setListSort(MapSidebarData.SortField.ALTITUDE)

        mapRequests.publish(showAirport(frankfurt))

        viewModel.listControls.value shouldBe MapSidebarData.Controls(sort = MapSidebarData.SortField.ALTITUDE)
        lastFilter().query shouldBe ""
        lastFilter().watches shouldBe null
    }

    @Test
    fun `an airport keeps military-only and the chosen kinds`() = runTest {
        militaryOnly.value = true
        chosenKinds.value = setOf(AircraftKind.HELICOPTER)
        mapRequests.publish(showAirport(frankfurt))
        val viewModel = shownViewModel()

        mapRequests.publish(showAirport(munich))

        viewModel.selection.shouldBeInstanceOf<Selected.Airport>().target shouldBe munich
        coVerify(exactly = 0) { militaryOnlySetting.update(any()) }
        coVerify(exactly = 0) { kindsSetting.update(any()) }
        coVerify(exactly = 0) { mapSettings.toggleAircraftKind(any()) }
        coVerify(exactly = 0) { mapSettings.showAllAircraftKinds() }
        lastFilter().militaryOnly shouldBe true
        lastFilter().kinds shouldBe setOf(AircraftKind.HELICOPTER)
    }

    @Test
    fun `an airport replaces the selection, the pins and isolating them`() = runTest {
        val viewModel = shownViewModel()
        mapRequests.publish(MapOptions.focus(listOf("AAAAAA", "BBBBBB")))
        viewModel.onAircraftTapped("CCCCCC")
        lastFilter().isolated shouldBe setOf("AAAAAA", "BBBBBB", "CCCCCC")

        mapRequests.publish(showAirport(frankfurt))

        verifyOrder {
            provider.pin(listOf("AAAAAA", "BBBBBB"))
            provider.select("CCCCCC")
            provider.pin(emptyList())
            provider.select(null)
        }
        lastFilter().isolated shouldBe null
        viewModel.selection.shouldBeInstanceOf<Selected.Airport>().target shouldBe frankfurt
    }

    @Test
    fun `an airport ends an open history`() = runTest {
        withHistory("3C65A3")
        val viewModel = createViewModel().apply { init() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.historyView.collect {} }
        viewModel.onAircraftTapped("3C65A3")
        viewModel.openHistory()
        viewModel.historyView.value.shouldNotBeNull()

        mapRequests.publish(showAirport(frankfurt))

        viewModel.historyView.value.shouldBeNull()
        verify { provider.select(null) }
        viewModel.airportSheet.value.shouldNotBeNull().target shouldBe frankfurt
    }

    @Test
    fun `an airport closes an open area editor`() = runTest {
        val viewModel = createViewModel().apply { init() }
        viewModel.onMapLongPressed(50.77, 6.08)
        viewModel.areaDraft.value.shouldNotBeNull()

        mapRequests.publish(showAirport(frankfurt))

        viewModel.areaDraft.value.shouldBeNull()
        viewModel.airportSheet.value.shouldNotBeNull().target shouldBe frankfurt
    }

    // Taps

    @Test
    fun `tapping a map airport selects it in the map's sheet`() = runTest {
        val viewModel = shownViewModel()
        val events = navEventsOf(viewModel)

        viewModel.onAirportTapped(frankfurt)

        viewModel.selection.shouldBeInstanceOf<Selected.Airport>().target shouldBe frankfurt
        viewModel.airportSheet.value shouldBe AirportDetailsState.of(frankfurt)
        events shouldBe emptyList()
    }

    @Test
    fun `tapping an aircraft after an airport selects the aircraft`() = runTest {
        val viewModel = shownViewModel()
        viewModel.onAirportTapped(frankfurt)

        viewModel.onAircraftTapped("3C65A3")

        viewModel.selection shouldBe Selected.Aircraft("3C65A3")
        viewModel.airportSheet.value.shouldBeNull()
    }

    @Test
    fun `clicking an aircraft in the list after an airport selects the aircraft`() = runTest {
        val viewModel = shownViewModel()
        viewModel.onAirportTapped(frankfurt)

        viewModel.onSidebarAircraftClicked("3C65A3", docked = true)

        viewModel.selection shouldBe Selected.Aircraft("3C65A3")
        viewModel.airportSheet.value.shouldBeNull()
    }

    @Test
    fun `tapping an airport after an aircraft selects the airport`() = runTest {
        val viewModel = shownViewModel()
        viewModel.onAircraftTapped("3C65A3")
        viewModel.toggleFollow()

        viewModel.onAirportTapped(frankfurt)

        viewModel.selection.shouldBeInstanceOf<Selected.Airport>().target shouldBe frankfurt
        viewModel.state.value.shouldNotBeNull().apply {
            selectedHex.shouldBeNull()
            toggles.follow shouldBe false
        }
        verifyOrder {
            provider.select("3C65A3")
            provider.select(null)
        }
    }

    @Test
    fun `a switch between aircraft and airport never shows nothing selected`() = runTest {
        val viewModel = createViewModel()
        val seen = mutableListOf<Selected?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect { seen += it?.selection }
        }
        viewModel.init()
        viewModel.onAircraftTapped("3C65A3")

        viewModel.onAirportTapped(frankfurt)
        viewModel.onAircraftTapped("4B1814")

        seen.dropWhile { it == null }.distinct().map { (it as? Selected.Airport)?.target ?: it } shouldBe listOf(
            Selected.Aircraft("3C65A3"),
            frankfurt,
            Selected.Aircraft("4B1814"),
        )
    }

    @Test
    fun `tapping the empty map ends the airport selection`() = runTest {
        val viewModel = shownViewModel()
        viewModel.onAirportTapped(frankfurt)

        viewModel.onMapTapped()

        viewModel.selection.shouldBeNull()
        viewModel.airportSheet.value.shouldBeNull()
    }

    @Test
    fun `tapping the empty map ends the airport selection while pinning too`() = runTest {
        val viewModel = shownViewModel()
        viewModel.toggleMultiSelect()
        viewModel.onAircraftTapped("AAAAAA")
        viewModel.onAirportTapped(frankfurt)

        viewModel.onMapTapped()

        viewModel.selection.shouldBeNull()
        pinCalls().last() shouldBe listOf("AAAAAA")
    }

    @Test
    fun `tapping an airport while pinning keeps the pins`() = runTest {
        val viewModel = shownViewModel()
        viewModel.toggleMultiSelect()
        viewModel.onAircraftTapped("AAAAAA")
        val pinsBefore = pinCalls()
        pinsBefore.last() shouldBe listOf("AAAAAA")

        viewModel.onAirportTapped(hamburgFromRoute)

        pinCalls() shouldBe pinsBefore
        viewModel.selection.shouldBeInstanceOf<Selected.Airport>().target shouldBe hamburgFromRoute
    }

    @Test
    fun `tapping an aircraft while pinning pins it and keeps the airport`() = runTest {
        val viewModel = shownViewModel()
        viewModel.toggleMultiSelect()
        viewModel.onAirportTapped(frankfurt)

        viewModel.onAircraftTapped("AAAAAA")

        pinCalls().last() shouldBe listOf("AAAAAA")
        viewModel.selection.shouldBeInstanceOf<Selected.Airport>().target shouldBe frankfurt
    }

    @Test
    fun `tapping an airport while the area editor is open does nothing`() = runTest {
        val viewModel = shownViewModel()
        viewModel.onMapLongPressed(50.77, 6.08)
        viewModel.areaDraft.value.shouldNotBeNull()

        viewModel.onAirportTapped(hamburgFromRoute)

        viewModel.selection.shouldBeNull()
        viewModel.areaDraft.value.shouldNotBeNull()
    }

    @Test
    fun `opening the area editor ends the airport selection`() = runTest {
        val viewModel = shownViewModel()
        viewModel.onAirportTapped(frankfurt)

        viewModel.onMapLongPressed(50.77, 6.08)

        viewModel.areaDraft.value.shouldNotBeNull()
        viewModel.selection.shouldBeNull()
        viewModel.airportSheet.value.shouldBeNull()
    }

    @Test
    fun `resetting the map ends the airport selection`() = runTest {
        val viewModel = shownViewModel()
        viewModel.onAirportTapped(frankfurt)

        viewModel.reset()

        viewModel.selection.shouldBeNull()
        viewModel.airportSheet.value.shouldBeNull()
    }

    @Test
    fun `tapping an airport during a history does nothing`() = runTest {
        withHistory("3C65A3")
        val viewModel = createViewModel().apply { init() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.historyView.collect {} }
        viewModel.onAircraftTapped("3C65A3")
        viewModel.openHistory()
        viewModel.historyView.value.shouldNotBeNull()

        viewModel.onAirportTapped(hamburgFromRoute)

        viewModel.airportSheet.value.shouldBeNull()
        viewModel.historyView.value.shouldNotBeNull()
    }

    @Test
    fun `deselecting ends the airport selection`() = runTest {
        val viewModel = shownViewModel()
        viewModel.onAirportTapped(frankfurt)

        viewModel.deselect()

        viewModel.selection.shouldBeNull()
        viewModel.airportSheet.value.shouldBeNull()
    }

    // The route's airport and back

    @Test
    fun `a route's airport is selected and the map moves to it`() = runTest {
        val viewModel = shownViewModel()
        val cameras = camerasOf(viewModel)
        viewModel.onAircraftTapped("3C65A3")

        viewModel.showAirport(hamburgRoute, before)

        viewModel.selection.shouldBeInstanceOf<Selected.Airport>().target shouldBe hamburgRouteTarget
        cameras shouldBe listOf(MapOptions.Camera(lat = 53.6304, lon = 9.98823, zoom = 12.0))
    }

    @Test
    fun `back from a route's airport returns to the aircraft where the map was`() = runTest {
        val viewModel = shownViewModel()
        val cameras = camerasOf(viewModel)
        viewModel.onAircraftTapped("3C65A3")
        viewModel.showAirport(hamburgRoute, before)

        viewModel.onBack()

        viewModel.selection shouldBe Selected.Aircraft("3C65A3")
        viewModel.airportSheet.value.shouldBeNull()
        cameras.last() shouldBe before
        viewModel.state.value.shouldNotBeNull().toggles.follow shouldBe false
    }

    @Test
    fun `without the map's camera back returns to where the map last reported`() = runTest {
        val viewModel = shownViewModel()
        val cameras = camerasOf(viewModel)
        val reported = MapOptions.Camera(lat = 47.0, lon = 10.0, zoom = 7.0)
        viewModel.onCameraIdle(mockk(relaxed = true), reported)
        viewModel.onAircraftTapped("3C65A3")
        viewModel.showAirport(hamburgRoute, currentCamera = null)

        viewModel.onBack()

        cameras.last() shouldBe reported
    }

    @Test
    fun `back from a route's airport follows the aircraft again`() = runTest {
        val viewModel = shownViewModel()
        val cameras = camerasOf(viewModel)
        viewModel.onAircraftTapped("3C65A3")
        providerFrames.value = frame(selectedHex = "3C65A3")
        viewModel.toggleFollow()
        viewModel.showAirport(hamburgRoute, before)
        viewModel.state.value.shouldNotBeNull().toggles.follow shouldBe false

        viewModel.onBack()

        viewModel.selection shouldBe Selected.Aircraft("3C65A3")
        viewModel.state.value.shouldNotBeNull().toggles.follow shouldBe true
        cameras shouldBe listOf(MapOptions.Camera(lat = 53.6304, lon = 9.98823, zoom = 12.0))
    }

    @Test
    fun `back returns only once`() = runTest {
        val viewModel = shownViewModel()
        viewModel.onAircraftTapped("3C65A3")
        viewModel.showAirport(hamburgRoute, before)
        viewModel.onBack()

        viewModel.onBack()

        viewModel.selection.shouldBeNull()
    }

    @Test
    fun `back from an airport tapped on the map ends the selection`() = runTest {
        val viewModel = shownViewModel()
        viewModel.onAircraftTapped("3C65A3")
        viewModel.onAirportTapped(frankfurt)

        viewModel.onBack()

        viewModel.selection.shouldBeNull()
    }

    @Test
    fun `any other selection forgets where back returns to`() = runTest {
        val changes: List<(NativeMapViewModel, MapRequests) -> Unit> = listOf(
            { vm, _ -> vm.onAirportTapped(frankfurt) },
            { vm, _ -> vm.onAircraftTapped("4B1814") },
            { vm, _ -> vm.onSidebarAircraftClicked("4B1814", docked = true) },
            { _, requests -> requests.publish(showAirport(munich)) },
            { vm, _ ->
                vm.onMapLongPressed(50.77, 6.08)
                vm.cancelAreaDraft()
            },
            { vm, _ -> vm.reset() },
            { vm, _ -> vm.deselect() },
            { vm, _ -> vm.onMapTapped() },
        )
        changes.forEach { change ->
            // Its own requests, the view models before it still take theirs
            val requests = MapRequests()
            val viewModel = shownViewModel(requests)
            viewModel.onAircraftTapped("3C65A3")
            viewModel.showAirport(hamburgRoute, before)
            change(viewModel, requests)
            viewModel.onAirportTapped(hamburgFromRoute)

            viewModel.onBack()

            viewModel.selection.shouldBeNull()
        }
    }

    @Test
    fun `a route's airport without a position is selected without moving the map`() = runTest {
        val viewModel = shownViewModel()
        val cameras = camerasOf(viewModel)
        viewModel.onAircraftTapped("3C65A3")

        viewModel.showAirport(hamburgRoute.copy(latitude = null, longitude = null), before)

        viewModel.selection.shouldBeInstanceOf<Selected.Airport>().target shouldBe
            AirportTarget.Partial(AirportRef.create(icao = "EDDH", iata = "HAM", name = "Hamburg Airport", countryName = "Germany")!!)
        cameras shouldBe emptyList()
    }

    // Centering

    @Test
    fun `centering moves the map to the selected airport`() = runTest {
        val viewModel = shownViewModel()
        val cameras = camerasOf(viewModel)
        viewModel.onAirportTapped(frankfurt)

        viewModel.centerOnAirport()

        cameras shouldBe listOf(MapOptions.Camera(lat = 50.0333, lon = 8.5706, zoom = 11.0))
    }

    @Test
    fun `centering a partial airport uses the airport the lookup found`() = runTest {
        coEvery { airportRepo.search(any()) } returns hamburgFound
        val viewModel = shownViewModel()
        val cameras = camerasOf(viewModel)
        viewModel.onAirportTapped(hamburgFromRoute)

        viewModel.centerOnAirport()

        cameras shouldBe listOf(MapOptions.Camera(lat = 53.630402, lon = 9.988228, zoom = 11.0))
    }

    @Test
    fun `centering a partial airport nothing was found for uses its own position`() = runTest {
        val viewModel = shownViewModel()
        val cameras = camerasOf(viewModel)
        viewModel.onAirportTapped(hamburgFromRoute)

        viewModel.centerOnAirport()

        cameras shouldBe listOf(MapOptions.Camera(lat = 53.6304, lon = 9.98823, zoom = 12.0))
    }

    @Test
    fun `centering an airport without a position does nothing`() = runTest {
        val viewModel = shownViewModel()
        val cameras = camerasOf(viewModel)
        viewModel.onAirportTapped(hamburgCodeOnly)

        viewModel.centerOnAirport()

        cameras shouldBe emptyList()
    }

    // The lookup

    @Test
    fun `a partial airport is looked up once and fills in the sheet`() = runTest {
        val answer = CompletableDeferred<AirportSearch>()
        coEvery { airportRepo.search(any()) } coAnswers { answer.await() }
        val viewModel = shownViewModel()

        viewModel.onAirportTapped(hamburgFromRoute)

        viewModel.airportSheet.value.shouldNotBeNull().apply {
            lookingUp shouldBe true
            display shouldBe AirportDetailsDisplay.from(hamburgFromRoute)
        }

        answer.complete(hamburgFound)

        viewModel.airportSheet.value.shouldNotBeNull().apply {
            lookingUp shouldBe false
            resolved shouldBe hamburg
            display shouldBe AirportDetailsDisplay.from(hamburgFromRoute, hamburg)
        }
        viewModel.selection.shouldBeInstanceOf<Selected.Airport>().target shouldBe hamburgFromRoute
        coVerify(exactly = 1) { airportRepo.search(listOf("EDDH")) }
    }

    @Test
    fun `a full airport is never looked up`() = runTest {
        val viewModel = shownViewModel()

        viewModel.onAirportTapped(frankfurt)

        coVerify(exactly = 0) { airportRepo.search(any()) }
    }

    @Test
    fun `a lookup finishing after another airport was selected leaves that airport's sheet`() = runTest {
        val answer = CompletableDeferred<AirportSearch>()
        coEvery { airportRepo.search(listOf("EDDH")) } coAnswers { answer.await() }
        val viewModel = shownViewModel()
        viewModel.onAirportTapped(hamburgFromRoute)

        viewModel.onAirportTapped(frankfurt)
        answer.complete(hamburgFound)

        viewModel.airportSheet.value.shouldNotBeNull().apply {
            target shouldBe frankfurt
            resolved.shouldBeNull()
        }
    }

    @Test
    fun `deselecting during the lookup leaves nothing selected`() = runTest {
        val answer = CompletableDeferred<AirportSearch>()
        coEvery { airportRepo.search(any()) } coAnswers { answer.await() }
        val viewModel = shownViewModel()
        viewModel.onAirportTapped(hamburgFromRoute)

        viewModel.deselect()
        answer.complete(hamburgFound)

        viewModel.airportSheet.value.shouldBeNull()
        viewModel.selection.shouldBeNull()
    }

    @Test
    fun `a failed lookup keeps what the airport itself knows`() = runTest {
        coEvery { airportRepo.search(any()) } throws IllegalStateException("boom")
        val viewModel = shownViewModel()
        val errors = mutableListOf<Throwable>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.errorEvents.toList(errors) }

        viewModel.onAirportTapped(hamburgFromRoute)

        viewModel.airportSheet.value.shouldNotBeNull().apply {
            lookingUp shouldBe false
            resolved.shouldBeNull()
            display shouldBe AirportDetailsDisplay.from(hamburgFromRoute)
        }
        errors shouldBe emptyList()
    }

    @Test
    fun `the screen stopping and starting does not look up again`() = runTest {
        coEvery { airportRepo.search(any()) } returns hamburgFound
        val viewModel = createViewModel().apply { init() }
        val first = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.airportSheet.collect {} }
        viewModel.onAirportTapped(hamburgFromRoute)

        first.cancel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.airportSheet.collect {} }

        viewModel.airportSheet.value.shouldNotBeNull().resolved shouldBe hamburg
        coVerify(exactly = 1) { airportRepo.search(any()) }
    }

    @Test
    fun `tapping the selected airport again keeps its sheet`() = runTest {
        coEvery { airportRepo.search(any()) } returns hamburgFound
        val viewModel = shownViewModel()
        viewModel.onAirportTapped(hamburgFromRoute)

        viewModel.onAirportTapped(AirportTarget.Full(hamburg))

        viewModel.selection.shouldBeInstanceOf<Selected.Airport>().target shouldBe hamburgFromRoute
        coVerify(exactly = 1) { airportRepo.search(any()) }
    }

    @Test
    fun `a route's airport without a position is drawn once the lookup found it`() = runTest {
        val answer = CompletableDeferred<AirportSearch>()
        coEvery { airportRepo.search(any()) } coAnswers { answer.await() }
        val viewModel = shownViewModel()
        viewModel.onAirportTapped(hamburgCodeOnly)

        viewModel.airportSheet.value.shouldNotBeNull().best shouldBe hamburgCodeOnly

        answer.complete(hamburgFound)

        viewModel.airportSheet.value.shouldNotBeNull().best shouldBe AirportTarget.Full(hamburg)
        viewModel.selection.shouldBeInstanceOf<Selected.Airport>().target shouldBe hamburgCodeOnly
    }

    @Test
    fun `the same partial airport requested again finishes its lookup`() = runTest {
        coEvery { airportRepo.search(any()) } returns hamburgFound
        val viewModel = shownViewModel()
        mapRequests.publish(showAirport(hamburgFromRoute))
        viewModel.airportSheet.value.shouldNotBeNull().apply {
            lookingUp shouldBe false
            resolved shouldBe hamburg
        }

        mapRequests.publish(showAirport(hamburgFromRoute))

        viewModel.selection.shouldBeInstanceOf<Selected.Airport>().target shouldBe hamburgFromRoute
        coVerify(exactly = 2) { airportRepo.search(listOf("EDDH")) }
        viewModel.airportSheet.value.shouldNotBeNull().apply {
            lookingUp shouldBe false
            resolved shouldBe hamburg
        }
    }
}
