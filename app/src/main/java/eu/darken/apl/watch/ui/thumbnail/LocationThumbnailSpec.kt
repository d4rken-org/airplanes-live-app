package eu.darken.apl.watch.ui.thumbnail

import kotlin.math.roundToInt

/**
 * Render size of a thumbnail and the range of box aspects (width/height) it may be cropped into.
 */
data class LocationThumbnailSpec(
    val widthDp: Int,
    val heightDp: Int,
    val minAspect: Float,
    val maxAspect: Float,
    /** The map is laid out this many times larger at a pixel ratio this many times smaller: closer zoom, smaller text */
    val renderScale: Float,
    /** Multiplies the area overlay's widths */
    val overlayScale: Float,
) {
    val circleDiameterDp: Float
        get() = LocationThumbnailGeometry.cropSafeDiameter(
            widthDp = widthDp.toFloat(),
            heightDp = heightDp.toFloat(),
            minAspect = minAspect,
            maxAspect = maxAspect,
        )

    fun query(
        latitude: Double,
        longitude: Double,
        radiusInMeters: Float,
        density: Float,
        darkTheme: Boolean,
    ) = LocationThumbnailQuery(
        latitude = latitude,
        longitude = longitude,
        radiusInMeters = radiusInMeters,
        widthDp = widthDp,
        heightDp = heightDp,
        density = density,
        circleDiameterDp = circleDiameterDp,
        darkTheme = darkTheme,
        renderScale = renderScale,
        overlayScale = overlayScale,
    )

    companion object {
        const val HEADER_RENDER_ASPECT = 1.5f

        /** Watch list card */
        val LIST = LocationThumbnailSpec(
            widthDp = 104,
            heightDp = 208,
            minAspect = 0.35f,
            maxAspect = 1.5f,
            renderScale = 2f,
            overlayScale = 1f,
        )

        /**
         * Watch details sheet header, rendered 3:2 at the header's width.
         * Shown much smaller than rendered while the sheet is partially expanded, hence the thicker overlay.
         */
        fun header(widthDp: Int) = LocationThumbnailSpec(
            widthDp = widthDp.coerceAtLeast(1),
            heightDp = (widthDp / HEADER_RENDER_ASPECT).roundToInt().coerceAtLeast(1),
            minAspect = 0.6f,
            maxAspect = 1.5f,
            renderScale = 2f,
            overlayScale = 1.5f,
        )
    }
}
