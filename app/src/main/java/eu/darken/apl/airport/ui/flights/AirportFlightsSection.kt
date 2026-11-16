package eu.darken.apl.airport.ui.flights

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.FlightLand
import androidx.compose.material.icons.twotone.FlightTakeoff
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.darken.apl.R
import eu.darken.apl.airport.ui.flights.AirportFlightsDisplay.Basis
import eu.darken.apl.airport.ui.flights.AirportFlightsDisplay.EventTime
import eu.darken.apl.airport.ui.flights.AirportFlightsDisplay.FlightStatus
import eu.darken.apl.airport.ui.flights.AirportFlightsDisplay.Identity
import eu.darken.apl.airport.ui.flights.AirportFlightsDisplay.OtherAirport
import eu.darken.apl.airport.ui.flights.AirportFlightsDisplay.Row as FlightRowData
import eu.darken.apl.airport.ui.flights.AirportFlightsDisplay.Section
import eu.darken.apl.airport.ui.flights.AirportFlightsDisplay.SectionKind
import eu.darken.apl.airport.ui.flights.AirportFlightsDisplay.TimeKind
import eu.darken.apl.common.compose.Preview2
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.error.localized
import eu.darken.apl.common.time.bestDateTimeFormatter
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private const val KEY_PREFIX = "airport_flights"

/** The callbacks of [airportFlights] for a sheet that hands them on. */
internal class AirportFlightsActions(
    val onShowFlights: () -> Unit,
    val onRefresh: () -> Unit,
    val onShowMore: (String) -> Unit,
    val onSelectSection: (SectionKind) -> Unit,
    val onRetry: () -> Unit,
    val onReload: () -> Unit,
    val onOpenAircraft: (String) -> Unit,
) {
    companion object {
        val None = AirportFlightsActions({}, {}, {}, {}, {}, {}, {})
    }
}

/** The flights section of an airport's details, [onShowMore] gets the wire direction to page. */
internal fun LazyListScope.airportFlights(
    state: AirportFlightsUiState,
    onShowFlights: () -> Unit,
    onRefresh: () -> Unit,
    onShowMore: (String) -> Unit,
    onSelectSection: (SectionKind) -> Unit,
    onRetry: () -> Unit,
    onReload: () -> Unit,
    onOpenAircraft: (String) -> Unit,
) {
    item(key = "${KEY_PREFIX}_header") {
        FlightsHeader(
            loaded = state as? AirportFlightsUiState.Loaded,
            showCaveat = state is AirportFlightsUiState.Ready ||
                state is AirportFlightsUiState.Loading ||
                state is AirportFlightsUiState.Loaded,
            onRefresh = onRefresh,
        )
    }

    val statusKey = "${KEY_PREFIX}_status"
    when (state) {
        AirportFlightsUiState.Resolving -> item(key = statusKey) {
            FlightsProgress(description = stringResource(R.string.airport_details_lookup_progress), label = null)
        }

        AirportFlightsUiState.NoList -> item(key = statusKey) {
            FlightsMessage(stringResource(R.string.airport_flights_no_list))
        }

        AirportFlightsUiState.Ready -> item(key = statusKey) {
            FilledTonalButton(
                onClick = onShowFlights,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            ) {
                Text(stringResource(R.string.airport_flights_show_action))
            }
        }

        AirportFlightsUiState.Loading -> item(key = statusKey) {
            val label = stringResource(R.string.airport_flights_loading)
            FlightsProgress(description = label, label = label)
        }

        is AirportFlightsUiState.Loaded -> loadedFlights(
            state = state,
            onShowMore = onShowMore,
            onSelectSection = onSelectSection,
            onRetry = onRetry,
            onReload = onReload,
            onOpenAircraft = onOpenAircraft,
        )

        AirportFlightsUiState.NotOffered -> item(key = statusKey) {
            FlightsMessage(stringResource(R.string.airport_flights_not_offered))
        }

        AirportFlightsUiState.UnknownAirport -> item(key = statusKey) {
            FlightsMessage(stringResource(R.string.airport_flights_unknown_airport))
        }

        AirportFlightsUiState.AllowanceExhausted -> item(key = statusKey) {
            FlightsMessage(stringResource(R.string.airport_flights_search_limit_reached))
        }

        AirportFlightsUiState.ReloadOffered -> item(key = statusKey) {
            FlightsMessage(
                text = stringResource(R.string.airport_flights_reload_offered),
                action = stringResource(R.string.airport_flights_reload_action),
                onAction = onReload,
            )
        }

        is AirportFlightsUiState.Waiting -> item(key = statusKey) {
            FlightsMessage(stringResource(R.string.airport_flights_waiting_x, clockText(state.until)))
        }

        is AirportFlightsUiState.Failed -> item(key = statusKey) {
            FlightsMessage(
                text = errorText(state.error),
                action = stringResource(R.string.airport_flights_retry_action).takeIf { state.canRetry },
                onAction = onRetry,
            )
        }

        AirportFlightsUiState.UpdateRequired -> item(key = statusKey) {
            FlightsMessage(stringResource(R.string.update_required_inline_msg))
        }

        AirportFlightsUiState.Restricted -> item(key = statusKey) {
            FlightsMessage(stringResource(R.string.upgrade_restricted_msg))
        }

        AirportFlightsUiState.Revoked -> item(key = statusKey) {
            FlightsMessage(stringResource(R.string.server_error_revoked_msg))
        }
    }
}

private fun LazyListScope.loadedFlights(
    state: AirportFlightsUiState.Loaded,
    onShowMore: (String) -> Unit,
    onSelectSection: (SectionKind) -> Unit,
    onRetry: () -> Unit,
    onReload: () -> Unit,
    onOpenAircraft: (String) -> Unit,
) {
    val display = state.display

    state.notice?.let { notice ->
        item(key = "${KEY_PREFIX}_notice") { FlightsNotice(notice, onRetry = onRetry, onReload = onReload) }
    }

    val section = display.sections.first { it.kind == state.selected }

    item(key = "${KEY_PREFIX}_segments") {
        SectionSegments(
            display = display,
            selected = state.selected,
            onSelectSection = onSelectSection,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
        )
    }

    items(section.rows, key = { "${KEY_PREFIX}_row_${it.id}" }) { row ->
        FlightRow(row = row, onOpenAircraft = onOpenAircraft)
    }

    item(key = "${KEY_PREFIX}_footer") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        ) {
            if (section.rows.isEmpty()) {
                Text(
                    text = stringResource(R.string.airport_flights_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
            section.showMoreDirection?.let { direction ->
                TextButton(
                    onClick = { onShowMore(direction) },
                    enabled = direction !in state.pagingDirections,
                ) {
                    Text(stringResource(R.string.airport_flights_show_more_action))
                }
            }
        }
    }
}

@Composable
private fun FlightsHeader(loaded: AirportFlightsUiState.Loaded?, showCaveat: Boolean, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.airport_flights_title),
                style = MaterialTheme.typography.titleMedium,
            )
            if (showCaveat) {
                Text(
                    text = stringResource(R.string.airport_flights_caveat),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (loaded != null) {
            val availableAt = loaded.refreshAvailableAt
            val available by produceState(availableAt == null || !Instant.now().isBefore(availableAt), availableAt) {
                if (availableAt == null) {
                    value = true
                    return@produceState
                }
                val wait = Duration.between(Instant.now(), availableAt).toMillis()
                if (wait > 0) {
                    value = false
                    delay(wait)
                }
                value = true
            }
            if (loaded.refreshing) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            }
            IconButton(
                onClick = onRefresh,
                enabled = loaded.refreshEnabled && !loaded.refreshing && available,
            ) {
                Icon(
                    imageVector = Icons.TwoTone.Refresh,
                    contentDescription = stringResource(R.string.common_refresh_action),
                )
            }
        }
    }
}

@Composable
private fun FlightsNotice(notice: AirportFlightsUiState.Notice, onRetry: () -> Unit, onReload: () -> Unit) {
    when (notice) {
        is AirportFlightsUiState.Notice.Failed -> FlightsMessage(
            text = errorText(notice.error),
            action = stringResource(R.string.airport_flights_retry_action).takeIf { notice.canRetry },
            onAction = onRetry,
            color = MaterialTheme.colorScheme.error,
        )

        AirportFlightsUiState.Notice.ReloadOffered -> FlightsMessage(
            text = stringResource(R.string.airport_flights_reload_offered),
            action = stringResource(R.string.airport_flights_reload_action),
            onAction = onReload,
        )

        AirportFlightsUiState.Notice.AllowanceExhausted -> FlightsMessage(
            text = stringResource(R.string.airport_flights_search_limit_reached),
        )

        is AirportFlightsUiState.Notice.Waiting -> FlightsMessage(
            text = stringResource(R.string.airport_flights_waiting_x, clockText(notice.until)),
        )
    }
}

@Composable
private fun SectionSegments(
    display: AirportFlightsDisplay,
    selected: SectionKind,
    onSelectSection: (SectionKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    val segments = SectionKind.entries
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        segments.forEachIndexed { index, kind ->
            val count = display.sections.first { it.kind == kind }.count
            SegmentedButton(
                selected = kind == selected,
                onClick = { onSelectSection(kind) },
                shape = SegmentedButtonDefaults.itemShape(index, segments.size),
                // Replaces the default icon, which is a check mark while selected (material3 1.4)
                icon = {
                    Icon(
                        imageVector = kind.icon,
                        contentDescription = null,
                        modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                    )
                },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(kind.label),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(text = count.toString(), softWrap = false, maxLines = 1)
                    }
                },
            )
        }
    }
}

@Composable
private fun FlightRow(row: FlightRowData, onOpenAircraft: (String) -> Unit, modifier: Modifier = Modifier) {
    val hex = row.aircraftHex
    val status = row.status?.let { stringResource(it.label) }
    // "XLA401 · A20N · Approaching"
    val details = listOfNotNull(identityText(row.identity), row.aircraftType, status).joinToString(" · ")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (hex != null) {
                    Modifier.clickable(role = Role.Button) { onOpenAircraft(hex) }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(
            text = routeText(row.otherAirport),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = details,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            row.time?.let { time ->
                Text(
                    text = timeText(time),
                    style = MaterialTheme.typography.bodyMedium,
                    softWrap = false,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun FlightsProgress(description: String, label: String?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier
                .size(16.dp)
                .semantics { contentDescription = description },
            strokeWidth = 2.dp,
        )
        if (label != null) {
            Spacer(Modifier.width(8.dp))
            Text(text = label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun FlightsMessage(
    text: String,
    action: String? = null,
    onAction: () -> Unit = {},
    color: Color = Color.Unspecified,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
        )
        if (action != null) {
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun errorText(error: Throwable): String {
    val context = LocalContext.current
    return remember(error, context) { error.localized(context).description.get(context) }
}

@Composable
private fun identityText(identity: Identity): String = when (identity) {
    // "XLD77 · XD77"
    is Identity.Flight -> listOfNotNull(identity.callsign, identity.number).distinct().joinToString(" · ")
    is Identity.Registration -> identity.registration
    is Identity.Hex -> identity.hex
    Identity.Unidentified -> stringResource(R.string.airport_flights_unidentified)
}

/** "🇩🇪 XYZ Example County International Airport" */
@Composable
private fun routeText(airport: OtherAirport?): String = airport
    ?.let { listOfNotNull(it.flag, it.code, it.name).joinToString(" ") }
    ?: stringResource(R.string.airport_flights_route_unknown)

@Composable
private fun timeText(time: EventTime): String {
    val clock = clockText(time.clock)
    return when (time.kind) {
        TimeKind.EXPECTED -> stringResource(R.string.airport_flights_time_estimated_x, clock)
        TimeKind.SCHEDULED, TimeKind.ACTUAL -> clock
    }
}

@Composable
private fun clockText(clock: ClockTime): String {
    val formats = rememberClockFormats()
    val time = formats.time.format(clock.local)
    return clock.date
        ?.let { stringResource(R.string.airport_flights_date_time_x, formats.date.format(it), time) }
        ?: time
}

private class ClockFormats(val time: DateTimeFormatter, val date: DateTimeFormatter)

@Composable
private fun rememberClockFormats(): ClockFormats {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val is24Hour = DateFormat.is24HourFormat(context)
    return remember(locale, is24Hour) {
        ClockFormats(
            time = bestDateTimeFormatter(locale, if (is24Hour) "Hm" else "hm"),
            date = bestDateTimeFormatter(locale, "EEEdMMM"),
        )
    }
}

private val SectionKind.label: Int
    get() = when (this) {
        SectionKind.ARRIVALS -> R.string.airport_flights_arrivals_title
        SectionKind.DEPARTURES -> R.string.airport_flights_departures_title
    }

private val SectionKind.icon: ImageVector
    get() = when (this) {
        SectionKind.ARRIVALS -> Icons.TwoTone.FlightLand
        SectionKind.DEPARTURES -> Icons.TwoTone.FlightTakeoff
    }

private val FlightStatus.label: Int
    get() = when (this) {
        FlightStatus.ON_GROUND -> R.string.airport_flights_status_on_ground
        FlightStatus.APPROACHING -> R.string.airport_flights_status_approaching
        FlightStatus.DEPARTED -> R.string.airport_flights_status_departed
        FlightStatus.EN_ROUTE -> R.string.airport_flights_status_en_route
        FlightStatus.SCHEDULED -> R.string.airport_flights_status_scheduled
        FlightStatus.DELAYED -> R.string.airport_flights_status_delayed
        FlightStatus.BOARDING -> R.string.airport_flights_status_boarding
        FlightStatus.LANDED -> R.string.airport_flights_status_landed
        FlightStatus.CANCELLED -> R.string.airport_flights_status_cancelled
        FlightStatus.DIVERTED -> R.string.airport_flights_status_diverted
    }

private fun previewClock(hour: Int, minute: Int) = ClockTime(
    local = LocalDateTime.of(2026, 11, 13, hour, minute),
    offset = ZoneOffset.ofHours(1),
    date = null,
)

private val previewDisplay = AirportFlightsDisplay(
    sources = emptyList(),
    sections = listOf(
        Section(
            kind = SectionKind.ARRIVALS,
            count = 128,
            rows = listOf(
                FlightRowData(
                    id = "arrival/k-a1",
                    direction = "arrival",
                    identity = Identity.Flight(callsign = "XLA401", number = "XA401"),
                    otherAirport = OtherAirport(
                        code = "XVR",
                        name = "Example Valley Regional and International Airport of the Northern Sample Counties",
                        flag = "🇩🇪",
                    ),
                    status = FlightStatus.APPROACHING,
                    estimated = true,
                    time = EventTime(
                        TimeKind.EXPECTED,
                        Basis.RUNWAY,
                        previewClock(0, 10).copy(date = LocalDate.of(2026, 11, 14)),
                    ),
                    evidence = emptyList(),
                    aircraftHex = "3c6444",
                    aircraftType = "A20N",
                ),
                FlightRowData(
                    id = "arrival/k-a2",
                    direction = "arrival",
                    identity = Identity.Flight(callsign = "XLB908", number = null),
                    otherAirport = null,
                    status = FlightStatus.LANDED,
                    estimated = false,
                    time = EventTime(TimeKind.ACTUAL, Basis.RUNWAY, previewClock(12, 52)),
                    evidence = emptyList(),
                    aircraftHex = null,
                    aircraftType = null,
                ),
                FlightRowData(
                    id = "arrival/k-a3",
                    direction = "arrival",
                    identity = Identity.Flight(callsign = "XLC1418", number = null),
                    otherAirport = OtherAirport(code = "XMP", name = null, flag = null),
                    status = FlightStatus.EN_ROUTE,
                    estimated = false,
                    time = null,
                    evidence = emptyList(),
                    aircraftHex = null,
                    aircraftType = null,
                ),
            ),
            showMoreDirection = "arrival",
        ),
        Section(kind = SectionKind.DEPARTURES, count = 64, rows = emptyList(), showMoreDirection = "departure"),
    ),
)

@Preview2
@Composable
private fun AirportFlightsLoadedPreview() {
    PreviewWrapper {
        LazyColumn {
            airportFlights(
                state = AirportFlightsUiState.Loaded(previewDisplay, refreshing = true),
                onShowFlights = {},
                onRefresh = {},
                onShowMore = {},
                onSelectSection = {},
                onRetry = {},
                onReload = {},
                onOpenAircraft = {},
            )
        }
    }
}

@Preview2
@Composable
private fun AirportFlightsReadyPreview() {
    PreviewWrapper {
        LazyColumn {
            airportFlights(
                state = AirportFlightsUiState.Ready,
                onShowFlights = {},
                onRefresh = {},
                onShowMore = {},
                onSelectSection = {},
                onRetry = {},
                onReload = {},
                onOpenAircraft = {},
            )
        }
    }
}
