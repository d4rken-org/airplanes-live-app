package eu.darken.apl.map.core

/** What kind of aircraft the server classified it as, [wire] is its value in map requests and answers. */
enum class AircraftKind(val wire: String) {
    PLANE("plane"),
    HELICOPTER("helicopter"),
    GLIDER("glider"),
    BALLOON("balloon"),
    DRONE("drone"),
    GROUND("ground"),
    UNKNOWN("unknown"),
    ;

    companion object {
        /** Older servers send no kind, and a value newer than this app is not one it can filter by. */
        fun fromWire(value: String?): AircraftKind = entries.firstOrNull { it.wire == value } ?: UNKNOWN
    }
}

/** The wire values in enum order, so equal sets always give equal requests. */
fun Collection<AircraftKind>.toWire(): List<String> = AircraftKind.entries.filter { it in this }.map { it.wire }
