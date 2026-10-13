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

    /**
     * Aircraft missing from [answer] are dropped, like the planes they belong to. Once [maxAircraft]
     * are tracked, a [preferred] aircraft takes the place of the longest tracked other one, so those
     * on screen get tracks even when the answer holds more aircraft than fit.
     */
    fun record(answer: Map<AircraftHex, MapAircraft>, preferred: Set<AircraftHex> = emptySet()) {
        tracks.keys.retainAll(answer.keys)
        val (first, rest) = answer.entries.partition { it.key in preferred }
        (first + rest).forEach { (hex, ac) ->
            val observedAt = ac.position.observedAt ?: return@forEach
            val track = tracks[hex] ?: run {
                if (tracks.size >= maxAircraft) {
                    if (hex !in preferred) return@forEach
                    val evicted = tracks.keys.firstOrNull { it !in preferred } ?: return@forEach
                    tracks.remove(evicted)
                }
                ArrayDeque<Point>().also { tracks[hex] = it }
            }
            if ((track.lastOrNull()?.observedAt ?: Long.MIN_VALUE) >= observedAt) return@forEach
            track.addLast(Point(ac.position.latitude, ac.position.longitude, ac.altitudeFeet?.toInt(), observedAt))
            while (track.size > pointsPerAircraft) track.removeFirst()
        }
    }

    fun clear() = tracks.clear()

    /** Oldest first; a live view that changes with the next [record]. */
    fun points(hex: AircraftHex): List<Point>? = tracks[hex]

    companion object {
        const val POINTS_PER_AIRCRAFT = 60
        const val MAX_AIRCRAFT = 1_000
    }
}
