package eu.darken.apl.watch.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.apl.R
import eu.darken.apl.watch.core.history.WatchActivityCheck
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ActivityHeatStrip(
    checks: List<WatchActivityCheck>,
    since: Instant,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier,
    showAxis: Boolean = false,
) {
    if (checks.size < 2) {
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

    val now = remember { Instant.now() }
    val rangeMs = (now.toEpochMilli() - since.toEpochMilli()).toFloat()

    if (showAxis) {
        Column(modifier = modifier) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                drawStrip(checks, since, now, rangeMs, activeColor, inactiveColor)
            }
            HeatStripAxis(since = since, now = now)
        }
    } else {
        Canvas(modifier = modifier) {
            drawStrip(checks, since, now, rangeMs, activeColor, inactiveColor)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStrip(
    checks: List<WatchActivityCheck>,
    since: Instant,
    now: Instant,
    rangeMs: Float,
    activeColor: Color,
    inactiveColor: Color,
) {
    if (rangeMs <= 0f) return

    val sorted = checks.sortedBy { it.timestamp }

    for (i in sorted.indices) {
        val check = sorted[i]
        val tMs = check.timestamp.toEpochMilli()
        if (tMs < since.toEpochMilli() || tMs > now.toEpochMilli()) continue

        // Segment extends from midpoint to previous neighbor to midpoint to next neighbor
        val prevMs = if (i > 0) sorted[i - 1].timestamp.toEpochMilli() else since.toEpochMilli()
        val nextMs = if (i < sorted.size - 1) sorted[i + 1].timestamp.toEpochMilli() else now.toEpochMilli()

        val halfLeft = (tMs - prevMs) / 2f
        val halfRight = (nextMs - tMs) / 2f

        val startMs = (tMs - halfLeft).coerceAtLeast(since.toEpochMilli().toFloat())
        val endMs = (tMs + halfRight).coerceAtMost(now.toEpochMilli().toFloat())

        val xStart = ((startMs - since.toEpochMilli()) / rangeMs) * size.width
        val xEnd = ((endMs - since.toEpochMilli()) / rangeMs) * size.width

        val color = if (check.aircraftCount > 0) activeColor else inactiveColor

        drawRect(
            color = color,
            topLeft = Offset(xStart, 0f),
            size = Size((xEnd - xStart).coerceAtLeast(1f), size.height),
        )
    }
}

@Composable
private fun HeatStripAxis(since: Instant, now: Instant) {
    val formatter = remember { DateTimeFormatter.ofPattern("MMM d").withZone(ZoneId.systemDefault()) }
    val labels = remember(since, now) {
        val count = 4
        val stepMs = (now.toEpochMilli() - since.toEpochMilli()) / count
        (0..count).map { i ->
            formatter.format(Instant.ofEpochMilli(since.toEpochMilli() + stepMs * i))
        }
    }
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
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
