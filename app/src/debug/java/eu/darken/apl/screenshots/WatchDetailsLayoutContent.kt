package eu.darken.apl.screenshots

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import eu.darken.apl.common.chart.ChartPoint
import eu.darken.apl.watch.core.history.HourAircraft
import eu.darken.apl.watch.core.history.HourAircraftState
import eu.darken.apl.watch.core.history.HourCoverage
import eu.darken.apl.watch.core.history.WatchActivityGrid
import eu.darken.apl.watch.core.history.WatchActivityTimeline
import eu.darken.apl.watch.ui.chart.ActivityGrid
import eu.darken.apl.watch.ui.chart.WatchCountChart
import eu.darken.apl.watch.ui.details.HourAircraftSection
import eu.darken.apl.watch.ui.details.WatchDetailsViewModel
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** A Tuesday. */
internal val LAYOUT_NOW: Instant = Instant.parse("2026-11-24T10:00:00Z")

private val LAYOUT_HOUR: Duration = Duration.ofHours(1)
private const val SELECTED_ROW = 3

private fun layoutGrid(zone: ZoneId): WatchActivityGrid {
    val from = WatchActivityTimeline.hourOf(WatchActivityGrid.startOf(LAYOUT_NOW, zone))
    val hours = generateSequence(from) { it.plus(LAYOUT_HOUR) }.takeWhile { it <= LAYOUT_NOW }.toList()
    // The current hour has no history yet
    val counts = hours.dropLast(1).map { (it.epochSecond / 3600 * 7 % 11 - 2).toInt().coerceAtLeast(0) }
    val cells = counts.map { if (it > 0) WatchActivityTimeline.Cell.ACTIVE else WatchActivityTimeline.Cell.INACTIVE }
    val timeline = WatchActivityTimeline(
        from = from,
        cells = cells + WatchActivityTimeline.Cell.UNKNOWN,
        counts = counts + 0,
    )
    return WatchActivityGrid.build(timeline, zone, LAYOUT_NOW)
}

private fun layoutAircraft(hour: Instant) = listOf(
    HourAircraft("3C65A3", hour.plusSeconds(600), "DLH4AB", "D-AIBC", "A319", "Lufthansa"),
    HourAircraft("4CA2D5", hour.plusSeconds(1_800), registration = "EI-DCL", aircraftType = "B738"),
    HourAircraft("4ACA13", hour.plusSeconds(3_000), "SAS1453", "SE-ROM", "A20N", "Scandinavian Airlines System Denmark-Norway-Sweden"),
)

/** The activity grid with the card for the hour picked in its [selectedHour] column, as on the details sheet. */
@Composable
internal fun ActivityHourLayoutContent(selectedHour: Int, rtl: Boolean = false, loading: Boolean = false) {
    val grid = layoutGrid(ZoneId.systemDefault())
    val slot = grid.days[SELECTED_ROW].slots[selectedHour]
    val aircraft = layoutAircraft(slot.hours.first())
    val selection = WatchDetailsViewModel.HourSelection(
        range = WatchDetailsViewModel.HourSelection.rangeAt(LAYOUT_NOW),
        selected = slot.hours,
        selectedSlot = slot,
        aircraft = if (loading) {
            HourAircraftState.Loading(aircraft = aircraft.take(2), provisional = false, coverage = HourCoverage.FULL)
        } else {
            HourAircraftState.Complete(aircraft = aircraft, provisional = false, coverage = HourCoverage.FULL)
        },
    )
    ScreenshotWrapper {
        CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            Column(modifier = Modifier.padding(16.dp)) {
                ActivityGrid(
                    grid = grid,
                    shadeByCount = true,
                    activeColor = MaterialTheme.colorScheme.primary,
                    inactiveColor = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.fillMaxWidth(),
                    selectedSlot = slot,
                    isSelectable = { selection.isSelectable(it) },
                    onSlotClick = {},
                )
                HourAircraftSection(
                    selection = selection,
                    grid = grid,
                    onPreviousHour = {},
                    onNextHour = {},
                    onClose = {},
                    onRetry = {},
                    onAircraftClick = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
        }
    }
}

/** 30 days of hourly counts up to [now]: three days without data, one day of zeros, today only up to [now]. */
private fun layoutCounts(now: Instant, zone: ZoneId): List<ChartPoint> {
    val todayStart = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
    val missingDays = setOf(4, 5, 17)
    return (0 until 30).flatMap { daysAgo ->
        val dayStart = todayStart.minus(Duration.ofDays(daysAgo.toLong()))
        (0 until 24)
            .map { hour -> dayStart.plus(Duration.ofHours(hour.toLong())) }
            .filter { it <= now && daysAgo !in missingDays }
            .map { at ->
                val hour = at.atZone(zone).hour
                val value = if (daysAgo == 9) 0 else (hour * 5 + daysAgo * 11) % 17 + (if (hour in 7..21) 4 else 0)
                ChartPoint(at, value.toDouble())
            }
    }
}

@Composable
internal fun CountChartLayoutContent(now: Instant) {
    val zone = ZoneId.of("Europe/Berlin")
    ScreenshotWrapper {
        WatchCountChart(
            counts = layoutCounts(now, zone),
            now = now,
            zone = zone,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
    }
}
