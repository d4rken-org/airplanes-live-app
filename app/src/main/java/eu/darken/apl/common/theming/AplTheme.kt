package eu.darken.apl.common.theming

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2D638E),
    onPrimary = Color(0xFFF6F9FF),
    primaryContainer = Color(0xFF91C3F4),
    onPrimaryContainer = Color(0xFF003E63),
    secondary = Color(0xFF4F6173),
    onSecondary = Color(0xFFF6F9FF),
    secondaryContainer = Color(0xFFD2E4FA),
    onSecondaryContainer = Color(0xFF425365),
    tertiary = Color(0xFF89521A),
    onTertiary = Color(0xFFFFF7F4),
    tertiaryContainer = Color(0xFFFDB473),
    onTertiaryContainer = Color(0xFF603300),
    error = Color(0xFFBB1B1B),
    onError = Color(0xFFFFF7F6),
    errorContainer = Color(0xFFFE4E44),
    onErrorContainer = Color(0xFF570003),
    background = Color(0xFFF9F9FD),
    onBackground = Color(0xFF303336),
    surface = Color(0xFFF9F9FD),
    onSurface = Color(0xFF303336),
    surfaceVariant = Color(0xFFE1E2E6),
    onSurfaceVariant = Color(0xFF5D5F63),
    outline = Color(0xFF797B7E),
    outlineVariant = Color(0xFFB1B2B6),
    inverseSurface = Color(0xFF0C0E11),
    inverseOnSurface = Color(0xFF9B9DA1),
    inversePrimary = Color(0xFF91C3F4),
    surfaceDim = Color(0xFFD9DADE),
    surfaceBright = Color(0xFFF9F9FD),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F3F7),
    surfaceContainer = Color(0xFFEDEEF2),
    surfaceContainerHigh = Color(0xFFE7E8EC),
    surfaceContainerHighest = Color(0xFFE1E2E6),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF91C3F4),
    onPrimary = Color(0xFF003E63),
    primaryContainer = Color(0xFF73A5D4),
    onPrimaryContainer = Color(0xFF00253E),
    secondary = Color(0xFFB6C8DE),
    onSecondary = Color(0xFF304253),
    secondaryContainer = Color(0xFF2B3D4E),
    onSecondaryContainer = Color(0xFFAFC1D6),
    tertiary = Color(0xFFFFC697),
    onTertiary = Color(0xFF6C3B02),
    tertiaryContainer = Color(0xFFFDB473),
    onTertiaryContainer = Color(0xFF603300),
    error = Color(0xFFFF7164),
    onError = Color(0xFF4A0002),
    errorContainer = Color(0xFFAC0C12),
    onErrorContainer = Color(0xFFFFB8B0),
    background = Color(0xFF0C0E11),
    onBackground = Color(0xFFE4E5E9),
    surface = Color(0xFF0C0E11),
    onSurface = Color(0xFFE4E5E9),
    surfaceVariant = Color(0xFF242629),
    onSurfaceVariant = Color(0xFF929397),
    outline = Color(0xFF747579),
    outlineVariant = Color(0xFF46484B),
    inverseSurface = Color(0xFFF9F9FD),
    inverseOnSurface = Color(0xFF535559),
    inversePrimary = Color(0xFF2D638E),
    surfaceDim = Color(0xFF0C0E11),
    surfaceBright = Color(0xFF2A2C2F),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF111416),
    surfaceContainer = Color(0xFF171A1D),
    surfaceContainerHigh = Color(0xFF1D2023),
    surfaceContainerHighest = Color(0xFF242629),
)

@Composable
fun AplTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

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
