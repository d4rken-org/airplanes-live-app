package eu.darken.apl.map.core

import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.main.core.query.MapSnapshot
import eu.darken.apl.server.api.TrailPoint

/**
 * The selected aircraft's path, kept in step with the server's trail cursor.
 *
 * Selecting (again) starts over at cursor 0, which asks for the whole trail. After that each answer
 * either replaces the trail (`trailReset`) or extends it. An answer asked for an earlier selection
 * is ignored even when the aircraft is the same, it may carry a delta for a trail that was reset.
 */
class SelectedTrail {

    private var generation: Long = -1
    private var answered = false
    private var fallbackCursor: Long? = null

    var points: List<TrailPoint> = emptyList()
        private set

    fun select(generation: Long) {
        this.generation = generation
        answered = false
        fallbackCursor = null
        points = emptyList()
    }

    /** Asked when a request goes out, see [AircraftRepo.mapViewing]. */
    fun cursor(query: AircraftRepo.ViewingQuery.Map): Long? {
        if (query.selected == null) return null
        if (query.selectionGeneration != generation || !answered) return 0L
        // With nothing kept yet, asking from the aircraft's own position time avoids re-requesting
        // an empty full trail on every poll
        return points.lastOrNull()?.observedAt ?: fallbackCursor ?: 0L
    }

    fun apply(snapshot: MapSnapshot) {
        if (snapshot.query.selectionGeneration != generation || snapshot.query.selected == null) return
        // Without the selected aircraft in the answer there is no trail either, nothing changed
        val trail = snapshot.trail ?: return
        answered = true
        points = if (snapshot.trailReset || snapshot.trailSince == 0L) {
            trail.sortedBy { it.observedAt }
        } else {
            val newest = points.lastOrNull()?.observedAt ?: Long.MIN_VALUE
            points + trail.filter { it.observedAt > newest }.sortedBy { it.observedAt }
        }
        if (points.isEmpty()) fallbackCursor = snapshot.selected?.positionSeenAt?.toEpochMilli()
    }

    /** Consecutive points further apart than [maxGapMillis] are not joined, the aircraft was not seen in between. */
    fun segments(maxGapMillis: Long = MAX_GAP_MS): List<List<TrailPoint>> {
        if (points.isEmpty()) return emptyList()
        val result = mutableListOf<MutableList<TrailPoint>>(mutableListOf(points.first()))
        points.zipWithNext { previous, next ->
            if (next.observedAt - previous.observedAt > maxGapMillis) result.add(mutableListOf())
            result.last().add(next)
        }
        return result.filter { it.size >= 2 }
    }

    companion object {
        /** Older trail points are thinned to about one a minute, a gap has to be clearly longer. */
        const val MAX_GAP_MS = 150_000L
    }
}
