package eu.darken.apl.upgrade.ui

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.CellTower
import androidx.compose.material.icons.twotone.DataUsage
import androidx.compose.material.icons.twotone.PhoneAndroid
import androidx.compose.material.icons.twotone.Stars
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import eu.darken.apl.feeder.core.link.FeederLinkRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.server.api.LinkedFeeder
import eu.darken.apl.server.api.ServerCodes
import eu.darken.apl.upgrade.UpgradeRepo
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun UpgradeScreenHost(
    vm: UpgradeViewModel = hiltViewModel(),
) {
    NavigationEventHandler(vm)
    ErrorEventHandler(vm)

    val state by vm.state.collectAsState(initial = null)
    val navController = LocalNavigationController.current

    UpgradeScreen(
        state = state ?: UpgradeViewModel.State(),
        onNavigateUp = { navController?.up() },
        onRefresh = vm::refresh,
        onResetIdentity = vm::resetIdentity,
        onFeederSetup = vm::openFeederSetup,
        onInputChanged = vm::updateInput,
        onDetect = vm::detect,
        onLink = vm::link,
        onUnlink = vm::unlink,
    )
}

@Composable
fun UpgradeScreen(
    state: UpgradeViewModel.State,
    onNavigateUp: () -> Unit,
    onRefresh: () -> Unit,
    onResetIdentity: () -> Unit,
    onFeederSetup: () -> Unit,
    onInputChanged: (String) -> Unit,
    onDetect: () -> Unit,
    onLink: (String) -> Unit,
    onUnlink: () -> Unit,
) {
    var showUnlinkConfirmation by remember { mutableStateOf(false) }

    UpgradeScreenScaffold(
        title = stringResource(R.string.upgrade_title),
        onNavigateUp = onNavigateUp,
    ) { contentPadding ->
        UpgradeScreenContent(contentPadding) {
            UpgradeStatusCard(
                title = when {
                    state.isPro -> stringResource(R.string.upgrade_status_pro)
                    state.isSettled -> stringResource(R.string.upgrade_status_free)
                    else -> stringResource(R.string.upgrade_status_checking)
                },
                body = when (state.source) {
                    UpgradeRepo.Source.FEEDER -> stringResource(R.string.upgrade_status_source_feeder)
                    UpgradeRepo.Source.SUBSCRIPTION -> stringResource(R.string.upgrade_status_source_subscription)
                    null -> null
                },
                isBusy = state.isBusy,
            )

            // A link that has stopped granting access still needs its status and its unlink action
            (state.link as? FeederLinkRepo.FeederLinkState.Linked)?.let { linked ->
                LinkedFeederCard(
                    feeder = linked.feeder,
                    onUnlink = { showUnlinkConfirmation = true },
                )
            }

            if (!state.isPro) {
                UpgradeHeroCard(text = stringResource(R.string.upgrade_hero_msg))

                UpgradeSectionCard(
                    title = stringResource(R.string.upgrade_features_title),
                    icon = Icons.TwoTone.Stars,
                ) {
                    UpgradeFeatureList(text = stringResource(R.string.upgrade_features_msg))
                }

                state.access?.takeIf { it.showsAllowances }?.let { access ->
                    UsageCard(access = access)
                }

                FeederCard(
                    registration = state.registration,
                    showRegisterControls = state.link !is FeederLinkRepo.FeederLinkState.Linked,
                    onFeederSetup = onFeederSetup,
                    onInputChanged = onInputChanged,
                    onDetect = onDetect,
                    onLink = onLink,
                )

                if (state.type == UpgradeRepo.Type.GPLAY) {
                    UpgradeSectionCard(
                        title = stringResource(R.string.upgrade_subscription_title),
                        icon = Icons.TwoTone.Stars,
                    ) {
                        Text(
                            text = stringResource(R.string.upgrade_subscription_planned_msg),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            InstallationCard(
                state = state,
                onRefresh = onRefresh,
                onResetIdentity = onResetIdentity,
            )
        }
    }

    if (showUnlinkConfirmation) {
        AlertDialog(
            onDismissRequest = { showUnlinkConfirmation = false },
            title = { Text(stringResource(R.string.upgrade_unlink_confirmation_title)) },
            text = { Text(stringResource(R.string.upgrade_unlink_confirmation_msg)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUnlink()
                        showUnlinkConfirmation = false
                    },
                ) {
                    Text(stringResource(R.string.feeder_link_unlink_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnlinkConfirmation = false }) {
                    Text(stringResource(R.string.common_cancel_action))
                }
            },
        )
    }
}

@Composable
private fun LinkedFeederCard(
    feeder: LinkedFeeder,
    onUnlink: () -> Unit,
) {
    UpgradeSectionCard(
        title = stringResource(R.string.upgrade_linked_feeder_title),
        icon = Icons.TwoTone.CellTower,
    ) {
        Text(
            text = feeder.feederId,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = if (feeder.eligible) {
                stringResource(
                    R.string.feeder_access_valid_until_x,
                    DateUtils.formatDateTime(
                        LocalContext.current,
                        feeder.validUntil,
                        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL,
                    ),
                )
            } else {
                stringResource(R.string.feeder_access_expired)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (feeder.eligible) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        Text(
            text = stringResource(
                R.string.feeder_access_status_x,
                when (feeder.status) {
                    STATUS_ACTIVE -> stringResource(R.string.feeder_access_status_active)
                    STATUS_INACTIVE -> stringResource(R.string.feeder_access_status_inactive)
                    STATUS_NOT_FOUND -> stringResource(R.string.feeder_access_status_not_found)
                    else -> stringResource(R.string.feeder_access_status_unavailable)
                },
            ),
            style = MaterialTheme.typography.bodySmall,
        )
        if (!feeder.networkVerified) {
            Text(
                text = stringResource(R.string.feeder_access_network_unverified),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onUnlink) {
            Text(stringResource(R.string.feeder_link_unlink_action))
        }
    }
}

@Composable
private fun UsageCard(access: AccessState) {
    UpgradeSectionCard(
        title = stringResource(R.string.upgrade_usage_title),
        icon = Icons.TwoTone.DataUsage,
    ) {
        val usage = access.usage
        Text(stringResource(R.string.upgrade_usage_viewing_x_of_y, usage.viewing.used, usage.viewing.limit))
        Text(stringResource(R.string.upgrade_usage_search_x_of_y, usage.search.used, usage.search.limit))
        Text(stringResource(R.string.upgrade_usage_watch_x_of_y, usage.watch.used, usage.watch.limit))
        Text(
            text = stringResource(
                R.string.upgrade_usage_resets_x,
                RESET_FORMAT.format(access.resetsAt.atZone(ZoneId.systemDefault())),
            ),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun FeederCard(
    registration: UpgradeViewModel.Registration,
    showRegisterControls: Boolean,
    onFeederSetup: () -> Unit,
    onInputChanged: (String) -> Unit,
    onDetect: () -> Unit,
    onLink: (String) -> Unit,
) {
    UpgradeSectionCard(
        title = stringResource(R.string.upgrade_feeder_title),
        icon = Icons.TwoTone.CellTower,
    ) {
        Text(
            text = stringResource(R.string.upgrade_feeder_msg),
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onFeederSetup) {
            Text(stringResource(R.string.upgrade_feeder_setup_action))
        }

        // Re-registering the feeder that is already linked can only be rejected
        if (showRegisterControls) {
            OutlinedTextField(
                value = registration.input,
                onValueChange = onInputChanged,
                label = { Text(stringResource(R.string.feeder_link_id_label)) },
                singleLine = true,
                enabled = !registration.isBusy,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDetect, enabled = !registration.isBusy) {
                    Text(stringResource(R.string.feeder_link_detect_action))
                }
                TextButton(
                    onClick = { onLink(registration.input) },
                    enabled = registration.input.isNotBlank() && !registration.isBusy,
                ) {
                    Text(stringResource(R.string.feeder_link_action))
                }
            }
            registration.candidates.forEach { candidate ->
                Text(
                    text = candidate,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onInputChanged(candidate) }
                        .padding(vertical = 4.dp),
                )
            }
            registrationError(registration)?.let { message ->
                Text(text = message, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun InstallationCard(
    state: UpgradeViewModel.State,
    onRefresh: () -> Unit,
    onResetIdentity: () -> Unit,
) {
    UpgradeSectionCard(
        title = stringResource(R.string.upgrade_installation_title),
        icon = Icons.TwoTone.PhoneAndroid,
    ) {
        state.installationId?.let { installationId ->
            Text(
                text = stringResource(R.string.upgrade_installation_x, installationId.take(8)),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (state.access?.restricted == true) {
            UpgradeInlineStateCard(
                title = stringResource(R.string.upgrade_restricted_title),
                body = stringResource(R.string.upgrade_restricted_msg),
                icon = Icons.TwoTone.Warning,
            )
        }
        TextButton(onClick = onRefresh, enabled = !state.isRefreshing) {
            Text(stringResource(R.string.upgrade_refresh_action))
        }
        if (state.canResetIdentity) {
            TextButton(onClick = onResetIdentity) {
                Text(stringResource(R.string.upgrade_reset_identity_action))
            }
        }
    }
}

@Composable
private fun registrationError(registration: UpgradeViewModel.Registration): String? = when {
    registration.failed -> stringResource(R.string.feeder_link_error_generic)
    registration.errorCode == null -> null
    else -> when (registration.errorCode) {
        ServerCodes.FEEDER_INACTIVE -> stringResource(R.string.feeder_link_error_inactive)
        ServerCodes.FEEDER_NETWORK_MISMATCH -> stringResource(R.string.feeder_link_error_network_mismatch)
        ServerCodes.FEEDER_VERIFICATION_UNAVAILABLE -> stringResource(R.string.feeder_link_error_unavailable)
        ServerCodes.FEEDER_VERIFICATION_ERROR -> stringResource(R.string.feeder_link_error_verification)
        ServerCodes.QUOTA_EXCEEDED -> when (val retryAfter = registration.retryAfterSeconds) {
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

/** Values of [LinkedFeeder.status], anything else reads as unavailable. */
private const val STATUS_ACTIVE = "active"
private const val STATUS_INACTIVE = "inactive"
private const val STATUS_NOT_FOUND = "not_found"

private val RESET_FORMAT: DateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)

@Preview2
@Composable
private fun UpgradeScreenFreePreview() {
    PreviewWrapper {
        UpgradeScreen(
            state = UpgradeViewModel.State(
                isSettled = true,
                installationId = "0123456789abcdef",
            ),
            onNavigateUp = {},
            onRefresh = {},
            onResetIdentity = {},
            onFeederSetup = {},
            onInputChanged = {},
            onDetect = {},
            onLink = {},
            onUnlink = {},
        )
    }
}
