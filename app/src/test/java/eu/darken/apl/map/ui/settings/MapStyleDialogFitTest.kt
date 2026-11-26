package eu.darken.apl.map.ui.settings

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import eu.darken.apl.R
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.map.core.NativeMapStyle
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.floats.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import testhelper.BaseComposeTest

/** The upgrade chips next to the fixed styles on a 360dp phone. Needs real text metrics. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapStyleDialogFitTest : BaseComposeTest() {

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

    private val SemanticsNode.left: Float get() = positionInRoot.x
    private val SemanticsNode.right: Float get() = positionInRoot.x + size.width
    private val SemanticsNode.top: Float get() = positionInRoot.y
    private val SemanticsNode.bottom: Float get() = positionInRoot.y + size.height

    private fun assertChipsFit(fontScale: Float = 1f) {
        val chip = context.getString(R.string.upgrade_chip_label)
        val title = context.getString(R.string.map_settings_layer_title)

        composeRule.setContent {
            PreviewWrapper {
                MapSettingsScreen(
                    state = MapSettingsViewModel.State(
                        nativeMapStyle = NativeMapStyle.AUTO,
                        isRestoreLastViewEnabled = false,
                        isUpgraded = false,
                    ),
                    onBack = {},
                    onSetNativeMapStyle = {},
                    onToggleRestoreLastView = {},
                    onUpgrade = {},
                )
            }
        }
        composeRule.onNodeWithText(title).performClick()
        composeRule.waitForIdle()

        composeRule.density.fontScale shouldBe fontScale

        val inDialog = hasAnyAncestor(isDialog())
        val dialogNode = composeRule.onNode(isDialog()).fetchSemanticsNode()
        val rows = composeRule.onAllNodes(isSelectable() and inDialog).fetchSemanticsNodes()
        val chipTexts = composeRule.onAllNodes(hasText(chip) and inDialog, useUnmergedTree = true).fetchSemanticsNodes()
        val chipNodes = composeRule.onAllNodes(hasClickAction() and hasText(chip) and inDialog).fetchSemanticsNodes()

        rows shouldHaveSize NativeMapStyle.entries.size
        chipTexts shouldHaveSize NativeMapStyle.entries.count { it.requiresPro }
        chipNodes shouldHaveSize chipTexts.size

        chipTexts.forEachIndexed { index, node -> node.assertWhole("chip text $index", chip) }

        chipNodes.forEachIndexed { index, chipNode ->
            val centerY = (chipNode.top + chipNode.bottom) / 2
            val row = rows.single { centerY >= it.top && centerY <= it.bottom }
            withClue(
                "chip $index=[${chipNode.left}, ${chipNode.right}] row=[${row.left}, ${row.right}] " +
                    "dialog=[${dialogNode.left}, ${dialogNode.right}] density=${composeRule.density.density}"
            ) {
                chipNode.left shouldBeGreaterThanOrEqual row.left
                chipNode.right shouldBeLessThanOrEqual row.right
                row.right shouldBeLessThanOrEqual dialogNode.right
            }
        }
    }

    @Test
    @Config(qualifiers = "en-w360dp-h800dp-420dpi")
    fun `English chips fit`() = assertChipsFit()

    @Test
    @Config(qualifiers = "en-w360dp-h800dp-420dpi", fontScale = 1.3f)
    fun `English chips fit at large font`() = assertChipsFit(fontScale = 1.3f)

    @Test
    @Config(qualifiers = "fr-w360dp-h800dp-420dpi")
    fun `French chips fit`() = assertChipsFit()

    @Test
    @Config(qualifiers = "fr-w360dp-h800dp-420dpi", fontScale = 1.3f)
    fun `French chips fit at large font`() = assertChipsFit(fontScale = 1.3f)

    @Test
    @Config(qualifiers = "it-w360dp-h800dp-420dpi")
    fun `Italian chips fit`() = assertChipsFit()

    @Test
    @Config(qualifiers = "it-w360dp-h800dp-420dpi", fontScale = 1.3f)
    fun `Italian chips fit at large font`() = assertChipsFit(fontScale = 1.3f)
}
