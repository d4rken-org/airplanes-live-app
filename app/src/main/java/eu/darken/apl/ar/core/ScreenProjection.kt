package eu.darken.apl.ar.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

data class ProjectionResult(
    val screenXNorm: Float,
    val screenYNorm: Float,
    val isVisible: Boolean,
    val distanceM: Double,
    val elevationDeg: Float,
)

object ScreenProjection {

    const val H_FOV_DEG = 65f

    private const val EARTH_RADIUS_M = 6_371_000.0
    private const val KNOTS_TO_MS = 0.514444
    private const val MAX_EXTRAPOLATION_SEC = 30f

    fun project(
        userLat: Double,
        userLon: Double,
        userAltM: Double,
        acLat: Double,
        acLon: Double,
        acAltM: Double,
        rotationMatrix: FloatArray,
        hFovDeg: Float = H_FOV_DEG,
        vFovDeg: Float = 50f,
    ): ProjectionResult? {
        val distM = haversineDistanceM(userLat, userLon, acLat, acLon)
        val bearingR = bearingRad(userLat, userLon, acLat, acLon)
        val altDelta = acAltM - userAltM
        val horizDist = distM.coerceAtLeast(1.0)
        val elevationRad = atan2(altDelta, horizDist)
        val elevationDeg = Math.toDegrees(elevationRad).toFloat()

        // World-space displacement vector (East-North-Up)
        val east = (sin(bearingR) * horizDist).toFloat()
        val north = (cos(bearingR) * horizDist).toFloat()
        val up = altDelta.toFloat()

        // Transform world ENU vector to device/camera space.
        // SensorManager gives R mapping device→world (row-major 4x4).
        // We need world→device = R^T, so read columns instead of rows.
        val camX = rotationMatrix[0] * east + rotationMatrix[4] * north + rotationMatrix[8] * up
        val camY = rotationMatrix[1] * east + rotationMatrix[5] * north + rotationMatrix[9] * up
        val camZ = rotationMatrix[2] * east + rotationMatrix[6] * north + rotationMatrix[10] * up

        // Behind camera check
        if (camZ >= 0) return null

        // Perspective divide (negate Z because camera looks along -Z)
        val ndcX = camX / -camZ
        val ndcY = camY / -camZ

        val hFovRad = Math.toRadians(hFovDeg.toDouble()).toFloat()
        val vFovRad = Math.toRadians(vFovDeg.toDouble()).toFloat()
        val tanHalfH = tan(hFovRad / 2f)
        val tanHalfV = tan(vFovRad / 2f)

        // Normalize to [-1, 1] within FOV, then to [0, 1] for screen coordinates
        val screenX = (ndcX / tanHalfH + 1f) / 2f
        val screenY = (1f - ndcY / tanHalfV) / 2f

        val isVisible = screenX in 0f..1f && screenY in 0f..1f

        val totalDist = sqrt(distM * distM + altDelta * altDelta)

        return ProjectionResult(
            screenXNorm = screenX,
            screenYNorm = screenY,
            isVisible = isVisible,
            distanceM = totalDist,
            elevationDeg = elevationDeg,
        )
    }

    fun haversineDistanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1Rad) * cos(lat2Rad) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * asin(sqrt(a))
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

    fun extrapolatePosition(
        lat: Double,
        lon: Double,
        trackDeg: Float,
        speedKts: Float,
        ageSec: Float,
    ): Pair<Double, Double> {
        val clampedAge = ageSec.coerceIn(0f, MAX_EXTRAPOLATION_SEC)
        val latRad = Math.toRadians(lat)
        val cosLat = cos(latRad)
        if (abs(cosLat) < 1e-10) return lat to lon

        val distM = speedKts * KNOTS_TO_MS * clampedAge
        val angularDist = distM / EARTH_RADIUS_M
        val trackRad = Math.toRadians(trackDeg.toDouble())

        val newLatRad = latRad + angularDist * cos(trackRad)
        val newLonRad = Math.toRadians(lon) + angularDist * sin(trackRad) / cosLat

        return Math.toDegrees(newLatRad) to Math.toDegrees(newLonRad)
    }

    fun extrapolateAltitudeFt(
        altitudeFt: Int,
        altitudeRateFtMin: Int,
        ageSec: Float,
    ): Int {
        val clampedAge = ageSec.coerceIn(0f, MAX_EXTRAPOLATION_SEC)
        val deltaFt = (altitudeRateFtMin / 60f * clampedAge).roundToInt()
        return altitudeFt + deltaFt
    }
}
