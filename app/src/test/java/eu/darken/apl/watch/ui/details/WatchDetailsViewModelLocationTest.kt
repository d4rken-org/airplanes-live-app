package eu.darken.apl.watch.ui.details

import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.watch.core.LocationAltitudeException
import eu.darken.apl.watch.core.LocationRadiusException
import eu.darken.apl.watch.core.WatchRepo
import eu.darken.apl.watch.core.types.LocationWatch
import eu.darken.apl.watch.ui.AltitudeBand
import eu.darken.apl.watch.ui.AltitudeBandInput
import eu.darken.apl.watch.ui.AltitudeBandUnit
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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

class WatchDetailsViewModelLocationTest : BaseTest() {

    private val watchRepo = mockk<WatchRepo>(relaxed = true)
    private val accessRepo = mockk<AccessRepo>()

    @BeforeEach
    fun setup() {
        // viewModelScope is built on the main dispatcher
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val access = mockk<AccessState> { every { maxLocationWatchRadiusKm } returns 25 }
        every { accessRepo.state } returns MutableStateFlow(access)
        storeWatch(minAltitudeFt = null, maxAltitudeFt = null)
    }

    private fun storeWatch(minAltitudeFt: Int?, maxAltitudeFt: Int?) {
        val watch = mockk<LocationWatch>(relaxed = true) {
            every { id } returns WATCH_ID
            every { latitude } returns 52.5
            every { longitude } returns 13.4
            // Stored under an older, higher limit
            every { radiusInMeters } returns 100_000f
            every { this@mockk.minAltitudeFt } returns minAltitudeFt
            every { this@mockk.maxAltitudeFt } returns maxAltitudeFt
        }
        every { watchRepo.status } returns flowOf(listOf(LocationWatch.Status(watch = watch, lastHit = null)))
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = WatchDetailsViewModel(
        dispatcherProvider = TestDispatcherProvider(),
        watchRepo = watchRepo,
        searchRepo = mockk(relaxed = true),
        aircraftRepo = mockk(relaxed = true),
        locationManager2 = mockk(relaxed = true),
        flightRepo = mockk(relaxed = true),
        historyRepo = mockk(relaxed = true),
        webpageTool = mockk(relaxed = true),
        serverClock = mockk(relaxed = true),
        liveCheck = mockk(relaxed = true),
        accessRepo = accessRepo,
        serverMode = mockk(relaxed = true),
    ).apply { init(WATCH_ID) }

    @Test
    fun `a label-only edit keeps a radius above today's limit`() = runTest {
        var done: Boolean? = null

        createViewModel().updateLocation(100_000f, NO_BAND, "Home") { done = it }

        coVerify { watchRepo.updateLocation(WATCH_ID, 52.5, 13.4, 100_000f, null, null, "Home") }
        done shouldBe true
    }

    @Test
    fun `a changed radius above the limit is not saved`() = runTest {
        val vm = createViewModel()
        var done: Boolean? = null

        vm.updateLocation(30_000f, NO_BAND, "Home") { done = it }

        coVerify(exactly = 0) { watchRepo.updateLocation(any(), any(), any(), any(), any(), any(), any()) }
        done shouldBe false
        vm.errorEvents.first().shouldBeInstanceOf<LocationRadiusException>()
    }

    @Test
    fun `a changed radius within the limit is saved`() = runTest {
        var done: Boolean? = null

        createViewModel().updateLocation(25_000f, NO_BAND, "Home") { done = it }

        coVerify { watchRepo.updateLocation(WATCH_ID, 52.5, 13.4, 25_000f, null, null, "Home") }
        done shouldBe true
    }

    @Test
    fun `a radius edit passes the band it was given`() = runTest {
        storeWatch(minAltitudeFt = 1_000, maxAltitudeFt = 10_000)
        var done: Boolean? = null

        createViewModel().updateLocation(25_000f, AltitudeBand(1_000, 10_000), "Home 2") { done = it }

        coVerify { watchRepo.updateLocation(WATCH_ID, 52.5, 13.4, 25_000f, 1_000, 10_000, "Home 2") }
        done shouldBe true
    }

    @Test
    fun `a new band is saved`() = runTest {
        var done: Boolean? = null

        createViewModel().updateLocation(100_000f, AltitudeBand(2_000, 30_000), "Home") { done = it }

        coVerify { watchRepo.updateLocation(WATCH_ID, 52.5, 13.4, 100_000f, 2_000, 30_000, "Home") }
        done shouldBe true
    }

    @Test
    fun `a band-only change passes the new band and keeps the area`() = runTest {
        storeWatch(minAltitudeFt = 1_000, maxAltitudeFt = 10_000)
        var done: Boolean? = null

        createViewModel().updateLocation(100_000f, AltitudeBand(null, 12_000), "Home") { done = it }

        coVerify { watchRepo.updateLocation(WATCH_ID, 52.5, 13.4, 100_000f, null, 12_000, "Home") }
        done shouldBe true
    }

    @Test
    fun `a label-only save on a banded watch shown in meters keeps the band exactly`() = runTest {
        storeWatch(minAltitudeFt = 1_000, maxAltitudeFt = 59_999)
        // What the details editor hands over when only the label changed
        val band = AltitudeBandInput.of(AltitudeBand(1_000, 59_999), AltitudeBandUnit.METERS).band!!
        var done: Boolean? = null

        createViewModel().updateLocation(100_000f, band, "Home 2") { done = it }

        // Area and band as stored, so the definition revision stays
        coVerify { watchRepo.updateLocation(WATCH_ID, 52.5, 13.4, 100_000f, 1_000, 59_999, "Home 2") }
        done shouldBe true
    }

    @Test
    fun `an invalid band is not saved`() = runTest {
        val vm = createViewModel()
        var done: Boolean? = null

        vm.updateLocation(100_000f, AltitudeBand(10_000, 1_000), "Home") { done = it }

        coVerify(exactly = 0) { watchRepo.updateLocation(any(), any(), any(), any(), any(), any(), any()) }
        done shouldBe false
        vm.errorEvents.first().shouldBeInstanceOf<LocationAltitudeException>()
    }

    companion object {
        private const val WATCH_ID = "location-1"
        private val NO_BAND = AltitudeBand(null, null)
    }
}
