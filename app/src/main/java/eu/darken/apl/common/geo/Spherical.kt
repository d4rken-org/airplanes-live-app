package eu.darken.apl.common.geo

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Great-circle math on a spherical earth. Latitudes and longitudes in degrees. */
object Spherical {

    const val EARTH_RADIUS_M = 6_371_000.0

    fun isValid(lat: Double, lon: Double): Boolean =
        lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0

    fun haversineDistanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1Rad) * cos(lat2Rad) * sin(dLon / 2) * sin(dLon / 2)
        // Rounding can push a just past 1 for antipodal points, and asin of that is NaN
        val c = 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
        return EARTH_RADIUS_M * c
    }

    fun bearingRad(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLon)
        return atan2(y, x)
    }

    /** Distance of the point from the great circle through start and end, positive to the right of it. */
    fun crossTrackM(
        startLat: Double, startLon: Double,
        endLat: Double, endLon: Double,
        lat: Double, lon: Double,
    ): Double {
        val angular = haversineDistanceM(startLat, startLon, lat, lon) / EARTH_RADIUS_M
        val delta = bearingRad(startLat, startLon, lat, lon) - bearingRad(startLat, startLon, endLat, endLon)
        return EARTH_RADIUS_M * asin((sin(angular) * sin(delta)).coerceIn(-1.0, 1.0))
    }

    /** Distance from start to the point's foot on the great circle towards end, negative behind start. */
    fun alongTrackM(
        startLat: Double, startLon: Double,
        endLat: Double, endLon: Double,
        lat: Double, lon: Double,
    ): Double {
        val angular = haversineDistanceM(startLat, startLon, lat, lon) / EARTH_RADIUS_M
        val delta = bearingRad(startLat, startLon, lat, lon) - bearingRad(startLat, startLon, endLat, endLon)
        return EARTH_RADIUS_M * atan2(sin(angular) * cos(delta), cos(angular))
    }
}
