package eu.darken.apl.map.core

import androidx.annotation.StringRes
import eu.darken.apl.R

enum class MapLayer(
    val key: String,
    @StringRes val labelRes: Int,
) {
    // OpenStreetMap
    OSM("osm", R.string.map_layer_osm),
    OSM_DE("osm_de", R.string.map_layer_osm_de),

    // CARTO
    CARTO_LIGHT("carto_light_all", R.string.map_layer_carto_light),
    CARTO_LIGHT_NOLABELS("carto_light_nolabels", R.string.map_layer_carto_light_nolabels),
    CARTO_DARK("carto_dark_all", R.string.map_layer_carto_dark),
    CARTO_DARK_NOLABELS("carto_dark_nolabels", R.string.map_layer_carto_dark_nolabels),
    CARTO_VOYAGER("carto_rastertiles/voyager", R.string.map_layer_carto_voyager),

    // ESRI
    ESRI("esri", R.string.map_layer_esri_satellite),
    ESRI_GRAY("esri_gray", R.string.map_layer_esri_gray),
    ESRI_STREETS("esri_streets", R.string.map_layer_esri_streets),

    // OpenFreeMap
    OFM_BRIGHT("OpenFreeMapBright", R.string.map_layer_ofm_bright),
    OFM_LIBERTY("OpenFreeMapLiberty", R.string.map_layer_ofm_liberty),
    OFM_POSITRON("OpenFreeMapPositron", R.string.map_layer_ofm_positron),
    OFM_DARK("OpenFreeMapDark", R.string.map_layer_ofm_dark),
    OFM_FIORD("OpenFreeMapFiord", R.string.map_layer_ofm_fiord),

    // Other
    GIBS("gibs", R.string.map_layer_gibs),

    // US Aviation Charts
    VFR_SECTIONAL("VFR_Sectional", R.string.map_layer_vfr_sectional),
    VFR_TERMINAL("VFR_Terminal", R.string.map_layer_vfr_terminal),
    IFR_LOW("IFR_AreaLow", R.string.map_layer_ifr_low),
    IFR_HIGH("IFR_High", R.string.map_layer_ifr_high),
    ;

    companion object {
        fun fromKey(key: String): MapLayer = entries.find { it.key == key } ?: OSM
    }
}
