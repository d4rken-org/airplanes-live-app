package eu.darken.apl.map.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.Layers
import androidx.compose.material.icons.twotone.Restore
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.apl.R
import eu.darken.apl.common.compose.aplContentWindowInsets
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.navigation.NavigationEventHandler
import eu.darken.apl.common.settings.SettingsPreferenceItem
import eu.darken.apl.common.settings.SettingsSwitchItem
import eu.darken.apl.map.core.NativeMapStyle

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
            onSetNativeMapStyle = { style -> vm.setNativeMapStyle(style) },
            onToggleRestoreLastView = { vm.toggleRestoreLastView() },
        )
    }
}

@Composable
fun MapSettingsScreen(
    state: MapSettingsViewModel.State,
    onBack: () -> Unit,
    onSetNativeMapStyle: (NativeMapStyle) -> Unit,
    onToggleRestoreLastView: () -> Unit,
) {
    var showStyleDialog by remember { mutableStateOf(false) }
    Scaffold(
        contentWindowInsets = aplContentWindowInsets(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.map_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = null)
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
                    icon = Icons.TwoTone.Restore,
                    onCheckedChange = { onToggleRestoreLastView() },
                )
            }
            item {
                SettingsPreferenceItem(
                    title = stringResource(R.string.map_settings_layer_title),
                    summary = stringResource(state.nativeMapStyle.labelRes),
                    icon = Icons.TwoTone.Layers,
                    onClick = { showStyleDialog = true },
                )
            }
        }
    }

    if (showStyleDialog) {
        MapStyleDialog(
            selected = state.nativeMapStyle,
            onSelect = { style ->
                onSetNativeMapStyle(style)
                showStyleDialog = false
            },
            onDismiss = { showStyleDialog = false },
        )
    }
}

@Composable
private fun MapStyleDialog(
    selected: NativeMapStyle,
    onSelect: (NativeMapStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.map_settings_layer_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                NativeMapStyle.entries.forEach { style ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(style) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = style == selected,
                            onClick = { onSelect(style) },
                        )
                        Text(
                            text = stringResource(style.labelRes),
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
