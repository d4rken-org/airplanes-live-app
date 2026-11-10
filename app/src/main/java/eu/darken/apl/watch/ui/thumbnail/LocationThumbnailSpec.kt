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
        circleColor: Int,
    ) = LocationThumbnailQuery(
        latitude = latitude,
        longitude = longitude,
        radiusInMeters = radiusInMeters,
        widthDp = widthDp,
        heightDp = heightDp,
        density = density,
        circleDiameterDp = circleDiameterDp,
        darkTheme = darkTheme,
        circleColor = circleColor,
    )

    companion object {
        const val HEADER_RENDER_ASPECT = 1.5f

        /** Watch list card */
        val LIST = LocationThumbnailSpec(widthDp = 104, heightDp = 208, minAspect = 0.35f, maxAspect = 1.5f)

        /** Watch details sheet header, rendered 3:2 at the header's width */
        fun header(widthDp: Int) = LocationThumbnailSpec(
            widthDp = widthDp.coerceAtLeast(1),
            heightDp = (widthDp / HEADER_RENDER_ASPECT).roundToInt().coerceAtLeast(1),
            minAspect = 0.6f,
            maxAspect = 1.5f,
        )
    }
}
