package eu.darken.apl.common.flight

import eu.darken.apl.common.geo.Spherical
import eu.darken.apl.common.units.AircraftUnits
import eu.darken.apl.common.units.Quantity
import eu.darken.apl.common.units.UnitFormatter
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.aircraft.LIVE_WITHIN
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

/**
 * Barometric altitude minus the airport's catalogue elevation, an estimate: the altitude is not
 * corrected for the local air pressure.
 */
data class HeightAboveAirport(
    val airport: Airport,
    val heightFt: Int,
) {
    /** Closer to the airport's elevation than the estimate can tell apart, also below it. */
    val aboutFieldLevel: Boolean
        get() = heightFt < FIELD_LEVEL_FT
}

/**
 * Only for an aircraft near and low above its origin or destination. [aircraft] has to be a current
 * server observation, its [Aircraft.altitudeFt] is barometric, a map position's altitude can be GPS.
 * [now] has to be on the same clock as [Aircraft.positionSeenAt].
 */
fun FlightRoute.heightAboveAirport(aircraft: Aircraft?, now: Instant): HeightAboveAirport? {
    if (aircraft == null || aircraft.callsign?.trim() != callsign) return null
    if (aircraft.onGround == true) return null
    val altitudeFt = aircraft.altitudeFt ?: return null

    val location = aircraft.location ?: return null
    val lat = location.latitude
    val lon = location.longitude
    if (!Spherical.isValid(lat, lon)) return null

    val seenAt = aircraft.positionSeenAt ?: return null
    val age = Duration.between(seenAt, now)
    if (age >= LIVE_WITHIN || age < MAX_FUTURE_SKEW.negated()) return null

    val nearest = listOfNotNull(origin, destination)
        .mapNotNull { airport ->
            val aLat = airport.latitude ?: return@mapNotNull null
            val aLon = airport.longitude ?: return@mapNotNull null
            val elevationFt = airport.elevationFt ?: return@mapNotNull null
            if (!Spherical.isValid(aLat, aLon)) return@mapNotNull null
            val distanceM = Spherical.haversineDistanceM(aLat, aLon, lat, lon)
            if (distanceM > NEAR_AIRPORT_M) return@mapNotNull null
            Triple(airport, elevationFt, distanceM)
        }
        .minByOrNull { it.third }
        ?: return null

    val (airport, elevationFt) = nearest
    val heightFt = altitudeFt - elevationFt
    if (heightFt > MAX_HEIGHT_FT) return null
    return HeightAboveAirport(airport, heightFt)
}

/** Rounded in the unit shown: 1,130 ft shows as 1,150 ft, or as 340 m. */
fun UnitFormatter.heightAboveField(feet: Int): Quantity = when (aircraftUnits) {
    AircraftUnits.METRIC -> altitude(feet)
    AircraftUnits.AVIATION, AircraftUnits.IMPERIAL -> altitude((feet / FEET_STEP).roundToInt() * FEET_STEP.toInt())
}

private const val NEAR_AIRPORT_M = 18_520.0
private const val MAX_HEIGHT_FT = 5_000
private const val FIELD_LEVEL_FT = 100
private const val FEET_STEP = 50.0
