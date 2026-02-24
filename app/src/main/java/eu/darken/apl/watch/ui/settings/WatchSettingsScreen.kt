package eu.darken.apl.watch.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.apl.R
import eu.darken.apl.common.compose.aplContentWindowInsets
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.navigation.NavigationEventHandler
import eu.darken.apl.common.settings.SettingsPreferenceItem
import java.time.Duration

@Composable
fun WatchSettingsScreenHost(
    vm: WatchSettingsViewModel = hiltViewModel(),
) {
    NavigationEventHandler(vm)
    ErrorEventHandler(vm)

    val state by vm.state.collectAsState(initial = null)
    state?.let {
        WatchSettingsScreen(
            state = it,
            onBack = { vm.navUp() },
            onUpdateInterval = { vm.updateWatchInterval(it) },
            onResetInterval = { vm.resetWatchInterval() },
        )
    }
}

@Composable
fun WatchSettingsScreen(
    state: WatchSettingsViewModel.State,
    onBack: () -> Unit,
    onUpdateInterval: (Duration) -> Unit,
    onResetInterval: () -> Unit,
) {
    var showIntervalDialog by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = aplContentWindowInsets(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.watch_settings_title)) },
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
                    icon = Icons.Outlined.Timer,
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

@Composable
internal fun IntervalPickerDialog(
    title: String,
    currentMinutes: Float,
    onSave: (Float) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var sliderValue by remember { mutableFloatStateOf(currentMinutes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = pluralStringResource(
                        R.plurals.watch_setting_check_interval_minutes,
                        sliderValue.toInt(),
                        sliderValue.toInt(),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    valueRange = 15f..1440f,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(sliderValue) }) {
                Text(stringResource(R.string.common_save_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel_action))
            }
        },
    )
}
