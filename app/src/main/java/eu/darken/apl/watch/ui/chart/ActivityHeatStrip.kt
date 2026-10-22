package eu.darken.apl.watch.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import eu.darken.apl.R
import eu.darken.apl.common.time.bestDateTimeFormatter
import eu.darken.apl.watch.core.history.WatchActivityTimeline
import eu.darken.apl.watch.core.history.WatchActivityTimeline.Cell
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * One segment per hour. An hour only the app's checks looked at is drawn weaker than one the
 * server's history covered completely, and an hour nobody looked at stays a faint gap.
 */
@Composable
fun ActivityHeatStrip(
    timeline: WatchActivityTimeline,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier,
    showAxis: Boolean = false,
) {
    if (!timeline.hasData) {
        if (showAxis) {
            Text(
                text = stringResource(R.string.watch_chart_no_data),
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Box(modifier)
        }
        return
    }

    val quietColor = inactiveColor.copy(alpha = 0.45f)
    val unknownColor = inactiveColor.copy(alpha = 0.12f)

    if (showAxis) {
        Column(modifier = modifier) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                drawStrip(timeline, activeColor, inactiveColor, quietColor, unknownColor)
            }
            HeatStripAxis(
                since = timeline.from,
                until = timeline.hourAt(timeline.cells.size),
            )
        }
    } else {
        Canvas(modifier = modifier) {
            drawStrip(timeline, activeColor, inactiveColor, quietColor, unknownColor)
        }
    }
}

private fun DrawScope.drawStrip(
    timeline: WatchActivityTimeline,
    activeColor: Color,
    inactiveColor: Color,
    quietColor: Color,
    unknownColor: Color,
) {
    val count = timeline.cells.size
    if (count == 0) return
    val cellWidth = size.width / count
    timeline.cells.forEachIndexed { index, cell ->
        val color = when (cell) {
            Cell.ACTIVE -> activeColor
            Cell.INACTIVE -> inactiveColor
            Cell.SAMPLED_QUIET -> quietColor
            Cell.UNKNOWN -> unknownColor
        }
        drawRect(
            color = color,
            topLeft = Offset(index * cellWidth, 0f),
            // Overlapping by a pixel keeps neighbouring cells from showing a seam
            size = Size((cellWidth + 1f).coerceAtLeast(1f), size.height),
        )
    }
}

@Composable
private fun HeatStripAxis(since: Instant, until: Instant) {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) {
        bestDateTimeFormatter(locale, "MMMd").withZone(ZoneId.systemDefault())
    }
    val labels = remember(since, until, formatter) {
        val count = 4
        val step = Duration.between(since, until).dividedBy(count.toLong())
        (0..count).map { i -> formatter.format(since.plus(step.multipliedBy(i.toLong()))) }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        labels.forEach { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
