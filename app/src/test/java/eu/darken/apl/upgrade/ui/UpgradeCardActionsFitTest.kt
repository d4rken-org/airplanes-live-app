package eu.darken.apl.upgrade.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import eu.darken.apl.R
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.feeder.core.link.FeederLinkRepo.FeederLinkState
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.server.access.AccessState.SubscriptionStatus
import eu.darken.apl.server.api.Allowance
import eu.darken.apl.server.api.RequestLimits
import eu.darken.apl.server.api.RequestRate
import eu.darken.apl.server.api.TierPolicy
import eu.darken.apl.server.api.Usage
import eu.darken.apl.upgrade.UpgradeRepo
import eu.darken.apl.upgrade.subscription.SubscriptionState
import eu.darken.apl.upgrade.subscription.SubscriptionState.Offer
import eu.darken.apl.upgrade.subscription.SubscriptionState.Ownership
import eu.darken.apl.upgrade.subscription.SubscriptionState.Submission
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.floats.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import testhelper.BaseComposeTest
import java.time.Duration
import java.time.Instant
import java.time.Period

/** Every subscription card row with two buttons, on a 360dp phone. Needs real text metrics. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UpgradeCardActionsFitTest : BaseComposeTest() {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val monthly = Offer.Available(
        formattedPrice = "€2.99",
        billingPeriod = Period.ofMonths(1),
        trialPeriod = null,
    )

    private val ownedActive = Ownership.Owned(suspended = false)

    private val future = System.currentTimeMillis() + Duration.ofDays(30).toMillis()

    private fun subscription(
        offer: Offer = monthly,
        ownership: Ownership = Ownership.None,
        submission: Submission = Submission.Idle,
    ) = SubscriptionState(
        supported = true,
        offer = offer,
        ownership = ownership,
        submission = submission,
    )

    private fun screenState(
        isPro: Boolean = false,
        source: UpgradeRepo.Source? = null,
        link: FeederLinkState = FeederLinkState.Unlinked(tier = "free"),
        subscription: SubscriptionState,
        access: AccessState? = null,
    ) = UpgradeViewModel.State(
        type = UpgradeRepo.Type.GPLAY,
        isPro = isPro,
        isSettled = true,
        source = source,
        link = link,
        subscription = subscription,
        access = access,
    )

    private fun ownedState(subscription: SubscriptionState, access: AccessState? = null) = screenState(
        isPro = true,
        source = UpgradeRepo.Source.SUBSCRIPTION,
        link = FeederLinkState.Unlinked(tier = "pro"),
        subscription = subscription,
        access = access,
    )

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

    private fun serverStatus(state: SubscriptionStatus.State, expiresAt: Long? = null) =
        accessStateOf(SubscriptionStatus(state = state, expiresAt = expiresAt))

    private class RowCase(
        val name: String,
        val state: UpgradeViewModel.State,
        @StringRes val leading: Int,
        @StringRes val trailing: Int,
    )

    private val restore = R.string.upgrade_subscription_restore_action
    private val contactSupport = R.string.upgrade_subscription_contact_support_action

    private val cases = listOf(
        RowCase(
            "free with a trial offer",
            screenState(subscription = subscription(offer = monthly.copy(trialPeriod = Period.ofDays(7)))),
            restore, R.string.upgrade_subscription_start_trial_action,
        ),
        RowCase(
            "free without a trial offer",
            screenState(subscription = subscription()),
            restore, R.string.upgrade_subscription_subscribe_action,
        ),
        RowCase(
            "rejected purchase",
            screenState(subscription = subscription(ownership = ownedActive, submission = Submission.Rejected)),
            contactSupport, R.string.upgrade_subscription_try_again_action,
        ),
        RowCase(
            "updating with a server status but no Google Play ownership",
            screenState(
                subscription = subscription(submission = Submission.Updating),
                access = serverStatus(SubscriptionStatus.State.IN_GRACE),
            ),
            contactSupport, restore,
        ),
        RowCase(
            "updating an owned subscription the server reports in grace",
            ownedState(
                subscription(ownership = ownedActive, submission = Submission.Updating),
                access = serverStatus(SubscriptionStatus.State.IN_GRACE),
            ),
            contactSupport, R.string.upgrade_subscription_fix_payment_action,
        ),
        RowCase(
            "updating an owned subscription the server reports active",
            ownedState(
                subscription(ownership = ownedActive, submission = Submission.Updating),
                access = serverStatus(SubscriptionStatus.State.ACTIVE, future),
            ),
            contactSupport, R.string.upgrade_subscription_manage_short_action,
        ),
        RowCase(
            "updating a subscription on hold",
            screenState(
                subscription = subscription(ownership = Ownership.Owned(suspended = true), submission = Submission.Updating),
            ),
            contactSupport, R.string.upgrade_subscription_fix_payment_action,
        ),
        RowCase(
            "updating an active subscription",
            ownedState(subscription(ownership = ownedActive, submission = Submission.Updating)),
            contactSupport, R.string.upgrade_subscription_manage_short_action,
        ),
        RowCase(
            "updating Google Play ownership the server doesn't grant",
            screenState(
                subscription = subscription(ownership = ownedActive, submission = Submission.Updating),
                access = accessStateOf(subscription = null, tier = AccessState.Tier.FREE),
            ),
            contactSupport, R.string.upgrade_subscription_manage_short_action,
        ),
    )

    private fun SemanticsNode.textLayout(): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        return layouts.single()
    }

    /** One line, not shortened and not cut off. */
    private fun SemanticsNode.assertWhole(name: String, text: String) = withClue(name) {
        val layout = textLayout()
        layout.lineCount shouldBe 1
        layout.didOverflowHeight shouldBe false
        layout.isLineEllipsized(0) shouldBe false
        layout.getLineEnd(0, visibleEnd = true) shouldBe text.length
    }

    private val SemanticsNode.left: Float get() = positionInRoot.x
    private val SemanticsNode.right: Float get() = positionInRoot.x + size.width

    private fun SemanticsNode.ancestors(): Sequence<SemanticsNode> = generateSequence(parent) { it.parent }

    private fun buttonNode(label: String): SemanticsNode {
        val nodes = composeRule.onAllNodes(hasClickAction() and hasText(label)).fetchSemanticsNodes()
        withClue("button \"$label\"") { nodes shouldHaveSize 1 }
        return nodes.single()
    }

    private fun labelNode(label: String): SemanticsNode {
        val nodes = composeRule
            .onAllNodes(hasText(label) and hasAnyAncestor(hasClickAction()), useUnmergedTree = true)
            .fetchSemanticsNodes()
        withClue("label \"$label\"") { nodes shouldHaveSize 1 }
        return nodes.single()
    }

    private fun assertRowsFit() {
        val state = mutableStateOf(cases.first().state)
        composeRule.setContent {
            PreviewWrapper {
                UpgradeScreen(
                    state = state.value,
                    onNavigateUp = {},
                    onRefresh = {},
                    onRegister = {},
                    onUnlink = {},
                )
            }
        }
        val locale = context.resources.configuration.locales[0].toLanguageTag()

        cases.forEach { case ->
            composeRule.runOnIdle { state.value = case.state }
            composeRule.waitForIdle()

            val leadingLabel = context.getString(case.leading)
            val trailingLabel = context.getString(case.trailing)

            withClue("$locale, ${case.name}") {
                labelNode(leadingLabel).assertWhole("leading \"$leadingLabel\"", leadingLabel)
                labelNode(trailingLabel).assertWhole("trailing \"$trailingLabel\"", trailingLabel)

                val window = composeRule.onRoot().fetchSemanticsNode()
                val leading = buttonNode(leadingLabel)
                val trailing = buttonNode(trailingLabel)
                // Rows and columns carry no semantics, so the closest shared ancestor is the card
                val trailingAncestors = trailing.ancestors().map { it.id }.toSet()
                val card = leading.ancestors().first { it.id in trailingAncestors }

                withClue(
                    "leading \"$leadingLabel\"=[${leading.left}, ${leading.right}] " +
                        "trailing \"$trailingLabel\"=[${trailing.left}, ${trailing.right}] " +
                        "card=[${card.left}, ${card.right}] window=[0, ${window.size.width}] " +
                        "density=${composeRule.density.density}"
                ) {
                    leading.right shouldBeLessThanOrEqual trailing.left
                    leading.left shouldBeGreaterThanOrEqual card.left
                    trailing.right shouldBeLessThanOrEqual card.right
                    card.left shouldBeGreaterThanOrEqual 0f
                    card.right shouldBeLessThanOrEqual window.size.width.toFloat()
                }
            }
        }
    }

    @Test
    @Config(qualifiers = "en-w360dp-h800dp-420dpi")
    fun `English button rows fit`() = assertRowsFit()

    @Test
    @Config(qualifiers = "de-w360dp-h800dp-420dpi")
    fun `German button rows fit`() = assertRowsFit()

    @Test
    @Config(qualifiers = "es-w360dp-h800dp-420dpi")
    fun `Spanish button rows fit`() = assertRowsFit()

    @Test
    @Config(qualifiers = "fr-w360dp-h800dp-420dpi")
    fun `French button rows fit`() = assertRowsFit()

    @Test
    @Config(qualifiers = "it-w360dp-h800dp-420dpi")
    fun `Italian button rows fit`() = assertRowsFit()

    @Test
    @Config(qualifiers = "ru-w360dp-h800dp-420dpi")
    fun `Russian button rows fit`() = assertRowsFit()
}
