package eu.darken.apl.watch.core.history

import eu.darken.apl.watch.core.db.history.WatchHistoryHourEntity
import java.time.Duration
import java.time.Instant

/**
 * One cell per UTC hour from [from], the last one is the current hour. [counts] holds, per cell, the
 * most aircraft any source reported for that hour, and is above zero exactly for active cells.
 */
data class WatchActivityTimeline(
    val from: Instant,
    val cells: List<Cell>,
    val counts: List<Int> = List(cells.size) { 0 },
) {
    init {
        require(counts.size == cells.size) { "${counts.size} counts for ${cells.size} cells" }
    }

    enum class Cell {
        /** A check or the server's history saw a match in this hour. */
        ACTIVE,

        /** The server's history covered the whole hour and saw nothing. */
        INACTIVE,

        /** Checks in this hour saw nothing, but they only sample the hour. */
        SAMPLED_QUIET,
        UNKNOWN,
    }

    val hasData: Boolean
        get() = cells.any { it != Cell.UNKNOWN }

    companion object {
        private val HOUR: Duration = Duration.ofHours(1)

        /**
         * A positive observation from either source wins, so an expected disagreement (a check
         * matching a message from the hour before, a position just inside the circle) never hides
         * a sighting. A zero only counts as inactive when the server covered the whole hour.
         */
        fun build(
            from: Instant,
            until: Instant,
            checks: List<WatchActivityCheck>,
            hours: List<WatchHistoryHourEntity>,
        ): WatchActivityTimeline {
            val start = WatchHistoryPlan.hourOf(from)
            val count = Duration.between(start, until).toHours().coerceAtLeast(0).toInt() + 1
            // Floored, so a check just before the window does not land in its first hour
            fun indexOf(at: Instant) = Math.floorDiv(Duration.between(start, at).toMillis(), HOUR.toMillis()).toInt()
            val local = checks.groupBy { indexOf(it.timestamp) }
            val server = hours.associateBy { indexOf(it.hour) }
            val cells = ArrayList<Cell>(count)
            // The server's hour and a check need not count the same way, so neither replaces the other
            val counts = ArrayList<Int>(count)
            for (index in 0 until count) {
                val localChecks = local[index].orEmpty()
                val bucket = server[index]
                cells += when {
                    localChecks.any { it.aircraftCount > 0 } -> Cell.ACTIVE
                    bucket != null && bucket.aircraftCount > 0 -> Cell.ACTIVE
                    bucket != null && bucket.coverage == COVERAGE_FULL -> Cell.INACTIVE
                    localChecks.isNotEmpty() -> Cell.SAMPLED_QUIET
                    else -> Cell.UNKNOWN
                }
                counts += maxOf(localChecks.maxOfOrNull { it.aircraftCount } ?: 0, bucket?.aircraftCount ?: 0)
            }
            return WatchActivityTimeline(start, cells, counts)
        }

        const val COVERAGE_FULL = "full"
    }

    fun hourAt(index: Int): Instant = from.plus(HOUR.multipliedBy(index.toLong()))
}
