package eu.darken.apl.watch.core.history

import eu.darken.apl.main.core.aircraft.AircraftHex
import java.time.Instant

/** The newest sighting in the server's history that names its aircraft, [hex] uppercase. */
data class LastSighting(
    val at: Instant,
    val hex: AircraftHex,
) {
    /** What the watch list calls the aircraft. */
    val name: String
        get() = hex
}
