package eu.darken.apl.watch.core.history

import eu.darken.apl.watch.core.db.history.WatchHistoryHourEntity
import eu.darken.apl.watch.core.db.server.WatchServerRollupEntity
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
         *
         * [rollups] count only for [serverWatchId] at [serverRevision], see [mergeHours].
         */
        fun build(
            from: Instant,
            until: Instant,
            checks: List<WatchActivityCheck>,
            hours: List<WatchHistoryHourEntity>,
            rollups: List<WatchServerRollupEntity> = emptyList(),
            serverWatchId: String? = null,
            serverRevision: Long? = null,
        ): WatchActivityTimeline {
            val start = WatchHistoryPlan.hourOf(from)
            val count = Duration.between(start, until).toHours().coerceAtLeast(0).toInt() + 1
            // Floored, so a check just before the window does not land in its first hour
            fun indexOf(at: Instant) = Math.floorDiv(Duration.between(start, at).toMillis(), HOUR.toMillis()).toInt()
            val local = checks.groupBy { indexOf(it.timestamp) }
            val server = mergeHours(hours, rollups, serverWatchId, serverRevision).associateBy { indexOf(it.hour) }
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

        /**
         * One row per UTC hour out of the server's history hours and the rollups of [serverWatchId] at
         * [serverRevision]; rollups of other watches or revisions are dropped, they watched another
         * definition. Per hour the count is the largest any row reported, and the coverage the widest:
         *
         * ```
         * history 0 partial + live 0 full      -> 0 full     (inactive)
         * history 2 partial + live 5 partial   -> 5 partial  (active, not 7)
         * live 0 partial + history 0 none      -> 0 partial  (unknown)
         * ```
         */
        fun mergeHours(
            hours: List<WatchHistoryHourEntity>,
            rollups: List<WatchServerRollupEntity>,
            serverWatchId: String?,
            serverRevision: Long?,
        ): List<WatchHistoryHourEntity> {
            val current = rollups
                .filter { it.serverWatchId == serverWatchId && it.revision == serverRevision }
                .map { WatchHistoryHourEntity(it.watchId, it.hour, it.aircraftCount, it.coverage, it.isFinal) }
            return (hours + current)
                .groupBy { WatchHistoryPlan.hourOf(it.hour) }
                .map { (hour, rows) ->
                    WatchHistoryHourEntity(
                        watchId = rows.first().watchId,
                        hour = hour,
                        aircraftCount = rows.maxOf { it.aircraftCount },
                        coverage = when {
                            rows.any { it.coverage == COVERAGE_FULL } -> COVERAGE_FULL
                            rows.any { it.coverage == COVERAGE_PARTIAL } -> COVERAGE_PARTIAL
                            else -> COVERAGE_NONE
                        },
                        isFinal = rows.all { it.isFinal },
                    )
                }
                .sortedBy { it.hour }
        }

        const val COVERAGE_FULL = "full"
        const val COVERAGE_PARTIAL = "partial"
        const val COVERAGE_NONE = "none"
    }

    fun hourAt(index: Int): Instant = from.plus(HOUR.multipliedBy(index.toLong()))
}
