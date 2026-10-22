package eu.darken.apl.map.ui

import android.content.Context
import android.os.SystemClock
import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.datastore.DataStoreValue
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.map.core.MapAircraftProvider
import eu.darken.apl.map.core.MapOptions
import eu.darken.apl.map.core.MapSettings
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.compat.AppCompatibility
import eu.darken.apl.server.compat.CompatState
import eu.darken.apl.watch.core.LocationWatchCreator
import eu.darken.apl.watch.core.LocationWatchNotAllowedException
import eu.darken.apl.watch.core.WatchLimit
import eu.darken.apl.watch.core.WatchLimitReachedException
import eu.darken.apl.watch.core.WatchRepo
import eu.darken.apl.watch.core.types.LocationWatch
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
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

class NativeMapViewModelAreaTest : BaseTest() {

    private val mapRequests = MapRequests()
    private val provider = mockk<MapAircraftProvider>(relaxed = true)
    private val watchRepo = mockk<WatchRepo>(relaxed = true) {
        every { limit } returns flowOf(WatchLimit(count = 0, max = null))
    }
    private val creator = mockk<LocationWatchCreator>(relaxed = true) {
        every { isAllowedNow() } returns true
        every { maxRadiusKm } returns flowOf(250)
        coEvery { labelFor(any(), any()) } returns "Aachen, DE"
    }
    private val aircraftRepo = mockk<AircraftRepo>(relaxed = true) {
        every { cache } returns MutableStateFlow(emptyMap())
    }

    private fun <T> setting(value: T) = mockk<DataStoreValue<T>> {
        every { flow } returns MutableStateFlow(value)
    }

    private val mapSettings = mockk<MapSettings>(relaxed = true) {
        every { isRestoreLastViewEnabled } returns setting(false)
        every { isMilitaryOnlyEnabled } returns setting(false)
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
        accessRepo = mockk(relaxed = true),
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

    private fun watch(label: String) = mockk<LocationWatch>(relaxed = true) {
        every { this@mockk.label } returns label
    }

    private fun zoneWatch() = mockk<LocationWatch>(relaxed = true) {
        every { id } returns "l1"
        every { latitude } returns 50.77
        every { longitude } returns 6.08
        // Stored under an older, higher limit
        every { radiusInMeters } returns 300_000f
        every { label } returns "Home"
    }

    @Test
    fun `a long-press on an area's center edits that watch, its outline makes way`() = runTest {
        every { watchRepo.watches } returns MutableStateFlow(listOf(zoneWatch()))
        val viewModel = createViewModel()
        viewModel.watchZones.first { it.isNotEmpty() }

        viewModel.onMapLongPressed(50.7701, 6.0801, zone = "l1")

        viewModel.areaDraft.value.shouldNotBeNull().apply {
            editing shouldBe "l1"
            latitude shouldBe 50.77
            longitude shouldBe 6.08
            label shouldBe "Home"
            shownRadius(listOf(2_000f, 25_000f, 250_000f)) shouldBe 300_000f
        }
        viewModel.watchZones.first() shouldBe emptyList()
        coVerify(exactly = 0) { creator.labelFor(any(), any()) }
    }

    @Test
    fun `saving an edited area changes the watch instead of adding one`() = runTest {
        every { watchRepo.watches } returns MutableStateFlow(listOf(zoneWatch()))
        val viewModel = createViewModel()
        viewModel.onMapLongPressed(50.77, 6.08, zone = "l1")
        viewModel.onMapLongPressed(51.0, 7.0)

        viewModel.createAreaWatch(300_000f)

        coVerify { watchRepo.updateLocation("l1", 51.0, 7.0, 300_000f, "Home") }
        coVerify(exactly = 0) { creator.create(any(), any(), any(), any(), any()) }
        viewModel.areaDraft.value.shouldBeNull()
    }

    @Test
    fun `an edited area's radius moved above the limit is not saved`() = runTest {
        every { watchRepo.watches } returns MutableStateFlow(listOf(zoneWatch()))
        val viewModel = createViewModel()
        viewModel.onMapLongPressed(50.77, 6.08, zone = "l1")

        viewModel.createAreaWatch(260_000f)

        coVerify(exactly = 0) { watchRepo.updateLocation(any(), any(), any(), any(), any()) }
        viewModel.areaDraft.value.shouldNotBeNull().saving shouldBe false
    }

    @Test
    fun `an edited watch deleted elsewhere closes its editor`() = runTest {
        val watches = MutableStateFlow(listOf(zoneWatch()))
        every { watchRepo.watches } returns watches
        val viewModel = createViewModel()
        viewModel.onMapLongPressed(50.77, 6.08, zone = "l1")
        viewModel.areaDraft.value.shouldNotBeNull()

        watches.value = emptyList()

        viewModel.areaDraft.value.shouldBeNull()
    }

    @Test
    fun `a map request closes an edit like a new area`() = runTest {
        every { watchRepo.watches } returns MutableStateFlow(listOf(zoneWatch()))
        val viewModel = createViewModel().apply { init() }
        viewModel.onMapLongPressed(50.77, 6.08, zone = "l1")
        viewModel.areaDraft.value.shouldNotBeNull()

        mapRequests.publish(MapOptions.focus("AAAAAA"))

        viewModel.areaDraft.value.shouldBeNull()
        viewModel.watchZones.first().map { it.watchId } shouldBe listOf("l1")
    }

    @Test
    fun `a tier without location watches gets no editor but the upgrade message`() = runTest {
        every { creator.isAllowedNow() } returns false
        val viewModel = createViewModel()

        viewModel.onMapLongPressed(50.77, 6.08)

        viewModel.areaDraft.value.shouldBeNull()
        viewModel.events.first() shouldBe NativeMapEvents.LocationWatchNotAllowed
    }

    @Test
    fun `a full watch list gets no editor but the limit message`() = runTest {
        every { watchRepo.limit } returns flowOf(WatchLimit(count = 10, max = 10))
        val viewModel = createViewModel()

        viewModel.onMapLongPressed(50.77, 6.08)

        viewModel.areaDraft.value.shouldBeNull()
        viewModel.events.first().shouldBeInstanceOf<NativeMapEvents.WatchLimitReached>()
    }

    @Test
    fun `the editor replaces the selected aircraft and is named after the place`() = runTest {
        val viewModel = createViewModel()
        viewModel.onAircraftTapped("AAAAAA")

        viewModel.onMapLongPressed(50.77, 6.08)

        verify { provider.select(null) }
        viewModel.areaDraft.value.shouldNotBeNull().apply {
            latitude shouldBe 50.77
            longitude shouldBe 6.08
            label shouldBe "Aachen, DE"
            resolving shouldBe false
        }
    }

    @Test
    fun `aircraft and the list can't be picked while the editor is open`() = runTest {
        val viewModel = createViewModel()
        viewModel.onMapLongPressed(50.77, 6.08)

        viewModel.onAircraftTapped("AAAAAA")
        viewModel.toggleSidebar()

        verify(exactly = 0) { provider.select("AAAAAA") }
        viewModel.isSidebarOpen.value shouldBe false
    }

    @Test
    fun `a name found for an earlier center is dropped`() = runTest {
        val first = CompletableDeferred<String>()
        coEvery { creator.labelFor(50.77, 6.08) } coAnswers { first.await() }
        coEvery { creator.labelFor(51.0, 7.0) } returns "Köln, DE"
        val viewModel = createViewModel()
        viewModel.onMapLongPressed(50.77, 6.08)

        viewModel.onMapLongPressed(51.0, 7.0)
        first.complete("Aachen, DE")

        viewModel.areaDraft.value.shouldNotBeNull().label shouldBe "Köln, DE"
    }

    @Test
    fun `a label the user wrote stays when the center moves`() = runTest {
        val viewModel = createViewModel()
        viewModel.onMapLongPressed(50.77, 6.08)
        viewModel.onAreaLabelChanged("Home")
        val session = viewModel.areaDraft.value!!.session

        viewModel.onMapLongPressed(51.0, 7.0)

        viewModel.areaDraft.value.shouldNotBeNull().apply {
            label shouldBe "Home"
            latitude shouldBe 51.0
            this.session shouldBe session
        }
        coVerify(exactly = 0) { creator.labelFor(51.0, 7.0) }
    }

    @Test
    fun `a second tap on Add while saving saves once`() = runTest {
        val saved = CompletableDeferred<LocationWatch>()
        coEvery { creator.create(any(), any(), any(), any(), any()) } coAnswers { saved.await() }
        val viewModel = createViewModel()
        viewModel.onMapLongPressed(50.77, 6.08)

        viewModel.createAreaWatch(25_000f)
        viewModel.createAreaWatch(25_000f)
        saved.complete(watch("Aachen, DE"))

        coVerify(exactly = 1) { creator.create(50.77, 6.08, 25_000f, "Aachen, DE", "") }
        viewModel.areaDraft.value.shouldBeNull()
        viewModel.events.first().shouldBeInstanceOf<NativeMapEvents.WatchAdded>()
    }

    @Test
    fun `a limit found when saving keeps the editor open`() = runTest {
        coEvery { creator.create(any(), any(), any(), any(), any()) } throws WatchLimitReachedException(10)
        val viewModel = createViewModel()
        viewModel.onMapLongPressed(50.77, 6.08)

        viewModel.createAreaWatch(25_000f)

        viewModel.areaDraft.value.shouldNotBeNull().saving shouldBe false
        viewModel.events.first().shouldBeInstanceOf<NativeMapEvents.WatchLimitReached>()
    }

    @Test
    fun `a tier that lost location watches when saving keeps the editor open`() = runTest {
        coEvery { creator.create(any(), any(), any(), any(), any()) } throws LocationWatchNotAllowedException()
        val viewModel = createViewModel()
        viewModel.onMapLongPressed(50.77, 6.08)

        viewModel.createAreaWatch(25_000f)

        viewModel.areaDraft.value.shouldNotBeNull()
        viewModel.events.first() shouldBe NativeMapEvents.LocationWatchNotAllowed
    }

    @Test
    fun `an editor still waiting to open stays closed after a map request`() = runTest {
        val limitAnswered = CompletableDeferred<Unit>()
        every { watchRepo.limit } returns flow {
            limitAnswered.await()
            emit(WatchLimit(count = 0, max = null))
        }
        val viewModel = createViewModel().apply { init() }
        viewModel.onMapLongPressed(50.77, 6.08)

        mapRequests.publish(MapOptions.focus("AAAAAA"))
        limitAnswered.complete(Unit)

        viewModel.areaDraft.value.shouldBeNull()
        verify(exactly = 0) { provider.select(null) }
    }

    @Test
    fun `a save finishing after its editor was replaced leaves the new one alone`() = runTest {
        val saved = CompletableDeferred<LocationWatch>()
        coEvery { creator.create(any(), any(), any(), any(), any()) } coAnswers { saved.await() }
        val named = CompletableDeferred<String>()
        coEvery { creator.labelFor(51.0, 7.0) } coAnswers { named.await() }
        val viewModel = createViewModel().apply { init() }
        viewModel.onMapLongPressed(50.77, 6.08)
        viewModel.createAreaWatch(25_000f)
        mapRequests.publish(MapOptions.focus("AAAAAA"))
        viewModel.onMapLongPressed(51.0, 7.0)

        saved.complete(watch("Aachen, DE"))
        named.complete("Köln, DE")

        viewModel.areaDraft.value.shouldNotBeNull().apply {
            latitude shouldBe 51.0
            label shouldBe "Köln, DE"
            resolving shouldBe false
        }
    }
}
