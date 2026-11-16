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
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.darken.apl.R
import eu.darken.apl.airport.ui.flights.AirportFlightsDisplay.Basis
import eu.darken.apl.airport.ui.flights.AirportFlightsDisplay.EventTime
import eu.darken.apl.airport.ui.flights.AirportFlightsDisplay.Evidence
import eu.darken.apl.airport.ui.flights.AirportFlightsDisplay.FlightStatus
import eu.darken.apl.airport.ui.flights.AirportFlightsDisplay.Identity
import eu.darken.apl.airport.ui.flights.AirportFlightsDisplay.Row as FlightRowData
import eu.darken.apl.airport.ui.flights.AirportFlightsDisplay.Section
import eu.darken.apl.airport.ui.flights.AirportFlightsDisplay.SectionKind
import eu.darken.apl.airport.ui.flights.AirportFlightsDisplay.SourceKind
import eu.darken.apl.airport.ui.flights.AirportFlightsDisplay.SourceState
import eu.darken.apl.airport.ui.flights.AirportFlightsDisplay.TimeKind
import eu.darken.apl.common.compose.Preview2
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.compose.seenAgeText
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
    val onRetry: () -> Unit,
    val onReload: () -> Unit,
    val onOpenAircraft: (String) -> Unit,
) {
    companion object {
        val None = AirportFlightsActions({}, {}, {}, {}, {}, {})
    }
}

/** The flights section of an airport's details, [onShowMore] gets the wire direction to page. */
internal fun LazyListScope.airportFlights(
    state: AirportFlightsUiState,
    onShowFlights: () -> Unit,
    onRefresh: () -> Unit,
    onShowMore: (String) -> Unit,
    onRetry: () -> Unit,
    onReload: () -> Unit,
    onOpenAircraft: (String) -> Unit,
) {
    item(key = "${KEY_PREFIX}_header") {
        FlightsHeader(loaded = state as? AirportFlightsUiState.Loaded, onRefresh = onRefresh)
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
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                FlightsCaveat()
                Text(
                    text = stringResource(R.string.airport_flights_load_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                FilledTonalButton(onClick = onShowFlights, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.airport_flights_show_action))
                }
            }
        }

        AirportFlightsUiState.Loading -> item(key = statusKey) {
            Column(modifier = Modifier.fillMaxWidth()) {
                FlightsCaveat(modifier = Modifier.padding(horizontal = 16.dp))
                val label = stringResource(R.string.airport_flights_loading)
                FlightsProgress(description = label, label = label)
            }
        }

        is AirportFlightsUiState.Loaded -> loadedFlights(state, onShowMore, onRetry, onReload, onOpenAircraft)

        AirportFlightsUiState.NotOffered -> item(key = statusKey) {
            FlightsMessage(stringResource(R.string.airport_flights_not_offered))
        }

        AirportFlightsUiState.UnknownAirport -> item(key = statusKey) {
            FlightsMessage(stringResource(R.string.airport_flights_unknown_airport))
        }

        AirportFlightsUiState.AllowanceExhausted -> item(key = statusKey) {
            FlightsMessage(stringResource(R.string.server_error_allowance_exhausted_msg))
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
    onRetry: () -> Unit,
    onReload: () -> Unit,
    onOpenAircraft: (String) -> Unit,
) {
    val display = state.display
    item(key = "${KEY_PREFIX}_info") { FlightsInfo(display) }

    state.notice?.let { notice ->
        item(key = "${KEY_PREFIX}_notice") { FlightsNotice(notice, onRetry = onRetry, onReload = onReload) }
    }

    val shown = display.sections.filter { it.rows.isNotEmpty() || it.showMoreDirection != null }
    if (shown.isEmpty()) {
        item(key = "${KEY_PREFIX}_empty") { FlightsMessage(stringResource(R.string.airport_flights_empty)) }
    }

    shown.forEach { section ->
        item(key = "${KEY_PREFIX}_section_${section.kind}") { SectionHeader(section) }
        items(section.rows, key = { "${KEY_PREFIX}_row_${it.id}" }) { row ->
            FlightRow(row, onOpenAircraft = onOpenAircraft)
        }
        section.showMoreDirection?.let { direction ->
            item(key = "${KEY_PREFIX}_more_${section.kind}") {
                TextButton(
                    onClick = { onShowMore(direction) },
                    enabled = direction !in state.pagingDirections,
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Text(stringResource(R.string.airport_flights_show_more_action))
                }
            }
        }
    }
}

@Composable
private fun FlightsHeader(loaded: AirportFlightsUiState.Loaded?, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.airport_flights_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp),
        )
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
private fun FlightsCaveat(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.airport_flights_caveat),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun FlightsInfo(display: AirportFlightsDisplay) {
    val separator = stringResource(R.string.common_list_separator)
    val sources = display.sources.map { source ->
        val name = stringResource(source.kind.label)
        when (source.state) {
            SourceState.OK -> name
            SourceState.PARTIAL -> stringResource(R.string.airport_flights_source_partial_x, name)
            SourceState.STALE -> stringResource(R.string.airport_flights_source_stale_x, name)
            SourceState.UNAVAILABLE -> stringResource(R.string.airport_flights_source_unavailable_x, name)
        }
    }
    val nowMs = System.currentTimeMillis()
    val age = stringResource(R.string.airport_flights_updated_x, seenAgeText(nowMs - display.age.toMillis(), nowMs))
    val ageLine = if (display.stale) {
        listOf(age, stringResource(R.string.airport_flights_stale)).joinToString(" · ")
    } else {
        age
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        FlightsCaveat()
        if (sources.isNotEmpty()) {
            Text(
                text = stringResource(R.string.airport_flights_sources_x, sources.joinToString(separator)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Text(
            text = ageLine,
            style = MaterialTheme.typography.bodySmall,
            color = if (display.stale) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        display.passUntil?.let {
            Text(
                text = stringResource(R.string.airport_flights_pass_until_x, clockText(it)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
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
            text = stringResource(R.string.server_error_allowance_exhausted_msg),
        )

        is AirportFlightsUiState.Notice.Waiting -> FlightsMessage(
            text = stringResource(R.string.airport_flights_waiting_x, clockText(notice.until)),
        )
    }
}

@Composable
private fun SectionHeader(section: Section) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(section.kind.label),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = pluralStringResource(R.plurals.airport_flights_count_x, section.count, section.count),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FlightRow(row: FlightRowData, onOpenAircraft: (String) -> Unit) {
    val hex = row.aircraftHex
    val separator = stringResource(R.string.common_list_separator)
    val status = statusText(row)
    val time = timeText(row.time)
    val otherAirport = row.otherAirport?.let { airport ->
        when {
            airport.name != null && airport.code != null -> stringResource(
                R.string.airport_name_with_code_x,
                airport.name,
                airport.code,
            )

            else -> airport.name ?: airport.code
        }
    }
    val evidence = row.evidence.map { stringResource(it.label) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (hex != null) {
                    Modifier.clickable(role = Role.Button) { onOpenAircraft(hex) }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = identityText(row.identity),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        otherAirport?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = listOfNotNull(status, time).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (evidence.isNotEmpty()) {
            Text(
                text = stringResource(R.string.airport_flights_evidence_x, evidence.joinToString(separator)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FlightsProgress(description: String, label: String?) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
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

@Composable
private fun statusText(row: FlightRowData): String? {
    val status = row.status?.let { stringResource(it.label) }
    return when {
        status != null && row.estimated -> stringResource(R.string.airport_flights_status_estimated_x, status)
        status != null -> status
        row.estimated -> stringResource(R.string.airport_flights_estimated)
        else -> null
    }
}

@Composable
private fun timeText(time: EventTime?): String = if (time == null) {
    stringResource(R.string.airport_flights_time_now)
} else {
    val kinded = stringResource(time.kind.label, clockText(time.clock))
    time.basis?.let { stringResource(R.string.airport_flights_time_basis_x, kinded, stringResource(it.label)) }
        ?: kinded
}

@Composable
private fun clockText(clock: ClockTime): String {
    val formats = rememberClockFormats()
    val time = formats.time.format(clock.local)
    val dated = clock.date
        ?.let { stringResource(R.string.airport_flights_date_time_x, formats.date.format(it), time) }
        ?: time
    return when (clock.kind) {
        DisplayZone.Kind.AIRPORT_LOCAL -> stringResource(R.string.airport_flights_time_local_x, dated)
        DisplayZone.Kind.DEVICE -> stringResource(R.string.airport_flights_time_device_x, dated)
    }
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
        SectionKind.OTHER -> R.string.airport_flights_other_title
    }

private val SourceKind.label: Int
    get() = when (this) {
        SourceKind.KNOWN_ROUTES -> R.string.airport_flights_source_known_routes
        SourceKind.CACHED_ROUTES -> R.string.airport_flights_source_cached_routes
        SourceKind.NEARBY_TRAFFIC -> R.string.airport_flights_source_nearby_traffic
        SourceKind.HISTORY -> R.string.airport_flights_source_history
        SourceKind.SCHEDULES -> R.string.airport_flights_source_schedules
        SourceKind.OTHER -> R.string.airport_flights_source_other
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

private val Evidence.label: Int
    get() = when (this) {
        Evidence.KNOWN_ROUTE -> R.string.airport_flights_evidence_known_route
        Evidence.CACHED_ROUTE -> R.string.airport_flights_evidence_cached_route
        Evidence.FITTED_LEG -> R.string.airport_flights_evidence_fitted_leg
        Evidence.NEARBY_LOW -> R.string.airport_flights_evidence_nearby_low
        Evidence.ON_GROUND -> R.string.airport_flights_evidence_on_ground
        Evidence.DESCENDING -> R.string.airport_flights_evidence_descending
        Evidence.CLIMBING -> R.string.airport_flights_evidence_climbing
        Evidence.HISTORY -> R.string.airport_flights_evidence_history
        Evidence.SCHEDULE -> R.string.airport_flights_evidence_schedule
    }

private val TimeKind.label: Int
    get() = when (this) {
        TimeKind.SCHEDULED -> R.string.airport_flights_time_scheduled_x
        TimeKind.EXPECTED -> R.string.airport_flights_time_expected_x
        TimeKind.ACTUAL -> R.string.airport_flights_time_actual_x
    }

private val Basis.label: Int
    get() = when (this) {
        Basis.GATE -> R.string.airport_flights_basis_gate
        Basis.RUNWAY -> R.string.airport_flights_basis_runway
    }

private fun previewClock(hour: Int, minute: Int) = ClockTime(
    local = LocalDateTime.of(2026, 11, 13, hour, minute),
    offset = ZoneOffset.ofHours(1),
    date = null,
    kind = DisplayZone.Kind.AIRPORT_LOCAL,
)

private val previewDisplay = AirportFlightsDisplay(
    sources = listOf(
        AirportFlightsDisplay.Source(SourceKind.KNOWN_ROUTES, SourceState.OK),
        AirportFlightsDisplay.Source(SourceKind.NEARBY_TRAFFIC, SourceState.PARTIAL),
    ),
    age = Duration.ofMinutes(2),
    stale = false,
    passUntil = previewClock(13, 30),
    sections = listOf(
        Section(
            kind = SectionKind.ARRIVALS,
            count = 31,
            rows = listOf(
                FlightRowData(
                    id = "arrival/k-a1",
                    direction = "arrival",
                    identity = Identity.Flight(callsign = "XLA401", number = null),
                    otherAirport = AirportFlightsDisplay.OtherAirport(
                        code = "XYZ",
                        name = "Example County International Airport",
                        flag = null,
                    ),
                    status = FlightStatus.APPROACHING,
                    estimated = true,
                    time = EventTime(TimeKind.EXPECTED, Basis.RUNWAY, previewClock(13, 10)),
                    evidence = listOf(Evidence.KNOWN_ROUTE, Evidence.DESCENDING),
                    aircraftHex = "3c6444",
                ),
                FlightRowData(
                    id = "arrival/k-a3",
                    direction = "arrival",
                    identity = Identity.Flight(callsign = "XLC1418", number = null),
                    otherAirport = null,
                    status = FlightStatus.EN_ROUTE,
                    estimated = false,
                    time = null,
                    evidence = listOf(Evidence.NEARBY_LOW),
                    aircraftHex = null,
                ),
            ),
            showMoreDirection = null,
        ),
        Section(
            kind = SectionKind.DEPARTURES,
            count = 64,
            rows = listOf(
                FlightRowData(
                    id = "departure/k-d3",
                    direction = "departure",
                    identity = Identity.Flight(callsign = "XLD77", number = "XD77"),
                    otherAirport = null,
                    status = FlightStatus.DELAYED,
                    estimated = false,
                    time = EventTime(
                        TimeKind.EXPECTED,
                        Basis.GATE,
                        previewClock(0, 10).copy(date = LocalDate.of(2026, 11, 14)),
                    ),
                    evidence = listOf(Evidence.SCHEDULE),
                    aircraftHex = null,
                ),
            ),
            showMoreDirection = "departure",
        ),
        Section(kind = SectionKind.OTHER, count = 0, rows = emptyList(), showMoreDirection = null),
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
                onRetry = {},
                onReload = {},
                onOpenAircraft = {},
            )
        }
    }
}
