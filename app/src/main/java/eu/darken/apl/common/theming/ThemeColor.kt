package eu.darken.apl.common.theming

import androidx.compose.ui.graphics.Color
import eu.darken.apl.R
import eu.darken.apl.common.preferences.EnumPreference
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ThemeColor(
    override val labelRes: Int,
    /** The seed this preset generates from, or null when the seed comes from settings. */
    val preset: ThemeSeed?,
) : EnumPreference<ThemeColor> {
    @SerialName("BLUE") BLUE(
        R.string.ui_theme_color_blue_label,
        ThemeSeed(Color(0xFF2D638E), ThemePalette.TONAL_SPOT),
    ),
    @SerialName("AMOLED") AMOLED(
        R.string.ui_theme_color_amoled_label,
        ThemeSeed(Color(0xFF2D638E), ThemePalette.TONAL_SPOT, isAmoled = true),
    ),
    ;
}
