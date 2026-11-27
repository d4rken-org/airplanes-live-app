package eu.darken.apl.map.ui

import android.content.Context
import android.os.SystemClock
import eu.darken.apl.airport.core.AirportFlightsRepo
import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.datastore.DataStoreValue
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.map.core.MapAircraftProvider
import eu.darken.apl.map.core.MapLabelFields
import eu.darken.apl.map.core.MapSettings
import eu.darken.apl.map.core.NativeMapStyle
import eu.darken.apl.map.core.overlay.OverlayRepo
import eu.darken.apl.map.core.overlay.OverlayState
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.compat.AppCompatibility
import eu.darken.apl.server.compat.CompatState
import eu.darken.apl.server.session.SessionManager
import eu.darken.apl.server.session.SessionState
import eu.darken.apl.server.status.ServerStatus
import eu.darken.apl.server.status.ServerStatusTracker
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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.coroutine.TestDispatcherProvider

class NativeMapViewModelStyleTest : BaseTest() {

    private data class Info(
        override val isPro: Boolean,
        override val isSettled: Boolean,
        override val type: UpgradeRepo.Type = UpgradeRepo.Type.GPLAY,
        override val source: UpgradeRepo.Source? = null,
        override val error: Throwable? = null,
    ) : UpgradeRepo.Info

    private val providerFrames = MutableStateFlow(
        MapAircraftProvider.Frame(
            traffic = emptyList(),
            trafficVersion = 1,
            fades = emptyMap(),
            selectedHex = null,
            selected = null,
            selectedDetails = null,
            selectedMissing = null,
            selectedTrail = emptyList(),
            selectedTrailHead = null,
            tracks = null,
            viewport = null,
            onScreen = emptyList(),
            shownCount = 0,
            capped = false,
            totalMatching = 0,
            cutoffAltitudeFeet = null,
        )
    )

    private val provider = mockk<MapAircraftProvider>(relaxed = true) {
        every { frames } returns providerFrames
        every { state } returns MutableStateFlow(null)
    }

    private fun <T> setting(value: T) = mockk<DataStoreValue<T>> {
        every { flow } returns MutableStateFlow(value)
    }

    private val mapSettings = mockk<MapSettings>(relaxed = true) {
        every { nativeMapStyle } returns setting(NativeMapStyle.LIBERTY.key)
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

    private fun createViewModel(upgrade: UpgradeRepo.Info) = NativeMapViewModel(
        dispatcherProvider = TestDispatcherProvider(),
        context = mockk<Context>(relaxed = true) {
            every { resources.getStringArray(any()) } returns arrayOf("tagline")
        },
        sensorManager = mockk(relaxed = true),
        mapSettings = mapSettings,
        webpageTool = mockk(relaxed = true),
        watchRepo = mockk(relaxed = true),
        aircraftRepo = mockk<AircraftRepo>(relaxed = true),
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
            every { upgradeInfo } returns MutableStateFlow(upgrade)
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

    @Test
    fun `a stored fixed style still applies on the free tier`() = runTest {
        val viewModel = createViewModel(Info(isPro = false, isSettled = true))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

        val state = viewModel.state.value!!
        state.style shouldBe NativeMapStyle.LIBERTY
        state.isPro shouldBe false
    }
}
