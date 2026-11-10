package eu.darken.apl.watch.ui.thumbnail

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.pow

data class LocationThumbnailCamera(
    val latitude: Double,
    val longitude: Double,
    /** MapLibre zoom */
    val zoom: Double,
)

object LocationThumbnailGeometry {
    const val CROP_MARGIN = 0.9f
    const val MAX_LATITUDE = 85.05
    const val METERS_PER_DEGREE_LATITUDE = 111_320.0
    const val MIN_ZOOM = 0.0
    const val MAX_ZOOM = 20.0
    private const val EARTH_CIRCUMFERENCE_METERS = 40_075_016.686
    private const val TILE_SIZE = 512.0

    /**
     * Largest circle that stays whole when the [widthDp] x [heightDp] image is shown with `ContentScale.Crop`
     * in any box whose width/height lies within [minAspect]..[maxAspect].
     * Crop keeps the largest centered region with the box's aspect: H * aspect wide when the box is narrower
     * than the image, W / aspect tall when it is wider.
     */
    fun cropSafeDiameter(widthDp: Float, heightDp: Float, minAspect: Float, maxAspect: Float): Float =
        CROP_MARGIN * minOf(widthDp, heightDp, heightDp * minAspect, widthDp / maxAspect)

    fun metersPerPixel(zoom: Double, latitude: Double): Double =
        EARTH_CIRCUMFERENCE_METERS * cos(Math.toRadians(latitude)) / (TILE_SIZE * 2.0.pow(zoom))

    fun latitudeSpan(radiusInMeters: Float): Double = radiusInMeters / METERS_PER_DEGREE_LATITUDE

    /** Web Mercator ends at about 85.05°; an area reaching past it can't be drawn as a circle. */
    fun isRenderable(query: LocationThumbnailQuery): Boolean =
        query.radiusInMeters > 0f && abs(query.latitude) + latitudeSpan(query.radiusInMeters) <= MAX_LATITUDE

    fun camera(query: LocationThumbnailQuery): LocationThumbnailCamera {
        val radiusPx = query.circleDiameterDp / 2.0
        val zoom = log2(
            EARTH_CIRCUMFERENCE_METERS * cos(Math.toRadians(query.latitude)) * radiusPx /
                (TILE_SIZE * query.radiusInMeters)
        )
        return LocationThumbnailCamera(
            latitude = query.latitude,
            longitude = query.longitude,
            zoom = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM),
        )
    }
}
