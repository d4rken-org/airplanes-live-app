package eu.darken.apl.upgrade.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.feeder.core.link.FeederLinkRepo.FeederLinkState
import eu.darken.apl.server.api.LinkedFeeder
import eu.darken.apl.upgrade.UpgradeRepo
import eu.darken.apl.upgrade.subscription.SubscriptionState
import eu.darken.apl.upgrade.subscription.SubscriptionState.Offer
import eu.darken.apl.upgrade.subscription.SubscriptionState.Ownership
import eu.darken.apl.upgrade.subscription.SubscriptionState.Submission
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelper.BaseComposeTest
import java.time.Period

class UpgradeScreenTest : BaseComposeTest() {

    private var subscribes = 0
    private var restores = 0
    private var manages = 0

    private val monthly = Offer.Available(
        formattedPrice = "€2.99",
        billingPeriod = Period.ofMonths(1),
        trialPeriod = null,
    )

    private val linked = FeederLinkState.Linked(
        feeder = LinkedFeeder(feederId = "feeder-1234", status = "active", linkedAt = 0, lastActiveAt = 0),
        tier = "pro",
    )

    private fun subscription(
        offer: Offer = monthly,
        ownership: Ownership = Ownership.None,
        submission: Submission = Submission.Idle,
        supported: Boolean = true,
    ) = SubscriptionState(
        supported = supported,
        offer = offer,
        ownership = ownership,
        submission = submission,
    )

    private fun setScreen(
        isPro: Boolean = false,
        source: UpgradeRepo.Source? = null,
        link: FeederLinkState = FeederLinkState.Unlinked(tier = "free"),
        subscription: SubscriptionState? = subscription(),
    ) = composeRule.setContent {
        PreviewWrapper {
            UpgradeScreen(
                state = UpgradeViewModel.State(
                    type = UpgradeRepo.Type.GPLAY,
                    isPro = isPro,
                    isSettled = true,
                    source = source,
                    link = link,
                    subscription = subscription,
                ),
                onNavigateUp = {},
                onRefresh = {},
                onRegister = {},
                onUnlink = {},
                onSubscribe = { subscribes++ },
                onRestore = { restores++ },
                onManage = { manages++ },
            )
        }
    }

    @Test
    fun `a free installation can subscribe at the offered price`() {
        setScreen()

        composeRule.onNodeWithText("Subscription").assertExists()
        composeRule.onNodeWithText("€2.99 per month").assertExists()
        composeRule.onNodeWithText("Subscribe").performScrollTo().assertIsEnabled().performClick()

        subscribes shouldBe 1
        composeRule.onNodeWithText("Manage subscription").assertDoesNotExist()
    }

    @Test
    fun `a period other than a month still shows the price`() {
        setScreen(subscription = subscription(offer = monthly.copy(billingPeriod = Period.ofYears(1))))

        composeRule.onNodeWithText("€2.99 per", substring = true).assertExists()
        composeRule.onNodeWithText("€2.99 per month").assertDoesNotExist()
    }

    @Test
    fun `the trial line only shows for an offer with a trial`() {
        setScreen(subscription = subscription(offer = monthly.copy(trialPeriod = Period.ofDays(7))))

        composeRule.onNodeWithText("First 7 days free").assertExists()
    }

    @Test
    fun `an offer without a trial shows no trial line`() {
        setScreen()

        composeRule.onNodeWithText("days free", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("day free", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a loading offer can't be bought yet`() {
        setScreen(subscription = subscription(offer = Offer.Loading))

        composeRule.onNodeWithText("Checking Google Play…").assertExists()
        composeRule.onNodeWithText("Subscribe").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `an unavailable offer says so and keeps Restore`() {
        setScreen(subscription = subscription(offer = Offer.Unavailable))

        composeRule.onNodeWithText("The subscription isn't available right now. Try again later.").assertExists()
        composeRule.onNodeWithText("Subscribe").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText("Restore").performScrollTo().performClick()

        restores shouldBe 1
        subscribes shouldBe 0
    }

    @Test
    fun `an owned subscription offers Manage instead of Subscribe`() {
        setScreen(
            isPro = true,
            source = UpgradeRepo.Source.SUBSCRIPTION,
            subscription = subscription(ownership = Ownership.Owned(suspended = false)),
        )

        composeRule.onNodeWithText("Subscribe").assertDoesNotExist()
        composeRule.onNodeWithText("Restore").assertExists()
        composeRule.onNodeWithText("Manage subscription").performScrollTo().performClick()

        manages shouldBe 1
    }

    @Test
    fun `an owned subscription is managed even when the offer is unavailable`() {
        setScreen(
            isPro = true,
            source = UpgradeRepo.Source.SUBSCRIPTION,
            subscription = subscription(offer = Offer.Unavailable, ownership = Ownership.Owned(suspended = false)),
        )

        composeRule.onNodeWithText("Subscribe").assertDoesNotExist()
        composeRule.onNodeWithText("Manage subscription").assertExists()
        composeRule.onNodeWithText("The subscription isn't available right now. Try again later.").assertDoesNotExist()
    }

    @Test
    fun `a subscription on hold says so and offers Manage`() {
        setScreen(subscription = subscription(ownership = Ownership.Owned(suspended = true)))

        composeRule.onNodeWithText("Google Play couldn't renew your subscription", substring = true).assertExists()
        composeRule.onNodeWithText("Manage subscription").assertExists()
        composeRule.onNodeWithText("Subscribe").assertDoesNotExist()
    }

    @Test
    fun `the subscription card stays for a subscriber`() {
        setScreen(
            isPro = true,
            source = UpgradeRepo.Source.SUBSCRIPTION,
            link = FeederLinkState.Unlinked(tier = "pro"),
            subscription = subscription(ownership = Ownership.Owned(suspended = false)),
        )

        composeRule.onNodeWithText("Subscription").assertExists()
        composeRule.onNodeWithText(
            "Pro through your Google Play subscription",
            substring = true,
        ).assertExists()
    }

    @Test
    fun `receiver Pro still sees the subscription card and can subscribe`() {
        setScreen(isPro = true, source = UpgradeRepo.Source.FEEDER, link = linked)

        composeRule.onNodeWithText("Linked feeder").assertExists()
        composeRule.onNodeWithText("Subscription").assertExists()
        composeRule.onNodeWithText("Subscribe").performScrollTo().assertIsEnabled()
    }

    @Test
    fun `a subscriber with a linked receiver sees both cards`() {
        setScreen(
            isPro = true,
            source = UpgradeRepo.Source.SUBSCRIPTION,
            link = linked,
            subscription = subscription(ownership = Ownership.Owned(suspended = false)),
        )

        composeRule.onNodeWithText("Linked feeder").assertExists()
        composeRule.onNodeWithText("Subscription").assertExists()
        composeRule.onNodeWithText("Run a feeder").assertDoesNotExist()
    }

    @Test
    fun `a subscriber without a receiver can still link one`() {
        setScreen(
            isPro = true,
            source = UpgradeRepo.Source.SUBSCRIPTION,
            link = FeederLinkState.Unlinked(tier = "pro"),
            subscription = subscription(ownership = Ownership.Owned(suspended = false)),
        )

        composeRule.onNodeWithText("Run a feeder").assertExists()
    }

    @Test
    fun `without subscription support there is no subscription card`() {
        setScreen(subscription = subscription(supported = false))

        composeRule.onNodeWithText("Subscription").assertDoesNotExist()
        composeRule.onNodeWithText("Subscribe").assertDoesNotExist()
        composeRule.onNodeWithText("Restore").assertDoesNotExist()
    }

    @Test
    fun `an installation without a subscription state shows no subscription card`() {
        setScreen(subscription = null)

        composeRule.onNodeWithText("Subscription").assertDoesNotExist()
    }

    @Test
    fun `unlinking a subscriber's receiver says Pro stays`() {
        setScreen(isPro = true, source = UpgradeRepo.Source.SUBSCRIPTION, link = linked)

        composeRule.onNodeWithText("Unlink").performScrollTo().performClick()

        composeRule.onNodeWithText("Pro stays active through your Google Play subscription.").assertExists()
        composeRule.onNodeWithText("This installation goes back to Free access.").assertDoesNotExist()
    }

    @Test
    fun `unlinking a receiver that grants Pro says access goes back to Free`() {
        setScreen(isPro = true, source = UpgradeRepo.Source.FEEDER, link = linked)

        composeRule.onNodeWithText("Unlink").performScrollTo().performClick()

        composeRule.onNodeWithText("This installation goes back to Free access.").assertExists()
        composeRule.onNodeWithText("Pro stays active through your Google Play subscription.").assertDoesNotExist()
    }

    @Test
    fun `a payment still pending in Google Play says so`() {
        setScreen(subscription = subscription(submission = Submission.PendingInPlay))

        composeRule.onNodeWithText("Google Play is still processing your payment.").assertExists()
    }

    @Test
    fun `an update in progress says so`() {
        setScreen(subscription = subscription(submission = Submission.Updating))

        composeRule.onNodeWithText("Updating your access…").assertExists()
    }

    @Test
    fun `a purchase not attached yet points at Restore`() {
        setScreen(subscription = subscription(submission = Submission.NotAttached))

        composeRule.onNodeWithText(
            "Google Play has your subscription, but this installation isn't using it yet. Tap Restore to try again."
        ).assertExists()
    }

    @Test
    fun `a rejected purchase points at Restore`() {
        setScreen(subscription = subscription(submission = Submission.Rejected))

        composeRule.onNodeWithText("This purchase couldn't be confirmed. Tap Restore to try again.").assertExists()
    }

    @Test
    fun `a Pro state without a source and without ownership still offers to subscribe`() {
        setScreen(isPro = true, source = null, subscription = subscription(ownership = Ownership.None))

        composeRule.onNodeWithText("Subscribe").performScrollTo().assertIsEnabled()
        composeRule.onNodeWithText("Manage subscription").assertDoesNotExist()
    }
}
