package eu.darken.apl.upgrade.ui

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.flow.combine
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.feeder.core.link.FeederLinkRepo
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.server.session.SessionManager
import eu.darken.apl.server.session.SessionState
import eu.darken.apl.upgrade.UpgradeRepo
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@HiltViewModel
class UpgradeViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    upgradeRepo: UpgradeRepo,
    private val feederLinkRepo: FeederLinkRepo,
    private val accessRepo: AccessRepo,
    sessionManager: SessionManager,
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

    val state = combine(
        upgradeRepo.upgradeInfo,
        feederLinkRepo.state,
        accessRepo.state,
        sessionManager.state,
        isRefreshing,
        isUnlinking,
    ) { upgrade, link, access, session, isRefreshing, isUnlinking ->
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
        )
    }.asStateFlow()

    /** The policy and the feeder metadata come from different endpoints, both have to be renewed. */
    fun refresh() = launch {
        log(tag) { "refresh()" }
        isRefreshing.value = true
        try {
            accessRepo.refresh("manual")
            feederLinkRepo.refresh()
        } finally {
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
}
