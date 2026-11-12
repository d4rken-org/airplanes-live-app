package eu.darken.apl.feeder.ui.settings

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import eu.darken.apl.common.compose.PreviewWrapper
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelper.BaseComposeTest

class FeederSettingsScreenTest : BaseComposeTest() {

    private fun setScreen(
        isServerMode: Boolean,
        notifyProblems: Boolean = true,
        onNotifyProblemsChange: (Boolean) -> Unit = {},
    ) = composeRule.setContent {
        PreviewWrapper {
            FeederSettingsScreen(
                state = FeederSettingsViewModel.State(
                    isNavTabVisible = true,
                    isServerMode = isServerMode,
                    notifyProblems = notifyProblems,
                ),
                onBack = {},
                onNavTabVisibleChange = {},
                onNotifyProblemsChange = onNotifyProblemsChange,
            )
        }
    }

    @Test
    fun `server mode offers problem alerts and no check interval`() {
        setScreen(isServerMode = true)

        composeRule.onNodeWithText("Problem alerts").assertExists()
        composeRule.onNodeWithText("Check interval").assertDoesNotExist()
    }

    @Test
    fun `without server mode neither problem alerts nor a check interval are offered`() {
        setScreen(isServerMode = false)

        composeRule.onNodeWithText("Check interval").assertDoesNotExist()
        composeRule.onNodeWithText("Problem alerts").assertDoesNotExist()
    }

    @Test
    fun `switching problem alerts off reports it`() {
        val changes = mutableListOf<Boolean>()
        setScreen(isServerMode = true, notifyProblems = true, onNotifyProblemsChange = { changes += it })

        composeRule.onNodeWithText("Problem alerts").performClick()

        changes shouldBe listOf(false)
    }
}
