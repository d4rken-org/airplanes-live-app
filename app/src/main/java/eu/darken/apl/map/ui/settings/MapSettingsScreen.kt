package eu.darken.apl.map.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.apl.R
import eu.darken.apl.common.compose.aplContentWindowInsets
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.navigation.NavigationEventHandler
import eu.darken.apl.common.settings.SettingsPreferenceItem
import eu.darken.apl.common.settings.SettingsSwitchItem
import eu.darken.apl.map.core.MapLayer

@Composable
fun MapSettingsScreenHost(
    vm: MapSettingsViewModel = hiltViewModel(),
) {
    NavigationEventHandler(vm)
    ErrorEventHandler(vm)

    val state by vm.state.collectAsState(initial = null)
    state?.let {
        MapSettingsScreen(
            state = it,
            onBack = { vm.navUp() },
            onToggleRestoreLastView = { vm.toggleRestoreLastView() },
            onToggleNativeInfoPanel = { vm.toggleNativeInfoPanel() },
            onSetMapLayer = { vm.setMapLayer(it) },
        )
    }
}

@Composable
fun MapSettingsScreen(
    state: MapSettingsViewModel.State,
    onBack: () -> Unit,
    onToggleRestoreLastView: () -> Unit,
    onToggleNativeInfoPanel: () -> Unit,
    onSetMapLayer: (MapLayer) -> Unit,
) {
    var showLayerDialog by remember { mutableStateOf(false) }
    Scaffold(
        contentWindowInsets = aplContentWindowInsets(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.map_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
        ) {
            item {
                SettingsSwitchItem(
                    title = stringResource(R.string.map_settings_restore_last_view_title),
                    summary = stringResource(R.string.map_settings_restore_last_view_summary),
                    checked = state.isRestoreLastViewEnabled,
                    onCheckedChange = { onToggleRestoreLastView() },
                )
            }
            item {
                SettingsSwitchItem(
                    title = stringResource(R.string.map_settings_native_info_panel_title),
                    summary = stringResource(R.string.map_settings_native_info_panel_summary),
                    checked = state.isNativeInfoPanelEnabled,
                    onCheckedChange = { onToggleNativeInfoPanel() },
                )
            }
            item {
                SettingsPreferenceItem(
                    title = stringResource(R.string.map_settings_layer_title),
                    summary = stringResource(state.mapLayer.labelRes),
                    onClick = { showLayerDialog = true },
                )
            }
        }
    }

    if (showLayerDialog) {
        MapLayerDialog(
            selected = state.mapLayer,
            onSelect = { layer ->
                onSetMapLayer(layer)
                showLayerDialog = false
            },
            onDismiss = { showLayerDialog = false },
        )
    }
}

@Composable
private fun MapLayerDialog(
    selected: MapLayer,
    onSelect: (MapLayer) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.map_settings_layer_title)) },
        text = {
            Column {
                MapLayer.entries.forEach { layer ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(layer) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = layer == selected,
                            onClick = { onSelect(layer) },
                        )
                        Text(
                            text = stringResource(layer.labelRes),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel_action))
            }
        },
    )
}
