package eu.darken.apl.main.core.aircraft

import android.location.Location
import eu.darken.apl.server.api.AircraftObservation
import java.time.Instant
import kotlin.math.roundToInt

/** [fetchedAt] is the server time of the response that carried this observation. */
fun AircraftObservation.toAircraft(fetchedAt: Instant): Aircraft = Aircraft(
    hex = id.uppercase(),
    registration = registration,
    callsign = callsign,
    operator = operator,
    airframe = aircraftType,
    description = description,
    squawk = squawk,
    emergency = emergency,
    source = source,
    military = military,
    ladd = ladd,
    pia = pia,
    outsideTemp = outsideTemperatureCelsius?.roundToInt(),
    altitudeFt = barometricAltitudeFeet?.roundToInt(),
    onGround = onGround,
    geometricAltitudeFt = geometricAltitudeFeet?.roundToInt(),
    altitudeRate = verticalRateFeetPerMinute?.roundToInt(),
    groundSpeed = groundSpeedKnots?.toFloat(),
    indicatedAirSpeed = indicatedAirspeedKnots?.roundToInt(),
    trackheading = trueHeadingDegrees,
    groundTrack = trackDegrees?.toFloat(),
    location = position?.let {
        Location(LOCATION_PROVIDER).apply {
            latitude = it.latitude
            longitude = it.longitude
            time = it.observedAt ?: 0L
        }
    },
    messageSeenAt = messageObservedAt?.let { Instant.ofEpochMilli(it) },
    positionSeenAt = position?.observedAt?.let { Instant.ofEpochMilli(it) },
    fetchedAt = fetchedAt,
)

private const val LOCATION_PROVIDER = "apl"
