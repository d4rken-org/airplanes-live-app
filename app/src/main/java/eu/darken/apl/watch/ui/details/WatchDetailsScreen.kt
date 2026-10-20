package eu.darken.apl.watch.ui.details

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.EditLocation
import androidx.compose.material.icons.twotone.EditNote
import androidx.compose.material.icons.twotone.Map
import androidx.compose.material.icons.twotone.MoreVert
import androidx.compose.material.icons.twotone.NotificationsActive
import androidx.compose.material.icons.twotone.NotificationsOff
import androidx.compose.material.icons.twotone.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.apl.R
import eu.darken.apl.common.chart.ChartState
import eu.darken.apl.common.chart.MetricLineChart
import eu.darken.apl.common.compose.GlanceMetric
import eu.darken.apl.common.compose.SheetAction
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.flight.ui.GlanceRoute
import eu.darken.apl.common.navigation.NavigationEventHandler
import eu.darken.apl.common.planespotters.PlanespottersMeta
import eu.darken.apl.common.planespotters.PlanespottersThumbnail
import eu.darken.apl.common.planespotters.coil.AircraftThumbnailQuery
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.aircraft.SquawkEmergency
import eu.darken.apl.main.core.aircraft.altitudeLabel
import eu.darken.apl.main.core.aircraft.isLiveAt
import eu.darken.apl.main.core.aircraft.messageTypeLabel
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.FlightWatch
import eu.darken.apl.watch.core.types.LocationWatch
import eu.darken.apl.watch.core.types.SquawkWatch
import eu.darken.apl.watch.core.types.Watch
import eu.darken.apl.watch.core.types.WatchCheckOutcome
import eu.darken.apl.watch.ui.chart.ActivityGrid
import eu.darken.apl.watch.ui.icon
import eu.darken.apl.watch.ui.isServerHeld
import eu.darken.apl.watch.ui.liveCountShown
import eu.darken.apl.watch.ui.type
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

    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = { vm.navUp() }, sheetState = sheetState) {
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
            onExpand = { sheetState.expand() },
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
    onExpand: suspend () -> Unit,
) {
    val now = state.now
    val status = state.status

    var editingArea by rememberSaveable(status.id) { mutableStateOf(false) }
    var editRequest by remember { mutableStateOf<EditRequest?>(null) }
    val noteFocus = remember { FocusRequester() }
    val noteInView = remember { BringIntoViewRequester() }
    val areaInView = remember { BringIntoViewRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // The menu's edit entries open the sheet fully, then bring the editor into reach
    LaunchedEffect(editRequest) {
        val request = editRequest ?: return@LaunchedEffect
        onExpand()
        when (request.target) {
            EditTarget.NOTE -> {
                // A note still focused from before gains nothing from the focus request alone
                noteFocus.requestFocus()
                noteInView.bringIntoView()
                keyboard?.show()
            }

            EditTarget.AREA -> areaInView.bringIntoView()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Glance(
            state = state,
            onThumbnailClick = onThumbnailClick,
            menu = {
                MoreMenu(
                    showEditArea = status is LocationWatch.Status,
                    onEditNote = { editRequest = EditRequest(EditTarget.NOTE) },
                    onEditArea = {
                        editingArea = true
                        editRequest = EditRequest(EditTarget.AREA)
                    },
                    onRemove = onRemove,
                )
            },
            modifier = Modifier.padding(start = 16.dp, end = 4.dp),
        )
        CheckHealth(state, modifier = SectionPadding.padding(top = 8.dp))
        Actions(
            status = status,
            onShowOnMap = onShowOnMap,
            onShowInSearch = onShowInSearch,
            onNotificationsChanged = onNotificationsChanged,
            modifier = SectionPadding.padding(top = 12.dp),
        )

        when (status) {
            // Only alongside the Live chip, older data would contradict its time
            is AircraftWatch.Status, is FlightWatch.Status -> state.aircraft
                ?.takeIf { state.liveCount > 0 && it.isLiveAt(now) }
                ?.let { aircraft ->
                    HorizontalDivider(modifier = SectionPadding.padding(top = 16.dp))
                    FlightData(aircraft, state, modifier = SectionPadding.padding(top = 12.dp))
                }

            is SquawkWatch.Status, is LocationWatch.Status -> state.matches?.let { matches ->
                HorizontalDivider(modifier = SectionPadding.padding(top = 16.dp))
                MatchesSection(
                    matches = matches,
                    now = now,
                    onAircraftClick = onAircraftClick,
                    onThumbnailClick = onThumbnailClick,
                    modifier = SectionPadding.padding(top = 12.dp),
                )
            }
        }

        HistorySection(state, modifier = SectionPadding.padding(top = 24.dp))

        HorizontalDivider(modifier = SectionPadding.padding(vertical = 16.dp))

        if (status is LocationWatch.Status) {
            LocationArea(
                status = status,
                maxRadiusKm = state.maxRadiusKm,
                editing = editingArea,
                onEditingChange = { editingArea = it },
                onSave = onLocationSaved,
                modifier = SectionPadding
                    .padding(bottom = 12.dp)
                    .bringIntoViewRequester(areaInView),
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
            modifier = SectionPadding
                .fillMaxWidth()
                .focusRequester(noteFocus)
                .bringIntoViewRequester(noteInView),
            minLines = 2,
            maxLines = 4,
        )
    }
}

private enum class EditTarget { NOTE, AREA }

/** A class, not a data class: every tap is a new request, so a cancelled one never blocks the next. */
private class EditRequest(val target: EditTarget)

/** Photo or type tile, then title and status, then what else identifies the watch. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Glance(
    state: WatchDetailsViewModel.State,
    onThumbnailClick: (PlanespottersMeta) -> Unit,
    menu: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = state.status
    val aircraft = state.aircraft
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val photo = when (status) {
            is AircraftWatch.Status -> AircraftThumbnailQuery(hex = status.hex, registration = state.registration)
            else -> aircraft?.let { AircraftThumbnailQuery(hex = it.hex, registration = it.registration) }
        }
        if (photo != null) {
            // A callsign can move to another aircraft, its photo must not linger
            key(photo.hex) {
                PlanespottersThumbnail(
                    query = photo,
                    onImageClick = onThumbnailClick,
                    modifier = Modifier.width(128.dp),
                )
            }
        } else {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(64.dp),
            ) {
                Icon(
                    imageVector = status.type.icon,
                    contentDescription = null,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            val registration = state.registration
            val title = when (status) {
                is AircraftWatch.Status -> registration ?: "#${status.hex.uppercase()}"
                is FlightWatch.Status -> status.callsign.uppercase()
                is SquawkWatch.Status -> stringResource(R.string.watch_list_item_squawk_title, status.squawk.uppercase())
                is LocationWatch.Status -> status.label
            }
            // A long title or a large font pushes the status onto its own line
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                StatusChip(state)
            }

            val lines = when (status) {
                is AircraftWatch.Status -> listOf(
                    aircraft?.description ?: aircraft?.airframe,
                    listOfNotNull(
                        "#${status.hex.uppercase()}".takeIf { registration != null },
                        aircraft?.callsign?.takeIf { it.isNotBlank() },
                    ).joinToString(" · "),
                    aircraft?.operator,
                )

                is FlightWatch.Status -> listOf(
                    aircraft?.description ?: aircraft?.airframe,
                    listOfNotNull(aircraft?.registration, aircraft?.hex?.let { "#${it.uppercase()}" })
                        .joinToString(" · "),
                    aircraft?.operator,
                )

                is SquawkWatch.Status -> listOf(
                    stringResource(R.string.watch_list_item_squawk_subtitle),
                    SquawkEmergency.fromSquawk(status.squawk)?.let { stringResource(it.meaningRes) },
                )

                is LocationWatch.Status -> {
                    val km = (status.radiusInMeters / 1000).roundToInt()
                    listOf(
                        pluralStringResource(R.plurals.watch_list_item_location_subtitle, km, km),
                        LocationWatch.formatCoordinates(status.watch.latitude, status.watch.longitude, decimals = 4),
                    )
                }
            }.filterNot { it.isNullOrBlank() }

            // Without aircraft data the type of watch is the only thing left to say
            val shown = lines.ifEmpty {
                listOf(
                    stringResource(
                        if (status is FlightWatch.Status) R.string.watch_list_item_flight_subtitle
                        else R.string.watch_list_item_aircraft_subtitle
                    )
                )
            }
            shown.forEachIndexed { index, line ->
                Text(
                    text = line!!,
                    style = if (index == 0) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                    color = if (index == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Box(modifier = Modifier.align(Alignment.Top)) { menu() }
    }
}

private fun relative(at: Instant, now: Instant): String = DateUtils.getRelativeTimeSpanString(
    at.toEpochMilli(),
    now.toEpochMilli().coerceAtLeast(at.toEpochMilli()),
    DateUtils.MINUTE_IN_MILLIS,
).toString()

@Composable
private fun StatusChip(state: WatchDetailsViewModel.State) {
    val liveCount = state.status.liveCountShown(state.liveCount)
    val live = liveCount > 0
    Surface(
        shape = RoundedCornerShape(50),
        color = if (live) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (live) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = when {
                live && state.status.isCounting ->
                    pluralStringResource(R.plurals.watch_list_status_x_live, liveCount, liveCount)

                live -> stringResource(R.string.watch_list_status_live)
                else -> state.lastSeenAt?.let { relative(it, state.now) }
                    ?: stringResource(R.string.watch_list_status_not_seen_yet)
            },
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

/** Whether the watch's checks are getting through, apart from what they saw. */
@Composable
private fun CheckHealth(state: WatchDetailsViewModel.State, modifier: Modifier = Modifier) {
    state.serverLabel?.let { label ->
        Text(
            text = stringResource(label.textRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = modifier,
        )
        return
    }
    if (state.status.isServerHeld) return
    val watch = state.status.watch
    val now = state.now
    val problem = when (watch.lastCheckOutcome) {
        WatchCheckOutcome.RESTRICTED -> stringResource(R.string.watch_state_restricted)
        WatchCheckOutcome.INCONCLUSIVE -> stringResource(R.string.watch_state_inconclusive)
        WatchCheckOutcome.EXHAUSTED -> stringResource(R.string.watch_state_exhausted)
        WatchCheckOutcome.FAILED -> stringResource(R.string.watch_details_check_failed)
        WatchCheckOutcome.INVALID -> stringResource(R.string.watch_details_check_invalid)
        WatchCheckOutcome.MATCHED, WatchCheckOutcome.ABSENT, null -> null
    } ?: return
    Text(
        text = watch.lastCheckAt
            ?.let { stringResource(R.string.watch_details_check_x_ago, problem, relative(it, now)) }
            ?: problem,
        style = MaterialTheme.typography.bodyMedium,
        color = when (watch.lastCheckOutcome) {
            WatchCheckOutcome.FAILED, WatchCheckOutcome.INVALID -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.tertiary
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Actions(
    status: Watch.Status,
    onShowOnMap: () -> Unit,
    onShowInSearch: () -> Unit,
    onNotificationsChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Short labels keep all three on one row of a narrow phone, large fonts still wrap
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        SheetAction(Icons.TwoTone.Map, stringResource(R.string.map_page_label), onShowOnMap)
        if (status !is LocationWatch.Status) {
            SheetAction(Icons.TwoTone.Search, stringResource(R.string.search_page_label), onShowInSearch)
        }
        NotifyToggle(
            enabled = status.watch.isNotificationEnabled,
            onChange = onNotificationsChanged,
        )
    }
}

@Composable
private fun NotifyToggle(enabled: Boolean, onChange: (Boolean) -> Unit) {
    val label = stringResource(if (enabled) R.string.watch_details_notify_on else R.string.watch_details_notify_off)
    val semantics = Modifier.semantics {
        role = Role.Switch
        toggleableState = ToggleableState(enabled)
    }
    val content: @Composable RowScope.() -> Unit = {
        Icon(
            imageVector = if (enabled) Icons.TwoTone.NotificationsActive else Icons.TwoTone.NotificationsOff,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Text(text = label, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
    }
    // One button restyled in place, swapping composables would drop focus on every toggle
    Button(
        onClick = { onChange(!enabled) },
        colors = if (enabled) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors(),
        elevation = if (enabled) ButtonDefaults.buttonElevation() else null,
        border = if (enabled) null else ButtonDefaults.outlinedButtonBorder(enabled = true),
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        modifier = semantics,
        content = content,
    )
}

@Composable
private fun MoreMenu(
    showEditArea: Boolean,
    onEditNote: () -> Unit,
    onEditArea: () -> Unit,
    onRemove: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.TwoTone.MoreVert, contentDescription = stringResource(R.string.watch_details_more_action))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.watch_details_edit_note_action)) },
                leadingIcon = { Icon(Icons.TwoTone.EditNote, contentDescription = null) },
                onClick = {
                    open = false
                    onEditNote()
                },
            )
            if (showEditArea) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.watch_details_location_edit_action)) },
                    leadingIcon = { Icon(Icons.TwoTone.EditLocation, contentDescription = null) },
                    onClick = {
                        open = false
                        onEditArea()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.watch_list_remove_action), color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.TwoTone.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = {
                    open = false
                    onRemove()
                },
            )
        }
    }
}

/** How the live aircraft of a single-aircraft watch flies and where to. */
@Composable
private fun FlightData(aircraft: Aircraft, state: WatchDetailsViewModel.State, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SquawkEmergency.fromSquawk(aircraft.squawk)?.let { emergency ->
            Text(
                text = stringResource(R.string.watch_details_squawk_meaning_x, aircraft.squawk!!, stringResource(emergency.meaningRes)),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            GlanceMetric(
                value = when {
                    aircraft.onGround == true -> aircraft.altitudeLabel
                    aircraft.altitudeFt != null -> "${aircraft.altitudeFt} ft"
                    else -> null
                },
                label = stringResource(R.string.common_altitude_label),
                modifier = Modifier.weight(1f),
            )
            val groundSpeed = aircraft.groundSpeed
            GlanceMetric(
                value = groundSpeed?.let { "${it.roundToInt()} kts" }
                    ?: aircraft.indicatedAirSpeed?.let { "$it kts" },
                label = stringResource(
                    if (groundSpeed == null && aircraft.indicatedAirSpeed != null) R.string.map_aircraft_details_ias_label
                    else R.string.common_speed_label
                ),
                modifier = Modifier.weight(1f),
            )
            GlanceMetric(
                value = aircraft.squawk,
                label = stringResource(R.string.common_squawk_label),
                modifier = Modifier.weight(1f),
            )
            val track = aircraft.groundTrack?.toDouble()
            GlanceMetric(
                value = (track ?: aircraft.trackheading)?.let { "${it.roundToInt()}°" },
                label = stringResource(
                    if (track == null && aircraft.trackheading != null) R.string.map_aircraft_details_heading_label
                    else R.string.map_aircraft_details_track_label
                ),
                modifier = Modifier.weight(1f),
            )
        }

        val route = state.route
        if (route != null && (route.origin != null || route.destination != null)) GlanceRoute(route)

        val footer = listOfNotNull(
            state.distanceInMeter?.let {
                stringResource(R.string.general_xdistance_away_label, "${(it / 1000).roundToInt()} km")
            },
            aircraft.messageTypeLabel,
        )
        Caption(footer.joinToString(" · "))
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

        // The check can have counted aircraft whose details are no longer cached
        val total = maxOf(matches.total ?: 0, matches.aircraft.size)
        if (matches.aircraft.isEmpty()) {
            Text(
                text = when {
                    total > 0 -> pluralStringResource(R.plurals.watch_details_matches_no_details_x, total, total)
                    matches.checkAt != null -> stringResource(R.string.watch_details_matches_none)
                    else -> stringResource(R.string.watch_details_matches_none_recent)
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            return@Column
        }

        if (matches.sortedByDistance) {
            Caption(
                stringResource(
                    if (matches.fromWatchCenter) R.string.watch_details_matches_sorted_center
                    else R.string.watch_details_matches_sorted_you
                )
            )
        }
        Spacer(Modifier.height(8.dp))
        val shown = matches.aircraft.take(MATCH_ROWS)
        shown.forEach { match ->
            MatchRow(match, onAircraftClick, onThumbnailClick)
        }
        if (shown.size < matches.aircraft.size) {
            Caption(
                stringResource(R.string.watch_details_matches_shown_x, shown.size, matches.aircraft.size),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (matches.aircraft.size < total) {
            Caption(
                stringResource(R.string.watch_details_matches_partial_x, matches.aircraft.size, total),
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
            val km = (it / 1000).roundToInt()
            Text(
                text = if (km < 1) {
                    stringResource(R.string.watch_details_matches_distance_near)
                } else {
                    stringResource(R.string.watch_details_matches_distance_x, km)
                },
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

/** Edited as a whole and saved once, a new radius starts the watch's history over. */
@Composable
private fun LocationArea(
    status: LocationWatch.Status,
    maxRadiusKm: Int,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onSave: (radiusInMeters: Float, label: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentRadiusKm = (status.radiusInMeters / 1000f).roundToInt()
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
                TextButton(onClick = { onEditingChange(true) }) {
                    Text(stringResource(R.string.watch_details_location_edit_action))
                }
            }
            return@Column
        }

        var label by rememberSaveable(status.id) { mutableStateOf(status.label) }
        val radiusRange = LocationWatch.MIN_RADIUS_KM.toFloat()..maxOf(maxRadiusKm, LocationWatch.MIN_RADIUS_KM + 1).toFloat()
        // Starts at the stored radius even beyond today's limit, so saving only a new label keeps it
        var radiusKm by rememberSaveable(status.id) { mutableFloatStateOf(currentRadiusKm.toFloat()) }
        // The limit can arrive or drop while editing, a moved radius never goes beyond it
        val newRadiusKm = if (radiusKm == currentRadiusKm.toFloat()) {
            currentRadiusKm
        } else {
            radiusKm.coerceIn(radiusRange).roundToInt()
        }

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
            TextButton(onClick = { onEditingChange(false) }) {
                Text(stringResource(R.string.common_cancel_action))
            }
            TextButton(
                enabled = label.isNotBlank(),
                onClick = {
                    // An unmoved slider keeps the exact stored radius, so only the label changes
                    val radiusInMeters = if (newRadiusKm == currentRadiusKm) status.radiusInMeters else newRadiusKm * 1000f
                    onSave(radiusInMeters, label)
                    onEditingChange(false)
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
