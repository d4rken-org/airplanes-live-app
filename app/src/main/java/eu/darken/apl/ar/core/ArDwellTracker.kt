package eu.darken.apl.ar.core

import eu.darken.apl.main.core.aircraft.AircraftHex
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ArDwellTracker(private val threshold: Duration = 3.seconds) {

    private val firstSeen = mutableMapOf<AircraftHex, Long>()

    fun update(visibleHexes: Set<AircraftHex>, nowMs: Long): Set<AircraftHex> {
        firstSeen.keys.retainAll(visibleHexes)

        for (hex in visibleHexes) {
            firstSeen.putIfAbsent(hex, nowMs)
        }

        return firstSeen.entries
            .filter { (_, seenMs) -> nowMs - seenMs >= threshold.inWholeMilliseconds }
            .mapTo(mutableSetOf()) { it.key }
    }
}
