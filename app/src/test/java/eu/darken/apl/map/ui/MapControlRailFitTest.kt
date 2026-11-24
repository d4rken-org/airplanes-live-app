package eu.darken.apl.map.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import eu.darken.apl.common.compose.PreviewWrapper
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import testhelper.BaseComposeTest

/** The rail's labels on a rail narrower than label + button. Needs real text metrics. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapControlRailFitTest : BaseComposeTest() {

    private val descriptions = listOf("Map controls", "Aircraft list", "My location", "AR view", "Fullscreen")

    @Test
    @Config(qualifiers = "en-w360dp-h800dp-420dpi")
    fun `icons keep their size while long labels show`() {
        composeRule.setContent {
            PreviewWrapper {
                Box(Modifier.width(120.dp)) {
                    MapControlRail(
                        isFullscreen = false,
                        showAr = true,
                        showLabels = true,
                        onOptions = {},
                        onAircraftList = {},
                        onAr = {},
                        onFullscreen = {},
                        onMyLocation = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag(RAIL_LABEL_TAG, useUnmergedTree = true).assertCountEquals(descriptions.size)

        val iconPx = with(composeRule.density) { 24.dp.roundToPx() }
        val widths = descriptions.associateWith { description ->
            composeRule.onNodeWithContentDescription(description, useUnmergedTree = true)
                .fetchSemanticsNode().size.width
        }
        withClue("icon widths in px, density=${composeRule.density.density}") {
            widths shouldBe descriptions.associateWith { iconPx }
        }
    }
}
