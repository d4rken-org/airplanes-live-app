package eu.darken.apl.watch.core.history

import eu.darken.apl.main.core.aircraft.AircraftHex
import eu.darken.apl.watch.core.WatchId
import java.time.Instant

data class WatchCheck(
    val watchId: WatchId,
    val checkAt: Instant,
    val aircraftCount: Int,
    val seenHexes: Set<AircraftHex> = emptySet(),
)