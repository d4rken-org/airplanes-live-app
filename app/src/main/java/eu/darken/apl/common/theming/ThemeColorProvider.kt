package eu.darken.apl.common.theming

import androidx.compose.material3.ColorScheme
import com.materialkolor.dynamicColorScheme

object ThemeColorProvider {

    fun getColorScheme(seed: ThemeSeed, style: ThemeStyle, dark: Boolean): ColorScheme =
        dynamicColorScheme(
            seedColor = seed.seed,
            isDark = dark,
            isAmoled = seed.isAmoled,
            style = seed.palette.style,
            contrastLevel = style.contrastLevel,
        )
}
