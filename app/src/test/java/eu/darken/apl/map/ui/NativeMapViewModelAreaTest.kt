package eu.darken.apl.map.ui

import android.content.Context
import android.os.SystemClock
import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.datastore.DataStoreValue
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.map.core.MapAircraftProvider
import eu.darken.apl.map.core.MapOptions
import eu.darken.apl.map.core.MapSettings
import eu.darken.apl.map.core.overlay.OverlayRepo
import eu.darken.apl.map.core.overlay.OverlayState
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.compat.AppCompatibility
import eu.darken.apl.server.compat.CompatState
import eu.darken.apl.server.session.SessionManager
import eu.darken.apl.server.session.SessionState
import eu.darken.apl.watch.core.LocationWatchChangedException
import eu.darken.apl.watch.core.LocationWatchCreator
import eu.darken.apl.watch.core.LocationWatchNotAllowedException
import eu.darken.apl.watch.core.WatchLimit
import eu.darken.apl.watch.core.WatchLimitReachedException
import eu.darken.apl.watch.core.WatchRepo
import eu.darken.apl.watch.core.types.LocationWatch
import eu.darken.apl.watch.ui.AltitudeBand
import eu.darken.apl.watch.ui.AltitudeBandInput
import eu.darken.apl.watch.ui.AltitudeBandUnit
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
        every { aircraftKinds } returns setting(emptySet())
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
        mapRequests = mapRequests,
        locationWatchCreator = creator,
        airportRepo = mockk(relaxed = true),
        sessionManager = mockk<SessionManager> { every { state } returns MutableStateFlow(SessionState.Unknown) },
        shapesRepo = mockk(relaxed = true),
        upgradeRepo = mockk(relaxed = true),
        compatibility = mockk<AppCompatibility> {
            every { state } returns MutableStateFlow(CompatState.Ok)
        },
        overlayRepo = mockk<OverlayRepo> {
            every { state } returns MutableStateFlow(OverlayState())
        },
        serverEndpoint = mockk(relaxed = true),
    )

    private fun watch(label: String) = mockk<LocationWatch>(relaxed = true) {
        every { this@mockk.label } returns label
    }

    private fun zoneWatch(
        radius: Float = 300_000f,
        revision: Long = 4,
        minAltitudeFt: Int? = null,
        maxAltitudeFt: Int? = null,
    ) = mockk<LocationWatch>(relaxed = true) {
        every { id } returns "l1"
        every { latitude } returns 50.77
        every { longitude } returns 6.08
        // Stored under an older, higher limit
        every { radiusInMeters } returns radius
        every { this@mockk.minAltitudeFt } returns minAltitudeFt
        every { this@mockk.maxAltitudeFt } returns maxAltitudeFt
        every { label } returns "Home"
        every { definitionRevision } returns revision
    }

    private val stops = listOf(2_000f, 25_000f, 250_000f)

    private fun feet(band: AltitudeBand = AltitudeBand(null, null)) = AltitudeBandInput.of(band, AltitudeBandUnit.FEET)

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
        every { watchRepo.watches } returns MutableStateFlow(listOf(zoneWatch(radius = 25_000f)))
        val viewModel = createViewModel()
        viewModel.onMapLongPressed(50.77, 6.08, zone = "l1")
        viewModel.onMapLongPressed(51.0, 7.0)

        viewModel.createAreaWatch(25_000f)

        coVerify { watchRepo.updateLocation("l1", 51.0, 7.0, 25_000f, null, null, "Home", expectedRevision = 4) }
        coVerify(exactly = 0) { creator.create(any(), any(), any(), any(), any(), any(), any()) }
        viewModel.areaDraft.value.shouldBeNull()
    }

    @Test
    fun `a new label alone keeps a radius above today's limit`() = runTest {
        every { watchRepo.watches } returns MutableStateFlow(listOf(zoneWatch()))
        val viewModel = createViewModel()
        viewModel.onMapLongPressed(50.77, 6.08, zone = "l1")
        viewModel.onAreaLabelChanged("Home 2")

        viewModel.createAreaWatch(300_000f)

        coVerify { watchRepo.updateLocation("l1", 50.77, 6.08, 300_000f, null, null, "Home 2", expectedRevision = 4) }
    }

    @Test
    fun `a moved area is held to today's limit`() = runTest {
        every { watchRepo.watches } returns MutableStateFlow(listOf(zoneWatch()))
        val viewModel = createViewModel()
        viewModel.onMapLongPressed(50.77, 6.08, zone = "l1")
        viewModel.onMapLongPressed(51.0, 7.0)

        viewModel.areaDraft.value.shouldNotBeNull().shownRadius(stops) shouldBe 250_000f
        viewModel.createAreaWatch(300_000f)

        coVerify(exactly = 0) { watchRepo.updateLocation(any(), any(), any(), any(), any(), any(), any(), any()) }
        viewModel.areaDraft.value.shouldNotBeNull().saving shouldBe false
    }

    @Test
    fun `an edited area's radius moved above the limit is not saved`() = runTest {
        every { watchRepo.watches } returns MutableStateFlow(listOf(zoneWatch()))
        val viewModel = createViewModel()
        viewModel.onMapLongPressed(50.77, 6.08, zone = "l1")

        viewModel.createAreaWatch(260_000f)

        coVerify(exactly = 0) { watchRepo.updateLocation(any(), any(), any(), any(), any(), any(), any(), any()) }
        viewModel.areaDraft.value.shouldNotBeNull().saving shouldBe false
    }

    @Test
    fun `removing an edited area deletes its watch and closes the editor`() = runTest {
        every { watchRepo.watches } returns MutableStateFlow(listOf(zoneWatch()))
        val viewModel = createViewModel()
        viewModel.onMapLongPressed(50.77, 6.08, zone = "l1")

        viewModel.deleteAreaWatch()

        coVerify(exactly = 1) { watchRepo.delete("l1") }
        viewModel.areaDraft.value.shouldBeNull()
    }

    @Test
    fun `a new area has nothing to remove`() = runTest {
        val viewModel = createViewModel()
        viewModel.onMapLongPressed(50.77, 6.08)

        viewModel.deleteAreaWatch()

        coVerify(exactly = 0) { watchRepo.delete(any()) }
        viewModel.areaDraft.value.shouldNotBeNull()
    }

    @Test
    fun `an edit whose watch changed meanwhile is refused and closed`() = runTest {
        every { watchRepo.watches } returns MutableStateFlow(listOf(zoneWatch(radius = 25_000f)))
        coEvery { watchRepo.updateLocation(any(), any(), any(), any(), any(), any(), any(), any()) } throws
            LocationWatchChangedException("l1")
        val viewModel = createViewModel()
        viewModel.onMapLongPressed(50.77, 6.08, zone = "l1")

        viewModel.createAreaWatch(25_000f)

        viewModel.areaDraft.value.shouldBeNull()
        viewModel.errorEvents.first().shouldBeInstanceOf<LocationWatchChangedException>()
    }

    @Test
    fun `an edited watch changed elsewhere closes its editor`() = runTest {
        val watches = MutableStateFlow(listOf(zoneWatch()))
        every { watchRepo.watches } returns watches
        val viewModel = createViewModel()
        viewModel.onMapLongPressed(50.77, 6.08, zone = "l1")
        viewModel.areaDraft.value.shouldNotBeNull()

        watches.value = listOf(zoneWatch(radius = 50_000f, revision = 5))

        viewModel.areaDraft.value.shouldBeNull()
    }

    @Test
    fun `resizing an edited area keeps its altitude band`() = runTest {
        every { watchRepo.watches } returns MutableStateFlow(
            listOf(zoneWatch(radius = 25_000f, minAltitudeFt = 1_000, maxAltitudeFt = 10_000))
        )
        val viewModel = createViewModel()
        viewModel.onMapLongPressed(50.77, 6.08, zone = "l1")
        viewModel.onAreaRadiusChanged(250_000f)

        viewModel.createAreaWatch(250_000f)

        coVerify { watchRepo.updateLocation("l1", 50.77, 6.08, 250_000f, 1_000, 10_000, "Home", expectedRevision = 4) }
    }

    @Test
    fun `moving an edited area keeps its altitude band`() = runTest {
        every { watchRepo.watches } returns MutableStateFlow(
            listOf(zoneWatch(radius = 25_000f, minAltitudeFt = 0, maxAltitudeFt = null))
        )
        val viewModel = createViewModel()
        viewModel.onMapLongPressed(50.77, 6.08, zone = "l1")
        viewModel.onMapLongPressed(51.0, 7.0)

        viewModel.createAreaWatch(25_000f)

        coVerify { watchRepo.updateLocation("l1", 51.0, 7.0, 25_000f, 0, null, "Home", expectedRevision = 4) }
    }

    @Test
    fun `a new area saves the altitude band entered`() = runTest {
        coEvery { creator.create(any(), any(), any(), any(), any(), any(), any()) } returns watch("Aachen, DE")
        val viewModel = createViewModel()
        viewModel.onMapLongPressed(50.77, 6.08)
        viewModel.onAreaBandChanged(feet().withMinText("1000").withMaxText("10000"))

        viewModel.createAreaWatch(25_000f)

        coVerify { creator.create(50.77, 6.08, 25_000f, 1_000, 10_000, "Aachen, DE", "") }
    }

    @Test
    fun `a band entered before the center moves is saved`() = runTest {
        coEvery { creator.create(any(), any(), any(), any(), any(), any(), any()) } returns watch("Aachen, DE")
        val viewModel = createViewModel()
        viewModel.onMapLongPressed(50.77, 6.08)
        viewModel.onAreaBandChanged(feet().withMinText("1000"))

        viewModel.onMapLongPressed(51.0, 7.0)
        viewModel.createAreaWatch(25_000f)

        coVerify { creator.create(51.0, 7.0, 25_000f, 1_000, null, "Aachen, DE", "") }
    }

    @Test
    fun `an invalid band blocks saving, also after the center moves`() = runTest {
        val viewModel = createViewModel()
        viewModel.onMapLongPressed(50.77, 6.08)
        viewModel.onAreaBandChanged(feet().withMinText("abc"))

        viewModel.onMapLongPressed(51.0, 7.0)
        viewModel.areaDraft.value.shouldNotBeNull().band.shouldBeNull()
        viewModel.createAreaWatch(25_000f)

        coVerify(exactly = 0) { creator.create(any(), any(), any(), any(), any(), any(), any()) }
        viewModel.areaDraft.value.shouldNotBeNull().saving shouldBe false
    }

    @Test
    fun `a new label alone keeps an edited watch's band and history`() = runTest {
        every { watchRepo.watches } returns MutableStateFlow(
            listOf(zoneWatch(radius = 25_000f, minAltitudeFt = 1_000, maxAltitudeFt = 10_000))
        )
        val viewModel = createViewModel()
        viewModel.onMapLongPressed(50.77, 6.08, zone = "l1")
        viewModel.onAreaLabelChanged("Home 2")

        viewModel.areaDraft.value.shouldNotBeNull().resetsHistory(25_000f) shouldBe false
        viewModel.createAreaWatch(25_000f)

        coVerify { watchRepo.updateLocation("l1", 50.77, 6.08, 25_000f, 1_000, 10_000, "Home 2", expectedRevision = 4) }
    }

    @Test
    fun `an untouched bound shown in meters keeps its stored feet`() = runTest {
        every { watchRepo.watches } returns MutableStateFlow(
            listOf(zoneWatch(radius = 25_000f, minAltitudeFt = 1_000, maxAltitudeFt = 10_000))
        )
        val viewModel = createViewModel()
        viewModel.onMapLongPressed(50.77, 6.08, zone = "l1")
        viewModel.onAreaBandChanged(
            AltitudeBandInput.of(AltitudeBand(1_000, 10_000), AltitudeBandUnit.METERS).withMaxText("3000")
        )

        viewModel.createAreaWatch(25_000f)

        coVerify { watchRepo.updateLocation("l1", 50.77, 6.08, 25_000f, 1_000, 9_843, "Home", expectedRevision = 4) }
    }

    @Test
    fun `a new band alone is saved and starts the history over`() = runTest {
        every { watchRepo.watches } returns MutableStateFlow(
            listOf(zoneWatch(radius = 25_000f, minAltitudeFt = 1_000, maxAltitudeFt = 10_000))
        )
        val viewModel = createViewModel()
        viewModel.onMapLongPressed(50.77, 6.08, zone = "l1")
        viewModel.onAreaBandChanged(feet(AltitudeBand(1_000, 10_000)).withMaxText("20000"))

        viewModel.areaDraft.value.shouldNotBeNull().resetsHistory(25_000f) shouldBe true
        viewModel.createAreaWatch(25_000f)

        coVerify { watchRepo.updateLocation("l1", 50.77, 6.08, 25_000f, 1_000, 20_000, "Home", expectedRevision = 4) }
    }

    @Test
    fun `a cleared bound stays cleared when the center moves`() = runTest {
        every { watchRepo.watches } returns MutableStateFlow(
            listOf(zoneWatch(radius = 25_000f, minAltitudeFt = 1_000, maxAltitudeFt = 10_000))
        )
        val viewModel = createViewModel()
        viewModel.onMapLongPressed(50.77, 6.08, zone = "l1")
        viewModel.onAreaBandChanged(feet(AltitudeBand(1_000, 10_000)).withMinText(""))

        viewModel.onMapLongPressed(51.0, 7.0)
        viewModel.createAreaWatch(25_000f)

        coVerify { watchRepo.updateLocation("l1", 51.0, 7.0, 25_000f, null, 10_000, "Home", expectedRevision = 4) }
    }

    @Test
    fun `an edited watch whose altitude band changed elsewhere closes its editor`() = runTest {
        val watches = MutableStateFlow(listOf(zoneWatch(minAltitudeFt = 1_000)))
        every { watchRepo.watches } returns watches
        val viewModel = createViewModel()
        viewModel.onMapLongPressed(50.77, 6.08, zone = "l1")
        viewModel.areaDraft.value.shouldNotBeNull()

        watches.value = listOf(zoneWatch(minAltitudeFt = 2_000, revision = 5))

        viewModel.areaDraft.value.shouldBeNull()
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
        coEvery { creator.create(any(), any(), any(), any(), any(), any(), any()) } coAnswers { saved.await() }
        val viewModel = createViewModel()
        viewModel.onMapLongPressed(50.77, 6.08)

        viewModel.createAreaWatch(25_000f)
        viewModel.createAreaWatch(25_000f)
        saved.complete(watch("Aachen, DE"))

        coVerify(exactly = 1) { creator.create(50.77, 6.08, 25_000f, null, null, "Aachen, DE", "") }
        viewModel.areaDraft.value.shouldBeNull()
        viewModel.events.first().shouldBeInstanceOf<NativeMapEvents.WatchAdded>()
    }

    @Test
    fun `a limit found when saving keeps the editor open`() = runTest {
        coEvery { creator.create(any(), any(), any(), any(), any(), any(), any()) } throws
            WatchLimitReachedException(10)
        val viewModel = createViewModel()
        viewModel.onMapLongPressed(50.77, 6.08)

        viewModel.createAreaWatch(25_000f)

        viewModel.areaDraft.value.shouldNotBeNull().saving shouldBe false
        viewModel.events.first().shouldBeInstanceOf<NativeMapEvents.WatchLimitReached>()
    }

    @Test
    fun `a tier that lost location watches when saving keeps the editor open`() = runTest {
        coEvery { creator.create(any(), any(), any(), any(), any(), any(), any()) } throws
            LocationWatchNotAllowedException()
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
        coEvery { creator.create(any(), any(), any(), any(), any(), any(), any()) } coAnswers { saved.await() }
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
