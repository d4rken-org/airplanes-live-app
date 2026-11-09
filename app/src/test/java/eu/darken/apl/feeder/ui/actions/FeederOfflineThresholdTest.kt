package eu.darken.apl.feeder.ui.actions

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import eu.darken.apl.common.compose.PreviewWrapper
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelper.BaseComposeTest
import java.time.Duration

class FeederOfflineThresholdTest : BaseComposeTest() {

    private val choiceTexts = listOf("Off", "6 hours", "12 hours", "24 hours", "48 hours")

    private fun setDialog(threshold: Duration?, onSelect: (Duration?) -> Unit = {}) = composeRule.setContent {
        PreviewWrapper {
            FeederOfflineThresholdDialog(threshold = threshold, onSelect = onSelect, onDismiss = {})
        }
    }

    @Test
    fun `the dialog offers the five choices with the current one selected`() {
        setDialog(Duration.ofHours(12))

        composeRule.onNodeWithText("12 hours").assertIsSelected()
        (choiceTexts - "12 hours").forEach { composeRule.onNodeWithText(it).assertIsNotSelected() }
    }

    @Test
    fun `no threshold selects Off`() {
        setDialog(null)

        composeRule.onNodeWithText("Off").assertIsSelected()
        (choiceTexts - "Off").forEach { composeRule.onNodeWithText(it).assertIsNotSelected() }
    }

    @Test
    fun `choosing 6 hours reports six hours`() {
        val selected = mutableListOf<Duration?>()
        setDialog(null, onSelect = { selected += it })

        composeRule.onNodeWithText("6 hours").performClick()

        selected shouldBe listOf(Duration.ofHours(6))
    }

    @Test
    fun `choosing Off reports no threshold`() {
        val selected = mutableListOf<Duration?>()
        setDialog(Duration.ofHours(48), onSelect = { selected += it })

        composeRule.onNodeWithText("Off").performClick()

        selected shouldBe listOf(null)
    }

    @Test
    fun `a stored threshold that is no choice selects nothing`() {
        setDialog(Duration.ofHours(3))

        choiceTexts.forEach { composeRule.onNodeWithText(it).assertIsNotSelected() }
    }

    @Test
    fun `the row shows a stored threshold that is no choice and opens the dialog`() {
        val selected = mutableListOf<Duration?>()
        composeRule.setContent {
            PreviewWrapper {
                FeederOfflineThresholdRow(threshold = Duration.ofHours(3), onSelect = { selected += it })
            }
        }

        composeRule.onNodeWithText("3 hours").performClick()
        composeRule.onNodeWithText("24 hours").performClick()

        selected shouldBe listOf(Duration.ofHours(24))
    }
}
