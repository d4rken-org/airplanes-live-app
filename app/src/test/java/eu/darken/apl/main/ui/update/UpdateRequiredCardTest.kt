package eu.darken.apl.main.ui.update

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import eu.darken.apl.common.compose.PreviewWrapper
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelper.BaseComposeTest

class UpdateRequiredCardTest : BaseComposeTest() {

    private var updateClicks = 0
    private var checkAgainClicks = 0

    private fun setCard(isChecking: Boolean = false) = composeRule.setContent {
        PreviewWrapper {
            UpdateRequiredCard(
                isChecking = isChecking,
                onUpdate = { updateClicks++ },
                onCheckAgain = { checkAgainClicks++ },
            )
        }
    }

    @Test
    fun `offers to update or check again`() {
        setCard()

        composeRule.onNodeWithText("Update").assertIsEnabled().performClick()
        composeRule.onNodeWithText("Check again").assertIsEnabled().performClick()

        updateClicks shouldBe 1
        checkAgainClicks shouldBe 1
    }

    @Test
    fun `check again waits for the running check`() {
        setCard(isChecking = true)

        composeRule.onNodeWithText("Check again").assertIsNotEnabled().performClick()
        composeRule.onNodeWithText("Update").assertIsEnabled()

        checkAgainClicks shouldBe 0
    }
}
