package eu.darken.apl.main.ui.onboarding

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import eu.darken.apl.R
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.main.ui.onboarding.privacy.PrivacyScreen
import eu.darken.apl.main.ui.onboarding.privacy.PrivacyViewModel
import eu.darken.apl.main.ui.onboarding.team.TeamScreen
import eu.darken.apl.main.ui.onboarding.welcome.WelcomeScreen
import io.kotest.assertions.withClue
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.floats.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import testhelper.BaseComposeTest

/** Each onboarding page on a 412dp phone at default font scale: no scrolling, dots and button on screen. Needs real text metrics. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingFitTest : BaseComposeTest() {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private enum class Page(val number: Int, val buttonRes: Int) {
        WELCOME(1, R.string.common_continue_action),
        TEAM(2, R.string.common_continue_action),
        PRIVACY(3, R.string.common_accept_action),
    }

    private fun show(page: Page, isGplay: Boolean = false, isUpdateCheckSupported: Boolean = true) {
        composeRule.setContent {
            PreviewWrapper { PageContent(page, isGplay, isUpdateCheckSupported) }
        }
        composeRule.waitForIdle()
    }

    @Composable
    private fun PageContent(page: Page, isGplay: Boolean, isUpdateCheckSupported: Boolean) = when (page) {
        Page.WELCOME -> WelcomeScreen(onContinue = {})
        Page.TEAM -> TeamScreen(isGplay = isGplay, onContinue = {})
        Page.PRIVACY -> PrivacyScreen(
            state = PrivacyViewModel.State(
                isUpdateCheckEnabled = false,
                isUpdateCheckSupported = isUpdateCheckSupported,
            ),
            onPrivacyPolicy = {},
            onToggleUpdateCheck = {},
            onAccept = {},
        )
    }

    private val scrollable: SemanticsNodeInteraction
        get() = composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))

    private fun SemanticsNode.scrollMax(): Float = config[SemanticsProperties.VerticalScrollAxisRange].maxValue()

    private fun pageDescription(page: Page): String =
        context.getString(R.string.onboarding_page_indicator_desc, page.number, 3)

    private fun indicator(page: Page): SemanticsNode = composeRule
        .onNodeWithContentDescription(pageDescription(page))
        .fetchSemanticsNode()

    private fun button(page: Page): SemanticsNodeInteraction =
        composeRule.onNode(hasClickAction() and hasText(context.getString(page.buttonRes)))

    private fun Rect.assertInside(name: String, root: Rect) = withClue("$name=$this root=$root") {
        left shouldBeGreaterThanOrEqual root.left
        top shouldBeGreaterThanOrEqual root.top
        right shouldBeLessThanOrEqual root.right
        bottom shouldBeLessThanOrEqual root.bottom
    }

    private fun rootBounds(): Rect = composeRule.onRoot().fetchSemanticsNode().boundsInRoot

    /** Returns the page indicator's description. */
    private fun assertFits(page: Page, isGplay: Boolean = false, isUpdateCheckSupported: Boolean = true): String {
        show(page, isGplay, isUpdateCheckSupported)

        val root = composeRule.onRoot().fetchSemanticsNode()
        // The screen size in px is truncated: 412dp at 420dpi is 1081.5px, the screen 1081px
        val density = composeRule.density.density
        root.size.width shouldBe (WIDTH.value * density).toInt()
        root.size.height shouldBe (HEIGHT.value * density).toInt()

        withClue("scroll range") { scrollable.fetchSemanticsNode().scrollMax() shouldBe 0f }

        val indicator = indicator(page)
        indicator.boundsInRoot.assertInside("indicator", rootBounds())
        button(page).fetchSemanticsNode().boundsInRoot.assertInside("button", rootBounds())

        return indicator.config[SemanticsProperties.ContentDescription].single()
    }

    @Test
    @Config(qualifiers = "en-w412dp-h839dp-420dpi")
    fun `English welcome page fits without scrolling`() {
        assertFits(Page.WELCOME) shouldBe "Page 1 of 3"
    }

    @Test
    @Config(qualifiers = "en-w412dp-h839dp-420dpi")
    fun `English team page of the FOSS build fits without scrolling`() {
        assertFits(Page.TEAM, isGplay = false) shouldBe "Page 2 of 3"
    }

    @Test
    @Config(qualifiers = "en-w412dp-h839dp-420dpi")
    fun `English team page of the Google Play build fits without scrolling`() {
        assertFits(Page.TEAM, isGplay = true) shouldBe "Page 2 of 3"
    }

    @Test
    @Config(qualifiers = "en-w412dp-h839dp-420dpi")
    fun `English privacy page with the update check fits without scrolling`() {
        assertFits(Page.PRIVACY, isUpdateCheckSupported = true) shouldBe "Page 3 of 3"
    }

    @Test
    @Config(qualifiers = "de-w412dp-h839dp-420dpi")
    fun `German welcome page fits without scrolling`() {
        assertFits(Page.WELCOME) shouldBe pageDescription(Page.WELCOME)
    }

    @Test
    @Config(qualifiers = "de-w412dp-h839dp-420dpi")
    fun `German team page of the FOSS build fits without scrolling`() {
        assertFits(Page.TEAM, isGplay = false) shouldBe pageDescription(Page.TEAM)
    }

    @Test
    @Config(qualifiers = "de-w412dp-h839dp-420dpi")
    fun `German team page of the Google Play build fits without scrolling`() {
        assertFits(Page.TEAM, isGplay = true) shouldBe pageDescription(Page.TEAM)
    }

    @Test
    @Config(qualifiers = "de-w412dp-h839dp-420dpi")
    fun `German privacy page with the update check fits without scrolling`() {
        assertFits(Page.PRIVACY, isUpdateCheckSupported = true) shouldBe pageDescription(Page.PRIVACY)
    }

    @Test
    @Config(qualifiers = "es-w412dp-h839dp-420dpi")
    fun `Spanish welcome page fits without scrolling`() {
        assertFits(Page.WELCOME) shouldBe pageDescription(Page.WELCOME)
    }

    @Test
    @Config(qualifiers = "es-w412dp-h839dp-420dpi")
    fun `Spanish team page of the FOSS build fits without scrolling`() {
        assertFits(Page.TEAM, isGplay = false) shouldBe pageDescription(Page.TEAM)
    }

    @Test
    @Config(qualifiers = "es-w412dp-h839dp-420dpi")
    fun `Spanish team page of the Google Play build fits without scrolling`() {
        assertFits(Page.TEAM, isGplay = true) shouldBe pageDescription(Page.TEAM)
    }

    @Test
    @Config(qualifiers = "es-w412dp-h839dp-420dpi")
    fun `Spanish privacy page with the update check fits without scrolling`() {
        assertFits(Page.PRIVACY, isUpdateCheckSupported = true) shouldBe pageDescription(Page.PRIVACY)
    }

    @Test
    @Config(qualifiers = "fr-w412dp-h839dp-420dpi")
    fun `French welcome page fits without scrolling`() {
        assertFits(Page.WELCOME) shouldBe pageDescription(Page.WELCOME)
    }

    @Test
    @Config(qualifiers = "fr-w412dp-h839dp-420dpi")
    fun `French team page of the FOSS build fits without scrolling`() {
        assertFits(Page.TEAM, isGplay = false) shouldBe pageDescription(Page.TEAM)
    }

    @Test
    @Config(qualifiers = "fr-w412dp-h839dp-420dpi")
    fun `French team page of the Google Play build fits without scrolling`() {
        assertFits(Page.TEAM, isGplay = true) shouldBe pageDescription(Page.TEAM)
    }

    @Test
    @Config(qualifiers = "fr-w412dp-h839dp-420dpi")
    fun `French privacy page with the update check fits without scrolling`() {
        assertFits(Page.PRIVACY, isUpdateCheckSupported = true) shouldBe pageDescription(Page.PRIVACY)
    }

    @Test
    @Config(qualifiers = "it-w412dp-h839dp-420dpi")
    fun `Italian welcome page fits without scrolling`() {
        assertFits(Page.WELCOME) shouldBe pageDescription(Page.WELCOME)
    }

    @Test
    @Config(qualifiers = "it-w412dp-h839dp-420dpi")
    fun `Italian team page of the FOSS build fits without scrolling`() {
        assertFits(Page.TEAM, isGplay = false) shouldBe pageDescription(Page.TEAM)
    }

    @Test
    @Config(qualifiers = "it-w412dp-h839dp-420dpi")
    fun `Italian team page of the Google Play build fits without scrolling`() {
        assertFits(Page.TEAM, isGplay = true) shouldBe pageDescription(Page.TEAM)
    }

    @Test
    @Config(qualifiers = "it-w412dp-h839dp-420dpi")
    fun `Italian privacy page with the update check fits without scrolling`() {
        assertFits(Page.PRIVACY, isUpdateCheckSupported = true) shouldBe pageDescription(Page.PRIVACY)
    }

    @Test
    @Config(qualifiers = "ru-w412dp-h839dp-420dpi")
    fun `Russian welcome page fits without scrolling`() {
        assertFits(Page.WELCOME) shouldBe pageDescription(Page.WELCOME)
    }

    @Test
    @Config(qualifiers = "ru-w412dp-h839dp-420dpi")
    fun `Russian team page of the FOSS build fits without scrolling`() {
        assertFits(Page.TEAM, isGplay = false) shouldBe pageDescription(Page.TEAM)
    }

    @Test
    @Config(qualifiers = "ru-w412dp-h839dp-420dpi")
    fun `Russian team page of the Google Play build fits without scrolling`() {
        assertFits(Page.TEAM, isGplay = true) shouldBe pageDescription(Page.TEAM)
    }

    @Test
    @Config(qualifiers = "ru-w412dp-h839dp-420dpi")
    fun `Russian privacy page with the update check fits without scrolling`() {
        assertFits(Page.PRIVACY, isUpdateCheckSupported = true) shouldBe pageDescription(Page.PRIVACY)
    }

    @Test
    @Config(qualifiers = "en-w412dp-h480dp-420dpi")
    fun `on a short screen the privacy page scrolls and keeps the button on screen`() {
        show(Page.PRIVACY, isUpdateCheckSupported = true)

        withClue("scroll range") { scrollable.fetchSemanticsNode().scrollMax() shouldBeGreaterThan 0f }

        scrollable.performScrollToNode(hasText(context.getString(R.string.updatecheck_setting_enabled_label)))
        composeRule.waitForIdle()

        button(Page.PRIVACY).assertIsDisplayed()
        button(Page.PRIVACY).fetchSemanticsNode().boundsInRoot.assertInside("button", rootBounds())
    }

    private companion object {
        val WIDTH = 412.dp
        val HEIGHT = 839.dp
    }
}
