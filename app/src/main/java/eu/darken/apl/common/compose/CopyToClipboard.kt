package eu.darken.apl.common.compose

import android.content.ClipData
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import eu.darken.apl.R
import kotlinx.coroutines.launch

/** Copies text to the clipboard. From Android 13 on the system confirms it, below that [snackbars] do. */
@Composable
fun rememberCopyToClipboard(snackbars: SnackbarPresenter): (String) -> Unit {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(clipboard, context, scope, snackbars) {
        { text ->
            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(text, text))) }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                snackbars.showCopied(context.getString(R.string.common_copied_msg))
            }
        }
    }
}
