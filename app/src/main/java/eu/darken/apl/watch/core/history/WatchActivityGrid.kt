package eu.darken.apl.watch.core.history

import eu.darken.apl.watch.core.history.WatchActivityTimeline.Cell
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * A watch's activity by local day and clock hour, today last. Each UTC hour of the timeline lands in
 * the local hour it starts in, so in a zone offset by half an hour its activity sits up to half an
 * hour early, and the hour before local midnight can hold activity from just after it.
 */
data class WatchActivityGrid(
    val days: List<Day>,
) {
    data class Day(val date: LocalDate, val slots: List<Slot>)

    data class Slot(val state: State, val count: Int = 0)

    enum class State {
        ACTIVE,
        INACTIVE,
        SAMPLED_QUIET,
        UNKNOWN,

        /** Later today. */
        NOT_YET,
    }

    /** The most aircraft in any one slot, zero when nothing was seen. */
    val maxCount: Int
        get() = days.maxOfOrNull { day -> day.slots.maxOf { it.count } } ?: 0

    val hasData: Boolean
        get() = days.any { day -> day.slots.any { it.state != State.UNKNOWN && it.state != State.NOT_YET } }

    companion object {
        const val DAYS = 7
        const val HOURS_PER_DAY = 24

        /** Where the timeline for a grid at [now] has to start. */
        fun startOf(now: Instant, zone: ZoneId, days: Int = DAYS): Instant =
            now.atZone(zone).toLocalDate().minusDays(days - 1L).atStartOfDay(zone).toInstant()

        /** The next moment the grid can change on its own: a new UTC hour or a new local hour. */
        fun nextRefresh(now: Instant, zone: ZoneId): Instant {
            val nextUtcHour = now.truncatedTo(ChronoUnit.HOURS).plus(1, ChronoUnit.HOURS)
            val nextLocalHour = ZonedDateTime.of(
                now.atZone(zone).toLocalDateTime().truncatedTo(ChronoUnit.HOURS).plusHours(1),
                zone,
            ).toInstant()
            return if (nextLocalHour.isAfter(now)) minOf(nextUtcHour, nextLocalHour) else nextUtcHour
        }

        fun build(
            timeline: WatchActivityTimeline,
            zone: ZoneId,
            now: Instant,
            days: Int = DAYS,
        ): WatchActivityGrid {
            val currentHour = now.atZone(zone).toLocalDateTime().truncatedTo(ChronoUnit.HOURS)
            val first = currentHour.toLocalDate().minusDays(days - 1L)
            val rows = List(days) { row ->
                val date = first.plusDays(row.toLong())
                Array(HOURS_PER_DAY) { hour ->
                    // Compared as local times, so on the day the clocks go back an hour already seen stays
                    val upcoming = LocalDateTime.of(date, LocalTime.of(hour, 0)).isAfter(currentHour)
                    Slot(if (upcoming) State.NOT_YET else State.UNKNOWN)
                }
            }

            timeline.cells.forEachIndexed { index, cell ->
                val local = timeline.hourAt(index).atZone(zone).toLocalDateTime()
                val row = ChronoUnit.DAYS.between(first, local.toLocalDate()).toInt()
                if (row !in rows.indices) return@forEachIndexed
                val slots = rows[row]
                // When the clocks go back, two UTC hours share one local hour
                slots[local.hour] = merge(slots[local.hour], Slot(cell.toState(), timeline.counts[index]))
            }

            return WatchActivityGrid(
                days = rows.mapIndexed { row, slots -> Day(first.plusDays(row.toLong()), slots.toList()) },
            )
        }

        private fun Cell.toState(): State = when (this) {
            Cell.ACTIVE -> State.ACTIVE
            Cell.INACTIVE -> State.INACTIVE
            Cell.SAMPLED_QUIET -> State.SAMPLED_QUIET
            Cell.UNKNOWN -> State.UNKNOWN
        }

        /** The stronger evidence wins, earlier [State] entries first. Counterpart: WatchActivityTimeline.build */
        private fun merge(a: Slot, b: Slot): Slot = Slot(
            state = if (a.state.ordinal <= b.state.ordinal) a.state else b.state,
            count = maxOf(a.count, b.count),
        )
    }
}
