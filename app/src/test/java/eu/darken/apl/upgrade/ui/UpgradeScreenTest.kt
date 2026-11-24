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
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelper.BaseComposeTest
import java.time.Period

class UpgradeScreenTest : BaseComposeTest() {

    private var subscribes = 0
    private var restores = 0
    private var manages = 0
    private var contactSupports = 0

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
        isRestoring: Boolean = false,
    ) = composeRule.setContent {
        PreviewWrapper {
            UpgradeScreen(
                state = UpgradeViewModel.State(
                    type = UpgradeRepo.Type.GPLAY,
                    isPro = isPro,
                    isSettled = true,
                    source = source,
                    link = link,
                    isRestoring = isRestoring,
                    subscription = subscription,
                ),
                onNavigateUp = {},
                onRefresh = {},
                onRegister = {},
                onUnlink = {},
                onSubscribe = { subscribes++ },
                onRestore = { restores++ },
                onManage = { manages++ },
                onContactSupport = { contactSupports++ },
            )
        }
    }

    /** Unlike boundsInRoot, not clipped to the window, so cards below the fold still compare. */
    private fun topOf(title: String): Float = composeRule.onNodeWithText(title).fetchSemanticsNode().positionInRoot.y

    @Test
    fun `an available offer puts the subscription above the receiver option`() {
        setScreen(subscription = subscription(offer = monthly, ownership = Ownership.None))

        topOf("Get Pro") shouldBeLessThan topOf("Run a receiver")
    }

    @Test
    fun `a loading offer puts the subscription above the receiver option`() {
        setScreen(subscription = subscription(offer = Offer.Loading))

        topOf("Get Pro") shouldBeLessThan topOf("Run a receiver")
    }

    @Test
    fun `an owned subscription stays above the receiver option when the offer is unavailable`() {
        setScreen(
            isPro = true,
            source = UpgradeRepo.Source.SUBSCRIPTION,
            link = FeederLinkState.Unlinked(tier = "pro"),
            subscription = subscription(offer = Offer.Unavailable, ownership = Ownership.Owned(suspended = false)),
        )

        topOf("Subscription") shouldBeLessThan topOf("Run a receiver")
    }

    @Test
    fun `an unavailable offer keeps the receiver option first`() {
        setScreen(subscription = subscription(offer = Offer.Unavailable, ownership = Ownership.None))

        topOf("Run a receiver") shouldBeLessThan topOf("Get Pro")
    }

    @Test
    fun `without a subscription state the receiver option shows alone`() {
        setScreen(subscription = null)

        composeRule.onNodeWithText("Run a receiver").assertExists()
        composeRule.onNodeWithText("Get Pro").assertDoesNotExist()
        composeRule.onNodeWithText("Subscription").assertDoesNotExist()
    }

    @Test
    fun `an available offer explains both ways to Pro between the two options`() {
        setScreen(subscription = subscription(offer = monthly, ownership = Ownership.None))

        topOf("Get Pro") shouldBeLessThan topOf("Two ways to Pro")
        topOf("Two ways to Pro") shouldBeLessThan topOf("Run a receiver")
    }

    @Test
    fun `a loading offer explains both ways to Pro`() {
        setScreen(subscription = subscription(offer = Offer.Loading))

        composeRule.onNodeWithText("Two ways to Pro").assertExists()
    }

    @Test
    fun `an owned subscription doesn't explain both ways to Pro`() {
        setScreen(
            isPro = true,
            source = UpgradeRepo.Source.SUBSCRIPTION,
            link = FeederLinkState.Unlinked(tier = "pro"),
            subscription = subscription(ownership = Ownership.Owned(suspended = false)),
        )

        composeRule.onNodeWithText("Two ways to Pro").assertDoesNotExist()
    }

    @Test
    fun `an unavailable offer doesn't explain both ways to Pro`() {
        setScreen(subscription = subscription(offer = Offer.Unavailable, ownership = Ownership.None))

        composeRule.onNodeWithText("Two ways to Pro").assertDoesNotExist()
    }

    @Test
    fun `a linked receiver doesn't explain both ways to Pro`() {
        setScreen(
            isPro = true,
            source = UpgradeRepo.Source.FEEDER,
            link = linked,
            subscription = subscription(offer = monthly, ownership = Ownership.None),
        )

        composeRule.onNodeWithText("Get Pro").assertExists()
        composeRule.onNodeWithText("Two ways to Pro").assertDoesNotExist()
    }

    @Test
    fun `without a subscription state there is no explanation of both ways to Pro`() {
        setScreen(subscription = null)

        composeRule.onNodeWithText("Two ways to Pro").assertDoesNotExist()
    }

    @Test
    fun `a free installation can subscribe at the offered price`() {
        setScreen()

        composeRule.onNodeWithText("Get Pro").assertExists()
        composeRule.onNodeWithText("Monthly subscription · €2.99").assertExists()
        composeRule.onNodeWithText("Subscribe").performScrollTo().assertIsEnabled().performClick()

        subscribes shouldBe 1
        composeRule.onNodeWithText("Manage").assertDoesNotExist()
    }

    @Test
    fun `a period other than a month still shows the price and the terms`() {
        setScreen(subscription = subscription(offer = monthly.copy(billingPeriod = Period.ofYears(1))))

        composeRule.onNodeWithText("Subscription · €2.99 per", substring = true).assertExists()
        composeRule.onNodeWithText("Monthly subscription · €2.99").assertDoesNotExist()
        composeRule.onNodeWithText("Billed every", substring = true).assertExists()
        composeRule.onNodeWithText("Billed monthly", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a trial offer starts a free trial and states the terms`() {
        setScreen(subscription = subscription(offer = monthly.copy(trialPeriod = Period.ofDays(7))))

        composeRule.onNodeWithText("Free for 7 days, then billed monthly until you cancel in Google Play.").assertExists()
        composeRule.onNodeWithText("Subscribe").assertDoesNotExist()
        composeRule.onNodeWithText("Start free trial").performScrollTo().assertIsEnabled().performClick()

        subscribes shouldBe 1
    }

    @Test
    fun `an offer without a trial subscribes and states the terms`() {
        setScreen()

        composeRule.onNodeWithText("Billed monthly until you cancel in Google Play.").assertExists()
        composeRule.onNodeWithText("Free for", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Start free trial").assertDoesNotExist()
        composeRule.onNodeWithText("Subscribe").performScrollTo().assertIsEnabled()
    }

    @Test
    fun `a loading offer disables both buttons while it checks Google Play`() {
        setScreen(subscription = subscription(offer = Offer.Loading))

        composeRule.onNodeWithText("Checking Google Play…").assertExists()
        composeRule.onNodeWithText("Subscribe").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText("Restore").performScrollTo().assertIsNotEnabled()
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
    fun `a running restore disables both buttons and says so`() {
        setScreen(isRestoring = true)

        composeRule.onNodeWithText("Restoring your purchase…").assertExists()
        composeRule.onNodeWithText("Restore").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText("Subscribe").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `an update in progress disables both buttons and says so`() {
        setScreen(subscription = subscription(submission = Submission.Updating))

        composeRule.onNodeWithText("Updating your access…").assertExists()
        composeRule.onNodeWithText("Restore").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText("Subscribe").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `a payment still pending in Google Play blocks Subscribe but not Restore`() {
        setScreen(subscription = subscription(submission = Submission.PendingInPlay))

        composeRule.onNodeWithText(
            "Google Play is still processing your payment. Pro unlocks automatically once it goes through."
        ).assertExists()
        composeRule.onNodeWithText("Subscribe").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText("Restore").performScrollTo().assertIsEnabled().performClick()

        restores shouldBe 1
    }

    @Test
    fun `an active subscription offers Manage and no Restore`() {
        setScreen(
            isPro = true,
            source = UpgradeRepo.Source.SUBSCRIPTION,
            subscription = subscription(ownership = Ownership.Owned(suspended = false)),
        )

        composeRule.onNodeWithText("Your subscription is active. Change or cancel it in Google Play.").assertExists()
        composeRule.onNodeWithText("Subscribe").assertDoesNotExist()
        composeRule.onNodeWithText("Restore").assertDoesNotExist()
        composeRule.onNodeWithText("Manage").performScrollTo().performClick()

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
        composeRule.onNodeWithText("Manage").assertExists()
        composeRule.onNodeWithText("The subscription isn't available right now. Try again later.").assertDoesNotExist()
    }

    @Test
    fun `an update for an owned subscription disables its action`() {
        setScreen(
            isPro = true,
            source = UpgradeRepo.Source.SUBSCRIPTION,
            subscription = subscription(
                ownership = Ownership.Owned(suspended = false),
                submission = Submission.Updating,
            ),
        )

        composeRule.onNodeWithText("Updating your access…").assertExists()
        composeRule.onNodeWithText("Manage").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `a subscription on hold offers Fix payment`() {
        setScreen(subscription = subscription(ownership = Ownership.Owned(suspended = true)))

        composeRule.onNodeWithText("Google Play couldn't renew your subscription", substring = true).assertExists()
        composeRule.onNodeWithText("Manage").assertDoesNotExist()
        composeRule.onNodeWithText("Subscribe").assertDoesNotExist()
        composeRule.onNodeWithText("Fix payment").performScrollTo().performClick()

        manages shouldBe 1
    }

    @Test
    fun `a purchase not attached yet offers Try again`() {
        setScreen(
            subscription = subscription(
                ownership = Ownership.Owned(suspended = false),
                submission = Submission.NotAttached,
            ),
        )

        composeRule.onNodeWithText(
            "Google Play has your subscription, but this installation isn't using it yet."
        ).assertExists()
        composeRule.onNodeWithText("Restore").assertDoesNotExist()
        composeRule.onNodeWithText("Manage").assertDoesNotExist()
        composeRule.onNodeWithText("Try again").performScrollTo().performClick()

        restores shouldBe 1
    }

    @Test
    fun `a rejected purchase offers Try again and Contact support`() {
        setScreen(
            subscription = subscription(
                ownership = Ownership.Owned(suspended = false),
                submission = Submission.Rejected,
            ),
        )

        composeRule.onNodeWithText(
            "This purchase couldn't be confirmed. Try again, and if it keeps failing, contact support."
        ).assertExists()
        composeRule.onNodeWithText("Try again").performScrollTo().performClick()
        composeRule.onNodeWithText("Contact support").performScrollTo().performClick()

        restores shouldBe 1
        contactSupports shouldBe 1
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
            "Thanks for subscribing",
            substring = true,
        ).assertExists()
    }

    @Test
    fun `receiver Pro still sees the subscription card and can subscribe`() {
        setScreen(isPro = true, source = UpgradeRepo.Source.FEEDER, link = linked)

        composeRule.onNodeWithText("Linked receiver").assertExists()
        composeRule.onNodeWithText("Get Pro").assertExists()
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

        composeRule.onNodeWithText("Linked receiver").assertExists()
        composeRule.onNodeWithText("Subscription").assertExists()
        composeRule.onNodeWithText("Run a receiver").assertDoesNotExist()
    }

    @Test
    fun `a subscriber without a receiver can still link one`() {
        setScreen(
            isPro = true,
            source = UpgradeRepo.Source.SUBSCRIPTION,
            link = FeederLinkState.Unlinked(tier = "pro"),
            subscription = subscription(ownership = Ownership.Owned(suspended = false)),
        )

        composeRule.onNodeWithText("Run a receiver").assertExists()
        composeRule.onNodeWithText(
            "Share data with the airplanes.live community and link your receiver, then Pro no longer depends on the subscription."
        ).assertExists()
        composeRule.onNodeWithText(
            "Share data with the airplanes.live community and this installation goes Pro."
        ).assertDoesNotExist()
    }

    @Test
    fun `a free installation is offered to feed to go Pro`() {
        setScreen()

        composeRule.onNodeWithText("Run a receiver").assertExists()
        composeRule.onNodeWithText(
            "Share data with the airplanes.live community and this installation goes Pro."
        ).assertExists()
        composeRule.onNodeWithText(
            "Share data with the airplanes.live community and link your receiver, then Pro no longer depends on the subscription."
        ).assertDoesNotExist()
    }

    @Test
    fun `without subscription support there is no subscription card`() {
        setScreen(subscription = subscription(supported = false))

        composeRule.onNodeWithText("Get Pro").assertDoesNotExist()
        composeRule.onNodeWithText("Subscribe").assertDoesNotExist()
        composeRule.onNodeWithText("Restore").assertDoesNotExist()
    }

    @Test
    fun `an installation without a subscription state shows no subscription card`() {
        setScreen(subscription = null)

        composeRule.onNodeWithText("Get Pro").assertDoesNotExist()
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
    fun `a Pro state without a source and without ownership still offers to subscribe`() {
        setScreen(isPro = true, source = null, subscription = subscription(ownership = Ownership.None))

        composeRule.onNodeWithText("Subscribe").performScrollTo().assertIsEnabled()
        composeRule.onNodeWithText("Manage").assertDoesNotExist()
    }
}
