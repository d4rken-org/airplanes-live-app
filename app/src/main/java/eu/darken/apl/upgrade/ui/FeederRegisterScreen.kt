package eu.darken.apl.upgrade.ui

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.CellTower
import androidx.compose.material.icons.twotone.CheckCircle
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.RadioButtonChecked
import androidx.compose.material.icons.twotone.RadioButtonUnchecked
import androidx.compose.material.icons.twotone.SearchOff
import androidx.compose.material.icons.twotone.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
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

@Composable
fun FeederRegisterScreenHost(
    vm: FeederRegisterViewModel = hiltViewModel(),
) {
    NavigationEventHandler(vm)
    ErrorEventHandler(vm)

    val state by vm.state.collectAsState()
    val navController = LocalNavigationController.current

    FeederRegisterScreen(
        state = state,
        onNavigateUp = { navController?.up() },
        onFeederSetup = vm::openFeederSetup,
        onInputChanged = vm::updateInput,
        onDetect = vm::detect,
        onLink = vm::link,
    )
}

@Composable
fun FeederRegisterScreen(
    state: FeederRegisterViewModel.State,
    onNavigateUp: () -> Unit,
    onFeederSetup: () -> Unit,
    onInputChanged: (String) -> Unit,
    onDetect: () -> Unit,
    onLink: (String) -> Unit,
) {
    UpgradeScreenScaffold(
        title = stringResource(R.string.upgrade_register_title),
        onNavigateUp = onNavigateUp,
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
                    icon = Icons.TwoTone.Wifi,
                ) {
                    state.detection?.let { detection -> DetectionResult(detection) }

                    state.candidates.forEach { candidate ->
                        CandidateRow(
                            feederId = candidate,
                            isSelected = candidate == state.input,
                            onClick = { onInputChanged(candidate) },
                        )
                    }

                    UpgradeCardActions {
                        OutlinedButton(onClick = onDetect, enabled = !state.isBusy) {
                            Text(stringResource(R.string.feeder_link_detect_action))
                        }
                    }
                }

                UpgradeSectionCard(
                    title = stringResource(R.string.upgrade_register_manual_title),
                    icon = Icons.TwoTone.CellTower,
                ) {
                    OutlinedTextField(
                        value = state.input,
                        onValueChange = onInputChanged,
                        label = { Text(stringResource(R.string.feeder_link_id_label)) },
                        singleLine = true,
                        enabled = !state.isBusy,
                        isError = registrationError(state) != null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    registrationError(state)?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    UpgradeCardActions {
                        Button(
                            onClick = { onLink(state.input) },
                            enabled = state.input.isNotBlank() && !state.isBusy,
                        ) {
                            Text(stringResource(R.string.feeder_link_action))
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
}

/** What the scan saw, named by the address it looked on, so an empty result is still an answer. */
@Composable
private fun DetectionResult(detection: FeederRegisterViewModel.Detection) {
    val found = detection.found.size
    val host = detection.host
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = if (found > 0) Icons.TwoTone.CheckCircle else Icons.TwoTone.SearchOff,
            contentDescription = null,
            tint = if (found > 0) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp),
        )
        Text(
            text = when {
                found > 0 && host != null ->
                    pluralStringResource(R.plurals.upgrade_register_detect_found_x_at_y, found, found, host)

                found > 0 -> pluralStringResource(R.plurals.upgrade_register_detect_found_x, found, found)
                host != null -> stringResource(R.string.upgrade_register_detect_none_at_x, host)
                else -> stringResource(R.string.upgrade_register_detect_none)
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CandidateRow(
    feederId: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
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
        Text(
            text = feederId,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun registrationError(state: FeederRegisterViewModel.State): String? = when {
    state.failed -> stringResource(R.string.feeder_link_error_generic)
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
                monitored = listOf("0199a1f2-0000-7000-8000-a1b2c3d4e5f6"),
            ),
            onNavigateUp = {},
            onFeederSetup = {},
            onInputChanged = {},
            onDetect = {},
            onLink = {},
        )
    }
}
