package eu.darken.apl.feeder.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import eu.darken.apl.watch.ui.settings.IntervalPickerDialog
import java.time.Duration

@Composable
fun FeederSettingsScreenHost(
    vm: FeederSettingsViewModel = hiltViewModel(),
) {
    NavigationEventHandler(vm)
    ErrorEventHandler(vm)

    val state by vm.state.collectAsState(initial = null)
    state?.let {
        FeederSettingsScreen(
            state = it,
            onBack = { vm.navUp() },
            onUpdateInterval = { vm.updateFeederInterval(it) },
            onResetInterval = { vm.resetFeederInterval() },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeederSettingsScreen(
    state: FeederSettingsViewModel.State,
    onBack: () -> Unit,
    onUpdateInterval: (Duration) -> Unit,
    onResetInterval: () -> Unit,
) {
    var showIntervalDialog by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = aplContentWindowInsets(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feeder_settings_title)) },
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
                SettingsPreferenceItem(
                    title = stringResource(R.string.watch_settings_monitor_interval_title),
                    summary = stringResource(R.string.watch_settings_monitor_interval_summary),
                    onClick = { showIntervalDialog = true },
                )
            }
        }
    }

    if (showIntervalDialog) {
        IntervalPickerDialog(
            title = stringResource(R.string.watch_settings_monitor_interval_title),
            currentMinutes = state.currentIntervalMinutes,
            onSave = { minutes ->
                onUpdateInterval(Duration.ofMinutes(minutes.toLong()))
                showIntervalDialog = false
            },
            onReset = {
                onResetInterval()
                showIntervalDialog = false
            },
            onDismiss = { showIntervalDialog = false },
        )
    }
}
