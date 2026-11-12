package eu.darken.apl.feeder.ui.settings

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import eu.darken.apl.common.compose.PreviewWrapper
import io.kotest.matchers.collections.shouldBeStrictlyIncreasing
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelper.BaseComposeTest

class FeederSettingsScreenTest : BaseComposeTest() {

    private fun setScreen(
        isServerMode: Boolean,
        notifyProblems: Boolean = true,
        isNavTabVisible: Boolean = true,
        onNotifyProblemsChange: (Boolean) -> Unit = {},
        onNavTabVisibleChange: (Boolean) -> Unit = {},
    ) = composeRule.setContent {
        PreviewWrapper {
            FeederSettingsScreen(
                state = FeederSettingsViewModel.State(
                    isNavTabVisible = isNavTabVisible,
                    isServerMode = isServerMode,
                    notifyProblems = notifyProblems,
                ),
                onBack = {},
                onNavTabVisibleChange = onNavTabVisibleChange,
                onNotifyProblemsChange = onNotifyProblemsChange,
            )
        }
    }

    // Robolectric fakes text metrics, so only the relative order of the tops is meaningful.
    private fun topOf(text: String): Float = composeRule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top

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
    fun `server mode groups problem alerts under monitoring and the tab under navigation`() {
        setScreen(isServerMode = true)

        val order = listOf("Monitoring", "Problem alerts", "Navigation", "Show Feeder tab")
        order.forEach { composeRule.onNodeWithText(it).assertExists() }

        order.map { topOf(it) }.shouldBeStrictlyIncreasing()
    }

    @Test
    fun `without server mode only the navigation group is shown`() {
        setScreen(isServerMode = false)

        composeRule.onNodeWithText("Monitoring").assertDoesNotExist()
        composeRule.onNodeWithText("Problem alerts").assertDoesNotExist()
        composeRule.onNodeWithText("Navigation").assertExists()
        composeRule.onNodeWithText("Show Feeder tab").assertExists()

        listOf("Navigation", "Show Feeder tab").map { topOf(it) }.shouldBeStrictlyIncreasing()
    }

    @Test
    fun `switching problem alerts off reports it`() {
        val changes = mutableListOf<Boolean>()
        setScreen(isServerMode = true, notifyProblems = true, onNotifyProblemsChange = { changes += it })

        composeRule.onNodeWithText("Problem alerts").performClick()

        changes shouldBe listOf(false)
    }

    @Test
    fun `hiding the feeder tab reports it`() {
        val changes = mutableListOf<Boolean>()
        setScreen(isServerMode = false, isNavTabVisible = true, onNavTabVisibleChange = { changes += it })

        composeRule.onNodeWithText("Show Feeder tab").performClick()

        changes shouldBe listOf(false)
    }
}
