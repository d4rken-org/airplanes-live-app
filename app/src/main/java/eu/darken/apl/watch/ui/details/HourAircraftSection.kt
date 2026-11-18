package eu.darken.apl.watch.ui.details

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.twotone.KeyboardArrowRight
import androidx.compose.material.icons.twotone.Close
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.darken.apl.R
import eu.darken.apl.common.compose.Preview2
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.time.bestDateTimeFormatter
import eu.darken.apl.watch.core.history.HourAircraft
import eu.darken.apl.watch.core.history.HourAircraftState
import eu.darken.apl.watch.core.history.HourAircraftState.NotAvailable.Reason
import eu.darken.apl.watch.core.history.HourCoverage
import java.time.Duration
import java.time.Instant
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

/** The aircraft that passed the watch in the hour picked in the activity grid. */
@Composable
internal fun HourAircraftSection(
    selection: WatchDetailsViewModel.HourSelection,
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

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onPreviousHour, enabled = selection.canStepBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.TwoTone.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.watch_details_hour_previous_action),
                )
            }
            IconButton(onClick = onNextHour, enabled = selection.canStepForward) {
                Icon(
                    imageVector = Icons.AutoMirrored.TwoTone.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.watch_details_hour_next_action),
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.TwoTone.Close,
                    contentDescription = stringResource(R.string.common_close_action),
                )
            }
        }
        HourNote(stringResource(R.string.watch_details_hour_note))

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
                    HourMessage(
                        pluralStringResource(
                            R.plurals.watch_details_hour_count_x,
                            aircraftState.aircraft.size,
                            aircraftState.aircraft.size,
                        )
                    )
                    HourQualityNotes(aircraftState.provisional, aircraftState.coverage)
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
private fun HourQualityNotes(provisional: Boolean, coverage: HourCoverage) {
    if (provisional) HourNote(stringResource(R.string.watch_details_hour_provisional))
    if (coverage != HourCoverage.FULL) HourNote(stringResource(R.string.watch_details_hour_coverage_incomplete))
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
    aircraft.take(shown).forEach { HourAircraftRow(it, timeFormat, zone, onAircraftClick) }
    if (aircraft.size > shown) {
        TextButton(onClick = onShowMore) { Text(stringResource(R.string.watch_details_hour_show_more_action)) }
    }
}

@Composable
private fun HourAircraftRow(
    aircraft: HourAircraft,
    timeFormat: DateTimeFormatter,
    zone: ZoneId,
    onClick: (HourAircraft) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(aircraft) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
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
        Text(
            text = aircraft.lastSeenAt.atZone(zone).format(timeFormat),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun HourMessage(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun HourNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Preview2
@Composable
private fun HourAircraftSectionPreview() {
    val hour = Instant.parse("2026-11-10T14:00:00Z")
    PreviewWrapper {
        HourAircraftSection(
            selection = WatchDetailsViewModel.HourSelection(
                range = hour.minus(Duration.ofDays(7))..hour.plus(Duration.ofHours(3)),
                selected = listOf(hour),
                aircraft = HourAircraftState.Complete(
                    aircraft = listOf(
                        HourAircraft("3C65A3", hour.plusSeconds(600), "DLH4AB", "D-AIBC", "A319", "Lufthansa"),
                        HourAircraft("4CA2D5", hour.plusSeconds(1_800), registration = "EI-DCL", aircraftType = "B738"),
                        HourAircraft("3C6752", hour.plusSeconds(3_000)),
                    ),
                    provisional = true,
                    coverage = HourCoverage.PARTIAL,
                ),
            ),
            onPreviousHour = {},
            onNextHour = {},
            onClose = {},
            onRetry = {},
            onAircraftClick = {},
        )
    }
}
