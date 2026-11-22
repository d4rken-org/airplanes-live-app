package eu.darken.apl.upgrade.subscription

import android.app.Activity
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.confirmVerified
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class SubscriptionBillingFossTest : BaseTest() {

    private val billing = SubscriptionBillingFoss()

    @Test
    fun `state is unsupported with nothing owned or offered`() = runTest {
        billing.state.toList() shouldBe listOf(
            SubscriptionState(
                supported = false,
                offer = SubscriptionState.Offer.Unavailable,
                ownership = SubscriptionState.Ownership.None,
                submission = SubscriptionState.Submission.Idle,
            )
        )
        billing.manageUri.shouldBeNull()
    }

    @Test
    fun `actions do nothing and subscribing is unavailable`() = runTest {
        val activity = mockk<Activity>()

        billing.setup()
        billing.restore()
        billing.reconcile("test")
        billing.showInAppMessages(activity)
        billing.subscribe(activity) shouldBe SubscribeResult.Unavailable

        confirmVerified(activity)
    }
}
