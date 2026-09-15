package eu.darken.apl.upgrade.ui

import android.text.format.DateUtils
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.AutoAwesome
import androidx.compose.material.icons.twotone.CellTower
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.material.icons.twotone.ShoppingCart
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.apl.R
import eu.darken.apl.common.compose.Preview2
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.navigation.LocalNavigationController
import eu.darken.apl.common.navigation.NavigationEventHandler
import eu.darken.apl.feeder.core.link.FeederLinkRepo
import eu.darken.apl.server.api.LinkedFeeder
import eu.darken.apl.upgrade.UpgradeRepo

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
        onRegister = vm::openRegister,
        onUnlink = vm::unlink,
    )
}

@Composable
fun UpgradeScreen(
    state: UpgradeViewModel.State,
    onNavigateUp: () -> Unit,
    onRefresh: () -> Unit,
    onRegister: () -> Unit,
    onUnlink: () -> Unit,
) {
    var showUnlinkConfirmation by remember { mutableStateOf(false) }

    UpgradeScreenScaffold(
        title = stringResource(R.string.upgrade_title),
        onNavigateUp = onNavigateUp,
        actions = {
            IconButton(onClick = onRefresh, enabled = !state.isRefreshing) {
                Icon(
                    imageVector = Icons.TwoTone.Refresh,
                    contentDescription = stringResource(R.string.upgrade_refresh_action),
                )
            }
        },
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
                    icon = Icons.TwoTone.AutoAwesome,
                ) {
                    UpgradeFeatureList(text = stringResource(R.string.upgrade_features_msg))
                }

                // Re-registering the feeder that is already linked can only be rejected
                if (state.link !is FeederLinkRepo.FeederLinkState.Linked) {
                    FeederOptionCard(onRegister = onRegister)
                }

                if (state.type == UpgradeRepo.Type.GPLAY) {
                    UpgradeSectionCard(
                        title = stringResource(R.string.upgrade_subscription_title),
                        icon = Icons.TwoTone.ShoppingCart,
                    ) {
                        Text(
                            text = stringResource(R.string.upgrade_subscription_planned_msg),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            if (state.access?.restricted == true) {
                UpgradeInlineStateCard(
                    title = stringResource(R.string.upgrade_restricted_title),
                    body = stringResource(R.string.upgrade_restricted_msg),
                    icon = Icons.TwoTone.Warning,
                )
            }

            state.installationId?.let { installationId ->
                UpgradeFootnote(
                    text = stringResource(R.string.upgrade_installation_x, installationId.take(8)),
                )
            }
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
private fun FeederOptionCard(onRegister: () -> Unit) {
    UpgradeSectionCard(
        title = stringResource(R.string.upgrade_feeder_title),
        icon = Icons.TwoTone.CellTower,
    ) {
        Text(
            text = stringResource(R.string.upgrade_feeder_msg),
            style = MaterialTheme.typography.bodyMedium,
        )
        UpgradeCardActions {
            Button(onClick = onRegister) {
                Text(stringResource(R.string.upgrade_feeder_register_action))
            }
        }
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        UpgradeStateRow(
            text = when (feeder.status) {
                STATUS_ACTIVE -> stringResource(R.string.upgrade_linked_feeder_status_active)
                STATUS_INACTIVE -> stringResource(R.string.upgrade_linked_feeder_status_inactive)
                STATUS_NOT_FOUND -> stringResource(R.string.upgrade_linked_feeder_status_not_found)
                else -> stringResource(R.string.upgrade_linked_feeder_status_unavailable)
            },
            color = when (feeder.status) {
                STATUS_ACTIVE -> MaterialTheme.colorScheme.primary
                STATUS_INACTIVE, STATUS_NOT_FOUND -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            // A feeder that stopped is only actionable if you know when it stopped
            detail = feeder.lastActiveAt
                .takeIf { feeder.status != STATUS_ACTIVE && it > 0 }
                ?.let {
                    DateUtils.getRelativeTimeSpanString(
                        it,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                    ).toString()
                },
        )

        UpgradeCardActions {
            TextButton(onClick = onUnlink) {
                Text(stringResource(R.string.feeder_link_unlink_action))
            }
        }
    }
}


/** Values of [LinkedFeeder.status], anything else reads as unavailable. */
private const val STATUS_ACTIVE = "active"
private const val STATUS_INACTIVE = "inactive"
private const val STATUS_NOT_FOUND = "not_found"

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
            onRegister = {},
            onUnlink = {},
        )
    }
}
