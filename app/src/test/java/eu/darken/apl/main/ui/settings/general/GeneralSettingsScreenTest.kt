package eu.darken.apl.main.ui.settings.general

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.theming.ThemeColor
import eu.darken.apl.common.theming.ThemeMode
import eu.darken.apl.common.theming.ThemePalette
import eu.darken.apl.common.theming.ThemeStyle
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.robolectric.annotation.Config
import testhelper.BaseComposeTest

class GeneralSettingsScreenTest : BaseComposeTest() {

    private var upgradeClicks = 0

    private fun setScreen(isUpgraded: Boolean, themeStyle: ThemeStyle = ThemeStyle.DEFAULT) = composeRule.setContent {
        PreviewWrapper {
            GeneralSettingsScreen(
                state = GeneralSettingsViewModel.State(
                    themeMode = ThemeMode.SYSTEM,
                    themeStyle = themeStyle,
                    themeColor = ThemeColor.BLUE,
                    customSeed = null,
                    customPalette = ThemePalette.TONAL_SPOT,
                    isUpdateCheckEnabled = false,
                    isUpdateCheckSupported = false,
                    isUpgraded = isUpgraded,
                    isDebugBuild = false,
                    isFakeUpgradeEnabled = false,
                ),
                onBack = {},
                onThemeModeChange = {},
                onThemeStyleChange = {},
                onThemeColorChange = { _, _ -> },
                onCustomThemeApply = { _, _, _ -> },
                onUpgrade = { upgradeClicks++ },
                onToggleUpdateCheck = {},
                onToggleFakeUpgrade = {},
            )
        }
    }

    @Test
    fun `theme rows open the upgrade screen when not upgraded`() {
        setScreen(isUpgraded = false)

        composeRule.onAllNodesWithText("UPGRADE").assertCountEquals(3)

        composeRule.onNodeWithText("Theme mode").performClick()
        composeRule.onNodeWithText("Dark").assertDoesNotExist()
        composeRule.onNodeWithText("Theme style").performClick()
        composeRule.onNodeWithText("Theme color").performClick()
        composeRule.onNodeWithText("AMOLED").assertDoesNotExist()

        upgradeClicks shouldBe 3
    }

    @Test
    fun `theme rows open their pickers when upgraded`() {
        setScreen(isUpgraded = true)

        composeRule.onAllNodesWithText("UPGRADE").assertCountEquals(0)

        composeRule.onNodeWithText("Theme mode").performClick()
        composeRule.onNodeWithText("Dark").assertExists()

        upgradeClicks shouldBe 0
    }

    @Test
    @Config(sdk = [34])
    fun `color row disabled by Material You carries no upgrade chip`() {
        setScreen(isUpgraded = false, themeStyle = ThemeStyle.MATERIAL_YOU)

        composeRule.onAllNodesWithText("UPGRADE").assertCountEquals(2)

        composeRule.onNodeWithText("Theme color").performClick()

        upgradeClicks shouldBe 0
    }
}
