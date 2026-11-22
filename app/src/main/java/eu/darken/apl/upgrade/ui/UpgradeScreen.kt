package eu.darken.apl.upgrade.ui

import android.app.Activity
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.AutoAwesome
import androidx.compose.material.icons.twotone.CellTower
import androidx.compose.material.icons.twotone.CloudOff
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.material.icons.twotone.ShoppingCart
import androidx.compose.material.icons.twotone.SignalCellularAlt
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.apl.common.compose.Mascot
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
import eu.darken.apl.upgrade.subscription.SubscriptionState
import java.time.Period
import java.util.Locale

@Composable
fun UpgradeScreenHost(
    vm: UpgradeViewModel = hiltViewModel(),
) {
    NavigationEventHandler(vm)
    ErrorEventHandler(vm)

    val state by vm.state.collectAsState(initial = null)
    val navController = LocalNavigationController.current
    val context = LocalContext.current

    UpgradeScreen(
        state = state ?: UpgradeViewModel.State(),
        onNavigateUp = { navController?.up() },
        onRefresh = vm::refresh,
        onRegister = vm::openRegister,
        onUnlink = vm::unlink,
        onSubscribe = { (context as? Activity)?.let { vm.subscribe(it) } },
        onRestore = vm::restore,
        onManage = vm::openManage,
    )
}

@Composable
fun UpgradeScreen(
    state: UpgradeViewModel.State,
    onNavigateUp: () -> Unit,
    onRefresh: () -> Unit,
    onRegister: () -> Unit,
    onUnlink: () -> Unit,
    onSubscribe: () -> Unit = {},
    onRestore: () -> Unit = {},
    onManage: () -> Unit = {},
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
                // One meter, faded for Free: the title is what names the tier
                icon = Icons.TwoTone.SignalCellularAlt,
                iconAlpha = if (state.isPro) 1f else 0.4f,
                title = when {
                    state.isPro -> stringResource(R.string.upgrade_status_pro)
                    state.isSettled -> stringResource(R.string.upgrade_status_free)
                    else -> stringResource(R.string.upgrade_status_checking)
                },
                body = when {
                    state.isPro && state.source == UpgradeRepo.Source.FEEDER ->
                        stringResource(R.string.upgrade_status_pro_feeder_msg)

                    state.isPro && state.source == UpgradeRepo.Source.SUBSCRIPTION ->
                        stringResource(R.string.upgrade_status_pro_subscription_msg)

                    state.isSettled -> stringResource(R.string.upgrade_status_free_msg)
                    else -> null
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
                UpgradeSectionCard(
                    title = stringResource(R.string.upgrade_features_title),
                    icon = Icons.TwoTone.AutoAwesome,
                ) {
                    UpgradeFeatureList(text = stringResource(R.string.upgrade_features_msg))
                }
            }

            // Re-registering the feeder that is already linked can only be rejected
            if (state.link !is FeederLinkRepo.FeederLinkState.Linked) {
                FeederOptionCard(onRegister = onRegister)
            }

            // Pro through a linked receiver can still be backed by a subscription
            state.subscription?.takeIf { it.supported }?.let { subscription ->
                SubscriptionCard(
                    subscription = subscription,
                    onSubscribe = onSubscribe,
                    onRestore = onRestore,
                    onManage = onManage,
                )
            }

            if (state.linkUnavailable) {
                UpgradeInlineStateCard(
                    title = stringResource(R.string.upgrade_status_unavailable_title),
                    body = stringResource(R.string.upgrade_status_unavailable_msg),
                    icon = Icons.TwoTone.CloudOff,
                )
            }

            if (state.access?.restricted == true) {
                UpgradeInlineStateCard(
                    title = stringResource(R.string.upgrade_restricted_title),
                    body = stringResource(R.string.upgrade_restricted_msg),
                    icon = Icons.TwoTone.Warning,
                )
            }

            if (!state.isPro) {
                UpgradeHeroCard(
                    text = stringResource(R.string.upgrade_hero_msg),
                    leading = {
                        Mascot(
                            size = 56.dp,
                            cropPadding = true,
                            colorFilter = ColorFilter.tint(LocalContentColor.current),
                        )
                    },
                )
            }

            state.installationId?.let { installationId ->
                UpgradeFootnote(
                    text = stringResource(R.string.upgrade_installation_x, installationId),
                )
            }
        }
    }

    if (showUnlinkConfirmation) {
        AlertDialog(
            onDismissRequest = { showUnlinkConfirmation = false },
            title = { Text(stringResource(R.string.upgrade_unlink_confirmation_title)) },
            text = {
                Text(
                    stringResource(
                        if (state.source == UpgradeRepo.Source.SUBSCRIPTION) {
                            R.string.upgrade_unlink_confirmation_subscription_msg
                        } else {
                            R.string.upgrade_unlink_confirmation_msg
                        }
                    )
                )
            },
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
                Text(stringResource(R.string.feeder_register_action))
            }
        }
    }
}

/** [SubscriptionState.Ownership.Unknown] can't tell, so it is offered like [SubscriptionState.Ownership.None]. */
@Composable
private fun SubscriptionCard(
    subscription: SubscriptionState,
    onSubscribe: () -> Unit,
    onRestore: () -> Unit,
    onManage: () -> Unit,
) {
    val owned = subscription.ownership as? SubscriptionState.Ownership.Owned
    val offer = subscription.offer

    UpgradeSectionCard(
        title = stringResource(R.string.upgrade_subscription_title),
        icon = Icons.TwoTone.ShoppingCart,
    ) {
        if (owned == null) {
            when (offer) {
                is SubscriptionState.Offer.Available -> {
                    Text(
                        text = offer.priceText(),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    offer.trialDays()?.let { days ->
                        Text(
                            text = pluralStringResource(R.plurals.upgrade_subscription_trial_days, days, days),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                SubscriptionState.Offer.Loading -> Text(
                    text = stringResource(R.string.upgrade_subscription_loading_msg),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                SubscriptionState.Offer.Unavailable -> Text(
                    text = stringResource(R.string.upgrade_subscription_unavailable_msg),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(R.string.upgrade_subscription_why_msg),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (owned?.suspended == true) {
            Text(
                text = stringResource(R.string.upgrade_subscription_on_hold_msg),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        when (subscription.submission) {
            SubscriptionState.Submission.Idle -> Unit

            SubscriptionState.Submission.PendingInPlay -> Text(
                text = stringResource(R.string.upgrade_subscription_pending_msg),
                style = MaterialTheme.typography.bodyMedium,
            )

            SubscriptionState.Submission.Updating -> Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(R.string.upgrade_subscription_updating_msg),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            SubscriptionState.Submission.NotAttached -> Text(
                text = stringResource(R.string.upgrade_subscription_not_attached_msg),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )

            SubscriptionState.Submission.Rejected -> Text(
                text = stringResource(R.string.upgrade_subscription_rejected_msg),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        UpgradeCardActions {
            TextButton(onClick = onRestore) {
                Text(stringResource(R.string.upgrade_subscription_restore_action))
            }
            if (owned != null) {
                Button(onClick = onManage) {
                    Text(stringResource(R.string.upgrade_subscription_manage_action))
                }
            } else {
                Button(
                    onClick = onSubscribe,
                    enabled = offer is SubscriptionState.Offer.Available,
                ) {
                    Text(stringResource(R.string.upgrade_subscription_subscribe_action))
                }
            }
        }
    }
}

@Composable
private fun SubscriptionState.Offer.Available.priceText(): String = if (billingPeriod == Period.ofMonths(1)) {
    stringResource(R.string.upgrade_subscription_price_monthly, formattedPrice)
} else {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    stringResource(R.string.upgrade_subscription_price_period, formattedPrice, billingPeriod.format(locale))
}

/** Only trials counted in days get a line. */
private fun SubscriptionState.Offer.Available.trialDays(): Int? = trialPeriod
    ?.takeIf { it.toTotalMonths() == 0L && it.days > 0 }
    ?.days

/** e.g. "3 months", "1 year, 6 months" */
private fun Period.format(locale: Locale): String {
    val measures = listOfNotNull(
        years.takeIf { it != 0 }?.let { Measure(it, MeasureUnit.YEAR) },
        months.takeIf { it != 0 }?.let { Measure(it, MeasureUnit.MONTH) },
        days.takeIf { it != 0 }?.let { Measure(it, MeasureUnit.DAY) },
    )
    return MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.WIDE).formatMeasures(*measures.toTypedArray())
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
            text = stringResource(R.string.feeder_link_id_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SelectionContainer {
            Text(
                text = feeder.feederId,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        feeder.linkedAt.takeIf { it > 0 }?.let { linkedAt ->
            Text(
                text = stringResource(
                    R.string.upgrade_linked_feeder_since_x,
                    DateUtils.formatDateTime(
                        LocalContext.current,
                        linkedAt,
                        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_YEAR,
                    ),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

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
                type = UpgradeRepo.Type.GPLAY,
                isSettled = true,
                link = FeederLinkRepo.FeederLinkState.Unlinked(tier = "free"),
                installationId = "0123456789abcdef",
                subscription = SubscriptionState(
                    supported = true,
                    offer = SubscriptionState.Offer.Available(
                        formattedPrice = "€2.99",
                        billingPeriod = Period.ofMonths(1),
                        trialPeriod = Period.ofDays(7),
                    ),
                    ownership = SubscriptionState.Ownership.None,
                    submission = SubscriptionState.Submission.Idle,
                ),
            ),
            onNavigateUp = {},
            onRefresh = {},
            onRegister = {},
            onUnlink = {},
        )
    }
}

@Preview2
@Composable
private fun UpgradeScreenSubscribedPreview() {
    PreviewWrapper {
        UpgradeScreen(
            state = UpgradeViewModel.State(
                type = UpgradeRepo.Type.GPLAY,
                isPro = true,
                isSettled = true,
                source = UpgradeRepo.Source.SUBSCRIPTION,
                link = FeederLinkRepo.FeederLinkState.Unlinked(tier = "pro"),
                installationId = "0123456789abcdef",
                subscription = SubscriptionState(
                    supported = true,
                    offer = SubscriptionState.Offer.Unavailable,
                    ownership = SubscriptionState.Ownership.Owned(suspended = false),
                    submission = SubscriptionState.Submission.Idle,
                ),
            ),
            onNavigateUp = {},
            onRefresh = {},
            onRegister = {},
            onUnlink = {},
        )
    }
}
