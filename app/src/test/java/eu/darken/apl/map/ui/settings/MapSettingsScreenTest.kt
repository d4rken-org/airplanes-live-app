package eu.darken.apl.map.ui.settings

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.map.core.NativeMapStyle
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelper.BaseComposeTest

class MapSettingsScreenTest : BaseComposeTest() {

    private var upgradeClicks = 0
    private val reportedStyles = mutableListOf<NativeMapStyle>()

    private fun setScreen(isUpgraded: Boolean) = composeRule.setContent {
        PreviewWrapper {
            MapSettingsScreen(
                state = MapSettingsViewModel.State(
                    nativeMapStyle = NativeMapStyle.AUTO,
                    isRestoreLastViewEnabled = false,
                    isUpgraded = isUpgraded,
                ),
                onBack = {},
                onSetNativeMapStyle = { reportedStyles += it },
                onToggleRestoreLastView = {},
                onUpgrade = { upgradeClicks++ },
            )
        }
    }

    private fun openStyleDialog() {
        composeRule.onNodeWithText("Map style").performClick()
    }

    private fun inDialog(text: String) = composeRule.onNode(hasText(text) and hasAnyAncestor(isDialog()))

    private fun allInDialog(text: String) = composeRule.onAllNodes(hasText(text) and hasAnyAncestor(isDialog()))

    @Test
    fun `not upgraded, a fixed style opens the upgrade screen and reports no style`() {
        setScreen(isUpgraded = false)

        openStyleDialog()
        allInDialog("UPGRADE").assertCountEquals(5)
        inDialog("OpenFreeMap Liberty").performScrollTo().performClick()

        upgradeClicks shouldBe 1
        reportedStyles shouldBe emptyList()
        composeRule.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    fun `not upgraded, an upgrade chip opens the upgrade screen and reports no style`() {
        setScreen(isUpgraded = false)

        openStyleDialog()
        allInDialog("UPGRADE")[0].performScrollTo().performClick()

        upgradeClicks shouldBe 1
        reportedStyles shouldBe emptyList()
        composeRule.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    fun `not upgraded, automatic is reported`() {
        setScreen(isUpgraded = false)

        openStyleDialog()
        inDialog("Automatic (light or dark)").performScrollTo().performClick()

        reportedStyles shouldBe listOf(NativeMapStyle.AUTO)
        upgradeClicks shouldBe 0
    }

    @Test
    fun `upgraded, fixed styles carry no chip and are reported`() {
        setScreen(isUpgraded = true)

        openStyleDialog()
        allInDialog("UPGRADE").assertCountEquals(0)
        inDialog("OpenFreeMap Liberty").performScrollTo().performClick()

        reportedStyles shouldBe listOf(NativeMapStyle.LIBERTY)
        upgradeClicks shouldBe 0
    }

    @Test
    fun `not upgraded, a tap on a fixed style's radio opens the upgrade screen and reports no style`() {
        setScreen(isUpgraded = false)

        openStyleDialog()
        inDialog("OpenFreeMap Liberty").performScrollTo().performTouchInput {
            // The radio sits at the row's start and is 24dp wide
            click(Offset(12.dp.toPx(), centerY))
        }

        upgradeClicks shouldBe 1
        reportedStyles shouldBe emptyList()
        composeRule.onNode(isDialog()).assertDoesNotExist()
    }
}
