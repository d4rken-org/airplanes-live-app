package eu.darken.apl.watch.ui.create

import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.watch.core.LocationRadiusException
import eu.darken.apl.watch.core.LocationWatchCreator
import eu.darken.apl.watch.core.WatchRepo
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.coroutine.TestDispatcherProvider

class CreateLocationWatchViewModelTest : BaseTest() {

    private val watchRepo = mockk<WatchRepo>(relaxed = true)
    private val accessRepo = mockk<AccessRepo>()

    @BeforeEach
    fun setup() {
        // viewModelScope is built on the main dispatcher
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val access = mockk<AccessState>(relaxed = true) {
            every { maxLocationWatchRadiusKm } returns 25
            every { allowsWatchType("location") } returns true
        }
        every { accessRepo.state } returns MutableStateFlow(access)
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = CreateLocationWatchViewModel(
        dispatcherProvider = TestDispatcherProvider(),
        locationManager2 = mockk(relaxed = true),
        creator = LocationWatchCreator(watchRepo, mockk(relaxed = true), accessRepo),
    )

    @Test
    fun `a radius above the limit is not created`() = runTest {
        val vm = createViewModel()

        vm.create(52.5, 13.4, 30_000f, "Home", "")

        coVerify(exactly = 0) { watchRepo.createLocation(any(), any(), any(), any(), any()) }
        vm.errorEvents.first().shouldBeInstanceOf<LocationRadiusException>()
    }

    @Test
    fun `a radius at the limit is created`() = runTest {
        createViewModel().create(52.5, 13.4, 25_000f, "Home", "")

        coVerify { watchRepo.createLocation(52.5, 13.4, 25_000f, "Home", "") }
    }
}
