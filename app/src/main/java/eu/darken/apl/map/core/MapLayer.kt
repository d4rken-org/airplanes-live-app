package eu.darken.apl.map.core

import androidx.annotation.StringRes
import eu.darken.apl.R

enum class MapLayer(
    val key: String,
    @param:StringRes val labelRes: Int,
) {
    OSM("osm", R.string.map_layer_osm),
    CARTO_LIGHT("carto_light_all", R.string.map_layer_carto_light),
    CARTO_DARK("carto_dark_all", R.string.map_layer_carto_dark),
    ESRI("esri", R.string.map_layer_esri_satellite),
    ESRI_STREETS("esri_streets", R.string.map_layer_esri_streets),
    ;

    companion object {
        fun fromKey(key: String): MapLayer = entries.find { it.key == key } ?: OSM
    }
}
