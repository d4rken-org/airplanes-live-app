package eu.darken.apl.common.compose

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Which of one host's messages wins. An action message, e.g. "Added" with Undo, replaces whatever is shown or waiting.
 * A copy confirmation replaces an earlier one instead of queueing behind it, and gives way to a shown action message.
 */
class SnackbarPresenter(
    private val hostState: SnackbarHostState,
    private val scope: CoroutineScope,
) {
    private var action: Job? = null
    private var copied: Job? = null

    fun showAction(block: suspend SnackbarHostState.() -> Unit) {
        replaceShown()
        action = scope.launch { hostState.block() }
    }

    fun showCopied(message: String) {
        if (action?.isActive == true) return
        replaceShown()
        copied = scope.launch { hostState.showSnackbar(message, duration = SnackbarDuration.Short) }
    }

    private fun replaceShown() {
        action?.cancel()
        copied?.cancel()
        hostState.currentSnackbarData?.dismiss()
    }
}
