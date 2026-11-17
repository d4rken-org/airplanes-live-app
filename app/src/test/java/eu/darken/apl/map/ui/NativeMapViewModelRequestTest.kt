package eu.darken.apl.map.ui

import android.content.Context
import android.location.Location
import android.os.SystemClock
import androidx.lifecycle.viewModelScope
import eu.darken.apl.airport.core.AirportFlightsRepo
import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.compose.preview.FakeAircraft
import eu.darken.apl.common.datastore.DataStoreValue
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.map.core.AircraftKind
import eu.darken.apl.map.core.MapAircraftProvider
import eu.darken.apl.map.core.MapLabelFields
import eu.darken.apl.map.core.MapOptions
import eu.darken.apl.map.core.MapSettings
import eu.darken.apl.map.core.MapSidebarData
import eu.darken.apl.map.core.MapWatches
import eu.darken.apl.map.core.NativeMapStyle
import eu.darken.apl.map.core.overlay.OverlayRepo
import eu.darken.apl.map.core.overlay.OverlayState
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.compat.AppCompatibility
import eu.darken.apl.server.compat.CompatState
import eu.darken.apl.server.session.SessionManager
import eu.darken.apl.server.session.SessionState
import eu.darken.apl.upgrade.UpgradeRepo
import eu.darken.apl.watch.core.WatchRepo
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.Watch
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.coroutine.TestDispatcherProvider

class NativeMapViewModelRequestTest : BaseTest() {

    private val mapRequests = MapRequests()
    private val provider = mockk<MapAircraftProvider>(relaxed = true)
    private val storedWatches = MutableStateFlow<List<Watch>>(emptyList())
    private val watchRepo = mockk<WatchRepo>(relaxed = true) {
        every { watches } returns storedWatches
    }
    private val aircraftRepo = mockk<AircraftRepo>(relaxed = true) {
        every { cache } returns MutableStateFlow(emptyMap())
    }

    private fun <T> setting(value: T) = mockk<DataStoreValue<T>> {
        every { flow } returns MutableStateFlow(value)
    }

    private val chosenKinds = MutableStateFlow(emptySet<AircraftKind>())
    private val militaryOnly = MutableStateFlow(false)

    private val mapSettings = mockk<MapSettings>(relaxed = true) {
        every { isRestoreLastViewEnabled } returns setting(false)
        every { isMilitaryOnlyEnabled } returns mockk<DataStoreValue<Boolean>> {
            every { flow } returns militaryOnly
            coEvery { update(any()) } answers {
                val old = militaryOnly.value
                militaryOnly.value = firstArg<(Boolean) -> Boolean?>()(old) ?: false
                DataStoreValue.Updated(old, militaryOnly.value)
            }
        }
        every { aircraftKinds } returns mockk<DataStoreValue<Set<AircraftKind>>> { every { flow } returns chosenKinds }
        coEvery { showAllAircraftKinds() } answers { chosenKinds.value = emptySet() }
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

    private val upgradeRepo = mockk<UpgradeRepo>(relaxed = true)

    private fun createViewModel(dispatcherProvider: TestDispatcherProvider = TestDispatcherProvider()) = NativeMapViewModel(
        dispatcherProvider = dispatcherProvider,
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
        mapRequests = mapRequests,
        locationWatchCreator = mockk(relaxed = true),
        airportRepo = mockk(relaxed = true),
        sessionManager = mockk<SessionManager> { every { state } returns MutableStateFlow(SessionState.Unknown) },
        shapesRepo = mockk(relaxed = true),
        upgradeRepo = upgradeRepo,
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
    )

    @Test
    fun `a plain start shows everything from the default view`() = runTest {
        val viewModel = createViewModel().apply { init() }

        viewModel.initialCamera.value shouldBe MapOptions.Camera(lat = 30.0, lon = 0.0, zoom = 3.0)
        verify(exactly = 0) { provider.pin(any()) }
        verify(exactly = 0) { provider.select(any()) }
    }

    @Test
    fun `chosen kinds reach the map's filter, also when they change`() = runTest {
        chosenKinds.value = setOf(AircraftKind.HELICOPTER)
        createViewModel().init()

        verify { provider.setFilter(match { it.kinds == setOf(AircraftKind.HELICOPTER) }) }

        chosenKinds.value = setOf(AircraftKind.HELICOPTER, AircraftKind.GLIDER)

        verify { provider.setFilter(match { it.kinds == setOf(AircraftKind.HELICOPTER, AircraftKind.GLIDER) }) }
    }

    @Test
    fun `a request replaces what an earlier one pinned`() = runTest {
        createViewModel().init()
        mapRequests.publish(MapOptions.focus(listOf("AAAAAA", "BBBBBB")))

        mapRequests.publish(MapOptions.focus("CCCCCC"))

        verifyOrder {
            provider.pin(listOf("AAAAAA", "BBBBBB"))
            provider.pin(emptyList())
            provider.select("CCCCCC")
        }
    }

    @Test
    fun `a watch added while the map is shown says so`() = runTest {
        coEvery { watchRepo.addAircraft(any()) } returns WatchRepo.AircraftAdd.Created(watch("AAAAAA"))
        val viewModel = createViewModel().apply { onScreenShown() }

        viewModel.onWatchAction("AAAAAA")

        viewModel.events.first().shouldBeInstanceOf<NativeMapEvents.WatchAdded>()
    }

    @Test
    fun `a watch added after leaving and coming back says nothing`() = runTest {
        val added = CompletableDeferred<WatchRepo.AircraftAdd>()
        coEvery { watchRepo.addAircraft(any()) } coAnswers { added.await() }
        val viewModel = createViewModel().apply { onScreenShown() }
        viewModel.onWatchAction("AAAAAA")

        viewModel.onScreenHidden()
        viewModel.onScreenShown()
        added.complete(WatchRepo.AircraftAdd.Created(watch("AAAAAA")))

        withTimeoutOrNull(1_000) { viewModel.events.first() } shouldBe null
    }

    private fun watch(hex: String) = mockk<AircraftWatch>(relaxed = true) {
        every { this@mockk.hex } returns hex
    }

    @Test
    fun `a map opened for a place starts there`() = runTest {
        val camera = MapOptions.Camera(lat = 50.0, lon = 8.0, zoom = 9.0)
        mapRequests.publish(MapOptions(camera = camera))

        val viewModel = createViewModel().apply { init() }

        viewModel.initialCamera.value shouldBe camera
    }

    @Test
    fun `a place asked for while the map is not shown is where its next view starts`() = runTest {
        val viewModel = createViewModel().apply { init() }
        val camera = MapOptions.Camera(lat = 50.0, lon = 8.0, zoom = 9.0)

        mapRequests.publish(MapOptions(camera = camera))

        viewModel.initialCamera.value shouldBe camera
    }

    private fun lastFilter(): MapAircraftProvider.Filter {
        val filters = mutableListOf<MapAircraftProvider.Filter>()
        verify { provider.setFilter(capture(filters)) }
        return filters.last()
    }

    @Test
    fun `the list's search and watched filter reach the map while the list is closed`() = runTest {
        storedWatches.value = listOf(watch("AAAAAA"))
        val viewModel = createViewModel().apply { init() }

        viewModel.setListQuery("  dlh ")
        lastFilter() shouldBe MapAircraftProvider.Filter(query = "dlh")

        viewModel.toggleListWatchedOnly()
        lastFilter() shouldBe MapAircraftProvider.Filter(query = "dlh", watches = MapWatches(hexes = setOf("AAAAAA")))
    }

    @Test
    fun `a watch added while only watched aircraft are shown reaches the map`() = runTest {
        val viewModel = createViewModel().apply { init() }
        viewModel.toggleListWatchedOnly()
        lastFilter().watches shouldBe MapWatches()

        storedWatches.value = listOf(watch("AAAAAA"))

        lastFilter().watches shouldBe MapWatches(hexes = setOf("AAAAAA"))
    }

    @Test
    fun `a request for aircraft clears the list's search and watched filter, not its sort`() = runTest {
        val viewModel = createViewModel().apply { init() }
        viewModel.setListQuery("dlh")
        viewModel.toggleListWatchedOnly()
        viewModel.setListSort(MapSidebarData.SortField.ALTITUDE)

        mapRequests.publish(MapOptions.focus("CCCCCC"))

        viewModel.listControls.value shouldBe MapSidebarData.Controls(sort = MapSidebarData.SortField.ALTITUDE)
        lastFilter().query shouldBe ""
        lastFilter().watches shouldBe null

        viewModel.setListQuery("dlh")
        viewModel.toggleListWatchedOnly()

        mapRequests.publish(MapOptions.focus(listOf("AAAAAA", "BBBBBB")))

        viewModel.listControls.value shouldBe MapSidebarData.Controls(sort = MapSidebarData.SortField.ALTITUDE)
        lastFilter().query shouldBe ""
        lastFilter().watches shouldBe null
    }

    @Test
    fun `a request for a place keeps the list's search and watched filter`() = runTest {
        val viewModel = createViewModel().apply { init() }
        viewModel.setListQuery("dlh")
        viewModel.toggleListWatchedOnly()

        mapRequests.publish(MapOptions(camera = MapOptions.Camera(lat = 50.0, lon = 8.0, zoom = 9.0)))

        viewModel.listControls.value shouldBe MapSidebarData.Controls(query = "dlh", watchedOnly = true)
        lastFilter().query shouldBe "dlh"
        lastFilter().watches shouldBe MapWatches()
    }

    @Test
    fun `clearing all of the list's filters also clears the map's, not the sort`() = runTest {
        militaryOnly.value = true
        chosenKinds.value = setOf(AircraftKind.HELICOPTER)
        val viewModel = createViewModel().apply { init() }
        mapRequests.publish(MapOptions.focus(listOf("AAAAAA", "BBBBBB")))
        viewModel.setListQuery("dlh")
        viewModel.toggleListWatchedOnly()
        viewModel.setListSort(MapSidebarData.SortField.ALTITUDE)
        lastFilter().isolated shouldBe setOf("AAAAAA", "BBBBBB")

        viewModel.clearAllListFilters()

        viewModel.listControls.value shouldBe MapSidebarData.Controls(sort = MapSidebarData.SortField.ALTITUDE)
        militaryOnly.value shouldBe false
        coVerify { mapSettings.showAllAircraftKinds() }
        lastFilter() shouldBe MapAircraftProvider.Filter()
    }

    // Following

    private val providerFrames = MutableStateFlow(
        MapAircraftProvider.Frame(
            traffic = emptyList(), trafficVersion = 1, fades = emptyMap(), selectedHex = null, selected = null,
            selectedDetails = null, selectedMissing = null, selectedTrail = emptyList(), selectedTrailHead = null,
            tracks = null, viewport = null, onScreen = emptyList(), shownCount = 0, capped = false, totalMatching = null,
        )
    )

    private val followableViewModels = mutableListOf<NativeMapViewModel>()

    /** With what the map's state is built from, so following shows in it, and its own scope on virtual time. */
    private fun TestScope.followableViewModel(): NativeMapViewModel {
        every { provider.frames } returns providerFrames
        every { provider.state } returns MutableStateFlow(null)
        every { aircraftRepo.cachedByHex(any()) } returns flowOf(null)
        every { upgradeRepo.upgradeInfo } returns flowOf(mockk(relaxed = true))
        every { mapSettings.nativeMapStyle } returns setting(NativeMapStyle.AUTO.key)
        every { mapSettings.isLabelsEnabled } returns setting(true)
        every { mapSettings.labelFields } returns flowOf(MapLabelFields())
        every { mapSettings.isAllTracksEnabled } returns setting(false)
        every { mapSettings.isMilitaryHighlightEnabled } returns setting(true)
        every { mapSettings.isWatchHighlightEnabled } returns setting(true)
        every { mapSettings.enabledOverlays } returns setting(emptySet())
        return createViewModel(TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler))).also { vm ->
            followableViewModels += vm
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        }
    }

    // The camera sampling never ends on its own, the test's end would wait for it
    private fun followTest(body: suspend TestScope.() -> Unit) = runTest {
        try {
            body()
        } finally {
            followableViewModels.forEach { it.viewModelScope.cancel() }
        }
    }

    private fun TestScope.eventsOf(vm: NativeMapViewModel): List<NativeMapEvents> {
        val events = mutableListOf<NativeMapEvents>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.toList(events) }
        return events
    }

    private fun List<NativeMapEvents>.cameras() = filterIsInstance<NativeMapEvents.MoveCamera>().map { it.camera }

    private fun List<NativeMapEvents>.peeks() = filterIsInstance<NativeMapEvents.PeekSheet>()

    private val NativeMapViewModel.following: Boolean
        get() = state.value.shouldNotBeNull().toggles.follow

    private fun cachedAt(hex: String, lat: Double, lon: Double) {
        every { aircraftRepo.cache } returns MutableStateFlow(
            mapOf(
                hex to FakeAircraft(
                    hex = hex,
                    location = mockk<Location> {
                        every { latitude } returns lat
                        every { longitude } returns lon
                    },
                )
            )
        )
    }

    private val cachedCamera = MapOptions.Camera(lat = 50.1, lon = 8.6, zoom = 9.0)

    @Test
    fun `an aircraft to follow when the map opens is followed with its sheet at the peek`() = followTest {
        cachedAt("CCCCCC", lat = 50.1, lon = 8.6)
        mapRequests.publish(MapOptions.focus("CCCCCC").copy(follow = true))
        val viewModel = followableViewModel()
        val events = eventsOf(viewModel)

        viewModel.init()

        viewModel.state.value.shouldNotBeNull().selection shouldBe NativeMapViewModel.Selected.Aircraft("CCCCCC")
        viewModel.following shouldBe true
        events.peeks() shouldBe listOf(NativeMapEvents.PeekSheet(NativeMapViewModel.Selected.Aircraft("CCCCCC")))
        // Where the map opens, not a move
        viewModel.initialCamera.value shouldBe cachedCamera
        events.cameras() shouldBe emptyList()
    }

    @Test
    fun `an aircraft asked for when the map opens without follow is not followed`() = followTest {
        cachedAt("CCCCCC", lat = 50.1, lon = 8.6)
        mapRequests.publish(MapOptions.focus("CCCCCC"))
        val viewModel = followableViewModel()
        val events = eventsOf(viewModel)

        viewModel.init()

        viewModel.following shouldBe false
        events.peeks() shouldBe emptyList()
        viewModel.initialCamera.value shouldBe cachedCamera
    }

    @Test
    fun `an aircraft to follow while the map runs is followed without moving to its cached position`() = followTest {
        cachedAt("CCCCCC", lat = 50.1, lon = 8.6)
        val viewModel = followableViewModel()
        val events = eventsOf(viewModel)
        viewModel.init()

        mapRequests.publish(MapOptions.focus("CCCCCC").copy(follow = true))

        viewModel.following shouldBe true
        events.peeks() shouldBe listOf(NativeMapEvents.PeekSheet(NativeMapViewModel.Selected.Aircraft("CCCCCC")))
        events.cameras() shouldBe emptyList()
    }

    @Test
    fun `a camera in a request to follow is still where the map goes`() = followTest {
        cachedAt("CCCCCC", lat = 50.1, lon = 8.6)
        val viewModel = followableViewModel()
        val events = eventsOf(viewModel)
        viewModel.init()
        val camera = MapOptions.Camera(lat = 50.0, lon = 8.0, zoom = 7.0)

        mapRequests.publish(MapOptions.focus("CCCCCC").copy(camera = camera, follow = true))

        events.cameras() shouldBe listOf(camera)
        viewModel.following shouldBe true
    }

    @Test
    fun `a request without follow while the map runs ends following and moves to the cached position`() = followTest {
        cachedAt("CCCCCC", lat = 50.1, lon = 8.6)
        val viewModel = followableViewModel()
        val events = eventsOf(viewModel)
        viewModel.init()
        mapRequests.publish(MapOptions.focus("CCCCCC").copy(follow = true))

        mapRequests.publish(MapOptions.focus("CCCCCC"))

        viewModel.following shouldBe false
        events.peeks().size shouldBe 1
        events.cameras() shouldBe listOf(cachedCamera)
    }
}
