package eu.darken.apl.common.theming

import com.materialkolor.PaletteStyle
import eu.darken.apl.R
import eu.darken.apl.common.preferences.EnumPreference
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ThemePalette(
    override val labelRes: Int,
    val style: PaletteStyle,
) : EnumPreference<ThemePalette> {
    @SerialName("TONAL_SPOT") TONAL_SPOT(R.string.ui_theme_palette_tonalspot_label, PaletteStyle.TonalSpot),
    @SerialName("NEUTRAL") NEUTRAL(R.string.ui_theme_palette_neutral_label, PaletteStyle.Neutral),
    @SerialName("VIBRANT") VIBRANT(R.string.ui_theme_palette_vibrant_label, PaletteStyle.Vibrant),
    @SerialName("EXPRESSIVE") EXPRESSIVE(R.string.ui_theme_palette_expressive_label, PaletteStyle.Expressive),
    @SerialName("RAINBOW") RAINBOW(R.string.ui_theme_palette_rainbow_label, PaletteStyle.Rainbow),
    @SerialName("FRUIT_SALAD") FRUIT_SALAD(R.string.ui_theme_palette_fruitsalad_label, PaletteStyle.FruitSalad),
    @SerialName("MONOCHROME") MONOCHROME(R.string.ui_theme_palette_monochrome_label, PaletteStyle.Monochrome),
    @SerialName("FIDELITY") FIDELITY(R.string.ui_theme_palette_fidelity_label, PaletteStyle.Fidelity),
    @SerialName("CONTENT") CONTENT(R.string.ui_theme_palette_content_label, PaletteStyle.Content),
    ;
}
