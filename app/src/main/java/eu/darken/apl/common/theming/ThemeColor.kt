package eu.darken.apl.common.theming

import eu.darken.apl.R
import eu.darken.apl.common.preferences.EnumPreference
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ThemeColor(override val labelRes: Int) : EnumPreference<ThemeColor> {
    @SerialName("BLUE") BLUE(R.string.ui_theme_color_blue_label),
    @SerialName("AMOLED") AMOLED(R.string.ui_theme_color_amoled_label),
    ;
}
