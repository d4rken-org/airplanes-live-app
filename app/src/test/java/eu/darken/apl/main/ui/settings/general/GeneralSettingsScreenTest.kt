package eu.darken.apl.main.ui.settings.general

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.theming.ThemeColor
import eu.darken.apl.common.theming.ThemeMode
import eu.darken.apl.common.theming.ThemePalette
import eu.darken.apl.common.theming.ThemeStyle
import eu.darken.apl.main.core.BackgroundSyncLevel
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.robolectric.annotation.Config
import testhelper.BaseComposeTest

class GeneralSettingsScreenTest : BaseComposeTest() {

    private var upgradeClicks = 0
    private val reportedLevels = mutableListOf<BackgroundSyncLevel>()

    private fun setScreen(
        isUpgraded: Boolean,
        themeStyle: ThemeStyle = ThemeStyle.DEFAULT,
        backgroundSyncLevel: BackgroundSyncLevel = BackgroundSyncLevel.BALANCED,
    ) = composeRule.setContent {
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
                    backgroundSyncLevel = backgroundSyncLevel,
                ),
                onBack = {},
                onThemeModeChange = {},
                onThemeStyleChange = {},
                onThemeColorChange = { _, _ -> },
                onCustomThemeApply = { _, _, _ -> },
                onUpgrade = { upgradeClicks++ },
                onToggleUpdateCheck = {},
                onToggleFakeUpgrade = {},
                onBackgroundSyncLevelChange = { reportedLevels += it },
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

    private fun backgroundSyncRow(): SemanticsNodeInteraction {
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Background sync"))
        return composeRule.onNodeWithText("Background sync")
    }

    private fun inDialog(text: String) = composeRule.onNode(hasText(text) and hasAnyAncestor(isDialog()))

    @Test
    fun `background sync shows the level in effect`() {
        setScreen(isUpgraded = false, backgroundSyncLevel = BackgroundSyncLevel.SLOW)

        backgroundSyncRow().assert(hasText("Slow"))
    }

    @Test
    fun `background sync dialog lists the levels with their descriptions`() {
        setScreen(isUpgraded = true)

        backgroundSyncRow().performClick()

        inDialog("Fast").assertExists()
        inDialog("About every 15 minutes. Uses more battery.").assertExists()
        inDialog("Balanced").assertExists()
        inDialog("About every hour.").assertExists()
        inDialog("Slow").assertExists()
        inDialog("About every 6 hours. Saves battery, but alerts arrive late.").assertExists()
    }

    @Test
    fun `not upgraded, the fast chip opens the upgrade screen and reports no level`() {
        setScreen(isUpgraded = false)

        backgroundSyncRow().performClick()
        inDialog("UPGRADE").performClick()

        upgradeClicks shouldBe 1
        reportedLevels shouldBe emptyList()
        composeRule.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    fun `not upgraded, the fast option opens the upgrade screen and reports no level`() {
        setScreen(isUpgraded = false)

        backgroundSyncRow().performClick()
        inDialog("Fast").performClick()

        upgradeClicks shouldBe 1
        reportedLevels shouldBe emptyList()
        composeRule.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    fun `upgraded, fast carries no chip and is reported`() {
        setScreen(isUpgraded = true)

        backgroundSyncRow().performClick()
        inDialog("UPGRADE").assertDoesNotExist()
        inDialog("Fast").performClick()

        reportedLevels shouldBe listOf(BackgroundSyncLevel.FAST)
        upgradeClicks shouldBe 0
        composeRule.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    fun `picking slow reports slow`() {
        setScreen(isUpgraded = false)

        backgroundSyncRow().performClick()
        inDialog("Slow").performClick()

        reportedLevels shouldBe listOf(BackgroundSyncLevel.SLOW)
        upgradeClicks shouldBe 0
    }
}
