package eu.darken.apl.main.ui.settings.deletion

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.main.ui.settings.deletion.DeleteDataViewModel.State
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelper.BaseComposeTest

class DeleteDataScreenTest : BaseComposeTest() {

    private var deletes = 0
    private var emails = 0

    private fun setScreen(state: State) = composeRule.setContent {
        PreviewWrapper {
            DeleteDataScreen(
                state = state,
                onBack = {},
                onDelete = { deletes++ },
                onRetry = {},
                onCancelPending = {},
                onEmail = { emails++ },
            )
        }
    }

    // The top bar carries the same text, only the button is clickable
    private fun deleteButton() = composeRule.onNode(hasText("Delete my data") and hasClickAction())

    @Test
    fun `the delete button asks before deleting`() {
        setScreen(State.Idle)

        deleteButton().performScrollTo().performClick()

        composeRule.onNodeWithText("Delete your data?").assertExists()
        deletes shouldBe 0
    }

    @Test
    fun `confirming in the dialog deletes once`() {
        setScreen(State.Idle)

        deleteButton().performScrollTo().performClick()
        composeRule.onNodeWithText("Delete").performClick()

        deletes shouldBe 1
        composeRule.onNodeWithText("Delete your data?").assertDoesNotExist()
    }

    @Test
    fun `cancelling the dialog deletes nothing`() {
        setScreen(State.Idle)

        deleteButton().performScrollTo().performClick()
        composeRule.onNodeWithText("Cancel").performClick()

        deletes shouldBe 0
        composeRule.onNodeWithText("Delete your data?").assertDoesNotExist()
    }

    @Test
    fun `a refused deletion shows the installation id and offers an email`() {
        setScreen(State.Refused(installationId = "inst-1234"))

        composeRule.onNodeWithText("inst-1234").assertExists()
        composeRule.onNodeWithText("Email").performScrollTo().performClick()

        emails shouldBe 1
        deletes shouldBe 0
    }
}
