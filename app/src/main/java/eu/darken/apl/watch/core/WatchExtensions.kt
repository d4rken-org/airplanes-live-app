package eu.darken.apl.watch.core

import eu.darken.apl.server.api.WatchDefinition
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.FlightWatch
import eu.darken.apl.watch.core.types.LocationWatch
import eu.darken.apl.watch.core.types.SquawkWatch
import eu.darken.apl.watch.core.types.Watch
import kotlinx.coroutines.flow.first

suspend fun WatchRepo.getStatus(id: WatchId): Watch.Status? = status.first().singleOrNull { it.id == id }

/** The watch as the server's watch endpoints take it. */
fun Watch.toDefinition(): WatchDefinition = when (this) {
    is AircraftWatch -> WatchDefinition(type = "hex", value = hex.uppercase())
    is FlightWatch -> WatchDefinition(type = "callsign", value = callsign.uppercase())
    is SquawkWatch -> WatchDefinition(type = "squawk", value = code)
    is LocationWatch -> WatchDefinition(
        type = "location",
        latitude = latitude,
        longitude = longitude,
        radiusKm = radiusInMeters / 1000.0,
    )
}
