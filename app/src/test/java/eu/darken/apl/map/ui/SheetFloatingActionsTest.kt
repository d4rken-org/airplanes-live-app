package eu.darken.apl.map.ui

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.darken.apl.common.compose.PreviewWrapper
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelper.BaseComposeTest

class SheetFloatingActionsTest : BaseComposeTest() {

    private var centered = 0

    private fun setActions(
        onCenter: (() -> Unit)? = { centered++ },
        progress: Float = 0f,
        sheetMaxWidth: Dp = 2_000.dp,
    ) = composeRule.setContent {
        PreviewWrapper {
            SheetFloatingActions(
                sheetTop = { 0f },
                contentScroll = { 0 },
                progress = { progress },
                safeTop = 0,
                sheetMaxWidth = with(LocalDensity.current) { sheetMaxWidth.roundToPx() },
                startInset = 0,
                endInset = 0,
                onRowHeight = {},
                follow = null,
                onToggleFollow = {},
                onShare = null,
                onClose = {},
                onCenter = onCenter,
            )
        }
    }

    // The row is also composed unplaced to measure its width, the placed one comes last

    @Test
    fun `center is offered at rest and centers on the airport`() {
        setActions()

        composeRule.onAllNodesWithText("Center").onLast().performClick()

        centered shouldBe 1
    }

    @Test
    fun `without anything to center on there is no center chip`() {
        setActions(onCenter = null)

        composeRule.onAllNodesWithText("Center").assertCountEquals(0)
    }

    @Test
    fun `an expanded sheet has no center chip`() {
        setActions(progress = 1f)

        composeRule.onAllNodesWithText("Center").assertCountEquals(0)
    }

    @Test
    fun `where the row cannot fit labels center is an icon`() {
        setActions(sheetMaxWidth = 100.dp)

        composeRule.onAllNodesWithContentDescription("Center").onLast()
            .assertHasClickAction()
            .assert(!hasText("Center"))
            .performClick()

        centered shouldBe 1
    }
}
