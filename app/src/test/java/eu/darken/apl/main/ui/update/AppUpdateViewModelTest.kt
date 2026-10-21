package eu.darken.apl.main.ui.update

import eu.darken.apl.main.core.update.AppUpdatePrompt
import eu.darken.apl.server.compat.AppCompatibility
import eu.darken.apl.server.compat.AppCompatibilityCheck
import eu.darken.apl.server.compat.CompatState
import io.kotest.matchers.nulls.shouldBeNull
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
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

class AppUpdateViewModelTest : BaseTest() {

    private val compatibility = mockk<AppCompatibility> {
        every { state } returns MutableStateFlow(CompatState.UpdateRecommended(recommendedVersion = 110))
        every { dismissedRecommendation } returns flowOf(null)
        coEvery { dismissRecommendation(any()) } just runs
    }

    private val compatibilityCheck = mockk<AppCompatibilityCheck> {
        every { checking } returns MutableStateFlow(false)
    }

    @BeforeEach
    fun setup() {
        // viewModelScope is built on the main dispatcher
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `not now puts off the version it is given while nothing collects the prompt`() = runTest {
        val updatePrompt = AppUpdatePrompt(compatibility)
        val vm = AppUpdateViewModel(
            dispatcherProvider = TestDispatcherProvider(),
            updatePrompt = updatePrompt,
            compatibilityCheck = compatibilityCheck,
            updateTarget = mockk(relaxed = true),
        )

        vm.notNow(110)

        coVerify(exactly = 1) { compatibility.dismissRecommendation(110) }
        updatePrompt.prompt.first().shouldBeNull()
    }
}
