package eu.darken.apl.watch.ui.thumbnail

import coil3.key.Keyer
import coil3.request.Options
import kotlin.math.roundToInt

data class MapPointThumbnailQuery(
    val latitude: Double,
    val longitude: Double,
    /** MapLibre zoom */
    val zoom: Double,
    val widthDp: Int,
    val heightDp: Int,
    val density: Float,
    val darkTheme: Boolean,
    val renderScale: Float,
) {
    /** Logical map size */
    val renderWidth: Int
        get() = (widthDp * renderScale).roundToInt()

    val renderHeight: Int
        get() = (heightDp * renderScale).roundToInt()

    /** Keeps the bitmap at widthDp x heightDp in device pixels */
    val pixelRatio: Float
        get() = density / renderScale

    // Also the disk cache key: bump the version when the rendered image changes for the same query
    val cacheKey: String
        get() = buildString {
            append(KEY_PREFIX)
            append(latitude).append(',').append(longitude)
            append("-z").append(zoom)
            append('-').append(widthDp).append('x').append(heightDp)
            append('@').append(density)
            append(if (darkTheme) "-dark" else "-light")
            append("-s").append(renderScale)
        }

    companion object {
        const val KEY_PREFIX = "map-point-thumb-v1-"
    }
}

class MapPointThumbnailKeyer : Keyer<MapPointThumbnailQuery> {
    override fun key(data: MapPointThumbnailQuery, options: Options): String = data.cacheKey
}
