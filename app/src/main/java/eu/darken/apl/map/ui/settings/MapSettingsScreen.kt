package eu.darken.apl.map.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.apl.R
import eu.darken.apl.common.compose.aplContentWindowInsets
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.navigation.NavigationEventHandler
import eu.darken.apl.common.settings.SettingsSwitchItem

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
        )
    }
}

@Composable
fun MapSettingsScreen(
    state: MapSettingsViewModel.State,
    onBack: () -> Unit,
    onToggleRestoreLastView: () -> Unit,
    onToggleNativeInfoPanel: () -> Unit,
) {
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
        }
    }
}
