package eu.darken.apl.upgrade.ui

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.CellTower
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.RadioButtonChecked
import androidx.compose.material.icons.twotone.RadioButtonUnchecked
import androidx.compose.material.icons.twotone.SearchOff
import androidx.compose.material.icons.twotone.Lan
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.apl.R
import eu.darken.apl.common.compose.Preview2
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.navigation.LocalNavigationController
import eu.darken.apl.common.navigation.NavigationEventHandler
import eu.darken.apl.server.api.ServerCodes
import java.util.UUID

@Composable
fun FeederRegisterScreenHost(
    vm: FeederRegisterViewModel = hiltViewModel(),
) {
    NavigationEventHandler(vm)
    ErrorEventHandler(vm)

    val state by vm.state.collectAsState()
    val navController = LocalNavigationController.current
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is FeederRegisterEvents.FeedersFound -> snackbarHostState.showSnackbar(
                    when (event.host) {
                        null -> context.resources.getQuantityString(
                            R.plurals.upgrade_register_detect_found_x,
                            event.count,
                            event.count,
                        )

                        else -> context.resources.getQuantityString(
                            R.plurals.upgrade_register_detect_found_x_at_y,
                            event.count,
                            event.count,
                            event.host,
                        )
                    }
                )
            }
        }
    }

    FeederRegisterScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onNavigateUp = { navController?.up() },
        onFeederSetup = vm::openFeederSetup,
        onSelect = vm::select,
        onAddManual = vm::addManual,
        onDetect = vm::detect,
        onLink = vm::link,
    )
}

@Composable
fun FeederRegisterScreen(
    state: FeederRegisterViewModel.State,
    snackbarHostState: SnackbarHostState,
    onNavigateUp: () -> Unit,
    onFeederSetup: () -> Unit,
    onSelect: (String) -> Unit,
    onAddManual: (String) -> Unit,
    onDetect: () -> Unit,
    onLink: () -> Unit,
) {
    var showManualEntry by remember { mutableStateOf(false) }

    UpgradeScreenScaffold(
        title = stringResource(R.string.upgrade_register_title),
        onNavigateUp = onNavigateUp,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        UpgradeScreenContent(contentPadding) {
            UpgradeHeroCard(
                text = stringResource(R.string.upgrade_register_msg),
                icon = Icons.TwoTone.CellTower,
            )

            // Registering again against an existing link can only be rejected by the server
            if (state.isLinked) {
                UpgradeSectionCard(
                    title = stringResource(R.string.upgrade_register_already_linked_title),
                    icon = Icons.TwoTone.CellTower,
                ) {
                    Text(
                        text = stringResource(R.string.upgrade_register_already_linked_msg),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    UpgradeCardActions {
                        Button(onClick = onNavigateUp) {
                            Text(stringResource(R.string.common_done_action))
                        }
                    }
                }
            } else {
                UpgradeSectionCard(
                    title = stringResource(R.string.upgrade_register_detect_title),
                    icon = Icons.TwoTone.Lan,
                ) {
                    state.detection
                        ?.takeIf { it.found.isEmpty() }
                        ?.let { detection -> NothingDetected(detection.host) }

                    state.candidates.forEach { candidate ->
                        CandidateRow(
                            feederId = candidate,
                            host = state.detection?.takeIf { candidate in it.found }?.host,
                            isSelected = candidate == state.selected,
                            // An attempt already names a feeder, changing it under one would make
                            // the message that comes back describe a different id than is shown
                            enabled = !state.isBusy,
                            onClick = { onSelect(candidate) },
                        )
                    }

                    registrationError(state)?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    UpgradeCardActions {
                        if (state.isBusy) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        if (state.canEnterManually) {
                            TextButton(
                                onClick = { showManualEntry = true },
                                enabled = !state.isBusy,
                            ) {
                                Text(stringResource(R.string.feeder_link_manual_action))
                            }
                        }
                        // Linking takes over as the primary action once there is something to link
                        if (state.selected != null) {
                            OutlinedButton(onClick = onDetect, enabled = !state.isBusy) {
                                Text(stringResource(R.string.feeder_link_detect_action))
                            }
                        } else {
                            Button(onClick = onDetect, enabled = !state.isBusy) {
                                Text(stringResource(R.string.feeder_link_detect_action))
                            }
                        }
                        if (state.selected != null) {
                            Button(onClick = onLink, enabled = !state.isBusy) {
                                Text(stringResource(R.string.feeder_link_action))
                            }
                        }
                    }
                }
            }

            UpgradeSectionCard(
                title = stringResource(R.string.upgrade_feeder_title),
                icon = Icons.TwoTone.Info,
            ) {
                Text(
                    text = stringResource(R.string.upgrade_feeder_msg),
                    style = MaterialTheme.typography.bodyMedium,
                )
                UpgradeCardActions {
                    TextButton(onClick = onFeederSetup) {
                        Text(stringResource(R.string.upgrade_feeder_setup_action))
                    }
                }
            }
        }
    }

    if (showManualEntry) {
        ManualEntryDialog(
            onDismiss = { showManualEntry = false },
            onConfirm = { feederId ->
                onAddManual(feederId)
                showManualEntry = false
            },
        )
    }
}

/** A feeder id is a UUID; anything else the server can only reject. */
private fun isValidFeederId(input: String): Boolean = try {
    UUID.fromString(input.trim())
    input.isNotBlank()
} catch (_: IllegalArgumentException) {
    false
}

@Composable
private fun ManualEntryDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val isValid = isValidFeederId(input)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.upgrade_register_manual_title)) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text(stringResource(R.string.feeder_link_id_label)) },
                singleLine = true,
                isError = input.isNotBlank() && !isValid,
                supportingText = if (input.isNotBlank() && !isValid) {
                    { Text(stringResource(R.string.feeder_link_error_invalid)) }
                } else null,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(input.trim()) },
                enabled = isValid,
            ) {
                Text(stringResource(R.string.common_add_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel_action))
            }
        },
    )
}

/** Named by the address the scan looked on, so an empty result is still an answer. */
@Composable
private fun NothingDetected(host: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.TwoTone.SearchOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp),
        )
        Text(
            text = when (host) {
                null -> stringResource(R.string.upgrade_register_detect_none)
                else -> stringResource(R.string.upgrade_register_detect_none_at_x, host)
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CandidateRow(
    feederId: String,
    host: String?,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isSelected) Icons.TwoTone.RadioButtonChecked else Icons.TwoTone.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = feederId,
                style = MaterialTheme.typography.bodySmall,
            )
            host?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun registrationError(state: FeederRegisterViewModel.State): String? = when {
    state.noIpv4 -> stringResource(R.string.feeder_link_error_no_ipv4)
    state.outcomeUnknown -> stringResource(R.string.feeder_link_error_unknown_outcome)
    state.errorCode != null -> registrationErrorForCode(state)
    state.detectFailed -> stringResource(R.string.feeder_link_error_detect)
    else -> null
}

@Composable
private fun registrationErrorForCode(state: FeederRegisterViewModel.State): String? = when {
    state.errorCode == null -> null
    else -> when (state.errorCode) {
        ServerCodes.FEEDER_INACTIVE -> stringResource(R.string.feeder_link_error_inactive)
        ServerCodes.FEEDER_NETWORK_MISMATCH -> stringResource(R.string.feeder_link_error_network_mismatch)
        ServerCodes.FEEDER_VERIFICATION_UNAVAILABLE -> stringResource(R.string.feeder_link_error_unavailable)
        ServerCodes.FEEDER_VERIFICATION_ERROR -> stringResource(R.string.feeder_link_error_verification)
        ServerCodes.QUOTA_EXCEEDED -> when (val retryAfter = state.retryAfterSeconds) {
            null -> stringResource(R.string.feeder_link_error_quota)
            else -> stringResource(
                R.string.feeder_link_error_quota_x,
                DateUtils.formatElapsedTime(retryAfter),
            )
        }

        ServerCodes.INVALID_REQUEST -> stringResource(R.string.feeder_link_error_invalid)
        else -> stringResource(R.string.feeder_link_error_generic)
    }
}

@Preview2
@Composable
private fun FeederRegisterScreenPreview() {
    PreviewWrapper {
        FeederRegisterScreen(
            state = FeederRegisterViewModel.State(
                detection = FeederRegisterViewModel.Detection(
                    host = "203.0.113.7",
                    found = listOf("0199a1f2-0000-7000-8000-a1b2c3d4e5f6"),
                ),
                selected = "0199a1f2-0000-7000-8000-a1b2c3d4e5f6",
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onNavigateUp = {},
            onFeederSetup = {},
            onSelect = {},
            onAddManual = {},
            onDetect = {},
            onLink = {},
        )
    }
}

@Preview2
@Composable
private fun FeederRegisterScreenEmptyDetectionPreview() {
    PreviewWrapper {
        FeederRegisterScreen(
            state = FeederRegisterViewModel.State(
                detection = FeederRegisterViewModel.Detection(host = "203.0.113.7", found = emptyList()),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onNavigateUp = {},
            onFeederSetup = {},
            onSelect = {},
            onAddManual = {},
            onDetect = {},
            onLink = {},
        )
    }
}
