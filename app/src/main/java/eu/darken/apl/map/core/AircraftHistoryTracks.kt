package eu.darken.apl.map.core

import eu.darken.apl.main.core.aircraft.AircraftHex
import eu.darken.apl.server.api.TrailPoint

/**
 * The past days of a few aircraft, loaded on request and kept for this map session. Requests are
 * tracked apart from the kept results, so dropping an old result never forgets a load in flight.
 * Not thread safe, the owner guards it.
 */
class AircraftHistoryTracks(
    private val maxAircraft: Int = MAX_AIRCRAFT,
    private val maxPoints: Int = MAX_POINTS,
) {

    /** [points] ascending, at most [MAX_POINTS] of the newest. */
    data class Track(val points: List<TrailPoint>, val maxGapMs: Long)

    data class Status(val loading: Boolean, val loaded: Boolean)

    private val results = LinkedHashMap<AircraftHex, Track>()
    private val newestRequest = mutableMapOf<AircraftHex, Long>()
    private val loading = mutableSetOf<AircraftHex>()
    private var requests = 0L

    /** A request number, or null while one for [hex] is still out. */
    fun begin(hex: AircraftHex): Long? {
        if (hex in loading) return null
        requests++
        newestRequest[hex] = requests
        loading.add(hex)
        return requests
    }

    /** Applies [points] unless a newer request for [hex] was made; false when it was not applied. */
    fun complete(hex: AircraftHex, request: Long, points: List<TrailPoint>, maxGapMs: Long): Boolean {
        if (newestRequest[hex] != request) return false
        loading.remove(hex)
        results.remove(hex)
        results[hex] = Track(points.sortedBy { it.observedAt }.takeLast(maxPoints), maxGapMs)
        while (results.size > maxAircraft) results.remove(results.keys.first())
        return true
    }

    /** What was shown before stays. */
    fun fail(hex: AircraftHex, request: Long) {
        if (newestRequest[hex] == request) loading.remove(hex)
    }

    /** Keeps [hex] among the kept results longest. */
    fun touch(hex: AircraftHex) {
        results.remove(hex)?.let { results[hex] = it }
    }

    fun track(hex: AircraftHex): Track? = results[hex]

    fun status(hex: AircraftHex): Status = Status(loading = hex in loading, loaded = hex in results)

    companion object {
        const val MAX_AIRCRAFT = 5
        const val MAX_POINTS = 20_000
    }
}
