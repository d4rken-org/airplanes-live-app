package eu.darken.apl.main.ui.navigation

import android.content.Context
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import eu.darken.apl.R
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.navigation.LocalNavigationController
import eu.darken.apl.common.navigation.NavigationController
import io.kotest.assertions.withClue
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import testhelper.BaseComposeTest

/** The bar's height checks need real text metrics. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BottomNavBarTest : BaseComposeTest() {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val map get() = context.getString(R.string.map_page_label)
    private val search get() = context.getString(R.string.search_page_label)
    private val watch get() = context.getString(R.string.watch_list_nav_label)
    private val feeder get() = context.getString(R.string.feeder_page_label)

    private var navigationBarInsetPx = -1

    private fun setBar(selectedTab: MainTab = MainTab.MAP, isFeederTabVisible: Boolean = true) {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalNavigationController provides NavigationController(),
                LocalIsFeederTabVisible provides isFeederTabVisible,
            ) {
                PreviewWrapper {
                    navigationBarInsetPx = WindowInsets.navigationBars.getBottom(LocalDensity.current)
                    BottomNavBar(selectedTab = selectedTab, modifier = Modifier.testTag(BAR))
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `the bar shows the four tabs and no Settings`() {
        setBar(isFeederTabVisible = true)

        listOf(map, search, watch, feeder).forEach { composeRule.onNodeWithText(it).assertExists() }
        val settings = context.getString(R.string.label_settings)
        composeRule
            .onAllNodes(hasText(settings) or hasContentDescription(settings), useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun `a hidden Feeder tab is not in the bar`() {
        setBar(isFeederTabVisible = false)

        composeRule.onAllNodesWithText(feeder).assertCountEquals(0)
    }

    @Test
    fun `a hidden Feeder tab stays in the bar while it is open`() {
        setBar(selectedTab = MainTab.FEEDER, isFeederTabVisible = false)

        composeRule.onNodeWithText(feeder).assertExists()
    }

    private fun SemanticsNode.textLayout(): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        return layouts.single()
    }

    /** At 360dp, with the locale's labels, the bar stays at its 64dp minimum and no label wraps. */
    private fun assertShort() {
        setBar(isFeederTabVisible = true)

        val density = composeRule.density
        val widthPx = with(density) { WIDTH.roundToPx() }
        composeRule.onRoot().fetchSemanticsNode().size.width shouldBe widthPx
        // Robolectric has no system bars, so the bar's own height is all there is
        navigationBarInsetPx shouldBe 0

        val labels = listOf(map, search, watch, feeder).map { label ->
            val item = composeRule.onNode(hasClickAction() and hasText(label)).fetchSemanticsNode()
            val text = composeRule.onAllNodes(hasText(label), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .single { it.config.contains(SemanticsActions.GetTextLayoutResult) }
            Triple(label, item, text.textLayout())
        }
        val barHeightDp = with(density) { composeRule.onNodeWithTag(BAR).fetchSemanticsNode().size.height.toDp() }

        val summary = "bar=${barHeightDp}; " + labels.joinToString { (label, item, layout) ->
            "$label: text=${with(density) { layout.size.width.toDp() }} item=${with(density) { item.size.width.toDp() }}"
        }
        withClue(summary) {
            labels.forEach { (label, _, layout) -> withClue(label) { layout.lineCount shouldBe 1 } }
            barHeightDp.value shouldBe (64f plusOrMinus 0.5f)
        }
    }

    @Test
    @Config(qualifiers = "en-w360dp-h800dp-420dpi")
    fun `English bar is short`() = assertShort()

    @Test
    @Config(qualifiers = "de-w360dp-h800dp-420dpi")
    fun `German bar is short`() = assertShort()

    @Test
    @Config(qualifiers = "fr-w360dp-h800dp-420dpi")
    fun `French bar is short`() = assertShort()

    @Test
    @Config(qualifiers = "es-w360dp-h800dp-420dpi")
    fun `Spanish bar is short`() = assertShort()

    @Test
    @Config(qualifiers = "it-w360dp-h800dp-420dpi")
    fun `Italian bar is short`() = assertShort()

    @Test
    @Config(qualifiers = "ru-w360dp-h800dp-420dpi")
    fun `Russian bar is short`() = assertShort()

    private companion object {
        const val BAR = "bar"
        val WIDTH = 360.dp
    }
}
