package eu.darken.apl.map.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.onNodeWithText
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.compose.seenAgeText
import io.kotest.matchers.shouldNotBe
import org.junit.Test
import testhelper.BaseComposeTest

class LastPositionNoteTest : BaseComposeTest() {

    private val seenAt = 1_700_000_000_000L
    private var serverNow = seenAt + 3 * 60_000L

    @Test
    fun `the note counts on with the server clock`() {
        composeRule.setContent {
            PreviewWrapper { Text(lastPositionNote(seenAt) { serverNow }) }
        }
        val before = "Last position " + seenAgeText(seenAt, serverNow)
        composeRule.onNodeWithText(before).assertExists()

        serverNow += 5 * 60_000L
        composeRule.mainClock.advanceTimeBy(16_000)

        val after = "Last position " + seenAgeText(seenAt, serverNow)
        after shouldNotBe before
        composeRule.onNodeWithText(after).assertExists()
        composeRule.onNodeWithText(before).assertDoesNotExist()
    }
}
