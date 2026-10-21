package eu.darken.apl.main.ui.update

import eu.darken.apl.main.core.update.AppUpdateTarget
import eu.darken.apl.server.compat.AppCompatibility
import eu.darken.apl.server.compat.AppCompatibilityCheck
import eu.darken.apl.server.compat.CompatState
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
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

class UpdateRequiredViewModelTest : BaseTest() {

    private val compatState = MutableStateFlow<CompatState>(CompatState.Unknown)
    private val checkRunning = MutableStateFlow(false)
    private val compatibility = mockk<AppCompatibility> {
        every { state } returns compatState
    }
    private val compatibilityCheck = mockk<AppCompatibilityCheck> {
        every { checking } returns checkRunning
        coEvery { check(any()) } just Runs
    }
    private val updateTarget = mockk<AppUpdateTarget>(relaxed = true)

    @BeforeEach
    fun setup() {
        // viewModelScope is built on the main dispatcher
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.createViewModel() = UpdateRequiredViewModel(
        dispatcherProvider = TestDispatcherProvider(),
        compatibility = compatibility,
        compatibilityCheck = compatibilityCheck,
        updateTarget = updateTarget,
    ).also { vm ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
    }

    @Test
    fun `hidden until the server refuses this version`() = runTest {
        val vm = createViewModel()

        vm.state.value.shouldBeNull()

        compatState.value = CompatState.Ok
        vm.state.value.shouldBeNull()

        compatState.value = CompatState.UpdateRecommended(recommendedVersion = 200)
        vm.state.value.shouldBeNull()

        compatState.value = CompatState.UpdateRequired
        vm.state.value shouldBe UpdateRequiredViewModel.State(isChecking = false)
    }

    @Test
    fun `follows a running check`() = runTest {
        compatState.value = CompatState.UpdateRequired
        val vm = createViewModel()

        checkRunning.value = true
        vm.state.value shouldBe UpdateRequiredViewModel.State(isChecking = true)

        checkRunning.value = false
        vm.state.value shouldBe UpdateRequiredViewModel.State(isChecking = false)
    }

    @Test
    fun `goes away once the server serves this version again`() = runTest {
        compatState.value = CompatState.UpdateRequired
        val vm = createViewModel()

        compatState.value = CompatState.Ok

        vm.state.value.shouldBeNull()
    }

    @Test
    fun `check again asks the server past its cache`() = runTest {
        compatState.value = CompatState.UpdateRequired
        val vm = createViewModel()

        vm.checkAgain()

        coVerify(exactly = 1) { compatibilityCheck.check(force = true) }
    }

    @Test
    fun `update opens where this build gets its updates`() = runTest {
        compatState.value = CompatState.UpdateRequired
        val vm = createViewModel()

        vm.update()

        verify(exactly = 1) { updateTarget.open() }
    }
}
