package eu.darken.apl.map.ui

import android.content.Context
import android.location.Location
import android.os.SystemClock
import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.compose.preview.FakeAircraft
import eu.darken.apl.common.datastore.DataStoreValue
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.map.core.MapAircraftProvider
import eu.darken.apl.map.core.MapLabelFields
import eu.darken.apl.map.core.MapSettings
import eu.darken.apl.map.core.NativeMapStyle
import eu.darken.apl.map.core.Sighting
import eu.darken.apl.map.core.overlay.OverlayRepo
import eu.darken.apl.map.core.overlay.OverlayState
import eu.darken.apl.map.ui.NativeMapViewModel.Seen
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.compat.AppCompatibility
import eu.darken.apl.server.compat.CompatState
import eu.darken.apl.upgrade.UpgradeRepo
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
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

class NativeMapViewModelLastSeenTest : BaseTest() {

    private fun frame(selectedHex: String? = null, lastSeen: Sighting? = null) = MapAircraftProvider.Frame(
        traffic = emptyList(), trafficVersion = 1, fades = emptyMap(), selectedHex = selectedHex, selected = null,
        selectedDetails = null, selectedMissing = selectedHex, selectedTrail = emptyList(), selectedTrailHead = null,
        tracks = null, viewport = null, onScreen = emptyList(), shownCount = 0, capped = false, totalMatching = null,
        selectedLastSeen = lastSeen,
    )

    private val providerFrames = MutableStateFlow(frame())

    private val provider = mockk<MapAircraftProvider>(relaxed = true) {
        every { frames } returns providerFrames
        every { state } returns MutableStateFlow(null)
    }

    private val cached = MutableStateFlow<Aircraft?>(null)

    private val aircraftRepo = mockk<AircraftRepo>(relaxed = true) {
        every { cachedByHex(HEX) } returns cached
    }

    private fun cachedAt(lat: Double, observedAt: Long) = FakeAircraft(
        hex = HEX,
        location = mockk<Location> {
            every { latitude } returns lat
            every { longitude } returns 10.0
        },
        positionSeenAt = Instant.ofEpochMilli(observedAt),
        groundTrack = 90f,
    )

    private fun <T> setting(value: T) = mockk<DataStoreValue<T>> {
        every { flow } returns MutableStateFlow(value)
    }

    private val mapSettings = mockk<MapSettings>(relaxed = true) {
        every { nativeMapStyle } returns setting(NativeMapStyle.AUTO.key)
        every { isLabelsEnabled } returns setting(true)
        every { labelFields } returns flowOf(MapLabelFields())
        every { isAllTracksEnabled } returns setting(false)
        every { isMilitaryOnlyEnabled } returns setting(false)
        every { isMilitaryHighlightEnabled } returns setting(true)
        every { isWatchHighlightEnabled } returns setting(true)
        every { isRestoreLastViewEnabled } returns setting(false)
        every { aircraftKinds } returns setting(emptySet())
        every { enabledOverlays } returns setting(emptySet())
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
        context = mockk<Context>(relaxed = true) {
            every { resources.getStringArray(any()) } returns arrayOf("tagline")
        },
        sensorManager = mockk(relaxed = true),
        mapSettings = mapSettings,
        webpageTool = mockk(relaxed = true),
        watchRepo = mockk(relaxed = true),
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

    private fun TestScope.selectedWithLastSeen(lastSeen: Sighting): NativeMapViewModel {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        viewModel.onAircraftTapped(HEX)
        providerFrames.value = frame(selectedHex = HEX, lastSeen = lastSeen)
        return viewModel
    }

    @Test
    fun `a newer cached position wins over the server's last seen one`() = runTest {
        cached.value = cachedAt(lat = 52.0, observedAt = SERVER_TIME - 60_000)

        val viewModel = selectedWithLastSeen(Sighting(48.0, 11.0, SERVER_TIME - 600_000, 45f))

        viewModel.state.value!!.apply {
            seen shouldBe Seen.At(SERVER_TIME - 60_000)
            lastSeenMark?.hex shouldBe HEX
            lastSeenMark?.latitude shouldBe 52.0
            lastSeenMark?.longitude shouldBe 10.0
            lastSeenMark?.trackDegrees shouldBe 90f
        }
    }

    @Test
    fun `a newer server last seen position wins over the cached one`() = runTest {
        cached.value = cachedAt(lat = 52.0, observedAt = SERVER_TIME - 600_000)

        val viewModel = selectedWithLastSeen(Sighting(48.0, 11.0, SERVER_TIME - 60_000, 45f))

        viewModel.state.value!!.apply {
            seen shouldBe Seen.At(SERVER_TIME - 60_000)
            lastSeenMark?.hex shouldBe HEX
            lastSeenMark?.latitude shouldBe 48.0
            lastSeenMark?.longitude shouldBe 11.0
            lastSeenMark?.trackDegrees shouldBe 45f
        }
    }

    companion object {
        private const val HEX = "3C65A3"
        private const val SERVER_TIME = 1_710_000_000_000L
    }
}
