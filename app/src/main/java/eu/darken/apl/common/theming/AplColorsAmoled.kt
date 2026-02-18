package eu.darken.apl.common.theming

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

object AplColorsAmoled {

    // Light mode: same as Blue (AMOLED is a dark-mode feature)
    val lightDefault = AplColorsBlue.lightDefault
    val lightMediumContrast = AplColorsBlue.lightMediumContrast
    val lightHighContrast = AplColorsBlue.lightHighContrast

    // region Dark Default — pure black backgrounds
    val darkDefault = darkColorScheme(
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
        background = Color(0xFF000000),
        onBackground = Color(0xFFE4E5E9),
        surface = Color(0xFF000000),
        onSurface = Color(0xFFE4E5E9),
        surfaceVariant = Color(0xFF1A1C1F),
        onSurfaceVariant = Color(0xFF929397),
        outline = Color(0xFF747579),
        outlineVariant = Color(0xFF46484B),
        inverseSurface = Color(0xFFF9F9FD),
        inverseOnSurface = Color(0xFF535559),
        inversePrimary = Color(0xFF2D638E),
        surfaceDim = Color(0xFF000000),
        surfaceBright = Color(0xFF1E2023),
        surfaceContainerLowest = Color(0xFF000000),
        surfaceContainerLow = Color(0xFF0A0C0E),
        surfaceContainer = Color(0xFF0F1114),
        surfaceContainerHigh = Color(0xFF151719),
        surfaceContainerHighest = Color(0xFF1A1C1F),
    )
    // endregion

    // region Dark Medium Contrast — pure black backgrounds
    val darkMediumContrast = darkColorScheme(
        primary = Color(0xFF91C3F4),
        onPrimary = Color(0xFF003353),
        primaryContainer = Color(0xFF73A5D4),
        onPrimaryContainer = Color(0xFF001627),
        secondary = Color(0xFFB6C8DE),
        onSecondary = Color(0xFF263849),
        secondaryContainer = Color(0xFF64768A),
        onSecondaryContainer = Color(0xFFFFFFFF),
        tertiary = Color(0xFFFFC697),
        onTertiary = Color(0xFF5F3200),
        tertiaryContainer = Color(0xFFFDB473),
        onTertiaryContainer = Color(0xFF512A00),
        error = Color(0xFFFF9F94),
        onError = Color(0xFF600004),
        errorContainer = Color(0xFFDA342E),
        onErrorContainer = Color(0xFFFFFFFF),
        background = Color(0xFF000000),
        onBackground = Color(0xFFFFFFFF),
        surface = Color(0xFF000000),
        onSurface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFF1A1C1F),
        onSurfaceVariant = Color(0xFFB7B9BC),
        outline = Color(0xFF929397),
        outlineVariant = Color(0xFF747579),
        inverseSurface = Color(0xFFF9F9FD),
        inverseOnSurface = Color(0xFF36383C),
        inversePrimary = Color(0xFF205984),
        surfaceDim = Color(0xFF000000),
        surfaceBright = Color(0xFF1E2023),
        surfaceContainerLowest = Color(0xFF000000),
        surfaceContainerLow = Color(0xFF0A0C0E),
        surfaceContainer = Color(0xFF0F1114),
        surfaceContainerHigh = Color(0xFF151719),
        surfaceContainerHighest = Color(0xFF1A1C1F),
    )
    // endregion

    // region Dark High Contrast — pure black backgrounds
    val darkHighContrast = darkColorScheme(
        primary = Color(0xFFD3E8FF),
        onPrimary = Color(0xFF002E4C),
        primaryContainer = Color(0xFF73A5D4),
        onPrimaryContainer = Color(0xFF000000),
        secondary = Color(0xFFD5E7FD),
        onSecondary = Color(0xFF1C2E3E),
        secondaryContainer = Color(0xFF8DA0B4),
        onSecondaryContainer = Color(0xFF000000),
        tertiary = Color(0xFFFFE0C8),
        onTertiary = Color(0xFF452300),
        tertiaryContainer = Color(0xFFFDB473),
        onTertiaryContainer = Color(0xFF190900),
        error = Color(0xFFFFDEDA),
        onError = Color(0xFF600004),
        errorContainer = Color(0xFFFF7164),
        onErrorContainer = Color(0xFF000000),
        background = Color(0xFF000000),
        onBackground = Color(0xFFFFFFFF),
        surface = Color(0xFF000000),
        onSurface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFF1A1C1F),
        onSurfaceVariant = Color(0xFFE4E5E9),
        outline = Color(0xFFB7B9BC),
        outlineVariant = Color(0xFF929397),
        inverseSurface = Color(0xFFF9F9FD),
        inverseOnSurface = Color(0xFF000000),
        inversePrimary = Color(0xFF003B5F),
        surfaceDim = Color(0xFF000000),
        surfaceBright = Color(0xFF1E2023),
        surfaceContainerLowest = Color(0xFF000000),
        surfaceContainerLow = Color(0xFF0A0C0E),
        surfaceContainer = Color(0xFF0F1114),
        surfaceContainerHigh = Color(0xFF151719),
        surfaceContainerHighest = Color(0xFF1A1C1F),
    )
    // endregion
}
