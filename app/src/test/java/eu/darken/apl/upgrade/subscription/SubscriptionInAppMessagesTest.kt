package eu.darken.apl.upgrade.subscription

import eu.darken.apl.upgrade.subscription.SubscriptionState.Ownership
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class SubscriptionInAppMessagesTest : BaseTest() {

    private fun state(ownership: Ownership) = SubscriptionState(
        supported = true,
        offer = SubscriptionState.Offer.Loading,
        ownership = ownership,
        submission = SubscriptionState.Submission.Idle,
    )

    private val state = MutableStateFlow(state(Ownership.Unknown))
    private var shown = 0

    private fun TestScope.resume() = launch { showWhenOwned(state) { shown++ } }.also { runCurrent() }

    @Test
    fun `ownership that arrives after resuming shows the messages once`() = runTest {
        val resumed = resume()
        shown shouldBe 0

        state.value = state(Ownership.Owned(suspended = false))
        runCurrent()
        shown shouldBe 1

        state.value = state(Ownership.Owned(suspended = true))
        runCurrent()
        shown shouldBe 1

        resumed.cancel()
    }

    @Test
    fun `each resume while owned shows the messages again`() = runTest {
        state.value = state(Ownership.Owned(suspended = false))

        resume().cancel()
        shown shouldBe 1

        resume().cancel()
        shown shouldBe 2
    }

    @Test
    fun `nothing is shown after leaving the resumed state`() = runTest {
        resume().cancel()

        state.value = state(Ownership.Owned(suspended = false))
        runCurrent()

        shown shouldBe 0
    }

    @Test
    fun `nothing is shown without a subscription`() = runTest {
        val resumed = resume()

        state.value = state(Ownership.None)
        runCurrent()

        shown shouldBe 0
        resumed.cancel()
    }
}
