package eu.darken.apl.common.theming

import androidx.compose.material3.ColorScheme

object ThemeColorProvider {

    fun getLightColorScheme(color: ThemeColor, style: ThemeStyle): ColorScheme = when (color) {
        ThemeColor.BLUE, ThemeColor.AMOLED -> when (style) {
            ThemeStyle.MEDIUM_CONTRAST -> AplColorsBlue.lightMediumContrast
            ThemeStyle.HIGH_CONTRAST -> AplColorsBlue.lightHighContrast
            else -> AplColorsBlue.lightDefault
        }
    }

    fun getDarkColorScheme(color: ThemeColor, style: ThemeStyle): ColorScheme = when (color) {
        ThemeColor.BLUE -> when (style) {
            ThemeStyle.MEDIUM_CONTRAST -> AplColorsBlue.darkMediumContrast
            ThemeStyle.HIGH_CONTRAST -> AplColorsBlue.darkHighContrast
            else -> AplColorsBlue.darkDefault
        }

        ThemeColor.AMOLED -> when (style) {
            ThemeStyle.MEDIUM_CONTRAST -> AplColorsAmoled.darkMediumContrast
            ThemeStyle.HIGH_CONTRAST -> AplColorsAmoled.darkHighContrast
            else -> AplColorsAmoled.darkDefault
        }
    }
}
