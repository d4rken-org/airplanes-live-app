package eu.darken.apl.map.ui

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import eu.darken.apl.R
import eu.darken.apl.common.compose.PreviewWrapper
import io.kotest.assertions.withClue
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.floats.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import testhelper.BaseComposeTest

/** The title next to the settings gear on a 360dp phone, with each locale's longest tagline. Needs real text metrics. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapTopBarFitTest : BaseComposeTest() {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

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

    private fun textNode(text: String): SemanticsNode = composeRule
        .onAllNodes(hasText(text), useUnmergedTree = true)
        .fetchSemanticsNodes()
        .single()

    private val SemanticsNode.left: Float get() = positionInRoot.x
    private val SemanticsNode.right: Float get() = positionInRoot.x + size.width

    /** Lays the bar out with the locale's longest tagline and returns that tagline's layout. */
    private fun assertFits(isPro: Boolean): TextLayoutResult {
        val tagline = context.resources.getStringArray(R.array.map_taglines).maxBy { it.length }
        val appName = context.getString(R.string.app_name)
        val chip = context.getString(if (isPro) R.string.upgrade_tier_chip_pro else R.string.upgrade_tier_chip_free)
        val settings = context.getString(R.string.label_settings)

        composeRule.setContent {
            PreviewWrapper {
                MapTopBar(
                    isPro = isPro,
                    tagline = tagline,
                    onTierClick = {},
                    onSettingsClick = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(BAR),
                )
            }
        }
        composeRule.waitForIdle()

        val widthPx = with(composeRule.density) { WIDTH.roundToPx() }
        composeRule.onRoot().fetchSemanticsNode().size.width shouldBe widthPx
        composeRule.onNodeWithTag(BAR).fetchSemanticsNode().size.width shouldBe widthPx

        val appNameNode = textNode(appName)
        val chipTextNode = textNode(chip)
        val chipNode = composeRule.onNode(hasClickAction() and hasText(chip)).fetchSemanticsNode()
        val gearNode = composeRule.onNodeWithContentDescription(settings).fetchSemanticsNode()

        withClue(
            "appName=[${appNameNode.left}, ${appNameNode.right}] chip=[${chipNode.left}, ${chipNode.right}] " +
                "gear=[${gearNode.left}, ${gearNode.right}] width=$widthPx density=${composeRule.density.density}"
        ) {
            appNameNode.assertWhole("app name", appName)
            chipTextNode.assertWhole("chip", chip)
            chipNode.right shouldBeLessThanOrEqual gearNode.left
            gearNode.left shouldBeGreaterThanOrEqual 0f
            gearNode.right shouldBeLessThanOrEqual widthPx.toFloat()
        }

        val taglineLayout = textNode(tagline).textLayout()
        withClue("tagline") { taglineLayout.lineCount shouldBe 1 }
        return taglineLayout
    }

    @Test
    @Config(qualifiers = "de-w360dp-h800dp-420dpi")
    fun `German free title fits next to the gear`() {
        assertFits(isPro = false)
    }

    @Test
    @Config(qualifiers = "ru-w360dp-h800dp-420dpi")
    fun `Russian free title fits next to the gear`() {
        assertFits(isPro = false)
    }

    @Test
    @Config(qualifiers = "es-w360dp-h800dp-420dpi")
    fun `Spanish free title fits next to the gear and its tagline is shortened`() {
        val tagline = assertFits(isPro = false)
        withClue("tagline") { tagline.isLineEllipsized(0) shouldBe true }
    }

    @Test
    @Config(qualifiers = "en-w360dp-h800dp-420dpi")
    fun `English pro title fits next to the gear`() {
        assertFits(isPro = true)
    }

    private companion object {
        const val BAR = "bar"
        val WIDTH = 360.dp
    }
}
