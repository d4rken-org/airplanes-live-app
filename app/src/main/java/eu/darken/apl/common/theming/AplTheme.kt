package eu.darken.apl.common.theming

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import eu.darken.apl.main.core.ThemeState

@Composable
fun AplTheme(
    state: ThemeState = ThemeState(),
    content: @Composable () -> Unit,
) {
    val darkTheme = when (state.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val context = LocalContext.current
    val dynamicColorsAvailable = state.style == ThemeStyle.MATERIAL_YOU && Build.VERSION.SDK_INT >= 31

    val colorScheme = remember(state, darkTheme, dynamicColorsAvailable) {
        when {
            dynamicColorsAvailable -> {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            darkTheme -> ThemeColorProvider.getDarkColorScheme(state.color, state.style)
            else -> ThemeColorProvider.getLightColorScheme(state.color, state.style)
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
