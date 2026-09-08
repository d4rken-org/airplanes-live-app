package eu.darken.apl.main.core.api

import android.location.Location
import eu.darken.apl.main.core.aircraft.Aircraft
import java.time.Instant


suspend fun AirplanesLiveEndpoint.getByLocation(location: Location, radiusInMeter: Long) =
    getByLocation(location.latitude, location.longitude, radiusInMeter)

/** Bridges the old upstream shape onto the domain model until the server client replaces it. */
fun AirplanesLiveApi.Aircraft.toDomain(fetchedAt: Instant): Aircraft {
    val flags = dbFlags ?: 0
    val altitudeRaw = altitude?.trim()?.lowercase()
    return Aircraft(
        hex = hex,
        registration = registration,
        callsign = callsign,
        operator = operator,
        airframe = airframe,
        description = description,
        squawk = squawk,
        emergency = emergency,
        source = messageType,
        military = flags and 1 != 0,
        ladd = flags and 8 != 0,
        pia = flags and 4 != 0,
        outsideTemp = outsideTemp,
        altitudeFt = if (altitudeRaw == "ground") null else altitudeRaw?.replace(",", "")?.toIntOrNull(),
        onGround = if (altitudeRaw == "ground") true else altitudeRaw?.let { false },
        geometricAltitudeFt = null,
        altitudeRate = altitudeRate,
        groundSpeed = groundSpeed,
        indicatedAirSpeed = indicatedAirSpeed,
        trackheading = trackheading,
        groundTrack = groundTrack,
        location = location,
        messageSeenAt = seenAt,
        positionSeenAt = if (location != null) seenAt else null,
        fetchedAt = fetchedAt,
    )
}
