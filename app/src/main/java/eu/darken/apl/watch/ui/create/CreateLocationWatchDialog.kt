package eu.darken.apl.watch.ui.create

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.apl.R
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.navigation.NavigationEventHandler
import eu.darken.apl.common.units.LocalUnitFormatter
import eu.darken.apl.watch.core.types.LocationWatch
import eu.darken.apl.watch.ui.LocationRadiusPicker
import eu.darken.apl.watch.ui.RadiusStops

@Composable
fun CreateLocationWatchDialogHost(
    latitude: Double? = null,
    longitude: Double? = null,
    note: String? = null,
    vm: CreateLocationWatchViewModel = hiltViewModel(),
) {
    NavigationEventHandler(vm)
    ErrorEventHandler(vm)

    val resolvedLocation by vm.resolvedLocation.collectAsState()
    val resolvedLabel by vm.resolvedLabel.collectAsState()
    val isResolving by vm.isResolving.collectAsState()

    var locationInput by remember { mutableStateOf("") }
    var labelValue by remember { mutableStateOf("") }
    // Null until the slider moves, then the picked stop in meters
    var pickedRadius by remember { mutableStateOf<Float?>(null) }
    var noteValue by remember { mutableStateOf(note ?: "") }

    LaunchedEffect(latitude, longitude) {
        if (latitude != null && longitude != null) {
            locationInput = "$latitude, $longitude"
            vm.resolveInput(locationInput)
        }
    }

    LaunchedEffect(resolvedLabel) {
        resolvedLabel?.let { labelValue = it }
    }

    val isAllowed by vm.isAllowed.collectAsState(true)
    val maxRadiusKm by vm.maxRadiusKm.collectAsState(LocationWatch.DEFAULT_MAX_RADIUS_KM)
    val distanceUnit = LocalUnitFormatter.current.distanceUnit
    val radiusStops = remember(maxRadiusKm, distanceUnit) { RadiusStops.forLimit(maxRadiusKm, distanceUnit) }
    val radiusInMeters = (pickedRadius ?: radiusStops.getOrNull(RadiusStops.nearestIndex(radiusStops, DEFAULT_RADIUS_METERS)))
        ?.let { RadiusStops.clamp(it, radiusStops) }
    val isValid = resolvedLocation != null && labelValue.isNotBlank() && isAllowed &&
            radiusInMeters != null && RadiusStops.allows(radiusInMeters, maxRadiusKm)

    AlertDialog(
        onDismissRequest = { vm.navUp() },
        title = { Text(stringResource(R.string.watch_list_add_location_title)) },
        text = {
            Column {
                Text(stringResource(R.string.watch_list_add_location_msg))
                if (!isAllowed) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.watch_create_feeder_required),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = locationInput,
                    onValueChange = { locationInput = it },
                    label = { Text(stringResource(R.string.watch_list_add_location_input_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))

                FilledTonalButton(
                    onClick = {
                        if (locationInput.isNotBlank()) {
                            vm.resolveInput(locationInput)
                        } else {
                            vm.useDeviceLocation()
                        }
                    },
                    enabled = !isResolving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (locationInput.isBlank()) {
                            stringResource(R.string.watch_list_add_location_use_current)
                        } else {
                            stringResource(R.string.watch_list_add_location_resolve_action)
                        }
                    )
                }

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = labelValue,
                    onValueChange = { labelValue = it },
                    label = { Text(stringResource(R.string.watch_list_add_location_label_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(16.dp))

                if (radiusInMeters != null) {
                    LocationRadiusPicker(
                        stops = radiusStops,
                        radiusInMeters = radiusInMeters,
                        onRadiusChange = { pickedRadius = it },
                    )
                }

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = noteValue,
                    onValueChange = { noteValue = it },
                    label = { Text(stringResource(R.string.watchlist_note_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val loc = resolvedLocation ?: return@TextButton
                    vm.create(
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        radiusInMeters = radiusInMeters ?: return@TextButton,
                        label = labelValue,
                        note = noteValue,
                    )
                },
                enabled = isValid,
            ) {
                Text(stringResource(R.string.common_add_action))
            }
        },
        dismissButton = {
            TextButton(onClick = { vm.navUp() }) {
                Text(stringResource(R.string.common_cancel_action))
            }
        },
    )
}

private const val DEFAULT_RADIUS_METERS = 25_000f
