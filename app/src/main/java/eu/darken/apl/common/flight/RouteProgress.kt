package eu.darken.apl.common.flight

import eu.darken.apl.common.geo.Spherical
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.aircraft.LIVE_WITHIN
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.max

/** Where between its airports an aircraft is, roughly: a position, not how much of the flight is done. */
sealed interface RouteProgress {
    data object AtOrigin : RouteProgress
    data object AtDestination : RouteProgress
    data class Airborne(val fraction: Float) : RouteProgress
    data object Unknown : RouteProgress
}

/** [now] has to be on the same clock as [Aircraft.positionSeenAt]. */
fun FlightRoute.progressOf(aircraft: Aircraft?, now: Instant): RouteProgress {
    if (aircraft == null || aircraft.callsign?.trim() != callsign) return RouteProgress.Unknown

    val oLat = origin?.latitude ?: return RouteProgress.Unknown
    val oLon = origin.longitude ?: return RouteProgress.Unknown
    val dLat = destination?.latitude ?: return RouteProgress.Unknown
    val dLon = destination.longitude ?: return RouteProgress.Unknown
    if (!Spherical.isValid(oLat, oLon) || !Spherical.isValid(dLat, dLon)) return RouteProgress.Unknown

    val routeM = Spherical.haversineDistanceM(oLat, oLon, dLat, dLon)
    // Near-antipodal airports have no single great circle between them
    if (routeM < MIN_ROUTE_M || routeM > MAX_ROUTE_M) return RouteProgress.Unknown

    val location = aircraft.location ?: return RouteProgress.Unknown
    val lat = location.latitude
    val lon = location.longitude
    if (!Spherical.isValid(lat, lon)) return RouteProgress.Unknown

    val seenAt = aircraft.positionSeenAt ?: return RouteProgress.Unknown
    val age = Duration.between(seenAt, now)
    if (age >= LIVE_WITHIN || age < MAX_FUTURE_SKEW.negated()) return RouteProgress.Unknown

    return when (aircraft.onGround) {
        true -> {
            val atOrigin = Spherical.haversineDistanceM(oLat, oLon, lat, lon) <= GROUND_RADIUS_M
            val atDestination = Spherical.haversineDistanceM(dLat, dLon, lat, lon) <= GROUND_RADIUS_M
            when {
                atOrigin && !atDestination -> RouteProgress.AtOrigin
                atDestination && !atOrigin -> RouteProgress.AtDestination
                else -> RouteProgress.Unknown
            }
        }

        false -> {
            val crossM = Spherical.crossTrackM(oLat, oLon, dLat, dLon, lat, lon)
            if (abs(crossM) > max(MIN_CROSS_TRACK_M, routeM * CROSS_TRACK_SHARE)) return RouteProgress.Unknown

            val alongM = Spherical.alongTrackM(oLat, oLon, dLat, dLon, lat, lon)
            // Room for a departure turn or a downwind leg beyond the runway, not for a flight elsewhere
            val overshootM = (routeM * OVERSHOOT_SHARE).coerceIn(MIN_OVERSHOOT_M, MAX_OVERSHOOT_M)
            if (alongM < -overshootM || alongM > routeM + overshootM) return RouteProgress.Unknown

            RouteProgress.Airborne((alongM / routeM).coerceIn(0.0, 1.0).toFloat())
        }

        null -> RouteProgress.Unknown
    }
}

private const val MIN_ROUTE_M = 10_000.0
private const val MAX_ROUTE_M = 19_000_000.0
private const val GROUND_RADIUS_M = 15_000.0
private const val MIN_CROSS_TRACK_M = 50_000.0
private const val CROSS_TRACK_SHARE = 0.25
private const val OVERSHOOT_SHARE = 0.05
private const val MIN_OVERSHOOT_M = 15_000.0
private const val MAX_OVERSHOOT_M = 20_000.0
private val MAX_FUTURE_SKEW: Duration = Duration.ofSeconds(60)
