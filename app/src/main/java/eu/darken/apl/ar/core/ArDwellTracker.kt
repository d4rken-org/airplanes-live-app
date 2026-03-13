package eu.darken.apl.ar.core

import eu.darken.apl.main.core.aircraft.AircraftHex

class ArDwellTracker(private val thresholdMs: Long = 3_000L) {

    private val firstSeen = mutableMapOf<AircraftHex, Long>()

    fun update(visibleHexes: Set<AircraftHex>, nowMs: Long): Set<AircraftHex> {
        firstSeen.keys.retainAll(visibleHexes)

        for (hex in visibleHexes) {
            firstSeen.putIfAbsent(hex, nowMs)
        }

        return firstSeen.entries
            .filter { (_, seenMs) -> nowMs - seenMs >= thresholdMs }
            .mapTo(mutableSetOf()) { it.key }
    }
}
