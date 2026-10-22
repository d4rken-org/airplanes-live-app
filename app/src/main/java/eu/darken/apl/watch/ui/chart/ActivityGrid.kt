package eu.darken.apl.watch.ui.chart

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.darken.apl.R
import eu.darken.apl.common.compose.Preview2
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.time.bestDateTimeFormatter
import eu.darken.apl.watch.core.history.WatchActivityGrid
import eu.darken.apl.watch.core.history.WatchActivityGrid.State
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil

/**
 * One row per day, today last, one cell per local hour. With [shadeByCount] active cells get darker
 * the more aircraft they held, relative to the busiest hour shown.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActivityGrid(
    grid: WatchActivityGrid,
    shadeByCount: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier,
) {
    val colors = GridColors(activeColor, inactiveColor)
    val maxCount = grid.maxCount
    val today = grid.days.last().date
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        grid.days.forEach { day ->
            val label = if (day.date == today) {
                stringResource(R.string.watch_details_activity_today)
            } else {
                day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            }
            val description = rowDescription(day, dateFormat.format(day.date), shadeByCount)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clearAndSetSemantics { contentDescription = description },
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(LABEL_WIDTH),
                )
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(14.dp),
                ) {
                    val gap = 1.dp.toPx()
                    val cellWidth = (size.width - gap * (day.slots.size - 1)) / day.slots.size
                    day.slots.forEachIndexed { hour, slot ->
                        val color = colors.of(slot, if (shadeByCount) maxCount else 0)
                        if (color == Color.Transparent) return@forEachIndexed
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(hour * (cellWidth + gap), 0f),
                            size = Size(cellWidth, size.height),
                            cornerRadius = CornerRadius(1.dp.toPx()),
                        )
                    }
                }
            }
        }

        val hourFormat = rememberHourFormat()
        Row(modifier = Modifier.padding(start = LABEL_WIDTH)) {
            listOf(0, 6, 12, 18).forEach { hour ->
                Text(
                    text = LocalTime.of(hour, 0).format(hourFormat),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            if (shadeByCount && maxCount > 0) {
                thresholds(maxCount).forEach { (level, from) ->
                    LegendEntry(
                        color = colors.active(level),
                        label = stringResource(R.string.watch_details_activity_legend_count_x, from),
                    )
                }
            } else {
                LegendEntry(colors.active(LEVELS), stringResource(R.string.watch_details_activity_legend_seen))
            }
            LegendEntry(colors.inactive, stringResource(R.string.watch_details_activity_legend_quiet))
            LegendEntry(colors.sampled, stringResource(R.string.watch_details_activity_legend_sampled))
            LegendEntry(colors.unknown, stringResource(R.string.watch_details_activity_legend_unknown))
        }
    }
}

@Composable
private fun rowDescription(day: WatchActivityGrid.Day, date: String, shadeByCount: Boolean): String {
    val active = day.slots.count { it.state == State.ACTIVE }
    val peak = day.slots.maxOf { it.count }
    return if (shadeByCount && peak > 0) {
        pluralStringResource(R.plurals.watch_details_activity_row_peak_description, active, date, active, peak)
    } else {
        pluralStringResource(R.plurals.watch_details_activity_row_description, active, date, active)
    }
}

@Composable
private fun LegendEntry(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private class GridColors(private val activeColor: Color, inactiveColor: Color) {
    val inactive = inactiveColor
    val sampled = inactiveColor.copy(alpha = 0.45f)
    val unknown = inactiveColor.copy(alpha = 0.12f)

    /** Even the lowest level stays clearly apart from the quiet and unknown cells. */
    fun active(level: Int): Color = activeColor.copy(alpha = 0.35f + 0.65f * (level - 1) / (LEVELS - 1))

    fun of(slot: WatchActivityGrid.Slot, maxCount: Int): Color = when (slot.state) {
        State.ACTIVE -> active(if (maxCount > 0) levelOf(slot.count, maxCount) else LEVELS)
        State.INACTIVE -> inactive
        State.SAMPLED_QUIET -> sampled
        State.UNKNOWN -> unknown
        State.NOT_YET -> Color.Transparent
    }
}

private const val LEVELS = 4
private val LABEL_WIDTH = 40.dp

/** 1 to [LEVELS], any positive count reaches at least the first. */
private fun levelOf(count: Int, maxCount: Int): Int =
    ceil(LEVELS * count.coerceAtLeast(1).toDouble() / maxCount).toInt().coerceIn(1, LEVELS)

/** The smallest count of each level that some count can reach, e.g. max 12 gives 1+, 4+, 7+, 10+. */
private fun thresholds(maxCount: Int): List<Pair<Int, Int>> = (1..maxCount)
    .groupBy { levelOf(it, maxCount) }
    .map { (level, counts) -> level to counts.min() }
    .sortedBy { it.first }

private fun previewGrid(counting: Boolean): WatchActivityGrid {
    val today = LocalDate.of(2026, 10, 14)
    return WatchActivityGrid(
        days = (6 downTo 0).map { back ->
            WatchActivityGrid.Day(
                date = today.minusDays(back.toLong()),
                slots = List(24) { hour ->
                    when {
                        back == 0 && hour > 15 -> WatchActivityGrid.Slot(State.NOT_YET)
                        back == 6 && hour < 10 -> WatchActivityGrid.Slot(State.UNKNOWN)
                        counting && hour in 6..22 -> WatchActivityGrid.Slot(State.ACTIVE, (hour * 7 + back * 3) % 12 + 1)
                        !counting && hour in 7..9 && back % 2 == 0 -> WatchActivityGrid.Slot(State.ACTIVE, 1)
                        hour % 3 == 0 -> WatchActivityGrid.Slot(State.SAMPLED_QUIET)
                        else -> WatchActivityGrid.Slot(State.INACTIVE)
                    }
                },
            )
        },
    )
}

@Composable
private fun rememberHourFormat(): DateTimeFormatter {
    val locale = LocalConfiguration.current.locales[0]
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    return remember(locale, is24Hour) {
        bestDateTimeFormatter(locale, if (is24Hour) "HH" else "ha")
    }
}

@Preview2
@Composable
private fun ActivityGridPreview() {
    PreviewWrapper {
        ActivityGrid(
            grid = previewGrid(counting = false),
            shadeByCount = false,
            activeColor = MaterialTheme.colorScheme.primary,
            inactiveColor = MaterialTheme.colorScheme.outline,
        )
    }
}

@Preview2
@Composable
private fun ActivityGridCountsPreview() {
    PreviewWrapper {
        ActivityGrid(
            grid = previewGrid(counting = true),
            shadeByCount = true,
            activeColor = MaterialTheme.colorScheme.primary,
            inactiveColor = MaterialTheme.colorScheme.outline,
        )
    }
}
