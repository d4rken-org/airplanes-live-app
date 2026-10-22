package eu.darken.apl.server.compat

import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.watch.core.history.WatchHistoryScheduler
import eu.darken.apl.watch.core.server.WatchListSyncScheduler
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class AppCompatibilityRecoveryTest : BaseTest() {

    private val state = MutableStateFlow<CompatState>(CompatState.Unknown)
    private val compatibility = mockk<AppCompatibility>().apply {
        every { state } returns this@AppCompatibilityRecoveryTest.state
    }
    private val accessRepo = mockk<AccessRepo>(relaxed = true)
    private val watchListSyncScheduler = mockk<WatchListSyncScheduler>(relaxed = true)
    private val watchHistoryScheduler = mockk<WatchHistoryScheduler>(relaxed = true)

    private fun TestScope.startRecovery() {
        AppCompatibilityRecovery(
            appScope = backgroundScope,
            compatibility = compatibility,
            accessRepo = accessRepo,
            watchListSyncScheduler = watchListSyncScheduler,
            watchHistoryScheduler = watchHistoryScheduler,
        ).setup()
        runCurrent()
    }

    private fun verifyResumed(times: Int) {
        coVerify(exactly = times) { accessRepo.refresh("update-unblocked") }
        verify(exactly = times) { watchListSyncScheduler.requestSync("update-unblocked") }
        coVerify(exactly = times) { watchHistoryScheduler.runAfterCurrent("update-unblocked") }
    }

    @Test
    fun `a lifted refusal resumes the server work once`() = runTest {
        state.value = CompatState.UpdateRequired
        startRecovery()
        verifyResumed(times = 0)

        state.value = CompatState.Ok
        runCurrent()
        verifyResumed(times = 1)

        state.value = CompatState.UpdateRecommended(2)
        runCurrent()
        verifyResumed(times = 1)
    }

    @Test
    fun `a refusal lifted into a recommendation resumes the server work`() = runTest {
        startRecovery()
        state.value = CompatState.UpdateRequired
        runCurrent()

        state.value = CompatState.UpdateRecommended(2)
        runCurrent()

        verifyResumed(times = 1)
    }

    @Test
    fun `loading the stored answer resumes nothing`() = runTest {
        startRecovery()

        state.value = CompatState.Ok
        runCurrent()

        verifyResumed(times = 0)
    }

    @Test
    fun `a new recommendation resumes nothing`() = runTest {
        state.value = CompatState.Ok
        startRecovery()

        state.value = CompatState.UpdateRecommended(2)
        runCurrent()

        verifyResumed(times = 0)
    }
}
