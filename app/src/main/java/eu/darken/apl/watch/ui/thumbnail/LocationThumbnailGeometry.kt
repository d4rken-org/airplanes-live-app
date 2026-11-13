package eu.darken.apl.watch.ui.thumbnail

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.tan

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
    private const val TILE_SIZE = 512.0

    /**
     * Largest circle that stays whole when the [widthDp] x [heightDp] image is shown with `ContentScale.Crop`
     * in any box whose width/height lies within [minAspect]..[maxAspect].
     * Crop keeps the largest centered region with the box's aspect: H * aspect wide when the box is narrower
     * than the image, W / aspect tall when it is wider.
     */
    fun cropSafeDiameter(widthDp: Float, heightDp: Float, minAspect: Float, maxAspect: Float): Float =
        CROP_MARGIN * minOf(widthDp, heightDp, heightDp * minAspect, widthDp / maxAspect)

    fun latitudeSpan(radiusInMeters: Float): Double = radiusInMeters / METERS_PER_DEGREE_LATITUDE

    /** Web Mercator ends at about 85.05°; an area reaching past it can't be drawn as a circle. */
    fun isRenderable(query: LocationThumbnailQuery): Boolean =
        query.radiusInMeters > 0f && abs(query.latitude) + latitudeSpan(query.radiusInMeters) <= MAX_LATITUDE

    fun isRenderable(query: MapPointThumbnailQuery): Boolean =
        abs(query.latitude) <= MAX_LATITUDE && query.widthDp > 0 && query.heightDp > 0

    /** Web Mercator ordinate in world units: 0 at the equator, 1 is one world width */
    private fun mercatorY(latitude: Double): Double = ln(tan(PI / 4 + Math.toRadians(latitude) / 2)) / (2 * PI)

    fun camera(query: LocationThumbnailQuery): LocationThumbnailCamera {
        val radiusPx = query.circleDiameterDp / 2.0 * query.renderScale
        // Scale by the projected offset to the north point: Web Mercator stretches it beyond the center's scale
        val north = query.latitude + latitudeSpan(query.radiusInMeters)
        val delta = abs(mercatorY(north) - mercatorY(query.latitude))
        val zoom = log2(radiusPx / (TILE_SIZE * delta))
        return LocationThumbnailCamera(
            latitude = query.latitude,
            longitude = query.longitude,
            zoom = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM),
        )
    }

    fun camera(query: MapPointThumbnailQuery) = LocationThumbnailCamera(
        latitude = query.latitude,
        longitude = query.longitude,
        zoom = query.zoom.coerceIn(MIN_ZOOM, MAX_ZOOM),
    )

    // MapLibre's pixelForLatLng may answer in logical or in bitmap pixels; the camera center tells which
    fun snapshotToBitmapScale(
        centerX: Float,
        centerY: Float,
        renderWidth: Int,
        renderHeight: Int,
        bitmapWidth: Int,
        bitmapHeight: Int,
    ): Float {
        val logicalDistance = hypot(centerX - renderWidth / 2f, centerY - renderHeight / 2f)
        val bitmapDistance = hypot(centerX - bitmapWidth / 2f, centerY - bitmapHeight / 2f)
        return if (logicalDistance < bitmapDistance) bitmapWidth.toFloat() / renderWidth else 1f
    }
}
