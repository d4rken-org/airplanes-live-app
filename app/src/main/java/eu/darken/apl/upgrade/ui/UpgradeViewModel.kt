package eu.darken.apl.upgrade.ui

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.BuildConfigWrap
import eu.darken.apl.common.WebpageTool
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.flow.combine
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.feeder.core.FeederDiscovery
import eu.darken.apl.feeder.core.FeederRepo
import eu.darken.apl.feeder.core.link.FeederLinkRepo
import eu.darken.apl.map.core.AirplanesLive
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.session.SessionManager
import eu.darken.apl.server.session.SessionState
import eu.darken.apl.upgrade.UpgradeRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class UpgradeViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    upgradeRepo: UpgradeRepo,
    private val feederLinkRepo: FeederLinkRepo,
    private val accessRepo: AccessRepo,
    private val sessionManager: SessionManager,
    private val feederDiscovery: FeederDiscovery,
    private val feederRepo: FeederRepo,
    private val webpageTool: WebpageTool,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Upgrade", "VM"),
) {

    data class Registration(
        val input: String = "",
        val detected: List<String> = emptyList(),
        val monitored: List<String> = emptyList(),
        val isBusy: Boolean = false,
        /** Server error code, the screen turns it into a message. */
        val errorCode: String? = null,
        /** How long the server asked to wait, when it said so. */
        val retryAfterSeconds: Long? = null,
        val failed: Boolean = false,
    ) {
        val candidates: List<String>
            get() = (detected + monitored).distinct()
    }

    data class State(
        val type: UpgradeRepo.Type = UpgradeRepo.Type.FOSS,
        val isPro: Boolean = false,
        val isSettled: Boolean = false,
        val source: UpgradeRepo.Source? = null,
        val link: FeederLinkRepo.FeederLinkState = FeederLinkRepo.FeederLinkState.Unknown,
        val access: AccessState? = null,
        val installationId: String? = null,
        val canResetIdentity: Boolean = BuildConfigWrap.DEBUG,
        val isRefreshing: Boolean = false,
        val registration: Registration = Registration(),
    ) {
        /** Neither the entitlement nor the feeder metadata has to have arrived yet. */
        val isBusy: Boolean
            get() = isRefreshing || !isSettled || link == FeederLinkRepo.FeederLinkState.Unknown
    }

    private val registration = MutableStateFlow(Registration())
    private val isRefreshing = MutableStateFlow(false)

    init {
        launch {
            // A user who already monitors their own feeder should not have to detect or type it
            val monitored = feederRepo.feeders.first().map { it.id }
            registration.update { it.copy(monitored = monitored) }
        }
    }

    val state = combine(
        upgradeRepo.upgradeInfo,
        feederLinkRepo.state,
        accessRepo.state,
        sessionManager.state,
        registration,
        isRefreshing,
    ) { upgrade, link, access, session, registration, isRefreshing ->
        State(
            type = upgrade.type,
            isPro = upgrade.isSettled && upgrade.isPro,
            isSettled = upgrade.isSettled,
            source = upgrade.source,
            link = link,
            access = access,
            installationId = (session as? SessionState.Active)?.installationId,
            isRefreshing = isRefreshing,
            registration = registration,
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

    fun resetIdentity() = launch {
        log(tag) { "resetIdentity()" }
        sessionManager.resetIdentity()
    }

    fun openFeederSetup() = launch {
        log(tag) { "openFeederSetup()" }
        webpageTool.open(AirplanesLive.URL_START_FEEDING)
    }

    fun updateInput(input: String) {
        registration.update {
            it.copy(input = input, errorCode = null, retryAfterSeconds = null, failed = false)
        }
    }

    fun detect() = launch {
        log(tag) { "detect()" }
        registration.update { it.copy(isBusy = true, errorCode = null, retryAfterSeconds = null, failed = false) }
        try {
            val detected = feederDiscovery.detect().map { it.uuid.toString() }
            registration.update { it.copy(detected = detected) }
        } catch (e: Exception) {
            log(tag, WARN) { "Detection failed: ${e.asLog()}" }
            registration.update { it.copy(failed = true) }
        } finally {
            registration.update { it.copy(isBusy = false) }
        }
    }

    fun link(feederId: String) = launch {
        log(tag) { "link(...)" }
        registration.update { it.copy(isBusy = true, errorCode = null, retryAfterSeconds = null, failed = false) }
        try {
            feederLinkRepo.register(feederId.trim())
            registration.update { it.copy(input = "") }
        } catch (e: ServerApiException) {
            log(tag, WARN) { "Linking rejected: ${e.code}" }
            registration.update { it.copy(errorCode = e.code, retryAfterSeconds = e.retryAfterSeconds) }
        } catch (e: IOException) {
            log(tag, WARN) { "Linking failed: ${e.asLog()}" }
            registration.update { it.copy(failed = true) }
        } finally {
            registration.update { it.copy(isBusy = false) }
        }
    }

    fun unlink() = launch {
        log(tag) { "unlink()" }
        registration.update { it.copy(isBusy = true) }
        try {
            feederLinkRepo.unlink()
        } finally {
            registration.update { it.copy(isBusy = false) }
        }
    }
}
