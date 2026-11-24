package eu.darken.apl.watch.ui.details

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.twotone.KeyboardArrowRight
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import eu.darken.apl.R
import eu.darken.apl.common.compose.Preview2
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.time.bestDateTimeFormatter
import eu.darken.apl.watch.core.history.HourAircraft
import eu.darken.apl.watch.core.history.HourAircraftState
import eu.darken.apl.watch.core.history.HourAircraftState.NotAvailable.Reason
import eu.darken.apl.watch.core.history.HourCoverage
import eu.darken.apl.watch.core.history.WatchActivityGrid
import eu.darken.apl.watch.ui.chart.GridColumn
import eu.darken.apl.watch.ui.chart.columnOf
import eu.darken.apl.watch.ui.chart.gridRowGeometry
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val HOUR_ROWS_PAGE = 50

/** The local time span of the UTC hours [first] to [last], e.g. "Tue 14:00–15:00", or "Tue 14:30–15:30" in India. */
internal fun hourRangeText(context: Context, first: Instant, last: Instant, zone: ZoneId): String {
    val locale = context.resources.configuration.locales[0]
    val time = bestDateTimeFormatter(locale, if (DateFormat.is24HourFormat(context)) "Hm" else "hm")
    val start = first.atZone(zone)
    val end = last.plus(WatchDetailsViewModel.HourSelection.HOUR).atZone(zone)
    return context.getString(
        R.string.watch_details_hour_range_x,
        DateTimeFormatter.ofPattern("EEE", locale).format(start),
        time.format(start),
        time.format(end),
    )
}

/**
 * The aircraft that passed the watch in the hour picked in the activity grid, in a card as wide as [grid]'s rows
 * whose caret points at the picked cell.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HourAircraftSection(
    selection: WatchDetailsViewModel.HourSelection,
    grid: WatchActivityGrid?,
    onPreviousHour: () -> Unit,
    onNextHour: () -> Unit,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onAircraftClick: (HourAircraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    val aircraftState = selection.aircraft ?: return
    val first = selection.selected.firstOrNull() ?: return
    val last = selection.selected.last()
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val is24Hour = DateFormat.is24HourFormat(context)
    val zone = ZoneId.systemDefault()
    val title = remember(first, last, locale, is24Hour, zone) { hourRangeText(context, first, last, zone) }
    val timeFormat = remember(locale, is24Hour) { bestDateTimeFormatter(locale, if (is24Hour) "Hm" else "hm") }
    var shown by rememberSaveable(selection.selected) { mutableIntStateOf(HOUR_ROWS_PAGE) }
    val shape = remember(grid, selection.selectedSlot) { CaretCardShape(grid?.columnOf(selection.selectedSlot)) }

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
            .padding(start = 12.dp, end = 12.dp, top = CARET_HEIGHT + 8.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                HourStepper(
                    title = title,
                    selection = selection,
                    onPreviousHour = onPreviousHour,
                    onNextHour = onNextHour,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                if (aircraftState is HourAircraftState.Complete && aircraftState.aircraft.isNotEmpty()) {
                    CountPill(
                        text = pluralStringResource(
                            R.plurals.watch_details_hour_count_x,
                            aircraftState.aircraft.size,
                            aircraftState.aircraft.size,
                        ),
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.TwoTone.Close,
                    contentDescription = stringResource(R.string.common_close_action),
                )
            }
        }
        HourNote(stringResource(R.string.watch_details_hour_note), icon = true, modifier = Modifier.padding(top = 8.dp))

        when (aircraftState) {
            is HourAircraftState.Loading -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = pluralStringResource(
                            R.plurals.watch_details_hour_loading_x,
                            aircraftState.aircraft.size,
                            aircraftState.aircraft.size,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                HourQualityNotes(aircraftState.provisional, aircraftState.coverage)
                HourAircraftRows(aircraftState.aircraft, shown, timeFormat, zone, onAircraftClick) { shown += HOUR_ROWS_PAGE }
            }

            is HourAircraftState.Complete -> {
                if (aircraftState.aircraft.isEmpty()) {
                    HourMessage(
                        stringResource(
                            if (aircraftState.coverage == HourCoverage.FULL) R.string.watch_details_hour_empty
                            else R.string.watch_details_hour_empty_incomplete
                        )
                    )
                    HourQualityNotes(aircraftState.provisional, HourCoverage.FULL)
                } else {
                    HourQualityNotes(aircraftState.provisional, aircraftState.coverage)
                    Spacer(Modifier.height(8.dp))
                    HourAircraftRows(aircraftState.aircraft, shown, timeFormat, zone, onAircraftClick) { shown += HOUR_ROWS_PAGE }
                }
            }

            HourAircraftState.Changed -> {
                HourMessage(stringResource(R.string.watch_details_hour_changed))
                TextButton(onClick = onRetry) { Text(stringResource(R.string.watch_details_hour_retry_action)) }
            }

            is HourAircraftState.NotAvailable -> HourMessage(
                stringResource(
                    when (aircraftState.reason) {
                        Reason.OUT_OF_RANGE -> R.string.watch_details_hour_out_of_range
                        Reason.TIER_RESTRICTED, Reason.LIST_DELETED -> R.string.watch_details_hour_not_available
                        Reason.INSTALLATION_RESTRICTED, Reason.INSTALLATION_REVOKED -> R.string.watch_details_hour_restricted
                    }
                )
            )

            is HourAircraftState.Failed -> {
                HourMessage(stringResource(R.string.watch_details_hour_failed))
                TextButton(onClick = onRetry) { Text(stringResource(R.string.watch_details_hour_retry_action)) }
            }
        }
    }
}

@Composable
private fun HourStepper(
    title: String,
    selection: WatchDetailsViewModel.HourSelection,
    onPreviousHour: () -> Unit,
    onNextHour: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
    ) {
        IconButton(onClick = onPreviousHour, enabled = selection.canStepBack) {
            Icon(
                imageVector = Icons.AutoMirrored.TwoTone.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.watch_details_hour_previous_action),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        IconButton(onClick = onNextHour, enabled = selection.canStepForward) {
            Icon(
                imageVector = Icons.AutoMirrored.TwoTone.KeyboardArrowRight,
                contentDescription = stringResource(R.string.watch_details_hour_next_action),
            )
        }
    }
}

@Composable
private fun CountPill(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        maxLines = 1,
        modifier = modifier
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun HourQualityNotes(provisional: Boolean, coverage: HourCoverage) {
    if (provisional) HourNote(stringResource(R.string.watch_details_hour_provisional), icon = false)
    if (coverage != HourCoverage.FULL) {
        HourNote(stringResource(R.string.watch_details_hour_coverage_incomplete), icon = false)
    }
}

@Composable
private fun HourAircraftRows(
    aircraft: List<HourAircraft>,
    shown: Int,
    timeFormat: DateTimeFormatter,
    zone: ZoneId,
    onAircraftClick: (HourAircraft) -> Unit,
    onShowMore: () -> Unit,
) {
    val visible = aircraft.take(shown)
    val times = visible.map { it.lastSeenAt.atZone(zone).format(timeFormat) }
    val timeStyle = MaterialTheme.typography.labelLarge.copy(
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = "tnum",
    )
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // As wide as the widest time shown, so 12-hour times and large fonts fit and all rows line up
    val timeWidth = remember(times, timeStyle, measurer, density) {
        val widest = times.maxOfOrNull { measurer.measure(it, timeStyle, maxLines = 1, softWrap = false).size.width }
        with(density) { (widest ?: 0).toDp() }.coerceAtLeast(MIN_TIME_WIDTH)
    }
    visible.forEachIndexed { index, item ->
        HourAircraftRow(
            aircraft = item,
            time = times[index],
            timeStyle = timeStyle,
            timeWidth = timeWidth,
            isFirst = index == 0,
            isLast = index == visible.lastIndex,
            onClick = onAircraftClick,
        )
    }
    if (aircraft.size > shown) {
        TextButton(onClick = onShowMore) { Text(stringResource(R.string.watch_details_hour_show_more_action)) }
    }
}

@Composable
private fun HourAircraftRow(
    aircraft: HourAircraft,
    time: String,
    timeStyle: TextStyle,
    timeWidth: Dp,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: (HourAircraft) -> Unit,
) {
    val dotColor = MaterialTheme.colorScheme.primary
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable { onClick(aircraft) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = time,
            style = timeStyle,
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(timeWidth),
        )
        Canvas(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .width(12.dp)
                .fillMaxHeight(),
        ) {
            val center = Offset(size.width / 2, size.height / 2)
            val top = if (isFirst) center.y else 0f
            val bottom = if (isLast) center.y else size.height
            if (bottom > top) {
                drawLine(lineColor, Offset(center.x, top), Offset(center.x, bottom), strokeWidth = 2.dp.toPx())
            }
            drawCircle(dotColor, radius = 4.dp.toPx(), center = center)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
        ) {
            Text(
                text = aircraft.callsign?.takeIf { it.isNotBlank() } ?: "#${aircraft.hex}",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val details = listOfNotNull(aircraft.registration, aircraft.aircraftType, aircraft.airlineName)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.TwoTone.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HourMessage(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
}

/** Without [icon] the text still lines up with that of a note with one. */
@Composable
private fun HourNote(text: String, icon: Boolean, modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
        if (icon) {
            Icon(
                imageVector = Icons.TwoTone.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(NOTE_ICON_SIZE),
            )
        } else {
            Spacer(Modifier.width(NOTE_ICON_SIZE))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A rounded card with a caret on its top edge over [column]'s cell, none without one. */
private data class CaretCardShape(private val column: GridColumn?) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        with(density) {
            val radius = CARD_CORNER.toPx()
            val caretHeight = CARET_HEIGHT.toPx()
            val halfCaret = CARET_WIDTH.toPx() / 2
            val card = Path().apply {
                addRoundRect(RoundRect(0f, caretHeight, size.width, size.height, CornerRadius(radius)))
            }
            if (column == null) return@with Outline.Generic(card)

            val min = radius + halfCaret
            val max = size.width - radius - halfCaret
            val x = gridRowGeometry(size.width, column.slots, layoutDirection == LayoutDirection.Rtl)
                .cellCenter(column.index)
                .let { if (max >= min) it.coerceIn(min, max) else size.width / 2 }
            val caret = Path().apply {
                moveTo(x - halfCaret, caretHeight)
                lineTo(x, 0f)
                lineTo(x + halfCaret, caretHeight)
                close()
            }
            Outline.Generic(Path.combine(PathOperation.Union, card, caret))
        }
}

private val CARD_CORNER = 16.dp
private val CARET_WIDTH = 12.dp
private val CARET_HEIGHT = 6.dp
private val NOTE_ICON_SIZE = 16.dp
private val MIN_TIME_WIDTH = 44.dp

private val PREVIEW_HOUR: Instant = Instant.parse("2026-11-10T14:00:00Z")

private val previewGrid = WatchActivityGrid(
    days = listOf(
        WatchActivityGrid.Day(
            date = LocalDate.of(2026, 11, 10),
            slots = List(24) { hour ->
                WatchActivityGrid.Slot(
                    state = WatchActivityGrid.State.INACTIVE,
                    hours = listOf(PREVIEW_HOUR.plus(Duration.ofHours(hour - 14L))),
                )
            },
        ),
    ),
)

private fun previewSelection(aircraft: HourAircraftState) = WatchDetailsViewModel.HourSelection(
    range = PREVIEW_HOUR.minus(Duration.ofDays(7))..PREVIEW_HOUR.plus(Duration.ofHours(3)),
    selected = listOf(PREVIEW_HOUR),
    selectedSlot = previewGrid.days.single().slots[14],
    aircraft = aircraft,
)

private val previewAircraft = listOf(
    HourAircraft("3C65A3", PREVIEW_HOUR.plusSeconds(600), "DLH4AB", "D-AIBC", "A319", "Lufthansa"),
    HourAircraft("4CA2D5", PREVIEW_HOUR.plusSeconds(1_800), registration = "EI-DCL", aircraftType = "B738"),
    HourAircraft("3C6752", PREVIEW_HOUR.plusSeconds(3_000)),
)

@Preview2
@Composable
private fun HourAircraftSectionPreview() {
    PreviewWrapper {
        HourAircraftSection(
            selection = previewSelection(
                HourAircraftState.Complete(
                    aircraft = previewAircraft,
                    provisional = true,
                    coverage = HourCoverage.PARTIAL,
                ),
            ),
            grid = previewGrid,
            onPreviousHour = {},
            onNextHour = {},
            onClose = {},
            onRetry = {},
            onAircraftClick = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview2
@Composable
private fun HourAircraftSectionLoadingPreview() {
    PreviewWrapper {
        HourAircraftSection(
            selection = previewSelection(
                HourAircraftState.Loading(
                    aircraft = previewAircraft.take(2),
                    provisional = false,
                    coverage = HourCoverage.FULL,
                ),
            ),
            grid = previewGrid,
            onPreviousHour = {},
            onNextHour = {},
            onClose = {},
            onRetry = {},
            onAircraftClick = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
