package eu.darken.apl.watch.ui.details

import android.content.Context
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import eu.darken.apl.R
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.compose.SnackbarPresenter
import eu.darken.apl.common.flow.SingleEventFlow
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelper.BaseComposeTest

class WatchDetailsEventEffectsTest : BaseComposeTest() {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val events = SingleEventFlow<WatchDetailsEvents>()
    private val hostState = SnackbarHostState()
    private var removals = 0
    private var upgrades = 0

    private val noAccessText get() = context.getString(R.string.map_history_no_access)
    private val upgradeText get() = context.getString(R.string.common_upgrade_action)

    private fun setEffects() = composeRule.setContent {
        PreviewWrapper {
            val scope = rememberCoroutineScope()
            val snackbars = remember { SnackbarPresenter(hostState, scope) }
            WatchDetailsEventEffects(
                events = events,
                snackbars = snackbars,
                onRemovalConfirmation = { removals++ },
                onUpgrade = { upgrades++ },
            )
            SnackbarHost(hostState)
        }
    }

    private fun emit(event: WatchDetailsEvents) {
        events.tryEmit(event)
        composeRule.waitForIdle()
    }

    @Test
    fun `the upsell says the history is part of Pro and offers the upgrade`() {
        setEffects()

        emit(WatchDetailsEvents.HistoryUpsell)

        composeRule.onNodeWithText(noAccessText).assertExists()
        composeRule.onNodeWithText(upgradeText).assertExists()
        upgrades shouldBe 0
    }

    @Test
    fun `tapping Upgrade on the upsell opens the upgrade`() {
        setEffects()
        emit(WatchDetailsEvents.HistoryUpsell)

        composeRule.onNodeWithText(upgradeText).performClick()
        composeRule.waitForIdle()

        upgrades shouldBe 1
    }

    @Test
    fun `a dismissed upsell does not open the upgrade`() {
        setEffects()
        emit(WatchDetailsEvents.HistoryUpsell)
        composeRule.onNodeWithText(noAccessText).assertExists()

        composeRule.runOnIdle { hostState.currentSnackbarData?.dismiss() }
        composeRule.waitForIdle()

        upgrades shouldBe 0
    }

    @Test
    fun `a removal confirmation asks for it without a snackbar`() {
        setEffects()

        emit(WatchDetailsEvents.RemovalConfirmation("w1"))

        removals shouldBe 1
        composeRule.onNodeWithText(noAccessText).assertDoesNotExist()
    }
}
