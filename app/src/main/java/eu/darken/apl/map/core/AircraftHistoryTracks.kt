package eu.darken.apl.map.core

import eu.darken.apl.main.core.aircraft.AircraftHex
import eu.darken.apl.server.api.HistoryAircraftResponse
import eu.darken.apl.server.api.HistoryHourStatus
import eu.darken.apl.server.api.HistoryRunSpan
import eu.darken.apl.server.api.TrailPoint

/**
 * The loads of aircraft history: one per aircraft at a time, and only the newest one counts. Not
 * thread safe, the owner guards it.
 */
class AircraftHistoryTracks(
    private val maxPoints: Int = MAX_POINTS,
) {

    /**
     * One answer for `[from, until)`, epoch ms. [points] ascending, at most [MAX_POINTS] of the
     * newest, with the server's neighbours just outside the range.
     */
    data class Track(
        val points: List<TrailPoint>,
        val maxGapMs: Long,
        val from: Long,
        val until: Long,
        /** Points before this were dropped to stay within the cap, null when none were. */
        val omittedUntil: Long? = null,
        val runs: List<HistoryRunSpan> = emptyList(),
        val hours: List<HistoryHourStatus> = emptyList(),
        val registration: String? = null,
        val aircraftType: String? = null,
        val category: String? = null,
    )

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

    /** Ascending and capped, without touching any state: safe to call without the owner's guard. */
    fun prepare(response: HistoryAircraftResponse): Track {
        val points = response.points
        val ascending = (1 until points.size).all { points[it - 1].observedAt <= points[it].observedAt }
        val ordered = if (ascending) points else points.sortedBy { it.observedAt }
        val kept = ordered.takeLast(maxPoints)
        return Track(
            points = kept,
            maxGapMs = response.maxGapMs,
            from = response.from,
            until = response.until,
            omittedUntil = kept.firstOrNull()?.observedAt?.takeIf { kept.size < ordered.size },
            runs = response.runs,
            hours = response.hours,
            registration = response.registration,
            aircraftType = response.aircraftType,
            category = response.category,
        )
    }

    /** False when a newer request for [hex] was made since, its answer is the one that counts. */
    fun complete(hex: AircraftHex, request: Long): Boolean {
        if (newestRequest[hex] != request) return false
        loading.remove(hex)
        return true
    }

    fun fail(hex: AircraftHex, request: Long) {
        if (newestRequest[hex] == request) loading.remove(hex)
    }

    companion object {
        const val MAX_POINTS = 20_000
    }
}
