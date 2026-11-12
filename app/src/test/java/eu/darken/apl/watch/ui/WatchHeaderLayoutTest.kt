package eu.darken.apl.watch.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import eu.darken.apl.common.compose.StatusPillDefaults
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.robolectric.annotation.Config
import testhelper.BaseComposeTest

@Config(qualifiers = "w411dp-h891dp")
class WatchHeaderLayoutTest : BaseComposeTest() {

    private fun setLayout(
        titleWidth: Dp,
        statusWidth: Dp,
        inset: Dp = 0.dp,
        direction: LayoutDirection = LayoutDirection.Ltr,
    ) = composeRule.setContent {
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
            // The padding keeps a status moved past the layout's start inside the root
            Box(Modifier.width(332.dp).padding(horizontal = 16.dp)) {
                WatchHeaderLayout(
                    title = { Box(Modifier.size(titleWidth, 20.dp).testTag(TITLE)) },
                    status = { Box(Modifier.size(statusWidth, 10.dp).testTag(STATUS)) },
                    wrappedStatusInset = inset,
                    modifier = Modifier.fillMaxWidth().testTag(LAYOUT),
                )
            }
        }
    }

    private fun bounds(tag: String): DpRect = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    @Test
    fun `title and status that fit share a line at either end`() {
        setLayout(titleWidth = 100.dp, statusWidth = 80.dp)

        val layout = bounds(LAYOUT)
        val title = bounds(TITLE)
        val status = bounds(STATUS)
        title.left shouldBe layout.left
        status.right shouldBe layout.right
        status.top shouldBe title.top + 5.dp
        layout.bottom - layout.top shouldBe 20.dp
    }

    @Test
    fun `title and status that exactly fit still share a line`() {
        setLayout(titleWidth = 220.dp, statusWidth = 80.dp)

        val layout = bounds(LAYOUT)
        val title = bounds(TITLE)
        val status = bounds(STATUS)
        title.left shouldBe layout.left
        status.right shouldBe layout.right
        status.top shouldBe title.top + 5.dp
        layout.bottom - layout.top shouldBe 20.dp
    }

    @Test
    fun `a status that does not fit goes below the title at its start`() {
        setLayout(titleWidth = 250.dp, statusWidth = 80.dp)

        val layout = bounds(LAYOUT)
        val title = bounds(TITLE)
        val status = bounds(STATUS)
        status.left shouldBe title.left
        status.top shouldBe title.bottom
        layout.bottom - layout.top shouldBe 30.dp
    }

    @Test
    fun `a wrapped status moves by the inset towards the start`() {
        setLayout(titleWidth = 250.dp, statusWidth = 80.dp, inset = 8.dp)

        bounds(STATUS).left shouldBe bounds(TITLE).left - 8.dp
    }

    @Test
    fun `right-to-left puts the title at the right and the status at the left`() {
        setLayout(titleWidth = 100.dp, statusWidth = 80.dp, direction = LayoutDirection.Rtl)

        val layout = bounds(LAYOUT)
        bounds(TITLE).right shouldBe layout.right
        bounds(STATUS).left shouldBe layout.left
    }

    @Test
    fun `right-to-left moves a wrapped status by the inset towards the right`() {
        setLayout(titleWidth = 250.dp, statusWidth = 80.dp, inset = 8.dp, direction = LayoutDirection.Rtl)

        bounds(STATUS).right shouldBe bounds(TITLE).right + 8.dp
    }

    @Test
    fun `an age pill on a plain card without bell is inset by the pill's padding`() {
        wrappedStatusInset(WatchStatusKind.AGE, hasBell = false, isSelected = false) shouldBe
            StatusPillDefaults.HorizontalPadding
    }

    @Test
    fun `a bell starts at the title`() {
        wrappedStatusInset(WatchStatusKind.AGE, hasBell = true, isSelected = false) shouldBe 0.dp
    }

    @Test
    fun `a pill on a selected card starts at the title`() {
        wrappedStatusInset(WatchStatusKind.AGE, hasBell = false, isSelected = true) shouldBe 0.dp
    }

    @Test
    fun `a live pill starts at the title`() {
        wrappedStatusInset(WatchStatusKind.LIVE, hasBell = false, isSelected = false) shouldBe 0.dp
    }

    @Test
    fun `the server text starts at the title`() {
        wrappedStatusInset(WatchStatusKind.SERVER_TEXT, hasBell = false, isSelected = false) shouldBe 0.dp
    }

    companion object {
        private const val LAYOUT = "layout"
        private const val TITLE = "title"
        private const val STATUS = "status"
    }
}
