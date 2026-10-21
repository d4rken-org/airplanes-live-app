package eu.darken.apl.watch.ui.details

import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.watch.core.LocationRadiusException
import eu.darken.apl.watch.core.WatchRepo
import eu.darken.apl.watch.core.types.LocationWatch
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
        val watch = mockk<LocationWatch>(relaxed = true) {
            every { id } returns WATCH_ID
            every { latitude } returns 52.5
            every { longitude } returns 13.4
            // Stored under an older, higher limit
            every { radiusInMeters } returns 100_000f
        }
        every { watchRepo.status } returns flowOf(listOf(LocationWatch.Status(watch = watch, lastCheck = null, lastHit = null)))
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
        accessRepo = accessRepo,
        serverMode = mockk(relaxed = true),
    ).apply { init(WATCH_ID) }

    @Test
    fun `a label-only edit keeps a radius above today's limit`() = runTest {
        var done: Boolean? = null

        createViewModel().updateLocation(100_000f, "Home") { done = it }

        coVerify { watchRepo.updateLocation(WATCH_ID, 52.5, 13.4, 100_000f, "Home") }
        done shouldBe true
    }

    @Test
    fun `a changed radius above the limit is not saved`() = runTest {
        val vm = createViewModel()
        var done: Boolean? = null

        vm.updateLocation(30_000f, "Home") { done = it }

        coVerify(exactly = 0) { watchRepo.updateLocation(any(), any(), any(), any(), any()) }
        done shouldBe false
        vm.errorEvents.first().shouldBeInstanceOf<LocationRadiusException>()
    }

    @Test
    fun `a changed radius within the limit is saved`() = runTest {
        var done: Boolean? = null

        createViewModel().updateLocation(25_000f, "Home") { done = it }

        coVerify { watchRepo.updateLocation(WATCH_ID, 52.5, 13.4, 25_000f, "Home") }
        done shouldBe true
    }

    companion object {
        private const val WATCH_ID = "location-1"
    }
}
