package eu.darken.apl.common.theming

import com.materialkolor.Contrast
import eu.darken.apl.R
import eu.darken.apl.common.preferences.EnumPreference
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ThemeStyle(
    override val labelRes: Int,
    val contrastLevel: Double,
) : EnumPreference<ThemeStyle> {
    @SerialName("DEFAULT") DEFAULT(R.string.ui_theme_style_default_label, Contrast.Default.value),
    /** Below API 31 there is no platform scheme, so this still reaches the generator. */
    @SerialName("MATERIAL_YOU") MATERIAL_YOU(R.string.ui_theme_style_materialyou_label, Contrast.Default.value),
    @SerialName("MEDIUM_CONTRAST") MEDIUM_CONTRAST(R.string.ui_theme_style_medium_contrast_label, Contrast.Medium.value),
    @SerialName("HIGH_CONTRAST") HIGH_CONTRAST(R.string.ui_theme_style_high_contrast_label, Contrast.High.value),
    ;
}
