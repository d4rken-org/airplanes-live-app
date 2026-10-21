package eu.darken.apl.map.core

import eu.darken.apl.main.core.aircraft.Aircraft

/** A position of an aircraft and when it was observed, in server time. */
data class Sighting(
    val latitude: Double,
    val longitude: Double,
    val observedAt: Long,
    val trackDegrees: Float? = null,
)

fun Aircraft.sighting(): Sighting? {
    val location = location ?: return null
    val observedAt = positionSeenAt ?: return null
    return Sighting(location.latitude, location.longitude, observedAt.toEpochMilli(), groundTrack)
}

/** The newest of what the live map knows about where the selection was, null for history frames. */
fun MapAircraftProvider.Frame.selectedSighting(): Sighting? {
    val hex = selectedHex ?: return null
    if (replay != null) return null
    val details = selectedDetails?.takeIf { it.hex == hex }
    val aircraftTrack = selectedAircraft?.trackDegrees?.toFloat()
    val newest = listOfNotNull(
        selectedAircraft?.position?.let { p -> p.observedAt?.let { Sighting(p.latitude, p.longitude, it, aircraftTrack) } },
        details?.sighting(),
        selectedTrailNewest?.let { Sighting(it.latitude, it.longitude, it.observedAt) },
    ).maxByOrNull { it.observedAt } ?: return null
    // Without a track of its own, the newest position borrows the aircraft's last known one
    return newest.takeIf { it.trackDegrees != null } ?: newest.copy(trackDegrees = aircraftTrack ?: details?.groundTrack)
}
