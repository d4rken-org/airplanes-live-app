package eu.darken.apl.watch.ui.thumbnail

import coil3.key.Keyer
import coil3.request.Options

data class LocationThumbnailQuery(
    val latitude: Double,
    val longitude: Double,
    val radiusInMeters: Float,
    val widthDp: Int,
    val heightDp: Int,
    val density: Float,
    val circleDiameterDp: Float,
    val darkTheme: Boolean,
    /** ARGB */
    val circleColor: Int,
) {
    // Also the disk cache key: bump the version when the rendered image changes for the same query
    val cacheKey: String
        get() = buildString {
            append(KEY_PREFIX)
            append(latitude).append(',').append(longitude)
            append("-r").append(radiusInMeters)
            append('-').append(widthDp).append('x').append(heightDp)
            append('@').append(density)
            append("-d").append(circleDiameterDp)
            append(if (darkTheme) "-dark" else "-light")
            append("-c").append(Integer.toHexString(circleColor))
        }

    companion object {
        const val KEY_PREFIX = "location-thumb-v1-"
    }
}

class LocationThumbnailKeyer : Keyer<LocationThumbnailQuery> {
    override fun key(data: LocationThumbnailQuery, options: Options): String = data.cacheKey
}
