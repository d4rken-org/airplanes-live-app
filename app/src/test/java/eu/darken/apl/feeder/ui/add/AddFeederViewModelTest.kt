package eu.darken.apl.feeder.ui.add

import eu.darken.apl.common.serialization.SerializationModule
import eu.darken.apl.feeder.core.FeederDiscovery
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.mockk
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
        feederRepo = mockk(relaxed = true),
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
}
