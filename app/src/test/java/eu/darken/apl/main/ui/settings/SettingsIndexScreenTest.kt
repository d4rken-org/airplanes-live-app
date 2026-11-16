package eu.darken.apl.main.ui.settings

import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import eu.darken.apl.common.compose.PreviewWrapper
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelper.BaseComposeTest

class SettingsIndexScreenTest : BaseComposeTest() {

    private var sponsorClicks = 0

    private fun setScreen(isSponsorVisible: Boolean) = composeRule.setContent {
        PreviewWrapper {
            SettingsIndexScreen(
                isSponsorVisible = isSponsorVisible,
                onBack = {},
                onGeneralSettings = {},
                onMapSettings = {},
                onFeederSettings = {},
                onBackupRestore = {},
                onUpgrade = {},
                onSponsor = { sponsorClicks++ },
                onChangelog = {},
                onSupport = {},
                onAcknowledgements = {},
                onPrivacyPolicy = {},
                onDeleteData = {},
            )
        }
    }

    private fun scrollTo(text: String) {
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
    }

    @Test
    fun `visible sponsor item triggers callback`() {
        setScreen(isSponsorVisible = true)

        scrollTo("Sponsor development")
        composeRule.onNodeWithText("Sponsor development").assertExists().performClick()

        sponsorClicks shouldBe 1
    }

    @Test
    fun `hidden sponsor item is not shown`() {
        setScreen(isSponsorVisible = false)

        scrollTo("Changelog")
        composeRule.onNodeWithText("Changelog").assertExists()
        composeRule.onNodeWithText("Sponsor development").assertDoesNotExist()
    }
}
