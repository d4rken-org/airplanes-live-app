package eu.darken.apl.upgrade.ui

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.WebpageTool
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.feeder.core.FeederDiscovery
import eu.darken.apl.feeder.core.FeederRepo
import eu.darken.apl.feeder.core.link.FeederLinkRepo
import eu.darken.apl.map.core.AirplanesLive
import eu.darken.apl.server.api.ServerApiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class FeederRegisterViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val feederLinkRepo: FeederLinkRepo,
    private val feederDiscovery: FeederDiscovery,
    private val feederRepo: FeederRepo,
    private val webpageTool: WebpageTool,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Upgrade", "Register", "VM"),
) {

    /** What a finished scan found, so the card can report the address it looked on either way. */
    data class Detection(
        val host: String?,
        val found: List<String>,
    )

    data class State(
        val input: String = "",
        val detection: Detection? = null,
        val monitored: List<String> = emptyList(),
        /** A restored entry can outlive the registration it was opened for. */
        val isLinked: Boolean = false,
        val isBusy: Boolean = false,
        /** Server error code, the screen turns it into a message. */
        val errorCode: String? = null,
        /** How long the server asked to wait, when it said so. */
        val retryAfterSeconds: Long? = null,
        val failed: Boolean = false,
    ) {
        val candidates: List<String>
            get() = ((detection?.found ?: emptyList()) + monitored).distinct()
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    init {
        launch {
            // A user who already monitors their own feeder should not have to detect or type it
            val monitored = feederRepo.feeders.first().map { it.id }
            _state.update { it.copy(monitored = monitored) }
        }
        feederLinkRepo.state
            .map { it is FeederLinkRepo.FeederLinkState.Linked }
            .distinctUntilChanged()
            .onEach { linked -> _state.update { it.copy(isLinked = linked) } }
            .launchInViewModel()
    }

    fun openFeederSetup() = launch {
        log(tag) { "openFeederSetup()" }
        webpageTool.open(AirplanesLive.URL_START_FEEDING)
    }

    fun updateInput(input: String) {
        _state.update {
            it.copy(input = input, errorCode = null, retryAfterSeconds = null, failed = false)
        }
    }

    fun detect() = launch {
        log(tag) { "detect()" }
        _state.update { it.copy(isBusy = true, errorCode = null, retryAfterSeconds = null, failed = false) }
        try {
            val scan = feederDiscovery.scan()
            _state.update {
                it.copy(
                    detection = Detection(
                        host = scan.host,
                        found = scan.feeders.map { feeder -> feeder.uuid.toString() },
                    ),
                )
            }
        } catch (e: Exception) {
            log(tag, WARN) { "Detection failed: ${e.asLog()}" }
            _state.update { it.copy(failed = true) }
        } finally {
            _state.update { it.copy(isBusy = false) }
        }
    }

    fun link(feederId: String) = launch {
        log(tag) { "link(...)" }
        _state.update { it.copy(isBusy = true, errorCode = null, retryAfterSeconds = null, failed = false) }
        try {
            feederLinkRepo.register(feederId.trim())
            done()
        } catch (e: ServerApiException) {
            log(tag, WARN) { "Linking rejected: ${e.code}" }
            _state.update { it.copy(errorCode = e.code, retryAfterSeconds = e.retryAfterSeconds) }
        } catch (e: IOException) {
            // register() stores the link and only then refreshes access, so a link that landed can
            // still throw here. Reporting that as a failure would send the user to retry a
            // registration the server has already accepted.
            if (feederLinkRepo.state.value is FeederLinkRepo.FeederLinkState.Linked) {
                log(tag, WARN) { "Linked, but the access refresh failed: ${e.asLog()}" }
                done()
            } else {
                log(tag, WARN) { "Linking failed: ${e.asLog()}" }
                _state.update { it.copy(failed = true) }
            }
        } finally {
            _state.update { it.copy(isBusy = false) }
        }
    }

    /**
     * Registration landing is the success signal, not the Pro state: the entitlement it grants only
     * shows up once an access refresh has run, which can be deferred or fail on its own.
     */
    private fun done() {
        _state.update { it.copy(input = "") }
        navUp()
    }
}
