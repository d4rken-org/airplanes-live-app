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
import eu.darken.apl.feeder.core.link.FeederLinkRepo
import eu.darken.apl.map.core.AirplanesLive
import eu.darken.apl.server.api.NoIpv4AddressException
import eu.darken.apl.server.api.ServerApiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class FeederRegisterViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val feederLinkRepo: FeederLinkRepo,
    private val feederDiscovery: FeederDiscovery,
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
        val detection: Detection? = null,
        /** Ids entered by hand. They live here only, so leaving the screen forgets them. */
        val manual: List<String> = emptyList(),
        val selected: String? = null,
        /** A restored entry can outlive the registration it was opened for. */
        val isLinked: Boolean = false,
        val isBusy: Boolean = false,
        /** Server error code, the screen turns it into a message. */
        val errorCode: String? = null,
        /** How long the server asked to wait, when it said so. */
        val retryAfterSeconds: Long? = null,
        val linkFailed: Boolean = false,
        /** The network offered no IPv4 route, which registration cannot do without. */
        val noIpv4: Boolean = false,
        /** A scan has run, whatever it returned. */
        val detectAttempted: Boolean = false,
        /** The last scan failed, and no registration attempt has answered since. */
        val detectFailed: Boolean = false,
    ) {
        /** Only what this network actually reported, plus what the user typed. */
        val candidates: List<String>
            get() = ((detection?.found ?: emptyList()) + manual).distinct()

        /**
         * Typing an id is the way out of a scan that came back with nothing. It stays available
         * once earned, so a later registration error cannot take the only way forward away again.
         */
        val canEnterManually: Boolean
            get() = detectAttempted && detection?.found.isNullOrEmpty()
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    /**
     * Admission for the two operations that own [State.isBusy]. A disabled button is a picture of
     * the state, not a guard on it: taps landing before recomposition still arrive here.
     */
    private val operation = Mutex()

    init {
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

    fun select(feederId: String) {
        log(tag) { "select(...)" }
        _state.update {
            it.copy(
                selected = feederId,
                errorCode = null,
                retryAfterSeconds = null,
                linkFailed = false,
                noIpv4 = false,
            )
        }
    }

    /** Entries keep arrival order behind the scan results, and repeats collapse. */
    fun addManual(feederId: String) {
        log(tag) { "addManual(...)" }
        val trimmed = feederId.trim()
        _state.update {
            it.copy(
                manual = (it.manual + trimmed).distinct(),
                selected = trimmed,
                errorCode = null,
                retryAfterSeconds = null,
                linkFailed = false,
                noIpv4 = false,
            )
        }
    }

    fun detect() = launch {
        if (!operation.tryLock()) {
            log(tag, WARN) { "detect(): another operation is running" }
            return@launch
        }
        log(tag) { "detect()" }
        _state.update {
            it.copy(
                isBusy = true,
                errorCode = null,
                retryAfterSeconds = null,
                linkFailed = false,
                detectFailed = false,
            )
        }
        try {
            val scan = feederDiscovery.scan()
            val found = scan.feeders.map { feeder -> feeder.uuid.toString() }
            _state.update {
                it.copy(
                    detection = Detection(host = scan.host, found = found),
                    detectAttempted = true,
                    // Linking an id this scan no longer lists would submit a feeder that is not here
                    selected = it.selected?.takeIf { sel -> sel in found || sel in it.manual },
                )
            }
        } catch (e: Exception) {
            log(tag, WARN) { "Detection failed: ${e.asLog()}" }
            _state.update { it.copy(detectAttempted = true, detectFailed = true) }
        } finally {
            _state.update { it.copy(isBusy = false) }
            operation.unlock()
        }
    }

    fun link() = launch {
        if (!operation.tryLock()) {
            log(tag, WARN) { "link(): another operation is running" }
            return@launch
        }
        val current = _state.value
        val feederId = current.selected?.takeIf { it in current.candidates }
        if (feederId == null) {
            log(tag, WARN) { "link(): the selection is no longer on offer" }
            operation.unlock()
            return@launch
        }
        log(tag) { "link(...)" }
        _state.update {
            it.copy(
                isBusy = true,
                errorCode = null,
                retryAfterSeconds = null,
                linkFailed = false,
                noIpv4 = false,
                // This attempt's outcome is the answer now, the earlier scan failure is not
                detectFailed = false,
            )
        }
        try {
            feederLinkRepo.register(feederId.trim())
            navUp()
        } catch (e: ServerApiException) {
            log(tag, WARN) { "Linking rejected: ${e.code}" }
            _state.update { it.copy(errorCode = e.code, retryAfterSeconds = e.retryAfterSeconds) }
        } catch (e: NoIpv4AddressException) {
            // Registration is pinned to IPv4 because that is the only family the match can succeed
            // on, so a network without it cannot register at all
            log(tag, WARN) { "No IPv4 route for registration: ${e.asLog()}" }
            _state.update { it.copy(noIpv4 = true) }
        } catch (e: IOException) {
            // register() stores the link and only then refreshes access, so a link that landed can
            // still throw here. Reporting that as a failure would send the user to retry a
            // registration the server has already accepted.
            if (feederLinkRepo.state.value is FeederLinkRepo.FeederLinkState.Linked) {
                log(tag, WARN) { "Linked, but the access refresh failed: ${e.asLog()}" }
                navUp()
            } else {
                log(tag, WARN) { "Linking failed: ${e.asLog()}" }
                _state.update { it.copy(linkFailed = true) }
            }
        } finally {
            _state.update { it.copy(isBusy = false) }
            operation.unlock()
        }
    }
}
