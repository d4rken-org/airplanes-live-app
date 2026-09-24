package eu.darken.apl.map.ui

import android.app.Activity
import android.os.Build
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/** Hides the system bars while [isFullscreen] and brings them back when the map leaves the screen. */
@Composable
internal fun MapFullscreenEffect(isFullscreen: Boolean) {
    val activity = LocalContext.current as? Activity
    val currentFullscreen = rememberUpdatedState(isFullscreen)

    LaunchedEffect(isFullscreen) {
        activity?.window?.let { window ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                @Suppress("DEPRECATION")
                window.setDecorFitsSystemWindows(!isFullscreen)
                window.insetsController?.let {
                    if (isFullscreen) {
                        it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                        it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    } else {
                        it.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (!currentFullscreen.value) return@onDispose
            activity?.window?.let { window ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    @Suppress("DEPRECATION")
                    window.setDecorFitsSystemWindows(true)
                    window.insetsController?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                }
            }
        }
    }
}
