package eu.darken.apl.upgrade.ui

import android.app.Activity
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.WebpageTool
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.flow.SingleEventFlow
import eu.darken.apl.common.flow.combine
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.feeder.core.link.FeederLinkRepo
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.server.session.SessionManager
import eu.darken.apl.server.session.SessionState
import eu.darken.apl.upgrade.UpgradeRepo
import eu.darken.apl.upgrade.subscription.SubscribeResult
import eu.darken.apl.upgrade.subscription.SubscriptionBilling
import eu.darken.apl.upgrade.subscription.SubscriptionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

@HiltViewModel
class UpgradeViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    upgradeRepo: UpgradeRepo,
    private val feederLinkRepo: FeederLinkRepo,
    private val accessRepo: AccessRepo,
    sessionManager: SessionManager,
    private val subscriptionBilling: SubscriptionBilling,
    private val webpageTool: WebpageTool,
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
    }

    private val isRefreshing = MutableStateFlow(false)
    private val isUnlinking = MutableStateFlow(false)
    private val isRestoring = MutableStateFlow(false)

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
        try {
            subscriptionBilling.restore()
        } finally {
            isRestoring.value = false
        }
        when (val ownership = subscriptionBilling.state.first().ownership) {
            SubscriptionState.Ownership.None -> events.emit(UpgradeEvents.RestoreFoundNothing)
            SubscriptionState.Ownership.Unknown -> events.emit(UpgradeEvents.RestoreUnreachable)
            is SubscriptionState.Ownership.Owned -> log(tag) { "restore(): $ownership" }
        }
    }

    fun openManage() {
        val uri = subscriptionBilling.manageUri
        log(tag) { "openManage(): $uri" }
        uri?.let { webpageTool.open(it.toString()) }
    }

    companion object {
        private val MIN_REFRESH_DISPLAY = 1.seconds
    }
}
