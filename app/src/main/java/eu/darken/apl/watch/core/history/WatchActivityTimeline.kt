package eu.darken.apl.watch.core.history

import eu.darken.apl.watch.core.db.server.WatchServerRollupEntity
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

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
        /** The server's history saw a match in this hour. */
        ACTIVE,

        /** The server's history covered the whole hour and saw nothing. */
        INACTIVE,
        UNKNOWN,
    }

    val hasData: Boolean
        get() = cells.any { it != Cell.UNKNOWN }

    val activeHours: Int
        get() = cells.count { it == Cell.ACTIVE }

    /** One UTC hour as the server's rollups for it add up. */
    data class Hour(
        val hour: Instant,
        val aircraftCount: Int,
        val coverage: String,
        val isFinal: Boolean,
    )

    companion object {
        private val HOUR: Duration = Duration.ofHours(1)

        fun hourOf(at: Instant): Instant = at.truncatedTo(ChronoUnit.HOURS)

        /**
         * A zero only counts as inactive when the server covered the whole hour.
         *
         * [rollups] count only for [serverWatchId] at [serverRevision], see [mergeHours].
         */
        fun build(
            from: Instant,
            until: Instant,
            rollups: List<WatchServerRollupEntity>,
            serverWatchId: String?,
            serverRevision: Long?,
        ): WatchActivityTimeline {
            val start = hourOf(from)
            val count = Duration.between(start, until).toHours().coerceAtLeast(0).toInt() + 1
            // Floored, so an hour just before the window does not land in its first cell
            fun indexOf(at: Instant) = Math.floorDiv(Duration.between(start, at).toMillis(), HOUR.toMillis()).toInt()
            val server = mergeHours(rollups, serverWatchId, serverRevision).associateBy { indexOf(it.hour) }
            val cells = ArrayList<Cell>(count)
            val counts = ArrayList<Int>(count)
            for (index in 0 until count) {
                val bucket = server[index]
                cells += when {
                    bucket != null && bucket.aircraftCount > 0 -> Cell.ACTIVE
                    bucket != null && bucket.coverage == COVERAGE_FULL -> Cell.INACTIVE
                    else -> Cell.UNKNOWN
                }
                counts += bucket?.aircraftCount ?: 0
            }
            return WatchActivityTimeline(start, cells, counts)
        }

        /**
         * One row per UTC hour out of the rollups of [serverWatchId] at [serverRevision]; rollups of
         * other watches or revisions are dropped, they watched another definition. Per hour the count
         * is the largest any rollup reported, and the coverage the widest:
         *
         * ```
         * history 0 partial + live 0 full      -> 0 full     (inactive)
         * history 2 partial + live 5 partial   -> 5 partial  (active, not 7)
         * live 0 partial + history 0 none      -> 0 partial  (unknown)
         * ```
         */
        fun mergeHours(
            rollups: List<WatchServerRollupEntity>,
            serverWatchId: String?,
            serverRevision: Long?,
        ): List<Hour> = rollups
            .filter { it.serverWatchId == serverWatchId && it.revision == serverRevision }
            .groupBy { hourOf(it.hour) }
            .map { (hour, rows) ->
                Hour(
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

        const val COVERAGE_FULL = "full"
        const val COVERAGE_PARTIAL = "partial"
        const val COVERAGE_NONE = "none"
    }

    fun hourAt(index: Int): Instant = from.plus(HOUR.multipliedBy(index.toLong()))
}
