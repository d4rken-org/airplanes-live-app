package eu.darken.apl.map.core

import eu.darken.apl.watch.core.WatchId
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.FlightWatch
import eu.darken.apl.watch.core.types.SquawkWatch
import eu.darken.apl.watch.core.types.Watch

/**
 * The watches the map can tell from what it draws of an aircraft. A location watch matches everything
 * inside its area, a badge on each of those would say nothing about any one of them.
 */
data class MapWatches(
    val hexes: Set<String> = emptySet(),
    val callsigns: Map<String, List<WatchId>> = emptyMap(),
    val squawks: Map<String, List<WatchId>> = emptyMap(),
) {
    val isEmpty: Boolean get() = hexes.isEmpty() && callsigns.isEmpty() && squawks.isEmpty()

    fun matches(hex: String, callsign: String?, squawk: String?): Boolean =
        normalize(hex) in hexes || normalize(callsign) in callsigns || normalize(squawk) in squawks

    fun matches(plane: MapPlane): Boolean = matches(plane.hex, plane.callsign, plane.squawk)

    /** The callsign and squawk watches an aircraft matches. Its own hex watch has its own button. */
    fun matching(callsign: String?, squawk: String?): List<Match> {
        val byCallsign = normalize(callsign)?.let { key ->
            callsigns[key].orEmpty().map { Match(it, Match.Kind.CALLSIGN, key) }
        }
        val bySquawk = normalize(squawk)?.let { key ->
            squawks[key].orEmpty().map { Match(it, Match.Kind.SQUAWK, key) }
        }
        return byCallsign.orEmpty() + bySquawk.orEmpty()
    }

    data class Match(val id: WatchId, val kind: Kind, val value: String) {
        enum class Kind { CALLSIGN, SQUAWK }
    }

    companion object {
        /** Null for nothing to match, so a missing callsign can't meet a blank watch. */
        fun normalize(value: String?): String? = value?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }

        fun from(watches: List<Watch>): MapWatches {
            fun <W : Watch> idsBy(key: (W) -> String?, typed: List<W>) = typed
                .mapNotNull { watch -> normalize(key(watch))?.let { it to watch.id } }
                .groupBy({ it.first }, { it.second })
            return MapWatches(
                hexes = watches.filterIsInstance<AircraftWatch>().mapNotNull { normalize(it.hex) }.toSet(),
                callsigns = idsBy({ it.callsign }, watches.filterIsInstance<FlightWatch>()),
                squawks = idsBy({ it.code }, watches.filterIsInstance<SquawkWatch>()),
            )
        }
    }
}
