package eu.darken.apl.main.core.aircraft

/** [designator] is the ICAO airline designator, e.g. "DLH"; [name] is shown as the server sends it. */
data class Airline(
    val designator: String,
    val name: String,
)
