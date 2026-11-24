package eu.darken.apl.map.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import eu.darken.apl.common.compose.PreviewWrapper
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelper.BaseComposeTest

class MapControlRailTest : BaseComposeTest() {

    private val clicked = mutableListOf<String>()

    private val descriptions = listOf("Map controls", "Aircraft list", "My location", "AR view", "Fullscreen")

    private var claimed = false
    private val claimOnce: () -> Boolean = { !claimed.also { claimed = true } }

    private fun setRail(showLabels: Boolean, isFullscreen: Boolean = false) = composeRule.setContent {
        PreviewWrapper {
            MapControlRail(
                isFullscreen = isFullscreen,
                showAr = true,
                showLabels = showLabels,
                onOptions = { clicked += "options" },
                onAircraftList = { clicked += "list" },
                onAr = { clicked += "ar" },
                onFullscreen = { clicked += "fullscreen" },
                onMyLocation = { clicked += "location" },
            )
        }
    }

    @Test
    fun `labels show while asked to`() {
        setRail(showLabels = true)

        composeRule.onAllNodesWithTag(RAIL_LABEL_TAG, useUnmergedTree = true).assertCountEquals(descriptions.size)
    }

    @Test
    fun `without labels only the icons show`() {
        setRail(showLabels = false)

        composeRule.onAllNodesWithTag(RAIL_LABEL_TAG, useUnmergedTree = true).assertCountEquals(0)
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(descriptions.size)
    }

    @Test
    fun `tapping a label runs its button once`() {
        setRail(showLabels = true)

        composeRule.onNode(
            hasTestTag(RAIL_LABEL_TAG) and hasAnySibling(hasContentDescription("My location")),
            useUnmergedTree = true,
        ).performClick()

        clicked shouldContainExactly listOf("location")
    }

    @Test
    fun `tapping an icon runs its button once`() {
        setRail(showLabels = true)

        composeRule.onNodeWithContentDescription("My location", useUnmergedTree = true).performClick()

        clicked shouldContainExactly listOf("location")
    }

    @Test
    fun `each button is one node carrying its description`() {
        setRail(showLabels = true)

        composeRule.onAllNodes(hasClickAction()).assertCountEquals(descriptions.size)
        descriptions.forEach { description ->
            composeRule.onAllNodesWithContentDescription(description).assertCountEquals(1)
            composeRule.onAllNodes(hasClickAction() and hasContentDescription(description)).assertCountEquals(1)
            composeRule.onAllNodesWithText(description).assertCountEquals(0)
        }
    }

    @Test
    fun `the fullscreen button offers to leave fullscreen`() {
        setRail(showLabels = true, isFullscreen = true)

        composeRule.onAllNodesWithContentDescription("Fullscreen").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Exit fullscreen")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Exit fullscreen")))
            .performClick()

        clicked shouldContainExactly listOf("fullscreen")
    }

    @Test
    fun `outside fullscreen the button offers fullscreen`() {
        setRail(showLabels = false, isFullscreen = false)

        composeRule.onAllNodesWithContentDescription("Exit fullscreen").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Fullscreen")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Fullscreen")))
    }

    @Test
    fun `the intro shows for its duration and then collapses`() {
        composeRule.mainClock.autoAdvance = false
        var intro: Boolean? = null
        composeRule.setContent { intro = rememberRailIntro(claimOnce) }

        intro shouldBe true

        composeRule.mainClock.advanceTimeBy(RAIL_INTRO_DURATION.inWholeMilliseconds - 100)
        intro shouldBe true

        composeRule.mainClock.advanceTimeBy(200)
        intro shouldBe false
    }

    @Test
    fun `without the claim there is no intro`() {
        composeRule.mainClock.autoAdvance = false
        var intro: Boolean? = null
        composeRule.setContent { intro = rememberRailIntro { false } }

        intro shouldBe false
    }

    @Test
    fun `coming back to the screen does not replay the intro`() {
        composeRule.mainClock.autoAdvance = false
        var shown by mutableStateOf(true)
        var intro: Boolean? = null
        composeRule.setContent {
            if (shown) intro = rememberRailIntro(claimOnce)
        }
        composeRule.mainClock.advanceTimeBy(RAIL_INTRO_DURATION.inWholeMilliseconds + 100)
        intro shouldBe false

        // Nothing is timed from here on
        composeRule.mainClock.autoAdvance = true
        shown = false
        composeRule.waitForIdle()
        intro = null
        shown = true
        composeRule.waitForIdle()

        intro shouldBe false
    }
}
