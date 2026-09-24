package eu.darken.apl.map.core

import eu.darken.apl.main.core.aircraft.AircraftHex
import eu.darken.apl.server.api.MapAircraft

/**
 * Where each aircraft was seen while the map has been open, for drawing tracks of every aircraft
 * at once. Unlike [SelectedTrail] this knows nothing from before the map opened.
 */
class RecentTracks(
    private val pointsPerAircraft: Int = POINTS_PER_AIRCRAFT,
    private val maxAircraft: Int = MAX_AIRCRAFT,
) {

    data class Point(val latitude: Double, val longitude: Double, val altitudeFt: Int?, val observedAt: Long)

    private val tracks = LinkedHashMap<AircraftHex, ArrayDeque<Point>>()

    /** Changes only when the tracks did, so a copy can be reused until then. */
    var version = 0L
        private set

    /** Aircraft missing from [answer] are dropped, like the planes they belong to. */
    fun record(answer: Map<AircraftHex, MapAircraft>) {
        var changed = tracks.keys.retainAll(answer.keys)
        answer.forEach { (hex, ac) ->
            val observedAt = ac.position.observedAt ?: return@forEach
            val track = tracks[hex] ?: run {
                if (tracks.size >= maxAircraft) return@forEach
                ArrayDeque<Point>().also { tracks[hex] = it }
            }
            if ((track.lastOrNull()?.observedAt ?: Long.MIN_VALUE) >= observedAt) return@forEach
            track.addLast(Point(ac.position.latitude, ac.position.longitude, ac.altitudeFeet?.toInt(), observedAt))
            while (track.size > pointsPerAircraft) track.removeFirst()
            changed = true
        }
        if (changed) version++
    }

    fun clear() {
        if (tracks.isEmpty()) return
        tracks.clear()
        version++
    }

    fun snapshot(): Map<AircraftHex, List<Point>> = tracks.mapValues { it.value.toList() }

    companion object {
        const val POINTS_PER_AIRCRAFT = 60
        const val MAX_AIRCRAFT = 1_000
    }
}
