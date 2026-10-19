package eu.darken.apl.feeder.ui.add

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import eu.darken.apl.common.compose.PreviewWrapper
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelper.BaseComposeTest

class AddFeederScreenTest : BaseComposeTest() {

    private var addClicks = 0

    private fun setScreen(state: AddFeederViewModel.State) = composeRule.setContent {
        PreviewWrapper {
            AddFeederScreen(
                state = state,
                isScanning = false,
                onBack = {},
                onUpdateId = {},
                onUpdateLabel = {},
                onUpdateIpAddress = {},
                onUpdatePosition = {},
                onScanQr = {},
                onStopScan = {},
                onQrDetected = {},
                onDetectLocal = {},
                onAddFeeder = { addClicks++ },
            )
        }
    }

    private fun state(
        isLoading: Boolean = false,
        isDetectingLocal: Boolean = false,
    ) = AddFeederViewModel.State(
        receiverId = "",
        receiverLabel = "",
        receiverIpAddress = "",
        receiverPosition = "",
        isAddButtonEnabled = true,
        isLoading = isLoading,
        isDetectingLocal = isDetectingLocal,
    )

    @Test
    fun `enabled add button triggers callback`() {
        setScreen(state())

        composeRule.onNodeWithText("Add").assertIsEnabled().performClick()

        addClicks shouldBe 1
    }

    @Test
    fun `detecting local feeder disables inputs`() {
        setScreen(state(isDetectingLocal = true))

        composeRule.onNodeWithText("Feeder ID").assertIsNotEnabled()
        composeRule.onNodeWithText("Scan QR Code").assertIsNotEnabled()
    }

    @Test
    fun `loading replaces add button with progress and disables inputs`() {
        setScreen(state(isLoading = true))

        composeRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertExists()
        composeRule.onNodeWithText("Add").assertDoesNotExist()
        composeRule.onNodeWithText("Feeder ID").assertIsNotEnabled()
    }
}
