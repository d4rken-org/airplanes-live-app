package eu.darken.apl.feeder.ui.add

import eu.darken.apl.common.navigation.NavEvent
import eu.darken.apl.common.serialization.SerializationModule
import eu.darken.apl.feeder.core.FeederDiscovery
import eu.darken.apl.feeder.core.FeederRepo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelper.coroutine.TestDispatcherProvider
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class AddFeederViewModelTest {

    private val feederDiscovery = mockk<FeederDiscovery>()
    private val feederRepo = mockk<FeederRepo>(relaxed = true)

    @Before
    fun setup() {
        // viewModelScope is built on the main dispatcher
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = AddFeederViewModel(
        dispatcherProvider = TestDispatcherProvider(),
        feederRepo = feederRepo,
        feederDiscovery = feederDiscovery,
        json = SerializationModule().json(),
    )

    @Test
    fun `a failed scan reports FAILED and stops detecting`() = runTest(UnconfinedTestDispatcher()) {
        val scan = CompletableDeferred<FeederDiscovery.Detection>()
        coEvery { feederDiscovery.scan() } coAnswers { scan.await() }
        val vm = createViewModel()
        val detecting = MutableStateFlow(emptyList<Boolean>())
        backgroundScope.launch { vm.state.collect { state -> detecting.update { it + state.isDetectingLocal } } }

        vm.detectLocalFeeder()
        detecting.first { it.lastOrNull() == true }

        scan.completeExceptionally(IOException("no network"))
        detecting.first { it.size >= 3 }

        detecting.value shouldBe listOf(false, true, false)
        vm.events.first() shouldBe AddFeederEvents.ShowLocalDetectionResult(LocalDetectionResult.FAILED)
    }

    @Test
    fun `a feeder code with broken data stops the camera and reports the code as invalid`() =
        runTest(UnconfinedTestDispatcher()) {
            val vm = createViewModel()

            vm.handleQrScan("${NewFeederQR.PREFIX}?data={\"receiverId\":")

            vm.events.first() shouldBe AddFeederEvents.StopCamera
            vm.errorEvents.first().shouldBeInstanceOf<InvalidFeederQrException>()
        }

    private suspend fun addAndVerifyStoredAs(input: String, stored: String) {
        val vm = createViewModel()
        vm.updateReceiverId(input)

        vm.addFeeder()
        vm.navEvents.first() shouldBe NavEvent.Up

        coVerify(exactly = 1) { feederRepo.addFeeder(id = stored, label = any(), address = any(), position = any()) }
        verify(exactly = 1) { feederRepo.fillLabelsDetached(setOf(stored)) }
    }

    @Test
    fun `an uppercase ID is stored and looked up in lowercase`() = runTest(UnconfinedTestDispatcher()) {
        addAndVerifyStoredAs(
            input = "  0F3A6C1E-8A51-4C5E-9E2B-7D6B1A2C3D4E \n",
            stored = "0f3a6c1e-8a51-4c5e-9e2b-7d6b1a2c3d4e",
        )
    }

    @Test
    fun `a short-group ID is stored as typed, trimmed`() = runTest(UnconfinedTestDispatcher()) {
        addAndVerifyStoredAs(input = " 1-2-3-4-5 ", stored = "1-2-3-4-5")
    }

    @Test
    fun `the all-zero ID is stored as typed, trimmed`() = runTest(UnconfinedTestDispatcher()) {
        addAndVerifyStoredAs(
            input = " 00000000-0000-0000-0000-000000000000 ",
            stored = "00000000-0000-0000-0000-000000000000",
        )
    }
}
