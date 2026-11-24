package eu.darken.apl.watch.ui.chart

import eu.darken.apl.common.chart.ChartPoint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Hourly peaks of a counting watch, folded into one peak per local day for the last [DAYS] days. */
data class WatchDailyPeaks(
    val days: List<Day>,
    val busiestHour: ChartPoint?,
) {
    /** [peak] is null when no hour of [date] was evaluated. */
    data class Day(val date: LocalDate, val peak: Double?)

    val todayIndex: Int get() = days.lastIndex

    val hasValues: Boolean get() = days.any { it.peak != null }

    companion object {
        const val DAYS = 30

        fun build(counts: List<ChartPoint>, now: Instant, zone: ZoneId): WatchDailyPeaks {
            val today = now.atZone(zone).toLocalDate()
            val first = today.minusDays(DAYS - 1L)
            val inWindow = counts
                .map { it to it.timestamp.atZone(zone).toLocalDate() }
                .filter { (_, date) -> !date.isBefore(first) && !date.isAfter(today) }

            val peaks = inWindow
                .groupBy({ (_, date) -> date }, { (point, _) -> point.value })
                .mapValues { (_, values) -> values.max() }

            val days = (0 until DAYS).map { offset ->
                val date = first.plusDays(offset.toLong())
                Day(date, peaks[date])
            }

            val busiest = inWindow
                .map { (point, _) -> point }
                .maxWithOrNull(compareBy<ChartPoint> { it.value }.thenBy { it.timestamp })

            return WatchDailyPeaks(days, busiest)
        }
    }
}
