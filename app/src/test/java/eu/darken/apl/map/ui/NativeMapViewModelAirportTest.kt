package eu.darken.apl.map.ui

import android.content.Context
import android.os.SystemClock
import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.datastore.DataStoreValue
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.map.core.AircraftHistoryTracks
import eu.darken.apl.map.core.AircraftKind
import eu.darken.apl.map.core.MapAircraftProvider
import eu.darken.apl.map.core.MapAirport
import eu.darken.apl.map.core.MapOptions
import eu.darken.apl.map.core.MapSettings
import eu.darken.apl.map.core.MapSidebarData
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.server.api.TrailPoint
import eu.darken.apl.server.compat.AppCompatibility
import eu.darken.apl.server.compat.CompatState
import eu.darken.apl.watch.core.LocationWatchCreator
import eu.darken.apl.watch.core.WatchLimit
import eu.darken.apl.watch.core.WatchRepo
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.Watch
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.coroutine.TestDispatcherProvider

class NativeMapViewModelAirportTest : BaseTest() {

    private val mapRequests = MapRequests()
    private val provider = mockk<MapAircraftProvider>(relaxed = true) {
        every { state } returns MutableStateFlow(null)
    }
    private val storedWatches = MutableStateFlow<List<Watch>>(emptyList())
    private val watchRepo = mockk<WatchRepo>(relaxed = true) {
        every { watches } returns storedWatches
        every { limit } returns flowOf(WatchLimit(count = 0, max = null))
    }
    private val aircraftRepo = mockk<AircraftRepo>(relaxed = true) {
        every { cache } returns MutableStateFlow(emptyMap())
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

    private fun <T> setting(value: T) = mockk<DataStoreValue<T>> {
        every { flow } returns MutableStateFlow(value)
    }

    private val militaryOnly = MutableStateFlow(false)
    private val militaryOnlySetting = mockk<DataStoreValue<Boolean>> { every { flow } returns militaryOnly }
    private val chosenKinds = MutableStateFlow(emptySet<AircraftKind>())
    private val kindsSetting = mockk<DataStoreValue<Set<AircraftKind>>> { every { flow } returns chosenKinds }

    private val mapSettings = mockk<MapSettings>(relaxed = true) {
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
        accessRepo = accessRepo,
        serverClock = ServerClock(object : MonotonicClock {
            override fun elapsed(): Long = 0L
        }),
        mapRequests = mapRequests,
        locationWatchCreator = creator,
        shapesRepo = mockk(relaxed = true),
        upgradeRepo = mockk(relaxed = true),
        compatibility = mockk<AppCompatibility> {
            every { state } returns MutableStateFlow(CompatState.Ok)
        },
    )

    private val frankfurt = MapAirport(
        id = "EDDF",
        name = "Frankfurt am Main Airport",
        code = "FRA",
        lat = 50.0333,
        lon = 8.5706,
        type = "large_airport",
    )
    private val munich = MapAirport(
        id = "EDDM",
        name = "Munich Airport",
        code = "MUC",
        lat = 48.3538,
        lon = 11.7861,
        type = "large_airport",
    )

    private fun showAirport(airport: MapAirport) = MapOptions(
        camera = MapOptions.Camera(lat = airport.lat, lon = airport.lon, zoom = 12.0),
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

    @Test
    fun `a plain start shows no airport`() = runTest {
        val viewModel = createViewModel().apply { init() }

        viewModel.airport.value.shouldBeNull()
    }

    @Test
    fun `an airport asked for when the map opens is shown there`() = runTest {
        mapRequests.publish(showAirport(frankfurt))

        val viewModel = createViewModel().apply { init() }

        viewModel.airport.value shouldBe frankfurt
        viewModel.initialCamera.value shouldBe MapOptions.Camera(lat = frankfurt.lat, lon = frankfurt.lon, zoom = 12.0)
    }

    @Test
    fun `an airport asked for while the map runs is shown there`() = runTest {
        val viewModel = createViewModel().apply { init() }

        mapRequests.publish(showAirport(frankfurt))

        viewModel.airport.value shouldBe frankfurt
        viewModel.initialCamera.value shouldBe MapOptions.Camera(lat = frankfurt.lat, lon = frankfurt.lon, zoom = 12.0)
    }

    @Test
    fun `the next airport replaces the shown one`() = runTest {
        val viewModel = createViewModel().apply { init() }
        mapRequests.publish(showAirport(frankfurt))

        mapRequests.publish(showAirport(munich))

        viewModel.airport.value shouldBe munich
    }

    @Test
    fun `a request without an airport removes the shown one`() = runTest {
        mapRequests.publish(showAirport(frankfurt))
        val viewModel = createViewModel().apply { init() }

        mapRequests.publish(MapOptions.focus("CCCCCC"))

        viewModel.airport.value.shouldBeNull()

        mapRequests.publish(showAirport(frankfurt))
        mapRequests.publish(MapOptions(camera = MapOptions.Camera(lat = 50.0, lon = 8.0, zoom = 9.0)))

        viewModel.airport.value.shouldBeNull()
    }

    @Test
    fun `clearing the airport removes it`() = runTest {
        val viewModel = createViewModel().apply { init() }
        mapRequests.publish(showAirport(frankfurt))

        viewModel.clearAirport()

        viewModel.airport.value.shouldBeNull()
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
        val viewModel = createViewModel().apply { init() }

        mapRequests.publish(showAirport(munich))

        viewModel.airport.value shouldBe munich
        coVerify(exactly = 0) { militaryOnlySetting.update(any()) }
        coVerify(exactly = 0) { kindsSetting.update(any()) }
        coVerify(exactly = 0) { mapSettings.toggleAircraftKind(any()) }
        coVerify(exactly = 0) { mapSettings.showAllAircraftKinds() }
        lastFilter().militaryOnly shouldBe true
        lastFilter().kinds shouldBe setOf(AircraftKind.HELICOPTER)
    }

    @Test
    fun `an airport replaces the selection, the pins and isolating them`() = runTest {
        val viewModel = createViewModel().apply { init() }
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
        viewModel.airport.value shouldBe frankfurt
    }

    @Test
    fun `an airport ends an open history`() = runTest {
        access.value = mockk<AccessState>(relaxed = true) {
            every { hasAircraftHistory } returns true
            every { offersAircraftHistory } returns true
        }
        val base = 1_791_972_000_000L
        coEvery { provider.loadHistory("3C65A3") } returns AircraftHistoryTracks.Track(
            points = listOf(0L, 60_000, 120_000, 3_600_000, 3_660_000).map { TrailPoint(50.0, 8.0, 35_000.0, base + it) },
            maxGapMs = 300_000,
            from = base - 3_600_000,
            until = base + 7_200_000,
        )
        val viewModel = createViewModel().apply { init() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.historyView.collect {} }
        viewModel.onAircraftTapped("3C65A3")
        viewModel.openHistory()
        viewModel.historyView.value.shouldNotBeNull()

        mapRequests.publish(showAirport(frankfurt))

        viewModel.historyView.value.shouldBeNull()
        verify { provider.select(null) }
        viewModel.airport.value shouldBe frankfurt
    }

    @Test
    fun `an airport closes an open area editor`() = runTest {
        val viewModel = createViewModel().apply { init() }
        viewModel.onMapLongPressed(50.77, 6.08)
        viewModel.areaDraft.value.shouldNotBeNull()

        mapRequests.publish(showAirport(frankfurt))

        viewModel.areaDraft.value.shouldBeNull()
        viewModel.airport.value shouldBe frankfurt
    }
}
