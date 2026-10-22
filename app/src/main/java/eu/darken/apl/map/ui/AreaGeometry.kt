package eu.darken.apl.map.ui

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * A circle on the earth as a closed ring, first point repeated last. Its longitudes are continuous
 * and never below -180, so a ring across the antimeridian goes past 180 in one piece:
 * centered at -179.9 it spans about 179.7..180.5 rather than -180.3..-179.5.
 */
internal data class AreaRing(val latitudes: List<Double>, val longitudes: List<Double>) {
    val south: Double get() = latitudes.min()
    val north: Double get() = latitudes.max()
    val west: Double get() = longitudes.min()
    val east: Double get() = longitudes.max()
}

/** What the map draws for an area being set up, the radius as the editor shows it. */
internal data class AreaCircle(val latitude: Double, val longitude: Double, val radiusMeters: Float)

internal fun areaRing(latitude: Double, longitude: Double, radiusMeters: Double, vertices: Int = 64): AreaRing {
    val phi1 = Math.toRadians(latitude)
    val lambda1 = Math.toRadians(longitude)
    val delta = radiusMeters / EARTH_RADIUS_METERS
    val latitudes = ArrayList<Double>(vertices + 1)
    val longitudes = ArrayList<Double>(vertices + 1)
    for (i in 0 until vertices) {
        val bearing = 2 * Math.PI * i / vertices
        val phi2 = asin(sin(phi1) * cos(delta) + cos(phi1) * sin(delta) * cos(bearing))
        // An offset from the center, so the ring stays continuous where it crosses ±180
        val lambda2 = lambda1 + atan2(sin(bearing) * sin(delta) * cos(phi1), cos(delta) - sin(phi1) * sin(phi2))
        latitudes.add(Math.toDegrees(phi2))
        longitudes.add(Math.toDegrees(lambda2))
    }
    latitudes.add(latitudes.first())
    longitudes.add(longitudes.first())
    val shift = if (longitudes.min() < -180.0) 360.0 else 0.0
    return AreaRing(latitudes, longitudes.map { it + shift })
}

private const val EARTH_RADIUS_METERS = 6_371_008.8
