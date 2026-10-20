package eu.darken.apl.main.core

import androidx.compose.ui.graphics.Color
import eu.darken.apl.common.theming.ThemeColor
import eu.darken.apl.common.theming.ThemeMode
import eu.darken.apl.common.theming.ThemePalette
import eu.darken.apl.common.theming.ThemeSeed
import eu.darken.apl.common.theming.ThemeStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class ThemeState(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val style: ThemeStyle = ThemeStyle.DEFAULT,
    val color: ThemeColor = ThemeColor.BLUE,
    /** An RGB seed for the custom color. Null falls back to [DEFAULT_CUSTOM_SEED]. */
    val customSeed: Int? = null,
    val customPalette: ThemePalette = ThemePalette.TONAL_SPOT,
) {

    /** Resolved regardless of [color], so a picker can show the custom swatch under any preset. */
    val customThemeSeed: ThemeSeed
        get() = ThemeSeed(
            seed = Color((customSeed ?: DEFAULT_CUSTOM_SEED) or 0xFF000000.toInt()),
            palette = customPalette,
        )

    val themeSeed: ThemeSeed
        get() = color.preset ?: customThemeSeed

    companion object {
        /** RGB without alpha, matching how the custom seed is stored. */
        const val DEFAULT_CUSTOM_SEED: Int = 0x2D638E
    }
}

val GeneralSettings.themeState: Flow<ThemeState>
    get() = combine(
        themeMode.flow,
        themeStyle.flow,
        themeColor.flow,
        themeCustomSeed.flow,
        themeCustomPalette.flow,
    ) { mode, style, color, customSeed, customPalette ->
        ThemeState(mode, style, color, customSeed, customPalette)
    }
