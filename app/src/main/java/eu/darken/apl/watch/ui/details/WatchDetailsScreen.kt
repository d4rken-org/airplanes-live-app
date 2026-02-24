package eu.darken.apl.watch.ui.details

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Campaign
import androidx.compose.material.icons.twotone.Hexagon
import androidx.compose.material.icons.twotone.MyLocation
import androidx.compose.material.icons.twotone.Router
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.apl.R
import kotlin.math.roundToInt
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.navigation.NavigationEventHandler
import eu.darken.apl.main.ui.AircraftDetails
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.FlightWatch
import eu.darken.apl.watch.core.types.LocationWatch
import eu.darken.apl.watch.core.types.SquawkWatch

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

    val state by vm.state.collectAsState(initial = null)
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
            onRemove = { vm.removeAlert() },
            onThumbnailClick = { meta ->
                // Open planespotters link via webpageTool if available
            },
            onLocationChanged = vm::updateLocation,
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

@Composable
private fun WatchDetailsContent(
    state: WatchDetailsViewModel.State,
    onNoteChanged: (String) -> Unit,
    onNotificationsChanged: (Boolean) -> Unit,
    onShowOnMap: () -> Unit,
    onRemove: () -> Unit,
    onThumbnailClick: (eu.darken.apl.common.planespotters.PlanespottersMeta) -> Unit,
    onLocationChanged: (latitude: Double, longitude: Double, radiusInMeters: Float, label: String) -> Unit = { _, _, _, _ -> },
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = when (state.status) {
                    is AircraftWatch.Status -> Icons.TwoTone.Hexagon
                    is FlightWatch.Status -> Icons.TwoTone.Campaign
                    is SquawkWatch.Status -> Icons.TwoTone.Router
                    is LocationWatch.Status -> Icons.TwoTone.MyLocation
                },
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = when (state.status) {
                            is AircraftWatch.Status -> state.aircraft?.registration ?: "?"
                            is FlightWatch.Status -> state.status.callsign
                            is SquawkWatch.Status -> state.status.squawk
                            is LocationWatch.Status -> state.status.label
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    when (state.status) {
                        is AircraftWatch.Status -> {
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "| #${state.status.hex}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        is FlightWatch.Status -> {
                            state.aircraft?.hex?.let {
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "| #$it",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        is SquawkWatch.Status -> {}
                        is LocationWatch.Status -> {}
                    }
                }
                Text(
                    text = when (state.status) {
                        is AircraftWatch.Status -> stringResource(R.string.watch_list_item_aircraft_subtitle)
                        is FlightWatch.Status -> stringResource(R.string.watch_list_item_flight_subtitle)
                        is SquawkWatch.Status -> stringResource(R.string.watch_list_item_squawk_subtitle)
                        is LocationWatch.Status -> stringResource(
                            R.string.watch_list_item_location_subtitle,
                            (state.status.radiusInMeters / 1000).toInt()
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Aircraft details (for aircraft/flight watches)
        if (state.aircraft != null && state.status !is SquawkWatch.Status && state.status !is LocationWatch.Status) {
            Spacer(Modifier.height(16.dp))
            AircraftDetails(
                aircraft = state.aircraft,
                route = state.route,
                distanceInMeter = state.distanceInMeter,
                onThumbnailClick = onThumbnailClick,
            )
        }

        // Location editing (for location watches)
        if (state.status is LocationWatch.Status) {
            val locStatus = state.status
            Spacer(Modifier.height(16.dp))
            LocationEditSection(
                label = locStatus.label,
                latitude = locStatus.watch.latitude,
                longitude = locStatus.watch.longitude,
                radiusInMeters = locStatus.radiusInMeters,
                onLocationChanged = onLocationChanged,
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // Note input
        var noteText by rememberSaveable { mutableStateOf(state.status.note) }
        OutlinedTextField(
            value = noteText,
            onValueChange = {
                noteText = it
                onNoteChanged(it)
            },
            label = { Text("Note") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4,
        )

        Spacer(Modifier.height(16.dp))

        // Notification toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.watch_details_enable_notifications_label),
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = state.status.watch.isNotificationEnabled,
                onCheckedChange = onNotificationsChanged,
            )
        }

        Spacer(Modifier.height(16.dp))

        // Show on map
        Button(
            onClick = onShowOnMap,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.common_show_on_map_action))
        }

        // Remove
        Button(
            onClick = onRemove,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        ) {
            Text(stringResource(R.string.watch_list_remove_action))
        }
    }
}

@Composable
private fun LocationEditSection(
    label: String,
    latitude: Double,
    longitude: Double,
    radiusInMeters: Float,
    onLocationChanged: (latitude: Double, longitude: Double, radiusInMeters: Float, label: String) -> Unit,
) {
    var labelText by rememberSaveable { mutableStateOf(label) }
    var radiusKm by rememberSaveable { mutableFloatStateOf(radiusInMeters / 1000f) }

    OutlinedTextField(
        value = labelText,
        onValueChange = {
            labelText = it
            onLocationChanged(latitude, longitude, radiusKm * 1000f, it)
        },
        label = { Text(stringResource(R.string.watch_list_add_location_label_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(8.dp))

    Text(
        text = "%.4f, %.4f".format(latitude, longitude),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(12.dp))

    Text(
        text = "${stringResource(R.string.watch_list_add_location_radius_label)}: ${radiusKm.roundToInt()} km",
        style = MaterialTheme.typography.bodyMedium,
    )
    Slider(
        value = radiusKm,
        onValueChange = { radiusKm = it },
        onValueChangeFinished = {
            onLocationChanged(latitude, longitude, radiusKm * 1000f, labelText)
        },
        valueRange = 2f..250f,
        modifier = Modifier.fillMaxWidth(),
    )
}
