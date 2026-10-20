package eu.darken.apl.watch.core

import eu.darken.apl.server.access.AccessState
import eu.darken.apl.server.api.WatchDefinition
import eu.darken.apl.watch.core.db.server.WatchServerStateEntity
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

/** Whether the watch takes one of the tier's active slots; the server pauses the others for the tier instead. */
fun AccessState.takesActiveSlot(definition: WatchDefinition): Boolean {
    if (!allowsWatchType(definition.type)) return false
    if (definition.type != "location") return true
    val maxRadius = maxLocationWatchRadiusKm ?: return true
    return (definition.radiusKm ?: 0.0) <= maxRadius
}

fun AccessState.takesActiveSlot(watch: Watch): Boolean = takesActiveSlot(watch.toDefinition())

/** Whether the watch uses up one of the server's active slots; a refused watch takes none. */
fun AccessState.countsAgainstActiveCap(
    watch: Watch,
    server: WatchServerStateEntity?,
): Boolean = takesActiveSlot(watch) && server?.uploadState !in WatchServerStateEntity.UPLOAD_REFUSED
