package eu.darken.apl.main.ui.settings.deletion

import eu.darken.apl.main.ui.settings.deletion.DeleteDataViewModel.State
import eu.darken.apl.server.deletion.DeletionResult
import eu.darken.apl.server.deletion.DeletionState
import eu.darken.apl.server.deletion.InstallationDeletion
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.coroutine.TestDispatcherProvider
import java.io.IOException

class DeleteDataViewModelTest : BaseTest() {

    private val persisted = MutableStateFlow(DeletionState.NONE)
    private val deletion = mockk<InstallationDeletion> {
        every { state } returns persisted
        coEvery { cancelPending() } answers { persisted.value = DeletionState.NONE }
    }

    @BeforeEach
    fun setup() {
        // viewModelScope is built on the main dispatcher, runTest shares its scheduler
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.createViewModel() = DeleteDataViewModel(
        dispatcherProvider = TestDispatcherProvider(StandardTestDispatcher(testScheduler)),
        context = mockk(relaxed = true),
        deletion = deletion,
        emailTool = mockk(relaxed = true),
    ).also { vm ->
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
    }

    private fun TestScope.stateAfterDeleting(result: DeletionResult): State? {
        coEvery { deletion.delete() } returns result
        val vm = createViewModel()
        vm.delete()
        runCurrent()
        return vm.state.value
    }

    @Test
    fun `nothing is deleted without the confirm action`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.state.value shouldBe State.Idle
        coVerify(exactly = 0) { deletion.delete() }
    }

    @Test
    fun `shows the deletion running until it answers`() = runTest {
        val answer = CompletableDeferred<DeletionResult>()
        coEvery { deletion.delete() } coAnswers { answer.await() }
        val vm = createViewModel()

        vm.delete()
        runCurrent()
        vm.state.value shouldBe State.Running

        answer.complete(DeletionResult.WipeFailed(null))
        runCurrent()
        vm.state.value shouldBe State.WipeFailed
    }

    @Test
    fun `a second tap while running does not delete twice`() = runTest {
        val answer = CompletableDeferred<DeletionResult>()
        coEvery { deletion.delete() } coAnswers { answer.await() }
        val vm = createViewModel()

        vm.delete()
        runCurrent()
        vm.delete()
        runCurrent()

        coVerify(exactly = 1) { deletion.delete() }
    }

    @Test
    fun `an unconfirmed deletion asks to try again`() = runTest {
        val result = DeletionResult.NotConfirmed(IOException("offline"), retryAfterSeconds = null)

        stateAfterDeleting(result) shouldBe State.NotConfirmed(tryLater = false)
    }

    @Test
    fun `an unconfirmed deletion with a retry delay asks to try again later`() = runTest {
        val result = DeletionResult.NotConfirmed(IOException("busy"), retryAfterSeconds = 60)

        stateAfterDeleting(result) shouldBe State.NotConfirmed(tryLater = true)
    }

    @Test
    fun `a refused deletion carries the installation id`() = runTest {
        val result = DeletionResult.Refused(DeletionResult.Refused.Reason.RESTRICTED, installationId = "inst-1")

        stateAfterDeleting(result) shouldBe State.Refused(installationId = "inst-1")
    }

    @Test
    fun `a revoked installation without a known id is refused too`() = runTest {
        val result = DeletionResult.Refused(DeletionResult.Refused.Reason.REVOKED, installationId = null)

        stateAfterDeleting(result) shouldBe State.Refused(installationId = null)
    }

    @Test
    fun `a required update is shown`() = runTest {
        stateAfterDeleting(DeletionResult.UpdateRequired) shouldBe State.UpdateRequired
    }

    @Test
    fun `a failed device wipe is shown`() = runTest {
        stateAfterDeleting(DeletionResult.WipeFailed(RuntimeException("no"))) shouldBe State.WipeFailed
    }

    @Test
    fun `a finished deletion keeps showing it running until the app closes`() = runTest {
        stateAfterDeleting(DeletionResult.Deleted) shouldBe State.Running
    }

    @Test
    fun `retry after an unconfirmed deletion deletes again`() = runTest {
        coEvery { deletion.delete() } returnsMany listOf(
            DeletionResult.NotConfirmed(IOException("offline"), retryAfterSeconds = null),
            DeletionResult.WipeFailed(null),
        )
        val vm = createViewModel()

        vm.delete()
        runCurrent()
        vm.state.value shouldBe State.NotConfirmed(tryLater = false)

        vm.delete()
        runCurrent()

        coVerify(exactly = 2) { deletion.delete() }
        vm.state.value shouldBe State.WipeFailed
    }

    @Test
    fun `an unfinished deletion from before shows the unfinished state`() = runTest {
        persisted.value = DeletionState.REQUEST_PENDING
        val vm = createViewModel()
        advanceUntilIdle()

        vm.state.value shouldBe State.Unfinished
        coVerify(exactly = 0) { deletion.delete() }
    }

    @Test
    fun `cancelling an unfinished deletion forgets it`() = runTest {
        persisted.value = DeletionState.REQUEST_PENDING
        val vm = createViewModel()

        vm.cancelPending()
        runCurrent()

        coVerify(exactly = 1) { deletion.cancelPending() }
        vm.state.value shouldBe State.Idle
        coVerify(exactly = 0) { deletion.delete() }
    }

    @Test
    fun `retrying an unfinished deletion deletes again`() = runTest {
        persisted.value = DeletionState.REQUEST_PENDING
        coEvery { deletion.delete() } returns DeletionResult.UpdateRequired
        val vm = createViewModel()

        vm.delete()
        runCurrent()

        coVerify(exactly = 1) { deletion.delete() }
        vm.state.value shouldBe State.UpdateRequired
    }

    @Test
    fun `a deletion the server confirmed finishes by itself once`() = runTest {
        persisted.value = DeletionState.WIPE_PENDING
        val answer = CompletableDeferred<DeletionResult>()
        coEvery { deletion.delete() } coAnswers { answer.await() }
        val vm = createViewModel()
        advanceUntilIdle()

        vm.state.value shouldBe State.Running
        coVerify(exactly = 1) { deletion.delete() }

        answer.complete(DeletionResult.WipeFailed(null))
        advanceUntilIdle()

        vm.state.value shouldBe State.WipeFailed
        coVerify(exactly = 1) { deletion.delete() }
    }
}
