package eu.darken.apl.watch.ui.thumbnail

import kotlin.math.roundToInt

/** Render size of a map snapshot centred on a point. */
data class MapPointThumbnailSpec(
    val widthDp: Int,
    val heightDp: Int,
    /** The map is laid out this many times larger at a pixel ratio this many times smaller: closer zoom, smaller text */
    val renderScale: Float,
) {
    fun query(
        latitude: Double,
        longitude: Double,
        zoom: Double,
        density: Float,
        darkTheme: Boolean,
    ) = MapPointThumbnailQuery(
        latitude = latitude,
        longitude = longitude,
        zoom = zoom,
        widthDp = widthDp,
        heightDp = heightDp,
        density = density,
        darkTheme = darkTheme,
        renderScale = renderScale,
    )

    companion object {
        /** Details sheet header, rendered 3:2 at the header's width */
        fun header(widthDp: Int) = MapPointThumbnailSpec(
            widthDp = widthDp.coerceAtLeast(1),
            heightDp = (widthDp / LocationThumbnailSpec.HEADER_RENDER_ASPECT).roundToInt().coerceAtLeast(1),
            renderScale = 2f,
        )
    }
}
