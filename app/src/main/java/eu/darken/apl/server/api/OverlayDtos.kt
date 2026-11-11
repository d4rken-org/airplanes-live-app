package eu.darken.apl.server.api

import kotlinx.serialization.Serializable

/** [overlays] holds only the overlays the server has switched on, in a fixed order. */
@Serializable
data class OverlayCatalogResponse(
    val generatedAt: Long,
    val overlays: List<OverlayDto>,
)

/**
 * [kind], [category], [status] and [access] stay plain strings, so a value this build does not know
 * decodes instead of failing the whole catalog.
 *
 * [tiles] is a path-absolute template such as `/api/v1/overlays/radar/<frame>/{z}/{x}/{y}.png`, resolved
 * against the API base URL. It and [frame] are only set while the overlay is available and allowed.
 * [bounds] is `[west, south, east, north]` in degrees and absent while unavailable.
 * [sourceLayer] and [style] are only set for [KIND_VECTOR].
 */
@Serializable
data class OverlayDto(
    val id: String,
    val kind: String,
    val category: String,
    val coverage: List<String>,
    val title: String,
    val forecast: Boolean,
    val attribution: List<OverlayAttributionDto>,
    val status: String,
    val access: String,
    val tiles: String? = null,
    val minZoom: Int,
    val maxZoom: Int,
    val tileSize: Int,
    val bounds: List<Double>? = null,
    val frame: OverlayFrameDto? = null,
    val refreshSeconds: Int,
    val sourceLayer: String? = null,
    val style: OverlayVectorStyleDto? = null,
) {

    val isRaster: Boolean
        get() = kind == KIND_RASTER

    val isVector: Boolean
        get() = kind == KIND_VECTOR

    val isAvailable: Boolean
        get() = status == STATUS_AVAILABLE

    val isAllowed: Boolean
        get() = access == ACCESS_ALLOWED

    val isUpgradeRequired: Boolean
        get() = access == ACCESS_UPGRADE_REQUIRED

    companion object {
        const val ID_RADAR = "radar"
        const val ID_AIRSPACE = "airspace"
        const val ID_RESTRICTIONS = "restrictions"
        const val ID_MILITARY = "military"

        const val KIND_RASTER = "raster"
        const val KIND_VECTOR = "vector"

        const val CATEGORY_WEATHER = "weather"
        const val CATEGORY_AIRSPACE = "airspace"

        const val STATUS_AVAILABLE = "available"
        const val STATUS_UNAVAILABLE = "unavailable"

        const val ACCESS_ALLOWED = "allowed"
        const val ACCESS_UPGRADE_REQUIRED = "upgrade_required"
    }
}

@Serializable
data class OverlayAttributionDto(
    val text: String,
    val url: String,
)

/** [time] is the oldest [parts] time. */
@Serializable
data class OverlayFrameDto(
    val id: String,
    val time: Long,
    val parts: List<OverlayFramePartDto>,
)

@Serializable
data class OverlayFramePartDto(
    val id: String,
    val coverage: List<String>,
    val time: Long,
)

/**
 * Colours are `#rrggbb`. [labelProperty] names the feature property holding the label text, without it
 * there are no labels. [colorProperty] names a feature property whose `#rrggbb` value replaces both colours.
 */
@Serializable
data class OverlayVectorStyleDto(
    val lineColor: String,
    val lineWidth: Double,
    val fillColor: String,
    val fillOpacity: Double,
    val labelProperty: String? = null,
    val colorProperty: String? = null,
)
