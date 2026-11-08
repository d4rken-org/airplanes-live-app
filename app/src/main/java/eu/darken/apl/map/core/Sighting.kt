package eu.darken.apl.map.core

import eu.darken.apl.main.core.aircraft.Aircraft

/** A position of an aircraft and when it was observed, in server time. */
data class Sighting(
    val latitude: Double,
    val longitude: Double,
    val observedAt: Long,
    /** Which way to draw it, see [MapTraffic.bearing]. */
    val trackDegrees: Float? = null,
)

/** For cached aircraft: track first, the heading may not belong to this position (CachedAircraftDao.keepingPositionOf). */
fun Aircraft.sighting(): Sighting? =
    sighting(MapTraffic.bearing(onGround = false, trackDegrees = groundTrack, headingDegrees = trackheading))

private fun Aircraft.sighting(direction: Float?): Sighting? {
    val location = location ?: return null
    val observedAt = positionSeenAt ?: return null
    return Sighting(location.latitude, location.longitude, observedAt.toEpochMilli(), direction)
}

/**
 * The newer of what the map and the cache know. A newer cached position without a direction takes
 * the map's, which already includes the aircraft's remembered one.
 */
fun newestSighting(map: Sighting?, cached: Sighting?): Sighting? {
    val newest = listOfNotNull(map, cached).maxByOrNull { it.observedAt } ?: return null
    return newest.takeIf { it.trackDegrees != null } ?: newest.copy(trackDegrees = map?.trackDegrees)
}

private fun Aircraft.bearing(): Float? = MapTraffic.bearing(onGround == true, groundTrack, trackheading)

/** The newest of what the live map and the server know about where the selection was, null for history frames. */
fun MapAircraftProvider.Frame.selectedSighting(): Sighting? {
    val hex = selectedHex ?: return null
    if (replay != null) return null
    val details = selectedDetails?.takeIf { it.hex == hex }
    val aircraftBearing = selectedAircraft?.let { MapTraffic.bearingOf(it) } ?: selectedRememberedBearing
    val newest = listOfNotNull(
        selectedAircraft?.position?.let { p -> p.observedAt?.let { Sighting(p.latitude, p.longitude, it, aircraftBearing) } },
        details?.let { it.sighting(it.bearing()) },
        selectedTrailNewest?.let { Sighting(it.latitude, it.longitude, it.observedAt) },
        selectedLastSeen,
    ).maxByOrNull { it.observedAt } ?: return null
    // Without a direction of its own, the newest position borrows the aircraft's last known one
    return newest.takeIf { it.trackDegrees != null } ?: newest.copy(trackDegrees = aircraftBearing ?: details?.bearing())
}
