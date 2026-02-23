package eu.darken.apl.main.ui.settings.support

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import eu.darken.apl.common.settings.SettingsCategoryHeader
import eu.darken.apl.common.settings.SettingsPreferenceItem

@Composable
fun SupportScreenHost(
    vm: SupportViewModel = hiltViewModel(),
) {
    NavigationEventHandler(vm)
    ErrorEventHandler(vm)

    val isRecording by vm.isRecording.collectAsState(initial = false)

    SupportScreen(
        isRecording = isRecording,
        onBack = { vm.navUp() },
        onDocumentation = { vm.openDocumentation() },
        onIssueTracker = { vm.openIssueTracker() },
        onAirplanesLiveDiscord = { vm.openAirplanesLiveDiscord() },
        onDarkensDiscord = { vm.openDarkensDiscord() },
        onStartDebugLog = { vm.startDebugLog() },
        onStopDebugLog = { vm.stopDebugLog() },
        onPrivacyPolicy = { vm.openPrivacyPolicy() },
    )
}

@Composable
fun SupportScreen(
    isRecording: Boolean,
    onBack: () -> Unit,
    onDocumentation: () -> Unit,
    onIssueTracker: () -> Unit,
    onAirplanesLiveDiscord: () -> Unit,
    onDarkensDiscord: () -> Unit,
    onStartDebugLog: () -> Unit,
    onStopDebugLog: () -> Unit,
    onPrivacyPolicy: () -> Unit,
) {
    var showConsentDialog by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = aplContentWindowInsets(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_support_label)) },
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
                    title = stringResource(R.string.documentation_label),
                    onClick = onDocumentation,
                )
            }
            item {
                SettingsPreferenceItem(
                    title = stringResource(R.string.issue_tracker_label),
                    summary = stringResource(R.string.issue_tracker_description),
                    onClick = onIssueTracker,
                )
            }
            item {
                SettingsPreferenceItem(
                    title = "airplanes.live Discord",
                    summary = stringResource(R.string.support_airplanes_live_discord_desc),
                    onClick = onAirplanesLiveDiscord,
                )
            }
            item {
                SettingsPreferenceItem(
                    title = "darken's Discord",
                    summary = stringResource(R.string.support_darkens_discord_desc),
                    onClick = onDarkensDiscord,
                )
            }

            item { SettingsCategoryHeader(title = stringResource(R.string.settings_category_other_label)) }

            item {
                SettingsPreferenceItem(
                    title = stringResource(
                        if (isRecording) R.string.debug_debuglog_stop_action
                        else R.string.debug_debuglog_record_action
                    ),
                    summary = stringResource(R.string.support_debuglog_desc),
                    onClick = {
                        if (isRecording) {
                            onStopDebugLog()
                        } else {
                            showConsentDialog = true
                        }
                    },
                )
            }
        }
    }

    if (showConsentDialog) {
        AlertDialog(
            onDismissRequest = { showConsentDialog = false },
            title = { Text(stringResource(R.string.support_debuglog_label)) },
            text = { Text(stringResource(R.string.settings_debuglog_explanation)) },
            confirmButton = {
                TextButton(onClick = {
                    showConsentDialog = false
                    onStartDebugLog()
                }) {
                    Text(stringResource(R.string.debug_debuglog_record_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConsentDialog = false }) {
                    Text(stringResource(R.string.common_cancel_action))
                }
            },
        )
    }
}
