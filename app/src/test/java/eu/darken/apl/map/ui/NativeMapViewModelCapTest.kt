package eu.darken.apl.map.ui

import android.content.Context
import android.os.SystemClock
import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.datastore.DataStoreValue
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.map.core.MapAircraftProvider
import eu.darken.apl.map.core.MapLabelFields
import eu.darken.apl.map.core.MapPlane
import eu.darken.apl.map.core.MapSettings
import eu.darken.apl.map.core.NativeMapStyle
import eu.darken.apl.map.ui.NativeMapViewModel.CapAltitude
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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.coroutine.TestDispatcherProvider

class NativeMapViewModelCapTest : BaseTest() {

    private fun aboveFeet(cutoffFeet: Double?, capped: Boolean = true): Int? =
        NativeMapViewModel.capAltitude(capped, cutoffFeet, shownCount = 2000, totalMatching = 5000)?.aboveFeet

    @Test
    fun `a cutoff between thousands rounds down to the thousand below`() {
        aboveFeet(24_125.0) shouldBe 24_000
    }

    @Test
    fun `a cutoff on a whole thousand rounds down to the thousand below it`() {
        aboveFeet(24_000.0) shouldBe 23_000
    }

    @Test
    fun `a cutoff just above 1,000 ft shows 1,000 ft`() {
        aboveFeet(1_000.5) shouldBe 1_000
    }

    @Test
    fun `a cutoff of exactly 1,000 ft gives no altitude`() {
        aboveFeet(1_000.0) shouldBe null
    }

    @Test
    fun `a cutoff below 1,000 ft gives no altitude`() {
        aboveFeet(999.0) shouldBe null
    }

    @Test
    fun `no cutoff gives no altitude`() {
        aboveFeet(null) shouldBe null
    }

    @Test
    fun `an answer that is not capped gives no altitude`() {
        aboveFeet(24_125.0, capped = false) shouldBe null
    }

    @Test
    fun `the altitude carries the counts it was given`() {
        NativeMapViewModel.capAltitude(
            capped = true,
            cutoffFeet = 24_125.0,
            shownCount = 2000,
            totalMatching = 5000,
        ) shouldBe CapAltitude(aboveFeet = 24_000, shownCount = 2000, totalMatching = 5000)
    }

    @Test
    fun `an unknown total stays unknown`() {
        NativeMapViewModel.capAltitude(
            capped = true,
            cutoffFeet = 24_125.0,
            shownCount = 2000,
            totalMatching = null,
        ) shouldBe CapAltitude(aboveFeet = 24_000, shownCount = 2000, totalMatching = null)
    }

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

    // A capped answer of 2,000 out of 5,000, of which 3 are drawn
    private val providerFrames = MutableStateFlow(
        MapAircraftProvider.Frame(
            traffic = listOf(plane("AAAAAA"), plane("BBBBBB"), plane("CCCCCC")),
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
            shownCount = 2000,
            capped = true,
            totalMatching = 5000,
            cutoffAltitudeFeet = 24_125.0,
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
        every { nativeMapStyle } returns setting(NativeMapStyle.AUTO.key)
        every { isLabelsEnabled } returns setting(true)
        every { labelFields } returns flowOf(MapLabelFields())
        every { isAllTracksEnabled } returns setting(false)
        every { isMilitaryOnlyEnabled } returns setting(false)
        every { isMilitaryHighlightEnabled } returns setting(true)
        every { isWatchHighlightEnabled } returns setting(true)
        every { isRestoreLastViewEnabled } returns setting(false)
        every { aircraftKinds } returns setting(emptySet())
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
        shapesRepo = mockk(relaxed = true),
        upgradeRepo = mockk<UpgradeRepo> {
            every { upgradeInfo } returns flowOf(mockk<UpgradeRepo.Info>(relaxed = true))
        },
        compatibility = mockk<AppCompatibility> {
            every { state } returns MutableStateFlow(CompatState.Ok)
        },
    )

    @Test
    fun `while the list filters the map, the counts are of what is drawn and the server total is left out`() = runTest {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

        fun counts() = viewModel.state.value!!.let { it.shownCount to it.totalMatching }
        counts() shouldBe (2000 to 5000)

        viewModel.setListQuery("aaa")
        counts() shouldBe (3 to null)
        viewModel.state.value!!.capAltitude shouldBe CapAltitude(aboveFeet = 24_000, shownCount = 3, totalMatching = null)

        viewModel.setListQuery(" ")
        counts() shouldBe (2000 to 5000)
        viewModel.state.value!!.capAltitude shouldBe CapAltitude(aboveFeet = 24_000, shownCount = 2000, totalMatching = 5000)

        viewModel.toggleListWatchedOnly()
        counts() shouldBe (3 to null)
        viewModel.state.value!!.capAltitude shouldBe CapAltitude(aboveFeet = 24_000, shownCount = 3, totalMatching = null)
    }
}
