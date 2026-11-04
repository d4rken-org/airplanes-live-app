package eu.darken.apl.map.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import eu.darken.apl.common.compose.PreviewWrapper
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelper.BaseComposeTest

class MapFilterChipsTest : BaseComposeTest() {

    private val cleared = mutableListOf<String>()

    private fun setChips(query: String = "", watchedOnly: Boolean = false) = composeRule.setContent {
        PreviewWrapper {
            MapFilterChips(
                militaryOnly = false,
                isolating = false,
                pinnedCount = 0,
                query = query,
                watchedOnly = watchedOnly,
                cap = null,
                onClearMilitaryOnly = { cleared += "military" },
                onClearIsolate = { cleared += "isolate" },
                onClearPinned = { cleared += "pinned" },
                onClearQuery = { cleared += "query" },
                onClearWatchedOnly = { cleared += "watched" },
                onCapInfo = {},
            )
        }
    }

    @Test
    fun `a query chip clears only the query`() {
        setChips(query = " dlh ", watchedOnly = true)

        composeRule.onNodeWithText("dlh").performClick()

        cleared shouldContainExactly listOf("query")
    }

    @Test
    fun `a blank query shows no chip`() {
        setChips(query = "   ")

        composeRule.onAllNodes(hasClickAction()).assertCountEquals(0)
    }

    @Test
    fun `the watched chip clears only watched-only`() {
        setChips(query = "dlh", watchedOnly = true)

        composeRule.onNodeWithText("Watched").performClick()

        cleared shouldContainExactly listOf("watched")
    }

    @Test
    fun `without a query or watched-only neither chip shows`() {
        setChips()

        composeRule.onAllNodes(hasClickAction()).assertCountEquals(0)
        composeRule.onNodeWithText("Watched").assertDoesNotExist()
    }

    @Test
    fun `the query chip describes the full query for accessibility`() {
        val trimmed = "LH400 Frankfurt to New York with a very long pasted search text"
        setChips(query = " $trimmed ")

        val chip = composeRule.onNode(hasClickAction() and hasText(trimmed))
        chip.assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(trimmed)))
        chip.fetchSemanticsNode().config[SemanticsActions.OnClick].label shouldBe "Clear the filter text"

        chip.performClick()

        cleared shouldContainExactly listOf("query")
    }
}
