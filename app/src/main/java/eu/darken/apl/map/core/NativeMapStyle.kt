package eu.darken.apl.map.core

import androidx.annotation.StringRes
import eu.darken.apl.R

/** OpenFreeMap vector styles; [AUTO] picks the light or dark one to match the app theme. */
enum class NativeMapStyle(
    val key: String,
    @StringRes val labelRes: Int,
) {
    AUTO("auto", R.string.map_style_auto),
    POSITRON("positron", R.string.map_layer_ofm_positron),
    DARK("dark", R.string.map_layer_ofm_dark),
    LIBERTY("liberty", R.string.map_layer_ofm_liberty),
    BRIGHT("bright", R.string.map_layer_ofm_bright),
    FIORD("fiord", R.string.map_layer_ofm_fiord),
    ;

    fun styleUrl(darkTheme: Boolean): String {
        val name = when (this) {
            AUTO -> if (darkTheme) DARK.key else POSITRON.key
            else -> key
        }
        return "https://tiles.openfreemap.org/styles/$name"
    }

    companion object {
        fun fromKey(key: String?): NativeMapStyle = entries.firstOrNull { it.key == key } ?: AUTO
    }
}
