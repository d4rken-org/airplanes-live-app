package eu.darken.apl.main.ui.update

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.main.core.update.AppUpdatePrompt.Prompt
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelper.BaseComposeTest

class AppUpdateScreenTest : BaseComposeTest() {

    private var updateClicks = 0
    private var notNowClicks = 0
    private var checkAgainClicks = 0
    private var continueClicks = 0

    private fun setScreen(prompt: Prompt, isChecking: Boolean = false) = composeRule.setContent {
        PreviewWrapper {
            AppUpdateScreen(
                prompt = prompt,
                isChecking = isChecking,
                onUpdate = { updateClicks++ },
                onNotNow = { notNowClicks++ },
                onCheckAgain = { checkAgainClicks++ },
                onContinueLimited = { continueClicks++ },
            )
        }
    }

    @Test
    fun `a recommendation offers to update or put it off`() {
        setScreen(Prompt.Recommended(version = 10200))

        composeRule.onNodeWithText("Check again").assertDoesNotExist()
        composeRule.onNodeWithText("Continue with limited functionality").assertDoesNotExist()

        composeRule.onNodeWithText("Update").assertIsEnabled().performClick()
        composeRule.onNodeWithText("Not now").assertIsEnabled().performClick()

        updateClicks shouldBe 1
        notNowClicks shouldBe 1
    }

    @Test
    fun `a required update offers to update, check again or continue`() {
        setScreen(Prompt.Required)

        composeRule.onNodeWithText("Not now").assertDoesNotExist()

        composeRule.onNodeWithText("Update").assertIsEnabled().performClick()
        composeRule.onNodeWithText("Check again").assertIsEnabled().performClick()
        composeRule.onNodeWithText("Continue with limited functionality").assertIsEnabled().performClick()

        updateClicks shouldBe 1
        checkAgainClicks shouldBe 1
        continueClicks shouldBe 1
    }

    @Test
    fun `check again waits for the running check`() {
        setScreen(Prompt.Required, isChecking = true)

        composeRule.onNodeWithText("Check again").assertIsNotEnabled().performClick()

        checkAgainClicks shouldBe 0
    }
}
