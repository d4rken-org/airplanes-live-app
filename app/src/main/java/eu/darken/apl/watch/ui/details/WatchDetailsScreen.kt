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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.apl.R
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.navigation.NavigationEventHandler
import eu.darken.apl.main.ui.AircraftDetails
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.FlightWatch
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
                painter = painterResource(
                    when (state.status) {
                        is AircraftWatch.Status -> R.drawable.ic_hexagon_multiple_24
                        is FlightWatch.Status -> R.drawable.ic_bullhorn_24
                        is SquawkWatch.Status -> R.drawable.ic_router_wireless_24
                    }
                ),
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
                    }
                }
                Text(
                    text = when (state.status) {
                        is AircraftWatch.Status -> stringResource(R.string.watch_list_item_aircraft_subtitle)
                        is FlightWatch.Status -> stringResource(R.string.watch_list_item_flight_subtitle)
                        is SquawkWatch.Status -> stringResource(R.string.watch_list_item_squawk_subtitle)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Aircraft details (for aircraft/flight watches)
        if (state.aircraft != null && state.status !is SquawkWatch.Status) {
            Spacer(Modifier.height(16.dp))
            AircraftDetails(
                aircraft = state.aircraft,
                route = state.route,
                distanceInMeter = state.distanceInMeter,
                onThumbnailClick = onThumbnailClick,
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
