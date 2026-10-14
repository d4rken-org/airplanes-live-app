package eu.darken.apl.watch.ui.details

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Campaign
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Hexagon
import androidx.compose.material.icons.twotone.Map
import androidx.compose.material.icons.twotone.MyLocation
import androidx.compose.material.icons.twotone.Pin
import androidx.compose.material.icons.twotone.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.apl.R
import eu.darken.apl.common.chart.ChartState
import eu.darken.apl.common.chart.MetricLineChart
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.navigation.NavigationEventHandler
import eu.darken.apl.common.planespotters.PlanespottersMeta
import eu.darken.apl.common.planespotters.PlanespottersThumbnail
import eu.darken.apl.common.planespotters.coil.AircraftThumbnailQuery
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.aircraft.SquawkEmergency
import eu.darken.apl.main.ui.AircraftDetails
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.FlightWatch
import eu.darken.apl.watch.core.types.LocationWatch
import eu.darken.apl.watch.core.types.SquawkWatch
import eu.darken.apl.watch.core.types.Watch
import eu.darken.apl.watch.core.types.WatchCheckOutcome
import eu.darken.apl.watch.ui.chart.ActivityGrid
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

@Composable
fun WatchDetailsSheetHost(
    watchId: String,
    vm: WatchDetailsViewModel = hiltViewModel(),
) {
    NavigationEventHandler(vm)
    ErrorEventHandler(vm)

    LaunchedEffect(watchId) {
        vm.init(watchId)
    }

    val state by vm.state.collectAsStateWithLifecycle(initialValue = null)
    val currentState = state ?: return

    var showRemoveDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is WatchDetailsEvents.RemovalConfirmation -> showRemoveDialog = true
            }
        }
    }

    ModalBottomSheet(onDismissRequest = { vm.navUp() }) {
        WatchDetailsContent(
            state = currentState,
            onNoteChanged = vm::updateNote,
            onNotificationsChanged = vm::enableNotifications,
            onShowOnMap = vm::showOnMap,
            onShowInSearch = vm::showInSearch,
            onRemove = { vm.removeAlert() },
            onThumbnailClick = vm::openThumbnail,
            onAircraftClick = vm::showAircraft,
            onLocationSaved = vm::updateLocation,
        )
    }

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text(stringResource(R.string.watch_list_remove_confirmation_title)) },
            text = { Text(stringResource(R.string.watch_list_remove_confirmation_message)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.removeAlert(confirmed = true)
                    showRemoveDialog = false
                }) {
                    Text(stringResource(R.string.common_remove_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) {
                    Text(stringResource(R.string.common_cancel_action))
                }
            },
        )
    }
}

/** Sections pad themselves, the aircraft details bring their own insets. */
private val SectionPadding = Modifier.padding(horizontal = 16.dp)

@Composable
internal fun WatchDetailsContent(
    state: WatchDetailsViewModel.State,
    onNoteChanged: (String) -> Unit,
    onNotificationsChanged: (Boolean) -> Unit,
    onShowOnMap: () -> Unit,
    onShowInSearch: () -> Unit,
    onRemove: () -> Unit,
    onThumbnailClick: (PlanespottersMeta) -> Unit,
    onAircraftClick: (Aircraft) -> Unit,
    onLocationSaved: (radiusInMeters: Float, label: String) -> Unit,
) {
    // Relative times and the age of aircraft data move on while the sheet stays open
    val now by produceState(Instant.now()) {
        while (true) {
            delay(60_000)
            value = Instant.now()
        }
    }
    val status = state.status

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Header(state, modifier = SectionPadding)
        StatusLines(state, now, modifier = SectionPadding.padding(top = 8.dp))
        Actions(
            showSearch = status !is LocationWatch.Status,
            onShowOnMap = onShowOnMap,
            onShowInSearch = onShowInSearch,
            modifier = SectionPadding.padding(top = 16.dp),
        )

        when (status) {
            is AircraftWatch.Status, is FlightWatch.Status -> SingleAircraftSection(
                state = state,
                now = now,
                onThumbnailClick = onThumbnailClick,
                modifier = Modifier.padding(top = 16.dp),
            )

            is SquawkWatch.Status, is LocationWatch.Status -> {
                if (status is SquawkWatch.Status) {
                    SquawkEmergency.fromSquawk(status.squawk)?.let { emergency ->
                        Text(
                            text = stringResource(R.string.watch_details_squawk_meaning_x, status.squawk, stringResource(emergency.meaningRes)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = SectionPadding.padding(top = 16.dp),
                        )
                    }
                }
                state.matches?.let { matches ->
                    MatchesSection(
                        matches = matches,
                        now = now,
                        onAircraftClick = onAircraftClick,
                        onThumbnailClick = onThumbnailClick,
                        modifier = SectionPadding.padding(top = 16.dp),
                    )
                }
            }
        }

        HistorySection(state, modifier = SectionPadding.padding(top = 24.dp))

        HorizontalDivider(modifier = SectionPadding.padding(vertical = 16.dp))

        SettingsSection(
            state = state,
            onNoteChanged = onNoteChanged,
            onNotificationsChanged = onNotificationsChanged,
            onLocationSaved = onLocationSaved,
            modifier = SectionPadding,
        )

        TextButton(
            onClick = onRemove,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = SectionPadding.padding(top = 8.dp),
        ) {
            Icon(Icons.TwoTone.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.watch_list_remove_action))
        }
    }
}

@Composable
private fun Header(state: WatchDetailsViewModel.State, modifier: Modifier = Modifier) {
    val status = state.status
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = status.icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            val registration = state.aircraft?.registration
            val (title, hex) = when (status) {
                // Without a registration, an aircraft watch's title is already its hex
                is AircraftWatch.Status -> (registration ?: "#${status.hex.uppercase()}") to status.hex.takeIf { registration != null }
                is FlightWatch.Status -> status.callsign.uppercase() to state.aircraft?.hex
                is SquawkWatch.Status -> status.squawk to null
                is LocationWatch.Status -> status.label to null
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (hex != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "#${hex.uppercase()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = when (status) {
                    is AircraftWatch.Status -> stringResource(R.string.watch_list_item_aircraft_subtitle)
                    is FlightWatch.Status -> stringResource(R.string.watch_list_item_flight_subtitle)
                    is SquawkWatch.Status -> stringResource(R.string.watch_list_item_squawk_subtitle)
                    is LocationWatch.Status -> {
                        val km = (status.radiusInMeters / 1000).roundToInt()
                        pluralStringResource(R.plurals.watch_list_item_location_subtitle, km, km)
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val Watch.Status.icon: ImageVector
    get() = when (this) {
        is AircraftWatch.Status -> Icons.TwoTone.Hexagon
        is FlightWatch.Status -> Icons.TwoTone.Campaign
        is SquawkWatch.Status -> Icons.TwoTone.Pin
        is LocationWatch.Status -> Icons.TwoTone.MyLocation
    }

private fun relative(at: Instant, now: Instant): String = DateUtils.getRelativeTimeSpanString(
    at.toEpochMilli(),
    now.toEpochMilli().coerceAtLeast(at.toEpochMilli()),
    DateUtils.MINUTE_IN_MILLIS,
).toString()

/** What the watch last saw, and apart from that, whether its checks are working. */
@Composable
private fun StatusLines(state: WatchDetailsViewModel.State, now: Instant, modifier: Modifier = Modifier) {
    val watch = state.status.watch
    Column(modifier = modifier) {
        Text(
            text = state.lastSeenAt
                ?.let { stringResource(R.string.watch_details_last_seen_x, relative(it, now)) }
                ?: stringResource(R.string.watch_details_not_seen_yet),
            style = MaterialTheme.typography.bodyMedium,
            color = if (state.status.tracked.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        val problem = when (watch.lastCheckOutcome) {
            WatchCheckOutcome.RESTRICTED -> stringResource(R.string.watch_state_restricted)
            WatchCheckOutcome.INCONCLUSIVE -> stringResource(R.string.watch_state_inconclusive)
            WatchCheckOutcome.EXHAUSTED -> stringResource(R.string.watch_state_exhausted)
            WatchCheckOutcome.FAILED -> stringResource(R.string.watch_details_check_failed)
            WatchCheckOutcome.INVALID -> stringResource(R.string.watch_details_check_invalid)
            WatchCheckOutcome.MATCHED, WatchCheckOutcome.ABSENT, null -> null
        }
        if (problem != null) {
            Text(
                text = watch.lastCheckAt
                    ?.let { stringResource(R.string.watch_details_check_x_ago, problem, relative(it, now)) }
                    ?: problem,
                style = MaterialTheme.typography.bodyMedium,
                color = when (watch.lastCheckOutcome) {
                    WatchCheckOutcome.FAILED, WatchCheckOutcome.INVALID -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.tertiary
                },
            )
        }
    }
}

@Composable
private fun Actions(
    showSearch: Boolean,
    onShowOnMap: () -> Unit,
    onShowInSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SheetAction(Icons.TwoTone.Map, stringResource(R.string.common_show_on_map_action), onShowOnMap, Modifier.weight(1f))
        if (showSearch) {
            SheetAction(Icons.TwoTone.Search, stringResource(R.string.common_show_in_search), onShowInSearch, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** Until the card's "Live" rule lands, aircraft data older than this is labelled as last known. */
private val LIVE_WINDOW: Duration = Duration.ofMinutes(5)

@Composable
private fun SingleAircraftSection(
    state: WatchDetailsViewModel.State,
    now: Instant,
    onThumbnailClick: (PlanespottersMeta) -> Unit,
    modifier: Modifier = Modifier,
) {
    val aircraft = state.aircraft
    Column(modifier = modifier) {
        if (aircraft == null) {
            val hex = (state.status as? AircraftWatch.Status)?.hex
            Row(modifier = SectionPadding, verticalAlignment = Alignment.CenterVertically) {
                if (hex != null) {
                    PlanespottersThumbnail(
                        query = AircraftThumbnailQuery(hex = hex),
                        onImageClick = onThumbnailClick,
                        modifier = Modifier.width(120.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    text = stringResource(R.string.watch_list_item_no_current_data_msg),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        val seenAt = aircraft.messageSeenAt
        if (seenAt == null || Duration.between(seenAt, now) > LIVE_WINDOW) {
            Text(
                text = seenAt
                    ?.let { stringResource(R.string.watch_details_aircraft_data_age_x, relative(it, now)) }
                    ?: stringResource(R.string.watch_details_aircraft_data_age_unknown),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = SectionPadding.padding(bottom = 4.dp),
            )
        }
        AircraftDetails(
            aircraft = aircraft,
            route = state.route,
            distanceInMeter = state.distanceInMeter,
            onThumbnailClick = onThumbnailClick,
        )
    }
}

private const val MATCH_ROWS = 5

@Composable
private fun MatchesSection(
    matches: WatchDetailsViewModel.Matches,
    now: Instant,
    onAircraftClick: (Aircraft) -> Unit,
    onThumbnailClick: (PlanespottersMeta) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SectionTitle(stringResource(R.string.watch_details_matches_title))
        Caption(
            matches.checkAt
                ?.let { stringResource(R.string.watch_details_matches_from_check_x, relative(it, now)) }
                ?: stringResource(R.string.watch_details_matches_from_cache)
        )

        if (matches.aircraft.isEmpty()) {
            if (matches.checkAt != null) {
                Text(
                    text = stringResource(R.string.watch_details_matches_none),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            return@Column
        }

        Caption(
            stringResource(
                if (matches.fromWatchCenter) R.string.watch_details_matches_sorted_center
                else R.string.watch_details_matches_sorted_you
            )
        )
        Spacer(Modifier.height(8.dp))
        val shown = matches.aircraft.take(MATCH_ROWS)
        shown.forEach { match ->
            MatchRow(match, onAircraftClick, onThumbnailClick)
        }
        val total = maxOf(matches.total ?: 0, matches.aircraft.size)
        if (shown.size < total) {
            Caption(
                stringResource(R.string.watch_details_matches_partial_x, shown.size, total),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun MatchRow(
    match: WatchDetailsViewModel.Match,
    onAircraftClick: (Aircraft) -> Unit,
    onThumbnailClick: (PlanespottersMeta) -> Unit,
) {
    val aircraft = match.aircraft
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onAircraftClick(aircraft) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlanespottersThumbnail(
            query = AircraftThumbnailQuery(hex = aircraft.hex, registration = aircraft.registration),
            onImageClick = onThumbnailClick,
            modifier = Modifier.width(72.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = aircraft.callsign?.takeIf { it.isNotBlank() }
                    ?: aircraft.registration
                    ?: "#${aircraft.hex.uppercase()}",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    aircraft.registration.takeIf { aircraft.callsign?.isNotBlank() == true },
                    aircraft.description ?: aircraft.airframe,
                ).joinToString(" · ").ifEmpty { "#${aircraft.hex.uppercase()}" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        match.distanceInMeter?.let {
            Text(
                text = "${(it / 1000).roundToInt()} km",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun HistorySection(state: WatchDetailsViewModel.State, modifier: Modifier = Modifier) {
    val counting = state.status.isCounting
    Column(modifier = modifier) {
        SectionTitle(stringResource(R.string.watch_details_activity_title))
        Caption(stringResource(R.string.watch_details_activity_caption))
        if (counting) Caption(stringResource(R.string.watch_details_activity_counts_caption))

        when (val activity = state.activityState) {
            is ChartState.Ready -> {
                if (!activity.data.hasData) {
                    Text(
                        text = stringResource(R.string.watch_details_activity_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                ActivityGrid(
                    grid = activity.data,
                    shadeByCount = counting,
                    activeColor = MaterialTheme.colorScheme.primary,
                    inactiveColor = MaterialTheme.colorScheme.outline,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }

            else -> ChartLoading()
        }

        state.countState?.let { countState ->
            Spacer(Modifier.height(24.dp))
            when (countState) {
                is ChartState.Ready -> MetricLineChart(
                    title = stringResource(R.string.watch_details_counts_title),
                    data = countState.data.counts,
                    lineColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    noDataText = stringResource(R.string.watch_chart_no_data),
                )

                is ChartState.NoData -> {
                    SectionTitle(stringResource(R.string.watch_details_counts_title))
                    Text(
                        text = stringResource(R.string.watch_chart_no_data),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                is ChartState.Loading -> ChartLoading()
            }
            Caption(stringResource(R.string.watch_details_counts_caption))
        }
    }
}

@Composable
private fun ColumnScope.ChartLoading() {
    CircularProgressIndicator(
        modifier = Modifier
            .padding(16.dp)
            .size(24.dp)
            .align(Alignment.CenterHorizontally),
        strokeWidth = 2.dp,
    )
}

@Composable
private fun SettingsSection(
    state: WatchDetailsViewModel.State,
    onNoteChanged: (String) -> Unit,
    onNotificationsChanged: (Boolean) -> Unit,
    onLocationSaved: (radiusInMeters: Float, label: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = state.status
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.watch_details_enable_notifications_label),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = status.watch.isNotificationEnabled,
                onCheckedChange = onNotificationsChanged,
            )
        }

        if (status is LocationWatch.Status) {
            LocationArea(
                status = status,
                maxRadiusKm = state.maxRadiusKm,
                onSave = onLocationSaved,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        var noteText by rememberSaveable(status.id) { mutableStateOf(status.note) }
        OutlinedTextField(
            value = noteText,
            onValueChange = {
                noteText = it
                onNoteChanged(it)
            },
            label = { Text(stringResource(R.string.watchlist_note_label)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            minLines = 2,
            maxLines = 4,
        )
    }
}

/** Edited as a whole and saved once, a new radius starts the watch's history over. */
@Composable
private fun LocationArea(
    status: LocationWatch.Status,
    maxRadiusKm: Int,
    onSave: (radiusInMeters: Float, label: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentRadiusKm = (status.radiusInMeters / 1000f).roundToInt()
    var editing by rememberSaveable(status.id) { mutableStateOf(false) }
    Column(modifier = modifier) {
        if (!editing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        R.string.watch_details_location_area_x,
                        LocationWatch.formatCoordinates(status.watch.latitude, status.watch.longitude, decimals = 4),
                        currentRadiusKm,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { editing = true }) {
                    Text(stringResource(R.string.watch_details_location_edit_action))
                }
            }
            return@Column
        }

        var label by rememberSaveable(status.id) { mutableStateOf(status.label) }
        val radiusRange = LocationWatch.MIN_RADIUS_KM.toFloat()..maxOf(maxRadiusKm, LocationWatch.MIN_RADIUS_KM + 1).toFloat()
        // Starts at the stored radius even beyond today's limit, so saving only a new label keeps it
        var radiusKm by rememberSaveable(status.id) { mutableFloatStateOf(currentRadiusKm.toFloat()) }
        val newRadiusKm = radiusKm.roundToInt()

        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            label = { Text(stringResource(R.string.watch_list_add_location_label_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.watch_details_location_radius_x, newRadiusKm),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
        Slider(
            value = radiusKm.coerceIn(radiusRange),
            onValueChange = { radiusKm = it },
            valueRange = radiusRange,
            modifier = Modifier.fillMaxWidth(),
        )
        if (newRadiusKm != currentRadiusKm) {
            Text(
                text = stringResource(R.string.watch_details_location_reset_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = { editing = false }) {
                Text(stringResource(R.string.common_cancel_action))
            }
            TextButton(
                enabled = label.isNotBlank(),
                onClick = {
                    // An unmoved slider keeps the exact stored radius, so only the label changes
                    val radiusInMeters = if (newRadiusKm == currentRadiusKm) status.radiusInMeters else newRadiusKm * 1000f
                    onSave(radiusInMeters, label)
                    editing = false
                },
            ) {
                Text(stringResource(R.string.common_save_action))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
