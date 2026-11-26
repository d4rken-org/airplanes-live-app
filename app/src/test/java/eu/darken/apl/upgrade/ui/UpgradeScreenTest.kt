package eu.darken.apl.upgrade.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.feeder.core.link.FeederLinkRepo.FeederLinkState
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.server.access.AccessState.SubscriptionStatus
import eu.darken.apl.server.api.Allowance
import eu.darken.apl.server.api.LinkedFeeder
import eu.darken.apl.server.api.RequestLimits
import eu.darken.apl.server.api.RequestRate
import eu.darken.apl.server.api.TierPolicy
import eu.darken.apl.server.api.Usage
import eu.darken.apl.upgrade.UpgradeRepo
import eu.darken.apl.upgrade.subscription.SubscriptionState
import eu.darken.apl.upgrade.subscription.SubscriptionState.Offer
import eu.darken.apl.upgrade.subscription.SubscriptionState.Ownership
import eu.darken.apl.upgrade.subscription.SubscriptionState.Submission
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelper.BaseComposeTest
import java.time.Duration
import java.time.Instant
import java.time.Period
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class UpgradeScreenTest : BaseComposeTest() {

    private var subscribes = 0
    private var restores = 0
    private var manages = 0
    private var contactSupports = 0
    private var screenLocale: Locale = Locale.getDefault()

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
        access: AccessState? = null,
        lifecycleOwner: LifecycleOwner? = null,
        onResume: () -> Unit = {},
        onPause: () -> Unit = {},
    ) = composeRule.setContent {
        screenLocale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
        PreviewWrapper {
            CompositionLocalProvider(LocalLifecycleOwner provides (lifecycleOwner ?: LocalLifecycleOwner.current)) {
                UpgradeScreen(
                    state = UpgradeViewModel.State(
                        type = UpgradeRepo.Type.GPLAY,
                        isPro = isPro,
                        isSettled = true,
                        source = source,
                        link = link,
                        isRestoring = isRestoring,
                        subscription = subscription,
                        access = access,
                    ),
                    onNavigateUp = {},
                    onRefresh = {},
                    onRegister = {},
                    onUnlink = {},
                    onSubscribe = { subscribes++ },
                    onRestore = { restores++ },
                    onManage = { manages++ },
                    onContactSupport = { contactSupports++ },
                    onResume = onResume,
                    onPause = onPause,
                )
            }
        }
    }

    private fun accessStateOf(
        subscription: SubscriptionStatus?,
        tier: AccessState.Tier = AccessState.Tier.SUBSCRIBER,
    ) = AccessState(
        installationId = "installation",
        fetchedAt = Instant.EPOCH,
        tier = tier,
        restricted = false,
        allowanceScope = "principal",
        limits = TierPolicy(
            viewingIntervalSeconds = 5, viewingBurst = 3, viewingPerDay = 25000, searchesPerDay = 25,
            watchesPerDay = 1000, maxRadiusNm = 25, watchTypes = listOf("hex", "callsign"),
        ),
        usage = Usage(
            resetsAt = 3_600_000,
            viewing = Allowance(25000, 0, 0, 25000),
            search = Allowance(25, 0, 0, 25),
            watch = Allowance(1000, 0, 0, 1000),
        ),
        installationRequests = RequestLimits(
            viewing = RequestRate(perSecond = 0.2, burst = 3),
            search = RequestRate(perSecond = 1.0, burst = 3),
            watch = RequestRate(perSecond = 1.0, burst = 3),
            concurrency = 3,
        ),
        subscription = subscription,
    )

    private val future = System.currentTimeMillis() + Duration.ofDays(30).toMillis()
    private val past = System.currentTimeMillis() - Duration.ofDays(1).toMillis()

    private fun serverStatus(state: SubscriptionStatus.State, expiresAt: Long? = null) =
        accessStateOf(SubscriptionStatus(state = state, expiresAt = expiresAt))

    /** Call after [setScreen], which picks up the locale the screen formats with. */
    private fun dateText(epochMs: Long): String = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(screenLocale)
        .format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

    private val ownedActive = Ownership.Owned(suspended = false)

    private class TestLifecycleOwner(initial: Lifecycle.State) : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = initial }
        override val lifecycle: Lifecycle get() = registry
    }

    /** Unlike boundsInRoot, not clipped to the window, so cards below the fold still compare. */
    private fun topOf(title: String): Float = composeRule.onNodeWithText(title).fetchSemanticsNode().positionInRoot.y

    @Test
    fun `the screen reports a resume only once its lifecycle is resumed`() {
        val owner = TestLifecycleOwner(Lifecycle.State.STARTED)
        var resumes = 0
        setScreen(lifecycleOwner = owner, onResume = { resumes++ })

        composeRule.runOnIdle { resumes shouldBe 0 }

        composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        composeRule.runOnIdle { resumes shouldBe 1 }
    }

    @Test
    fun `opening the screen and every later resume each report a resume`() {
        val owner = TestLifecycleOwner(Lifecycle.State.RESUMED)
        var resumes = 0
        setScreen(lifecycleOwner = owner, onResume = { resumes++ })

        composeRule.runOnIdle { resumes shouldBe 1 }

        composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        composeRule.runOnIdle { resumes shouldBe 1 }

        composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        composeRule.runOnIdle { resumes shouldBe 2 }
    }

    @Test
    fun `the screen reports a pause once its lifecycle leaves resumed`() {
        val owner = TestLifecycleOwner(Lifecycle.State.RESUMED)
        var pauses = 0
        setScreen(lifecycleOwner = owner, onPause = { pauses++ })

        composeRule.runOnIdle { pauses shouldBe 0 }

        composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        composeRule.runOnIdle { pauses shouldBe 1 }

        composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        composeRule.runOnIdle { pauses shouldBe 1 }

        composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        composeRule.runOnIdle { pauses shouldBe 2 }
    }

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

        composeRule.onNodeWithText("Support the app").assertExists()
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
    fun `an update in progress reassures and keeps Contact support available`() {
        setScreen(subscription = subscription(submission = Submission.Updating))

        composeRule.onNodeWithText(
            "This usually takes a few seconds. If Pro isn't unlocked within a day, contact support."
        ).assertExists()
        composeRule.onNodeWithText("Contact support").performScrollTo().assertIsEnabled().performClick()

        contactSupports shouldBe 1
        restores shouldBe 0
        subscribes shouldBe 0
    }

    @Test
    fun `a restore during an update still reassures and keeps Contact support available`() {
        setScreen(isRestoring = true, subscription = subscription(submission = Submission.Updating))

        composeRule.onNodeWithText("Restoring your purchase…").assertExists()
        composeRule.onNodeWithText(
            "This usually takes a few seconds. If Pro isn't unlocked within a day, contact support."
        ).assertExists()
        composeRule.onNodeWithText("Restore").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText("Subscribe").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText("Contact support").performScrollTo().assertIsEnabled().performClick()

        contactSupports shouldBe 1
        restores shouldBe 0
        subscribes shouldBe 0
    }

    @Test
    fun `a restore during an update of an owned subscription still reassures and keeps Contact support available`() {
        setScreen(
            isPro = true,
            source = UpgradeRepo.Source.SUBSCRIPTION,
            isRestoring = true,
            subscription = subscription(
                ownership = Ownership.Owned(suspended = false),
                submission = Submission.Updating,
            ),
        )

        composeRule.onNodeWithText("Restoring your purchase…").assertExists()
        composeRule.onNodeWithText(
            "This usually takes a few seconds. If Pro isn't unlocked within a day, contact support."
        ).assertExists()
        composeRule.onNodeWithText("Manage").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText("Contact support").performScrollTo().assertIsEnabled().performClick()

        contactSupports shouldBe 1
        manages shouldBe 0
    }

    @Test
    fun `a restore without an update shows neither the update hint nor Contact support`() {
        setScreen(isRestoring = true)

        composeRule.onNodeWithText("Restoring your purchase…").assertExists()
        composeRule.onNodeWithText("This usually takes a few seconds", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Contact support").assertDoesNotExist()
    }

    @Test
    fun `an idle offer shows neither the update hint nor Contact support`() {
        setScreen()

        composeRule.onNodeWithText("This usually takes a few seconds", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Contact support").assertDoesNotExist()
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

        composeRule.onNodeWithText(
            "Your subscription is active, thanks for keeping the app in the air ✈️ Change or cancel it in Google Play."
        ).assertExists()
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
    fun `an update for an owned subscription reassures and keeps Contact support available`() {
        setScreen(
            isPro = true,
            source = UpgradeRepo.Source.SUBSCRIPTION,
            subscription = subscription(
                ownership = Ownership.Owned(suspended = false),
                submission = Submission.Updating,
            ),
        )

        composeRule.onNodeWithText(
            "This usually takes a few seconds. If Pro isn't unlocked within a day, contact support."
        ).assertExists()
        composeRule.onNodeWithText("Manage").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText("Contact support").performScrollTo().assertIsEnabled().performClick()

        contactSupports shouldBe 1
        manages shouldBe 0
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
    fun `a rejected purchase shows its error before Google Play answers`() {
        setScreen(
            subscription = subscription(
                ownership = Ownership.Unknown,
                submission = Submission.Rejected,
            ),
        )

        composeRule.onNodeWithText(
            "This purchase couldn't be confirmed. Try again, and if it keeps failing, contact support."
        ).assertExists()
        composeRule.onNodeWithText("Try again").performScrollTo().assertIsEnabled()
        composeRule.onNodeWithText("Contact support").performScrollTo().assertIsEnabled()
        composeRule.onNodeWithText("Subscribe").assertDoesNotExist()
        composeRule.onNodeWithText("Start free trial").assertDoesNotExist()
    }

    @Test
    fun `a purchase not attached yet shows Try again before Google Play answers`() {
        setScreen(
            subscription = subscription(
                ownership = Ownership.Unknown,
                submission = Submission.NotAttached,
            ),
        )

        composeRule.onNodeWithText(
            "Google Play has your subscription, but this installation isn't using it yet."
        ).assertExists()
        composeRule.onNodeWithText("Try again").performScrollTo().assertIsEnabled()
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
            "Thanks for going Pro ❤️ You like aircraft, we like aircraft. " +
                "Your subscription keeps them on the map, and the free tier's limits are gone."
        ).assertExists()
    }

    @Test
    fun `receiver Pro still sees the subscription card and can subscribe`() {
        setScreen(isPro = true, source = UpgradeRepo.Source.FEEDER, link = linked)

        composeRule.onNodeWithText("Linked receiver").assertExists()
        composeRule.onNodeWithText("Support the app").assertExists()
        composeRule.onNodeWithText("Subscribe").performScrollTo().assertIsEnabled()
    }

    @Test
    fun `receiver Pro without a subscription is asked to support the app`() {
        setScreen(
            isPro = true,
            source = UpgradeRepo.Source.FEEDER,
            link = linked,
            subscription = subscription(ownership = Ownership.None),
        )

        composeRule.onNodeWithText("Support the app").assertExists()
        composeRule.onNodeWithText("Get Pro").assertDoesNotExist()
        composeRule.onNodeWithText(
            "You already have Pro through your receiver ❤️ A subscription on top helps keep the infrastructure running."
        ).assertExists()
        composeRule.onNodeWithText("Monthly subscription · €2.99").assertExists()
    }

    @Test
    fun `a linked receiver that doesn't grant Pro still offers Get Pro`() {
        setScreen(isPro = false, source = null, link = linked)

        composeRule.onNodeWithText("Get Pro").assertExists()
        composeRule.onNodeWithText("Support the app").assertDoesNotExist()
        composeRule.onNodeWithText("You already have Pro through your receiver", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a linked receiver with Pro from another source still offers Get Pro`() {
        setScreen(
            isPro = true,
            source = UpgradeRepo.Source.SUBSCRIPTION,
            link = linked,
            subscription = subscription(ownership = Ownership.None),
        )

        composeRule.onNodeWithText("Get Pro").assertExists()
        composeRule.onNodeWithText("Support the app").assertDoesNotExist()
        composeRule.onNodeWithText("You already have Pro through your receiver", substring = true).assertDoesNotExist()
    }

    @Test
    fun `Pro with both a linked receiver and a subscription thanks for both`() {
        setScreen(
            isPro = true,
            source = UpgradeRepo.Source.SUBSCRIPTION,
            link = linked,
            subscription = subscription(ownership = Ownership.Owned(suspended = false)),
        )

        composeRule.onNodeWithText(
            "Antenna and subscription? You're the best ✈️❤️ The free tier's limits are gone."
        ).assertExists()
        composeRule.onNodeWithText("Thanks for going Pro", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Thanks for feeding airplanes.live", substring = true).assertDoesNotExist()
    }

    @Test
    fun `receiver Pro thanks for feeding`() {
        setScreen(isPro = true, source = UpgradeRepo.Source.FEEDER, link = linked)

        composeRule.onNodeWithText(
            "Thanks for feeding airplanes.live ❤️ Your antenna puts aircraft on everyone's map, " +
                "and the free tier's limits are gone."
        ).assertExists()
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
            "Got a spot for an antenna? Share data with the airplanes.live community and put more aircraft " +
                "on everyone's map, yours included."
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
        composeRule.onNodeWithText("Got a spot for an antenna?", substring = true).assertDoesNotExist()
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

    private val activeText =
        "Your subscription is active, thanks for keeping the app in the air ✈️ Change or cancel it in Google Play."
    private val onHoldText =
        "Google Play couldn't renew your subscription. Update your payment in Google Play to keep Pro."
    private val inGraceText = "Google Play couldn't charge your payment method. Fix it in Google Play to keep Pro."
    private val pendingText =
        "Google Play is still processing your payment. Pro unlocks automatically once it goes through."
    private val updatingHintText =
        "This usually takes a few seconds. If Pro isn't unlocked within a day, contact support."

    private fun setOwnedScreen(
        access: AccessState?,
        ownership: Ownership = ownedActive,
        submission: Submission = Submission.Idle,
    ) = setScreen(
        isPro = true,
        source = UpgradeRepo.Source.SUBSCRIPTION,
        link = FeederLinkState.Unlinked(tier = "pro"),
        subscription = subscription(ownership = ownership, submission = submission),
        access = access,
    )

    private fun assertNoOfferOrPlayActions() {
        composeRule.onNodeWithText("Get Pro").assertDoesNotExist()
        composeRule.onNodeWithText("Subscribe").assertDoesNotExist()
        composeRule.onNodeWithText("Start free trial").assertDoesNotExist()
        composeRule.onNodeWithText("Monthly subscription · €2.99").assertDoesNotExist()
        composeRule.onNodeWithText("Billed monthly", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Manage").assertDoesNotExist()
        composeRule.onNodeWithText("Fix payment").assertDoesNotExist()
    }

    @Test
    fun `an owned subscription the server reports active shows its renewal date and Manage`() {
        setOwnedScreen(access = serverStatus(SubscriptionStatus.State.ACTIVE, future))

        composeRule.onNodeWithText("Renews on ${dateText(future)}.").assertExists()
        composeRule.onNodeWithText(activeText).assertDoesNotExist()
        composeRule.onNodeWithText("Manage").performScrollTo().assertIsEnabled().performClick()

        manages shouldBe 1
    }

    @Test
    fun `an owned subscription in grace asks to fix the payment`() {
        setOwnedScreen(access = serverStatus(SubscriptionStatus.State.IN_GRACE))

        composeRule.onNodeWithText(inGraceText).assertExists()
        composeRule.onNodeWithText("Manage").assertDoesNotExist()
        composeRule.onNodeWithText("Fix payment").performScrollTo().assertIsEnabled().performClick()

        manages shouldBe 1
    }

    @Test
    fun `an owned subscription the server reports canceled shows until when Pro stays`() {
        setOwnedScreen(access = serverStatus(SubscriptionStatus.State.CANCELED, future))

        composeRule.onNodeWithText("Canceled. Pro until ${dateText(future)}.").assertExists()
        composeRule.onNodeWithText(activeText).assertDoesNotExist()
        composeRule.onNodeWithText("Manage").performScrollTo().assertIsEnabled().performClick()

        manages shouldBe 1
    }

    @Test
    fun `an owned subscription the server reports paused says so and offers Manage`() {
        setOwnedScreen(access = serverStatus(SubscriptionStatus.State.PAUSED))

        composeRule.onNodeWithText("Paused in Google Play.").assertExists()
        composeRule.onNodeWithText(activeText).assertDoesNotExist()
        composeRule.onNodeWithText("Manage").performScrollTo().assertIsEnabled().performClick()

        manages shouldBe 1
    }

    @Test
    fun `the server's on hold wins over Google Play reporting the subscription as not suspended`() {
        setOwnedScreen(
            access = serverStatus(SubscriptionStatus.State.ON_HOLD),
            ownership = Ownership.Owned(suspended = false),
        )

        composeRule.onNodeWithText(onHoldText).assertExists()
        composeRule.onNodeWithText(activeText).assertDoesNotExist()
        composeRule.onNodeWithText("Manage").assertDoesNotExist()
        composeRule.onNodeWithText("Fix payment").performScrollTo().assertIsEnabled().performClick()

        manages shouldBe 1
    }

    @Test
    fun `the server's active status wins over Google Play reporting the subscription as suspended`() {
        setOwnedScreen(
            access = serverStatus(SubscriptionStatus.State.ACTIVE, future),
            ownership = Ownership.Owned(suspended = true),
        )

        composeRule.onNodeWithText("Renews on ${dateText(future)}.").assertExists()
        composeRule.onNodeWithText(onHoldText).assertDoesNotExist()
        composeRule.onNodeWithText("Fix payment").assertDoesNotExist()
        composeRule.onNodeWithText("Manage").performScrollTo().assertIsEnabled()
    }

    @Test
    fun `an owned subscription the server reports pending shows the active text`() {
        setOwnedScreen(access = serverStatus(SubscriptionStatus.State.PENDING))

        composeRule.onNodeWithText(activeText).assertExists()
        composeRule.onNodeWithText(pendingText).assertDoesNotExist()
        composeRule.onNodeWithText("Manage").performScrollTo().assertIsEnabled()
    }

    @Test
    fun `an owned subscription without a server status shows the active text`() {
        setOwnedScreen(access = accessStateOf(subscription = null))

        composeRule.onNodeWithText(activeText).assertExists()
        composeRule.onNodeWithText("Renews on", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Manage").performScrollTo().assertIsEnabled()
    }

    @Test
    fun `an owned subscription the server reports active without a date shows the active text`() {
        setOwnedScreen(access = serverStatus(SubscriptionStatus.State.ACTIVE, expiresAt = null))

        composeRule.onNodeWithText(activeText).assertExists()
        composeRule.onNodeWithText("Renews on", substring = true).assertDoesNotExist()
    }

    @Test
    fun `an owned subscription the server reports active with a past date shows the active text`() {
        setOwnedScreen(access = serverStatus(SubscriptionStatus.State.ACTIVE, past))

        composeRule.onNodeWithText(activeText).assertExists()
        composeRule.onNodeWithText("Renews on", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a rejected purchase wins over the server's active status`() {
        setOwnedScreen(
            access = serverStatus(SubscriptionStatus.State.ACTIVE, future),
            submission = Submission.Rejected,
        )

        composeRule.onNodeWithText(
            "This purchase couldn't be confirmed. Try again, and if it keeps failing, contact support."
        ).assertExists()
        composeRule.onNodeWithText("Renews on", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Try again").performScrollTo().assertIsEnabled()
    }

    @Test
    fun `a purchase not attached yet wins over the server's active status`() {
        setOwnedScreen(
            access = serverStatus(SubscriptionStatus.State.ACTIVE, future),
            submission = Submission.NotAttached,
        )

        composeRule.onNodeWithText(
            "Google Play has your subscription, but this installation isn't using it yet."
        ).assertExists()
        composeRule.onNodeWithText("Renews on", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Try again").performScrollTo().assertIsEnabled()
    }

    @Test
    fun `a server status without Google Play ownership shows the status and Restore instead of the offer`() {
        setScreen(
            isPro = true,
            source = UpgradeRepo.Source.SUBSCRIPTION,
            subscription = subscription(ownership = Ownership.None),
            access = serverStatus(SubscriptionStatus.State.CANCELED, future),
        )

        composeRule.onNodeWithText("Subscription").assertExists()
        composeRule.onNodeWithText("Canceled. Pro until ${dateText(future)}.").assertExists()
        assertNoOfferOrPlayActions()
        composeRule.onNodeWithText("Restore").performScrollTo().assertIsEnabled().performClick()

        restores shouldBe 1
    }

    @Test
    fun `a server status before Google Play answers shows the status and Restore instead of the offer`() {
        setScreen(
            isPro = true,
            source = UpgradeRepo.Source.SUBSCRIPTION,
            subscription = subscription(ownership = Ownership.Unknown),
            access = serverStatus(SubscriptionStatus.State.IN_GRACE),
        )

        composeRule.onNodeWithText("Subscription").assertExists()
        composeRule.onNodeWithText(inGraceText).assertExists()
        assertNoOfferOrPlayActions()
        composeRule.onNodeWithText("Restore").performScrollTo().assertIsEnabled().performClick()

        restores shouldBe 1
    }

    @Test
    fun `a pending server status without Google Play ownership says the payment is processing`() {
        setScreen(
            subscription = subscription(ownership = Ownership.None),
            access = serverStatus(SubscriptionStatus.State.PENDING),
        )

        composeRule.onNodeWithText(pendingText).assertExists()
        assertNoOfferOrPlayActions()
        composeRule.onNodeWithText("Restore").performScrollTo().assertIsEnabled()
    }

    @Test
    fun `a server status doesn't wait for a loading offer`() {
        setScreen(
            subscription = subscription(offer = Offer.Loading, ownership = Ownership.None),
            access = serverStatus(SubscriptionStatus.State.CANCELED, future),
        )

        composeRule.onNodeWithText("Canceled. Pro until ${dateText(future)}.").assertExists()
        composeRule.onNodeWithText("Checking Google Play…").assertDoesNotExist()
        composeRule.onNodeWithText("Restore").performScrollTo().assertIsEnabled()
    }

    @Test
    fun `a server status hides that the offer is unavailable`() {
        setScreen(
            subscription = subscription(offer = Offer.Unavailable, ownership = Ownership.None),
            access = serverStatus(SubscriptionStatus.State.CANCELED, future),
        )

        composeRule.onNodeWithText("Canceled. Pro until ${dateText(future)}.").assertExists()
        composeRule.onNodeWithText("The subscription isn't available right now. Try again later.").assertDoesNotExist()
        composeRule.onNodeWithText("Restore").performScrollTo().assertIsEnabled()
    }

    @Test
    fun `an update with a server status but no Google Play ownership reassures and keeps Contact support available`() {
        setScreen(
            subscription = subscription(ownership = Ownership.None, submission = Submission.Updating),
            access = serverStatus(SubscriptionStatus.State.IN_GRACE),
        )

        composeRule.onNodeWithText("Updating your access…").assertExists()
        composeRule.onNodeWithText(updatingHintText).assertExists()
        composeRule.onNodeWithText(inGraceText).assertDoesNotExist()
        composeRule.onNodeWithText("Restore").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText("Contact support").performScrollTo().assertIsEnabled().performClick()

        contactSupports shouldBe 1
        restores shouldBe 0
    }

    @Test
    fun `a canceled server status whose date passed shows no status line but keeps Restore`() {
        setScreen(
            subscription = subscription(ownership = Ownership.None),
            access = serverStatus(SubscriptionStatus.State.CANCELED, past),
        )

        composeRule.onNodeWithText("Subscription").assertExists()
        composeRule.onNodeWithText("Canceled. Pro until", substring = true).assertDoesNotExist()
        assertNoOfferOrPlayActions()
        composeRule.onNodeWithText("Restore").performScrollTo().assertIsEnabled()
    }

    @Test
    fun `a restore with a server status that has no line still shows the restore in progress`() {
        setScreen(
            isRestoring = true,
            subscription = subscription(ownership = Ownership.None),
            access = serverStatus(SubscriptionStatus.State.ACTIVE, expiresAt = null),
        )

        composeRule.onNodeWithText("Restoring your purchase…").assertExists()
        composeRule.onNodeWithText("Restore").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `a payment pending in Google Play shows the server's active status instead of the pending text`() {
        setScreen(
            subscription = subscription(ownership = Ownership.None, submission = Submission.PendingInPlay),
            access = serverStatus(SubscriptionStatus.State.ACTIVE, future),
        )

        composeRule.onNodeWithText("Renews on ${dateText(future)}.").assertExists()
        composeRule.onNodeWithText(pendingText).assertDoesNotExist()
        composeRule.onNodeWithText("Restore").performScrollTo().assertIsEnabled()
    }

    private val notAttachedText = "Google Play has your subscription, but this installation isn't using it yet."

    private fun setStaleOwnedScreen(submission: Submission) = setScreen(
        subscription = subscription(ownership = ownedActive, submission = submission),
        access = accessStateOf(subscription = null, tier = AccessState.Tier.FREE),
    )

    @Test
    fun `Google Play ownership the server doesn't grant offers Try again instead of calling it active`() {
        setStaleOwnedScreen(Submission.Idle)

        composeRule.onNodeWithText(notAttachedText).assertExists()
        composeRule.onNodeWithText(activeText).assertDoesNotExist()
        composeRule.onNodeWithText("Manage").assertDoesNotExist()
        composeRule.onNodeWithText("Subscribe").assertDoesNotExist()
        composeRule.onNodeWithText("Try again").performScrollTo().assertIsEnabled().performClick()

        restores shouldBe 1
    }

    @Test
    fun `Google Play ownership the server doesn't grant offers Try again while a payment is pending`() {
        setStaleOwnedScreen(Submission.PendingInPlay)

        composeRule.onNodeWithText(notAttachedText).assertExists()
        composeRule.onNodeWithText(activeText).assertDoesNotExist()
        composeRule.onNodeWithText("Manage").assertDoesNotExist()
        composeRule.onNodeWithText("Subscribe").assertDoesNotExist()
        composeRule.onNodeWithText("Try again").performScrollTo().assertIsEnabled().performClick()

        restores shouldBe 1
    }

    @Test
    fun `an update for Google Play ownership the server doesn't grant shows the update`() {
        setStaleOwnedScreen(Submission.Updating)

        composeRule.onNodeWithText("Updating your access…").assertExists()
        composeRule.onNodeWithText(updatingHintText).assertExists()
        composeRule.onNodeWithText(notAttachedText).assertDoesNotExist()
        composeRule.onNodeWithText(activeText).assertDoesNotExist()
        composeRule.onNodeWithText("Manage").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `a server status without the subscriber tier keeps Manage but doesn't call the subscription active`() {
        setScreen(
            subscription = subscription(ownership = ownedActive),
            access = accessStateOf(
                subscription = SubscriptionStatus(state = SubscriptionStatus.State.ACTIVE, expiresAt = past),
                tier = AccessState.Tier.FREE,
            ),
        )

        composeRule.onNodeWithText(activeText).assertDoesNotExist()
        composeRule.onNodeWithText(notAttachedText).assertDoesNotExist()
        composeRule.onNodeWithText("Manage").performScrollTo().assertIsEnabled()
    }

    @Test
    fun `Google Play ownership without a server policy yet shows the active text`() {
        setOwnedScreen(access = null)

        composeRule.onNodeWithText(activeText).assertExists()
        composeRule.onNodeWithText(notAttachedText).assertDoesNotExist()
        composeRule.onNodeWithText("Manage").performScrollTo().assertIsEnabled()
    }

    @Test
    fun `receiver Pro with Google Play ownership the server doesn't grant only thanks for feeding`() {
        setScreen(
            isPro = true,
            source = UpgradeRepo.Source.FEEDER,
            link = linked,
            subscription = subscription(ownership = ownedActive),
            access = accessStateOf(subscription = null, tier = AccessState.Tier.FEEDER),
        )

        composeRule.onNodeWithText(
            "Thanks for feeding airplanes.live ❤️ Your antenna puts aircraft on everyone's map, " +
                "and the free tier's limits are gone."
        ).assertExists()
        composeRule.onNodeWithText("Antenna and subscription?", substring = true).assertDoesNotExist()
    }
}
