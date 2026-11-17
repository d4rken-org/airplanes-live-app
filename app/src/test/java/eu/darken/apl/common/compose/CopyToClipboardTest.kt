package eu.darken.apl.common.compose

import android.content.Context
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.test.core.app.ApplicationProvider
import eu.darken.apl.R
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.robolectric.annotation.Config
import testhelper.BaseComposeTest

class CopyToClipboardTest : BaseComposeTest() {

    // Not shown by any SnackbarHost, so a message stays until the test dismisses it
    private val hostState = SnackbarHostState()
    private lateinit var snackbars: SnackbarPresenter
    private lateinit var copyToClipboard: (String) -> Unit
    private lateinit var clipboard: Clipboard

    private fun setUp() {
        composeRule.setContent {
            val scope = rememberCoroutineScope()
            snackbars = remember { SnackbarPresenter(hostState, scope) }
            copyToClipboard = rememberCopyToClipboard(snackbars)
            clipboard = LocalClipboard.current
        }
    }

    private fun copy(text: String) {
        composeRule.runOnIdle { copyToClipboard(text) }
        composeRule.waitForIdle()
    }

    private fun showAction(message: String) {
        composeRule.runOnIdle {
            snackbars.showAction {
                showSnackbar(message, actionLabel = "Undo", duration = SnackbarDuration.Indefinite)
            }
        }
        composeRule.waitForIdle()
    }

    private fun dismissShown() {
        composeRule.runOnIdle { hostState.currentSnackbarData?.dismiss() }
        composeRule.waitForIdle()
    }

    private val copiedMessage get() = ApplicationProvider.getApplicationContext<Context>().getString(R.string.common_copied_msg)

    private val shown: String? get() = composeRule.runOnIdle { hostState.currentSnackbarData?.visuals?.message }

    private val clipText: String?
        get() = composeRule.runOnIdle {
            runBlocking { clipboard.getClipEntry() }?.clipData?.getItemAt(0)?.text?.toString()
        }

    @Test
    @Config(sdk = [33])
    fun `from Android 13 on the text is copied without a confirmation`() {
        setUp()

        copy("4CA2B1")

        clipText shouldBe "4CA2B1"
        shown shouldBe null
    }

    @Test
    @Config(sdk = [29])
    fun `below Android 13 the text is copied and confirmed`() {
        setUp()

        copy("4CA2B1")

        clipText shouldBe "4CA2B1"
        shown shouldBe copiedMessage
    }

    @Test
    @Config(sdk = [29])
    fun `a second copy replaces the confirmation instead of queueing`() {
        setUp()

        copy("EI-ABC")
        copy("4CA2B1")

        clipText shouldBe "4CA2B1"
        shown shouldBe copiedMessage
        dismissShown()
        shown shouldBe null
    }

    @Test
    @Config(sdk = [29])
    fun `an action message replaces the copy confirmation`() {
        setUp()

        copy("4CA2B1")
        showAction("RYR1234 added")

        shown shouldBe "RYR1234 added"
        dismissShown()
        shown shouldBe null
    }

    @Test
    @Config(sdk = [29])
    fun `a copy keeps a shown action message and still copies`() {
        setUp()

        showAction("RYR1234 added")
        copy("4CA2B1")

        clipText shouldBe "4CA2B1"
        shown shouldBe "RYR1234 added"
        dismissShown()
        shown shouldBe null
    }
}
