package eu.darken.apl.upgrade.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.apl.R
import eu.darken.apl.common.EmailTool
import eu.darken.apl.common.WebpageTool
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.flow.SingleEventFlow
import eu.darken.apl.common.flow.combine
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.feeder.core.link.FeederLinkRepo
import eu.darken.apl.main.ui.settings.deletion.DeleteDataViewModel
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.server.session.SessionManager
import eu.darken.apl.server.session.SessionState
import eu.darken.apl.upgrade.UpgradeRepo
import eu.darken.apl.upgrade.subscription.RestoreResult
import eu.darken.apl.upgrade.subscription.SubscribeResult
import eu.darken.apl.upgrade.subscription.SubscriptionBilling
import eu.darken.apl.upgrade.subscription.SubscriptionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

@HiltViewModel
class UpgradeViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    upgradeRepo: UpgradeRepo,
    private val feederLinkRepo: FeederLinkRepo,
    private val accessRepo: AccessRepo,
    private val sessionManager: SessionManager,
    private val subscriptionBilling: SubscriptionBilling,
    private val webpageTool: WebpageTool,
    @param:ApplicationContext private val context: Context,
    private val emailTool: EmailTool,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Upgrade", "VM"),
) {

    data class State(
        val type: UpgradeRepo.Type = UpgradeRepo.Type.FOSS,
        val isPro: Boolean = false,
        val isSettled: Boolean = false,
        val source: UpgradeRepo.Source? = null,
        val link: FeederLinkRepo.FeederLinkState = FeederLinkRepo.FeederLinkState.Unknown,
        val access: AccessState? = null,
        val installationId: String? = null,
        val isRefreshing: Boolean = false,
        val isUnlinking: Boolean = false,
        val isRestoring: Boolean = false,
        val subscription: SubscriptionState? = null,
    ) {
        /**
         * Neither the entitlement nor the feeder metadata has to have arrived yet. A fetch that
         * finished without an answer is not waiting for anything, so it ends the wait.
         */
        val isBusy: Boolean
            get() = isRefreshing || isUnlinking || !isSettled || link == FeederLinkRepo.FeederLinkState.Unknown

        val linkUnavailable: Boolean
            get() = link == FeederLinkRepo.FeederLinkState.Unavailable

        /**
         * The server neither grants the subscriber tier nor reports a subscription, while Google Play's
         * ownership can still be from an earlier query. A missing status can also mean this installation
         * was never attached, so it doesn't say the subscription ended.
         */
        val serverDisownsSubscription: Boolean
            get() = access != null && access.tier != AccessState.Tier.SUBSCRIBER && access.subscription == null
    }

    private val isRefreshing = MutableStateFlow(false)
    private val isUnlinking = MutableStateFlow(false)
    private val isRestoring = MutableStateFlow(false)
    private val resumed = MutableStateFlow(false)

    val events = SingleEventFlow<UpgradeEvents>()

    val state = combine(
        upgradeRepo.upgradeInfo,
        feederLinkRepo.state,
        accessRepo.state,
        sessionManager.state,
        isRefreshing,
        isUnlinking,
        isRestoring,
        subscriptionBilling.state,
    ) { upgrade, link, access, session, isRefreshing, isUnlinking, isRestoring, subscription ->
        State(
            type = upgrade.type,
            isPro = upgrade.isSettled && upgrade.isPro,
            isSettled = upgrade.isSettled,
            source = upgrade.source,
            link = link,
            access = access,
            installationId = (session as? SessionState.Active)?.installationId,
            isRefreshing = isRefreshing,
            isUnlinking = isUnlinking,
            isRestoring = isRestoring,
            subscription = subscription,
        )
    }.asStateFlow()

    init {
        subscriptionBilling.reconcile("upgrade-screen")

        // A subscription can end while the screen stays open, and nothing else here asks the server again
        val eligible = accessRepo.state
            .map { it != null && (it.tier == AccessState.Tier.SUBSCRIBER || it.subscription != null) }
            .distinctUntilChanged()
        vmScope.launch {
            combine(resumed, eligible) { isResumed, isEligible -> isResumed && isEligible }
                .distinctUntilChanged()
                .collectLatest { polling ->
                    if (!polling) return@collectLatest
                    while (true) {
                        delay(ACCESS_POLL_INTERVAL)
                        pollAccess()
                    }
                }
        }
    }

    private suspend fun pollAccess() {
        try {
            accessRepo.refreshUnlessRetrying("upgrade-screen-poll")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(tag, WARN) { "pollAccess(): access refresh failed: ${e.asLog()}" }
        }
    }

    /** The policy and the feeder metadata come from different endpoints, both have to be renewed. */
    fun refresh() = launch {
        log(tag) { "refresh()" }
        subscriptionBilling.reconcile("manual")
        isRefreshing.value = true
        val shownSince = TimeSource.Monotonic.markNow()
        try {
            accessRepo.refresh("manual")
            feederLinkRepo.refresh()
        } finally {
            // A refresh that answers instantly would only flash the busy indicator
            delay(MIN_REFRESH_DISPLAY - shownSince.elapsedNow())
            isRefreshing.value = false
        }
    }

    /** Not something the user asked for, so a failure is only logged and the busy indicator stays off. */
    fun onResume() {
        resumed.value = true
        launch {
            log(tag) { "onResume()" }
            try {
                accessRepo.refresh("upgrade-screen")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(tag, WARN) { "onResume(): access refresh failed: ${e.asLog()}" }
            }
        }
    }

    fun onPause() {
        log(tag) { "onPause()" }
        resumed.value = false
    }

    fun openRegister() {
        log(tag) { "openRegister()" }
        navTo(DestinationUpgradeFeeder)
    }

    fun unlink() = launch {
        log(tag) { "unlink()" }
        isUnlinking.value = true
        try {
            feederLinkRepo.unlink()
        } finally {
            isUnlinking.value = false
        }
    }

    /** The outcome of a sheet that did open arrives through [SubscriptionBilling.state]. */
    fun subscribe(activity: Activity) = launch {
        log(tag) { "subscribe()" }
        when (val result = subscriptionBilling.subscribe(activity)) {
            SubscribeResult.Launched, SubscribeResult.Canceled, SubscribeResult.AlreadyOwned -> {
                log(tag) { "subscribe(): $result" }
            }

            SubscribeResult.Unavailable, is SubscribeResult.Failed -> {
                log(tag, WARN) { "subscribe(): $result" }
                throw SubscribeException(result)
            }
        }
    }

    fun restore() = launch {
        log(tag) { "restore()" }
        isRestoring.value = true
        val result = try {
            subscriptionBilling.restore()
        } finally {
            isRestoring.value = false
        }
        log(tag) { "restore(): $result" }
        when (result) {
            RestoreResult.NothingFound -> events.emit(UpgradeEvents.RestoreFoundNothing)
            RestoreResult.Unreachable -> events.emit(UpgradeEvents.RestoreUnreachable)
            RestoreResult.Found -> Unit
        }
    }

    fun openManage() {
        val uri = subscriptionBilling.manageUri
        log(tag) { "openManage(): $uri" }
        uri?.let { webpageTool.open(it.toString()) }
    }

    fun contactSupport() {
        val installationId = (sessionManager.state.value as? SessionState.Active)?.installationId
        log(tag) { "contactSupport(installationId=$installationId)" }
        val body = buildString {
            append(context.getString(R.string.upgrade_subscription_support_email_body))
            if (installationId != null) {
                append("\n\n")
                append(context.getString(R.string.settings_delete_data_email_id_line, installationId))
            }
        }
        val intent = emailTool.build(
            EmailTool.Email(
                receipients = listOf(DeleteDataViewModel.SUPPORT_EMAIL),
                subject = context.getString(R.string.upgrade_subscription_support_email_subject),
                body = body,
            )
        )
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            log(tag, WARN) { "contactSupport(): no email app: $e" }
            events.tryEmit(UpgradeEvents.SupportEmailUnavailable)
        }
    }

    companion object {
        private val MIN_REFRESH_DISPLAY = 1.seconds
        private val ACCESS_POLL_INTERVAL = 15.seconds
    }
}
