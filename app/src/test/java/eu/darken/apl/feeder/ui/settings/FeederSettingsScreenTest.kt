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
                    currentIntervalMinutes = 60f,
                    isNavTabVisible = true,
                    isServerMode = isServerMode,
                    notifyProblems = notifyProblems,
                ),
                onBack = {},
                onUpdateInterval = {},
                onResetInterval = {},
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
    fun `without server mode the check interval stays and problem alerts are hidden`() {
        setScreen(isServerMode = false)

        composeRule.onNodeWithText("Check interval").assertExists()
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
