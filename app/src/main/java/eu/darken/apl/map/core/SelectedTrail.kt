package eu.darken.apl.map.core

import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.main.core.query.MapSnapshot
import eu.darken.apl.server.api.TrailPoint
import kotlin.time.Duration.Companion.hours

/**
 * The selected aircraft's path, kept in step with the server's trail cursor.
 *
 * Selecting (again) starts over at cursor 0, which asks for the whole trail. After that each answer
 * either replaces the trail (`trailReset`) or extends it. An answer asked for an earlier selection
 * is ignored even when the aircraft is the same, it may carry a delta for a trail that was reset.
 *
 * Only the latest leg is kept: points before the answer's `trailLegStart` are dropped, also ones held
 * from before the cursor, e.g. when the aircraft landed and took off again while selected. The applied
 * leg start goes back with each request until a reset comes without one. Without a leg start, points
 * older than [TRAIL_WINDOW] are still dropped, so a long selection stops growing.
 */
class SelectedTrail {

    private var generation: Long = -1
    private var answered = false
    private var fallbackCursor: Long? = null
    private var legStart: Long? = null

    var points: List<TrailPoint> = emptyList()
        private set

    fun select(generation: Long) {
        this.generation = generation
        answered = false
        fallbackCursor = null
        legStart = null
        points = emptyList()
    }

    /** Asked when a request goes out, see [AircraftRepo.mapViewing]. */
    fun cursor(query: AircraftRepo.ViewingQuery.Map): AircraftRepo.TrailCursor? {
        if (query.selected == null) return null
        if (query.selectionGeneration != generation || !answered) return AircraftRepo.TrailCursor(0L)
        // With nothing kept yet, asking from the aircraft's own position time avoids re-requesting
        // an empty full trail on every poll
        val since = points.lastOrNull()?.observedAt ?: fallbackCursor ?: 0L
        return AircraftRepo.TrailCursor(since, legStart)
    }

    fun apply(snapshot: MapSnapshot) {
        if (snapshot.query.selectionGeneration != generation || snapshot.query.selected == null) return
        // Without the selected aircraft in the answer there is no trail either, nothing changed
        val trail = snapshot.trail ?: return
        answered = true
        if (snapshot.trailReset || snapshot.trailSince == 0L) {
            points = trail.sortedBy { it.observedAt }
            legStart = snapshot.trailLegStart
        } else {
            val newest = points.lastOrNull()?.observedAt ?: Long.MIN_VALUE
            points = points + trail.filter { it.observedAt > newest }.sortedBy { it.observedAt }
            snapshot.trailLegStart?.let { legStart = it }
        }
        val windowStart = snapshot.snapshot.serverTime.toEpochMilli() - TRAIL_WINDOW.inWholeMilliseconds
        val cutoff = maxOf(windowStart, legStart ?: Long.MIN_VALUE)
        if (points.isNotEmpty() && points.first().observedAt < cutoff) {
            points = points.filter { it.observedAt >= cutoff }
        }
        if (points.isEmpty()) fallbackCursor = snapshot.selected?.positionSeenAt?.toEpochMilli()
    }

    private var segmentsOf: List<TrailPoint>? = null
    private var segmentsCache: List<List<TrailPoint>> = emptyList()

    /**
     * Consecutive points further apart than [MAX_GAP_MS] are not joined, the aircraft was not seen
     * in between. The same list is returned until the points change, so it can be compared by identity.
     */
    fun segments(): List<List<TrailPoint>> {
        if (segmentsOf === points) return segmentsCache
        segmentsOf = points
        segmentsCache = if (points.isEmpty()) {
            emptyList()
        } else {
            val result = mutableListOf<MutableList<TrailPoint>>(mutableListOf(points.first()))
            points.zipWithNext { previous, next ->
                if (next.observedAt - previous.observedAt > MAX_GAP_MS) result.add(mutableListOf())
                result.last().add(next)
            }
            result.filter { it.size >= 2 }
        }
        return segmentsCache
    }

    companion object {
        /** Older trail points are thinned to about one a minute, a gap has to be clearly longer. */
        const val MAX_GAP_MS = 150_000L

        /** Matches the server's trail window. */
        val TRAIL_WINDOW = 24.hours
    }
}
