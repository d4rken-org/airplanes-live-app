package eu.darken.apl.watch.ui.create

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import eu.darken.apl.watch.ui.AltitudeBandInput
import eu.darken.apl.watch.ui.AltitudeBandInputSaver
import eu.darken.apl.watch.ui.LocationAltitudeBandFields
import eu.darken.apl.watch.ui.LocationRadiusPicker
import eu.darken.apl.watch.ui.altitudeBandUnit
import eu.darken.apl.watch.ui.RadiusStops

@Composable
fun CreateLocationWatchDialogHost(
    latitude: Double? = null,
    longitude: Double? = null,
    label: String? = null,
    note: String? = null,
    vm: CreateLocationWatchViewModel = hiltViewModel(),
) {
    NavigationEventHandler(vm)
    ErrorEventHandler(vm)

    LaunchedEffect(latitude, longitude, label, note) {
        vm.init(latitude = latitude, longitude = longitude, label = label, note = note)
    }

    val draft = vm.draft
    val isResolving by vm.isResolving.collectAsState()

    val bandUnit = altitudeBandUnit()
    var bandInput by rememberSaveable(bandUnit, stateSaver = AltitudeBandInputSaver) {
        mutableStateOf(AltitudeBandInput.empty(bandUnit))
    }
    val band = bandInput.band

    val isAllowed by vm.isAllowed.collectAsState(true)
    val maxRadiusKm by vm.maxRadiusKm.collectAsState(LocationWatch.DEFAULT_MAX_RADIUS_KM)
    val distanceUnit = LocalUnitFormatter.current.distanceUnit
    val radiusStops = remember(maxRadiusKm, distanceUnit) { RadiusStops.forLimit(maxRadiusKm, distanceUnit) }
    val radiusInMeters = RadiusStops.shown(draft.pickedRadius, radiusStops)
    val isValid = draft.isResolved && !isResolving && draft.label.isNotBlank() && isAllowed &&
            radiusInMeters != null && RadiusStops.allows(radiusInMeters, maxRadiusKm) && band != null

    AlertDialog(
        onDismissRequest = { vm.navUp() },
        title = { Text(stringResource(R.string.watch_list_add_location_title)) },
        text = {
            // The band fields make it taller than small screens
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
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
                    value = draft.input,
                    onValueChange = { vm.onLocationInputChange(it) },
                    label = { Text(stringResource(R.string.watch_list_add_location_input_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))

                FilledTonalButton(
                    onClick = {
                        if (draft.input.isNotBlank()) {
                            vm.resolveInput()
                        } else {
                            vm.useDeviceLocation()
                        }
                    },
                    enabled = !isResolving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (draft.input.isBlank()) {
                            stringResource(R.string.watch_list_add_location_use_current)
                        } else {
                            stringResource(R.string.watch_list_add_location_resolve_action)
                        }
                    )
                }

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = draft.label,
                    onValueChange = { vm.onLabelChange(it) },
                    label = { Text(stringResource(R.string.watch_list_add_location_label_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(16.dp))

                if (radiusInMeters != null) {
                    LocationRadiusPicker(
                        stops = radiusStops,
                        radiusInMeters = radiusInMeters,
                        onRadiusChange = { vm.onRadiusChange(it) },
                    )
                }

                Spacer(Modifier.height(16.dp))

                LocationAltitudeBandFields(
                    input = bandInput,
                    onInputChange = { bandInput = it },
                )

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = draft.note,
                    onValueChange = { vm.onNoteChange(it) },
                    label = { Text(stringResource(R.string.watchlist_note_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val chosenBand = band ?: return@TextButton
                    vm.create(
                        radiusInMeters = radiusInMeters ?: return@TextButton,
                        minAltitudeFt = chosenBand.minFt,
                        maxAltitudeFt = chosenBand.maxFt,
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
