package eu.darken.apl.upgrade.ui

import android.app.Activity
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.AutoAwesome
import androidx.compose.material.icons.twotone.CellTower
import androidx.compose.material.icons.twotone.CloudOff
import androidx.compose.material.icons.twotone.Info
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
import androidx.compose.material3.SnackbarDuration
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.apl.common.compose.Mascot
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import eu.darken.apl.R
import eu.darken.apl.common.compose.Preview2
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.navigation.LocalNavigationController
import eu.darken.apl.common.navigation.NavigationEventHandler
import eu.darken.apl.feeder.core.link.FeederLinkRepo
import eu.darken.apl.main.ui.settings.deletion.DeleteDataViewModel
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.server.api.LinkedFeeder
import eu.darken.apl.upgrade.UpgradeRepo
import eu.darken.apl.upgrade.subscription.SubscriptionState
import java.time.Instant
import java.time.Period
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
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
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                UpgradeEvents.RestoreFoundNothing -> snackbarHostState.showSnackbar(
                    context.getString(R.string.upgrade_subscription_restore_none_msg)
                )

                UpgradeEvents.RestoreUnreachable -> snackbarHostState.showSnackbar(
                    context.getString(R.string.upgrade_subscription_restore_unreachable_msg)
                )

                UpgradeEvents.SupportEmailUnavailable -> snackbarHostState.showSnackbar(
                    message = context.getString(
                        R.string.upgrade_subscription_support_no_email_app_msg,
                        DeleteDataViewModel.SUPPORT_EMAIL,
                    ),
                    duration = SnackbarDuration.Long,
                )
            }
        }
    }

    UpgradeScreen(
        state = state ?: UpgradeViewModel.State(),
        snackbarHostState = snackbarHostState,
        onNavigateUp = { navController?.up() },
        onRefresh = vm::refresh,
        onRegister = vm::openRegister,
        onUnlink = vm::unlink,
        onSubscribe = { (context as? Activity)?.let { vm.subscribe(it) } },
        onRestore = vm::restore,
        onManage = vm::openManage,
        onContactSupport = vm::contactSupport,
        onResume = vm::onResume,
        onPause = vm::onPause,
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
    onContactSupport: () -> Unit = {},
    onResume: () -> Unit = {},
    onPause: () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var showUnlinkConfirmation by remember { mutableStateOf(false) }

    // Keyed on Unit: re-keying on a new callback instance would re-run it while already resumed
    val currentOnResume by rememberUpdatedState(onResume)
    val currentOnPause by rememberUpdatedState(onPause)
    LifecycleResumeEffect(Unit) {
        currentOnResume()
        onPauseOrDispose { currentOnPause() }
    }

    UpgradeScreenScaffold(
        title = stringResource(R.string.upgrade_title),
        onNavigateUp = onNavigateUp,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    state.isPro &&
                        state.link is FeederLinkRepo.FeederLinkState.Linked &&
                        state.subscription?.ownership is SubscriptionState.Ownership.Owned &&
                        !state.serverDisownsSubscription ->
                        stringResource(R.string.upgrade_status_pro_feeder_and_subscription_thanks_msg)

                    state.isPro && state.source == UpgradeRepo.Source.FEEDER ->
                        stringResource(R.string.upgrade_status_pro_feeder_thanks_msg)

                    state.isPro && state.source == UpgradeRepo.Source.SUBSCRIPTION ->
                        stringResource(R.string.upgrade_status_pro_subscription_thanks_msg)

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

            val feederOption: @Composable () -> Unit = {
                // Re-registering the feeder that is already linked can only be rejected
                if (state.link !is FeederLinkRepo.FeederLinkState.Linked) {
                    FeederOptionCard(
                        isSubscriber = state.isPro && state.source == UpgradeRepo.Source.SUBSCRIPTION,
                        onRegister = onRegister,
                    )
                }
            }

            val subscriptionOption: @Composable () -> Unit = {
                // Pro through a linked receiver can still be backed by a subscription
                state.subscription?.takeIf { it.supported }?.let { subscription ->
                    SubscriptionCard(
                        subscription = subscription,
                        serverStatus = state.access?.subscription,
                        serverTier = state.access?.tier,
                        serverDisowns = state.serverDisownsSubscription,
                        isRestoring = state.isRestoring,
                        isProThroughFeeder = state.isPro &&
                            state.source == UpgradeRepo.Source.FEEDER &&
                            state.link is FeederLinkRepo.FeederLinkState.Linked,
                        onSubscribe = onSubscribe,
                        onRestore = onRestore,
                        onManage = onManage,
                        onContactSupport = onContactSupport,
                    )
                }
            }

            val supportedSubscription = state.subscription?.takeIf { it.supported }
            if (supportedSubscription != null && supportedSubscription.isAvailable) {
                subscriptionOption()
                // It compares the two options, so it only shows while both are on screen
                if (
                    supportedSubscription.ownership !is SubscriptionState.Ownership.Owned &&
                    state.link !is FeederLinkRepo.FeederLinkState.Linked
                ) {
                    TwoWaysToProCard()
                }
                feederOption()
            } else {
                feederOption()
                subscriptionOption()
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
                    text = stringResource(R.string.upgrade_hero_infra_msg),
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
private fun FeederOptionCard(
    isSubscriber: Boolean,
    onRegister: () -> Unit,
) {
    UpgradeSectionCard(
        title = stringResource(R.string.upgrade_feeder_title),
        icon = Icons.TwoTone.CellTower,
    ) {
        Text(
            text = stringResource(if (isSubscriber) R.string.upgrade_feeder_subscriber_bonus_msg else R.string.upgrade_feeder_msg),
            style = MaterialTheme.typography.bodyMedium,
        )
        UpgradeCardActions {
            Button(onClick = onRegister) {
                Text(stringResource(R.string.feeder_register_action))
            }
        }
    }
}

@Composable
private fun TwoWaysToProCard() {
    UpgradeSectionCard(
        title = stringResource(R.string.upgrade_two_ways_title),
        icon = Icons.TwoTone.Info,
    ) {
        Text(
            text = stringResource(R.string.upgrade_two_ways_infra_msg),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Loading counts, so the cards don't swap places when Google Play's product details arrive. */
private val SubscriptionState.isAvailable: Boolean
    get() = ownership is SubscriptionState.Ownership.Owned ||
        offer is SubscriptionState.Offer.Loading ||
        offer is SubscriptionState.Offer.Available

/**
 * [SubscriptionState.Ownership.Unknown] can't tell, so it is offered like [SubscriptionState.Ownership.None],
 * unless a stored submission already failed: that purchase exists, so its error shows before Google Play answers.
 *
 * A [serverStatus] that Google Play doesn't show as owned replaces the offer, and Google Play's actions with Restore:
 * Google Play would open the other account, and buying here would start a second subscription.
 *
 * [isProThroughFeeder] turns the offer into support on top of the receiver's Pro.
 *
 * Only the server grants Pro, so Google Play's ownership isn't called active against a [serverTier] other than
 * [AccessState.Tier.SUBSCRIBER], and with [serverDisowns] it reads like [SubscriptionState.Submission.NotAttached].
 */
@Composable
private fun SubscriptionCard(
    subscription: SubscriptionState,
    serverStatus: AccessState.SubscriptionStatus?,
    serverTier: AccessState.Tier?,
    serverDisowns: Boolean,
    isRestoring: Boolean,
    isProThroughFeeder: Boolean,
    onSubscribe: () -> Unit,
    onRestore: () -> Unit,
    onManage: () -> Unit,
    onContactSupport: () -> Unit,
) {
    val owned = subscription.ownership as? SubscriptionState.Ownership.Owned
    val offer = subscription.offer
    val submission = subscription.submission
    val submissionFailed = submission == SubscriptionState.Submission.Rejected ||
        submission == SubscriptionState.Submission.NotAttached
    val showsOwned = owned != null || (submissionFailed && subscription.ownership != SubscriptionState.Ownership.None)
    val showsServerOnly = !showsOwned && serverStatus != null
    val showsOffer = !showsOwned && !showsServerOnly
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val serverLine = serverStatus?.line(locale, includePending = showsServerOnly)
    // An update in progress already says what happens next
    val disownedWithoutUpdate = serverDisowns &&
        owned?.suspended == false &&
        (submission == SubscriptionState.Submission.Idle ||
            submission == SubscriptionState.Submission.PendingInPlay)
    val showsNotAttached = submission == SubscriptionState.Submission.NotAttached || disownedWithoutUpdate
    val serverConfirmsSubscriber = serverTier == null || serverTier == AccessState.Tier.SUBSCRIBER

    val busyText = when {
        isRestoring -> stringResource(R.string.upgrade_subscription_restoring_msg)
        submission == SubscriptionState.Submission.Updating -> stringResource(R.string.upgrade_subscription_updating_msg)
        // Only the offer waits for Google Play's product details
        showsOffer && offer == SubscriptionState.Offer.Loading ->
            stringResource(R.string.upgrade_subscription_loading_msg)

        else -> null
    }
    val isBusy = busyText != null
    val isUpdating = submission == SubscriptionState.Submission.Updating
    val busyHint = if (isUpdating) stringResource(R.string.upgrade_subscription_updating_hint_msg) else null
    // Not a billing operation, so it stays enabled while the card is busy
    val updatingSupport: @Composable RowScope.() -> Unit = {
        ContactSupportButton(onClick = onContactSupport, enabled = true)
    }

    UpgradeSectionCard(
        title = stringResource(
            when {
                showsOwned || showsServerOnly -> R.string.upgrade_subscription_title
                isProThroughFeeder -> R.string.upgrade_subscription_support_title
                else -> R.string.upgrade_subscription_get_pro_title
            }
        ),
        icon = Icons.TwoTone.ShoppingCart,
    ) {
        if (showsServerOnly) {
            if (serverLine != null) {
                SubscriptionOwnedMessage(
                    busyText = busyText,
                    busyHint = busyHint,
                    text = serverLine.text,
                    isError = serverLine.isError,
                )
            } else if (busyText != null) {
                SubscriptionBusyRow(busyText, busyHint)
            }
            UpgradeCardActions(leading = updatingSupport.takeIf { isUpdating }) {
                Button(onClick = onRestore, enabled = !isBusy) {
                    Text(stringResource(R.string.upgrade_subscription_restore_action))
                }
            }
        } else if (showsOffer) {
            val available = offer as? SubscriptionState.Offer.Available
            val trialText = available?.trialText(locale)

            if (isProThroughFeeder) {
                Text(
                    text = stringResource(R.string.upgrade_subscription_feeder_pro_intro_msg),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            when (offer) {
                is SubscriptionState.Offer.Available -> {
                    Text(
                        text = offer.headline(locale),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = offer.terms(locale, trialText),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                SubscriptionState.Offer.Loading -> Unit

                SubscriptionState.Offer.Unavailable -> Text(
                    text = stringResource(R.string.upgrade_subscription_unavailable_msg),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (busyText != null) {
                SubscriptionBusyRow(busyText, busyHint)
            } else if (submission == SubscriptionState.Submission.PendingInPlay) {
                Text(
                    text = stringResource(R.string.upgrade_subscription_pending_payment_msg),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            // Restore already takes the leading slot, and three buttons don't fit one row
            if (isUpdating) {
                Row(content = updatingSupport)
            }

            UpgradeCardActions(
                leading = {
                    TextButton(onClick = onRestore, enabled = !isBusy) {
                        Text(stringResource(R.string.upgrade_subscription_restore_action))
                    }
                },
            ) {
                Button(
                    onClick = onSubscribe,
                    enabled = available != null && !isBusy && submission != SubscriptionState.Submission.PendingInPlay,
                ) {
                    Text(
                        stringResource(
                            if (trialText != null) {
                                R.string.upgrade_subscription_start_trial_action
                            } else {
                                R.string.upgrade_subscription_subscribe_action
                            }
                        )
                    )
                }
            }
        } else {
            when {
                submission == SubscriptionState.Submission.Rejected -> {
                    SubscriptionOwnedMessage(
                        busyText = busyText,
                        busyHint = busyHint,
                        text = stringResource(R.string.upgrade_subscription_rejected_support_msg),
                        isError = true,
                    )
                    UpgradeCardActions(
                        leading = { ContactSupportButton(onClick = onContactSupport, enabled = !isBusy) },
                    ) {
                        Button(onClick = onRestore, enabled = !isBusy) {
                            Text(stringResource(R.string.upgrade_subscription_try_again_action))
                        }
                    }
                }

                showsNotAttached -> {
                    SubscriptionOwnedMessage(
                        busyText = busyText,
                        busyHint = busyHint,
                        text = stringResource(R.string.upgrade_subscription_not_attached_retry_msg),
                        isError = true,
                    )
                    UpgradeCardActions {
                        Button(onClick = onRestore, enabled = !isBusy) {
                            Text(stringResource(R.string.upgrade_subscription_try_again_action))
                        }
                    }
                }

                serverLine != null -> {
                    SubscriptionOwnedMessage(
                        busyText = busyText,
                        busyHint = busyHint,
                        text = serverLine.text,
                        isError = serverLine.isError,
                    )
                    UpgradeCardActions(leading = updatingSupport.takeIf { isUpdating }) {
                        Button(onClick = onManage, enabled = !isBusy) {
                            Text(
                                stringResource(
                                    if (serverLine.fixesPayment) {
                                        R.string.upgrade_subscription_fix_payment_action
                                    } else {
                                        R.string.upgrade_subscription_manage_short_action
                                    }
                                )
                            )
                        }
                    }
                }

                owned?.suspended == true -> {
                    SubscriptionOwnedMessage(
                        busyText = busyText,
                        busyHint = busyHint,
                        text = stringResource(R.string.upgrade_subscription_on_hold_msg),
                        isError = true,
                    )
                    UpgradeCardActions(leading = updatingSupport.takeIf { isUpdating }) {
                        Button(onClick = onManage, enabled = !isBusy) {
                            Text(stringResource(R.string.upgrade_subscription_fix_payment_action))
                        }
                    }
                }

                else -> {
                    if (serverConfirmsSubscriber) {
                        SubscriptionOwnedMessage(
                            busyText = busyText,
                            busyHint = busyHint,
                            text = stringResource(R.string.upgrade_subscription_active_msg),
                            isError = false,
                        )
                    } else if (busyText != null) {
                        SubscriptionBusyRow(busyText, busyHint)
                    }
                    UpgradeCardActions(leading = updatingSupport.takeIf { isUpdating }) {
                        Button(onClick = onManage, enabled = !isBusy) {
                            Text(stringResource(R.string.upgrade_subscription_manage_short_action))
                        }
                    }
                }
            }
        }
    }
}

private class SubscriptionServerLine(val text: String, val isError: Boolean, val fixesPayment: Boolean)

/** Null when the state adds nothing to the card, e.g. ACTIVE without a date. */
@Composable
private fun AccessState.SubscriptionStatus.line(locale: Locale, includePending: Boolean): SubscriptionServerLine? {
    // A stored status can be shown while offline, after its date has passed
    val date = expiresAt
        ?.takeIf { it > System.currentTimeMillis() }
        ?.let {
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(locale)
                .format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))
        }
    return when (state) {
        AccessState.SubscriptionStatus.State.ACTIVE -> date?.let {
            SubscriptionServerLine(
                text = stringResource(R.string.upgrade_subscription_renews_on_msg, it),
                isError = false,
                fixesPayment = false,
            )
        }

        AccessState.SubscriptionStatus.State.IN_GRACE -> SubscriptionServerLine(
            text = stringResource(R.string.upgrade_subscription_in_grace_msg),
            isError = true,
            fixesPayment = true,
        )

        AccessState.SubscriptionStatus.State.CANCELED -> date?.let {
            SubscriptionServerLine(
                text = stringResource(R.string.upgrade_subscription_canceled_until_msg, it),
                isError = false,
                fixesPayment = false,
            )
        }

        AccessState.SubscriptionStatus.State.ON_HOLD -> SubscriptionServerLine(
            text = stringResource(R.string.upgrade_subscription_on_hold_msg),
            isError = true,
            fixesPayment = true,
        )

        AccessState.SubscriptionStatus.State.PAUSED -> SubscriptionServerLine(
            text = stringResource(R.string.upgrade_subscription_paused_msg),
            isError = false,
            fixesPayment = false,
        )

        AccessState.SubscriptionStatus.State.PENDING -> if (includePending) {
            SubscriptionServerLine(
                text = stringResource(R.string.upgrade_subscription_pending_payment_msg),
                isError = false,
                fixesPayment = false,
            )
        } else {
            null
        }
    }
}

/** While busy, the status row takes the place of the state's message. */
@Composable
private fun SubscriptionOwnedMessage(
    busyText: String?,
    busyHint: String?,
    text: String,
    isError: Boolean,
) {
    if (busyText != null) {
        SubscriptionBusyRow(busyText, busyHint)
    } else {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) MaterialTheme.colorScheme.error else Color.Unspecified,
        )
    }
}

@Composable
private fun SubscriptionBusyRow(text: String, hint: String?) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    if (hint != null) {
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ContactSupportButton(onClick: () -> Unit, enabled: Boolean) {
    TextButton(onClick = onClick, enabled = enabled) {
        Text(stringResource(R.string.upgrade_subscription_contact_support_action))
    }
}

@Composable
private fun SubscriptionState.Offer.Available.headline(locale: Locale): String = if (billingPeriod.isMonthly) {
    stringResource(R.string.upgrade_subscription_offer_monthly, formattedPrice)
} else {
    stringResource(R.string.upgrade_subscription_offer_period, formattedPrice, billingPeriod.format(locale))
}

@Composable
private fun SubscriptionState.Offer.Available.terms(locale: Locale, trialText: String?): String = when {
    billingPeriod.isMonthly && trialText != null ->
        stringResource(R.string.upgrade_subscription_terms_trial_monthly, trialText)

    billingPeriod.isMonthly -> stringResource(R.string.upgrade_subscription_terms_monthly)

    trialText != null ->
        stringResource(R.string.upgrade_subscription_terms_trial_period, trialText, billingPeriod.format(locale))

    else -> stringResource(R.string.upgrade_subscription_terms_period, billingPeriod.format(locale))
}

private val Period.isMonthly: Boolean
    get() = this == Period.ofMonths(1)

/** e.g. "7 days". A trial not counted in days, e.g. "1 month", uses [format]. Null without a trial. */
@Composable
private fun SubscriptionState.Offer.Available.trialText(locale: Locale): String? {
    val trial = trialPeriod?.takeIf { !it.isZero && !it.isNegative } ?: return null
    return if (trial.toTotalMonths() == 0L) {
        pluralStringResource(R.plurals.upgrade_subscription_trial_length_days, trial.days, trial.days)
    } else {
        trial.format(locale)
    }
}

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
            text = stringResource(R.string.upgrade_linked_feeder_msg),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
