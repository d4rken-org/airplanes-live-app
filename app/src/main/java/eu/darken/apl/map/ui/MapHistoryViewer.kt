package eu.darken.apl.map.ui

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.twotone.KeyboardArrowRight
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.Pause
import androidx.compose.material.icons.twotone.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import eu.darken.apl.R
import eu.darken.apl.map.core.HistoryTimeline
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** The day shown and the time at the cursor, with the way back to the live map. */
@Composable
internal fun HistoryDateBar(
    view: NativeMapViewModel.HistoryView,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formats = rememberHistoryFormats()
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 4.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious, enabled = view.canGoBack) {
                Icon(
                    Icons.AutoMirrored.TwoTone.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.map_history_previous_day),
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = view.day.date.format(formats.date),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                )
                val time = Instant.ofEpochMilli(view.cursor).atZone(view.timeline.zone)
                // The offset tells the repeated hour of a clock change apart
                val zone = time.offset.takeIf { it != ZoneOffset.UTC }?.id?.let { "UTC$it" } ?: "UTC"
                Text(
                    text = listOfNotNull("${time.format(formats.time)} $zone", view.callsign).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onNext, enabled = view.canGoForward) {
                Icon(
                    Icons.AutoMirrored.TwoTone.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.map_history_next_day),
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.TwoTone.Close, contentDescription = stringResource(R.string.map_history_close))
            }
        }
    }
}

/** The playback controls above the timeline, which gets the whole width below them. */
@Composable
internal fun HistoryScrubber(
    view: NativeMapViewModel.HistoryView,
    follow: Boolean,
    onSeek: (Long) -> Unit,
    onTogglePlaying: () -> Unit,
    onCycleSpeed: () -> Unit,
    onToggleFollow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HistoryControls(
            view = view,
            follow = follow,
            onTogglePlaying = onTogglePlaying,
            onCycleSpeed = onCycleSpeed,
            onToggleFollow = onToggleFollow,
        )
        HistoryDayTimeline(view = view, onSeek = onSeek)
    }
}

/** Play and speed in one pill, follow beside it; follow drops its label where the row does not fit. */
@Composable
private fun HistoryControls(
    view: NativeMapViewModel.HistoryView,
    follow: Boolean,
    onTogglePlaying: () -> Unit,
    onCycleSpeed: () -> Unit,
    onToggleFollow: () -> Unit,
) {
    val speedDescription = stringResource(R.string.map_history_speed_description, view.speed)

    @Composable
    fun ControlsRow(compact: Boolean) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 4.dp,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClick = onTogglePlaying),
                    ) {
                        Icon(
                            if (view.playing) Icons.TwoTone.Pause else Icons.TwoTone.PlayArrow,
                            contentDescription = stringResource(if (view.playing) R.string.map_history_pause else R.string.map_history_play),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Text(
                        text = stringResource(R.string.map_history_speed, view.speed),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClick = onCycleSpeed)
                            .semantics { contentDescription = speedDescription }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
            FollowToggle(
                follow = follow,
                compact = compact,
                onToggle = onToggleFollow,
            )
        }
    }

    SubcomposeLayout { constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        var row = subcompose("full") { ControlsRow(compact = false) }.first()
            .measure(loose.copy(maxWidth = Constraints.Infinity))
        if (row.width > constraints.maxWidth) row = subcompose("compact") { ControlsRow(compact = true) }.first().measure(loose)
        layout(row.width, row.height) { row.placeRelative(0, 0) }
    }
}

/**
 * The shown day from 00:00 to 24:00: bars where the aircraft has positions, labelled with its
 * callsign, ticks for single sightings, and shading where nothing could be seen. Tap or drag to
 * move the cursor.
 */
@Composable
private fun HistoryDayTimeline(
    view: NativeMapViewModel.HistoryView,
    onSeek: (Long) -> Unit,
) {
    val formats = rememberHistoryFormats()
    val currentView by rememberUpdatedState(view)
    val currentOnSeek by rememberUpdatedState(onSeek)
    val measurer = rememberTextMeasurer()
    val colors = MaterialTheme.colorScheme
    val labelStyle = MaterialTheme.typography.labelSmall
    val timelineLabel = stringResource(R.string.map_history_timeline)

    fun seekTo(x: Float, width: Int) {
        val day = currentView.day
        val fraction = (x / width).coerceIn(0f, 1f)
        currentOnSeek(day.start + ((day.end - day.start) * fraction).toLong())
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colors.surfaceContainerHigh,
        shadowElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val day = view.day
            val fraction = ((view.cursor - day.start).toFloat() / (day.end - day.start)).coerceIn(0f, 1f)
            val timeLabel = Instant.ofEpochMilli(view.cursor).atZone(view.timeline.zone).format(formats.time)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .pointerInput(Unit) { detectTapGestures { seekTo(it.x, size.width) } }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { seekTo(it.x, size.width) },
                            onHorizontalDrag = { change, _ ->
                                change.consume()
                                seekTo(change.position.x, size.width)
                            },
                        )
                    }
                    .semantics {
                        contentDescription = timelineLabel
                        stateDescription = timeLabel
                        progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                        setProgress { value ->
                            currentOnSeek(day.start + ((day.end - day.start) * value).toLong())
                            true
                        }
                    },
            ) {
                val span = (day.end - day.start).toFloat()
                fun x(time: Long) = (time - day.start) / span * size.width
                val barTop = size.height * 0.45f
                val barHeight = size.height * 0.45f
                drawRoundRect(
                    color = colors.surfaceVariant,
                    topLeft = Offset(0f, barTop),
                    size = Size(size.width, barHeight),
                    cornerRadius = CornerRadius(barHeight / 2),
                )
                day.spans.forEach { gap ->
                    val left = x(gap.start)
                    val width = (x(gap.end) - left).coerceAtLeast(1f)
                    val color = when (gap.kind) {
                        HistoryTimeline.SpanKind.PARTIAL -> colors.onSurfaceVariant.copy(alpha = 0.12f)
                        HistoryTimeline.SpanKind.NOT_RECORDED -> colors.error.copy(alpha = 0.25f)
                        HistoryTimeline.SpanKind.OMITTED -> colors.tertiary.copy(alpha = 0.3f)
                        HistoryTimeline.SpanKind.OUTSIDE -> colors.onSurfaceVariant.copy(alpha = 0.35f)
                    }
                    drawRect(color, topLeft = Offset(left, barTop), size = Size(width, barHeight))
                    if (gap.kind == HistoryTimeline.SpanKind.NOT_RECORDED) hatch(left, barTop, width, barHeight, color)
                }
                var labelsEnd = Float.NEGATIVE_INFINITY
                day.stretches.forEach { stretch ->
                    val left = x(stretch.start)
                    val width = (x(stretch.end) - left).coerceAtLeast(2f)
                    drawRoundRect(
                        color = colors.primary,
                        topLeft = Offset(left, barTop),
                        size = Size(width, barHeight),
                        cornerRadius = CornerRadius(barHeight / 4),
                    )
                    val callsign = stretch.callsign ?: return@forEach
                    val text = measurer.measure(callsign, style = labelStyle.copy(color = colors.onSurface))
                    // Starts at its bar, unless that runs it off the end; skipped where it would cover the one before
                    val labelX = left.coerceAtMost(size.width - text.size.width)
                    if (labelX >= labelsEnd + 4.dp.toPx() && labelX >= 0f) {
                        drawText(text, topLeft = Offset(labelX, (barTop - text.size.height).coerceAtLeast(0f)))
                        labelsEnd = labelX + text.size.width
                    }
                }
                day.sightings.forEach { sighting ->
                    val at = x(sighting.observedAt)
                    drawLine(colors.primary, Offset(at, barTop), Offset(at, barTop + barHeight), strokeWidth = 2.dp.toPx())
                }
                val cursorX = fraction * size.width
                drawLine(colors.onSurface, Offset(cursorX, 0f), Offset(cursorX, size.height), strokeWidth = 2.dp.toPx())
                drawCircle(colors.onSurface, radius = 5.dp.toPx(), center = Offset(cursorX, barTop + barHeight / 2))
            }
            HourLabels(view, labelStyle, formats)
        }
    }
}

/** 00, 06, 12, 18 and 24 at their real place, a clock change moves them. */
@Composable
private fun HourLabels(view: NativeMapViewModel.HistoryView, style: TextStyle, formats: HistoryFormats) {
    val measurer = rememberTextMeasurer()
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    // Tall enough for the labels at any font scale
    val height = with(LocalDensity.current) { measurer.measure(formats.endOfDay, style).size.height.toDp() }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val day = view.day
        val span = (day.end - day.start).toFloat()
        (0..4).forEach { index ->
            val time = if (index == 4) day.end else day.date.atTime(index * 6, 0).atZone(view.timeline.zone).toInstant().toEpochMilli()
            val label = if (index == 4) formats.endOfDay else Instant.ofEpochMilli(time).atZone(view.timeline.zone).format(formats.hour)
            val text = measurer.measure(label, style = style.copy(color = color))
            val x = ((time - day.start) / span * size.width - text.size.width / 2f)
                .coerceIn(0f, (size.width - text.size.width).coerceAtLeast(0f))
            drawText(text, topLeft = Offset(x, 0f))
        }
    }
}

private fun DrawScope.hatch(left: Float, top: Float, width: Float, height: Float, color: Color) {
    val step = 6.dp.toPx()
    clipRect(left, top, left + width, top + height) {
        var x = left - height
        while (x < left + width) {
            drawLine(color, Offset(x, top + height), Offset(x + height, top), strokeWidth = 1.dp.toPx())
            x += step
        }
    }
}

private class HistoryFormats(
    val date: DateTimeFormatter,
    val time: DateTimeFormatter,
    val hour: DateTimeFormatter,
    val endOfDay: String,
)

@Composable
private fun rememberHistoryFormats(): HistoryFormats {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val is24Hour = DateFormat.is24HourFormat(context)
    return remember(locale, is24Hour) {
        HistoryFormats(
            date = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), locale),
            time = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, if (is24Hour) "Hm" else "hm"), locale),
            hour = DateTimeFormatter.ofPattern(if (is24Hour) "HH" else DateFormat.getBestDateTimePattern(locale, "ha"), locale),
            endOfDay = if (is24Hour) "24" else DateFormat.getBestDateTimePattern(locale, "ha").let {
                java.time.LocalTime.MIDNIGHT.format(DateTimeFormatter.ofPattern(it, locale))
            },
        )
    }
}

@Composable
internal fun HistoryProBadge(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Text(
                text = stringResource(R.string.upgrade_tier_chip_pro),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}
