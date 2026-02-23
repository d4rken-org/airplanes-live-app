package eu.darken.apl.map.core

import androidx.annotation.StringRes
import eu.darken.apl.R

enum class MapOverlay(
    val key: String,
    @StringRes val labelRes: Int,
    val category: Category,
) {
    // Worldwide
    OPENAIP("openaip", R.string.map_overlay_openaip, Category.WORLD),
    RAINVIEWER("rainviewer_radar", R.string.map_overlay_rainviewer, Category.WORLD),

    // US
    TFRS("tfrs", R.string.map_overlay_tfrs, Category.US),
    SUA("sua", R.string.map_overlay_sua, Category.US),
    NEXRAD("nexrad", R.string.map_overlay_nexrad, Category.US),
    NOAA_SAT("noaa_sat", R.string.map_overlay_noaa_sat, Category.US),
    NOAA_RADAR("noaa_radar", R.string.map_overlay_noaa_radar, Category.US),
    US_A2A_REFUELING("usa2arefueling", R.string.map_overlay_us_a2a_refueling, Category.US),
    US_ARTCC("usartccboundaries", R.string.map_overlay_us_artcc, Category.US),

    // Europe
    PL_AWACS("plawacsorbits", R.string.map_overlay_pl_awacs, Category.EUROPE),
    NL_AWACS("nlawacsorbits", R.string.map_overlay_nl_awacs, Category.EUROPE),
    DE_AWACS("deawacsorbits", R.string.map_overlay_de_awacs, Category.EUROPE),
    UK_RADAR_CORRIDORS("ukradarcorridors", R.string.map_overlay_uk_radar_corridors, Category.EUROPE),
    UK_A2A_REFUELING("uka2arefueling", R.string.map_overlay_uk_a2a_refueling, Category.EUROPE),
    UK_AWACS("ukawacsorbits", R.string.map_overlay_uk_awacs, Category.EUROPE),
    ;

    enum class Category(@param:StringRes val labelRes: Int) {
        WORLD(R.string.map_overlay_category_world),
        US(R.string.map_overlay_category_us),
        EUROPE(R.string.map_overlay_category_europe),
    }

    companion object {
        fun fromKey(key: String): MapOverlay? = entries.find { it.key == key }
    }
}
