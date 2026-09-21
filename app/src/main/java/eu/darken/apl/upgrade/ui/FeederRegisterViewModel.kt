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
import eu.darken.apl.server.api.Ipv4UnreachableException
import eu.darken.apl.server.api.ServerApiException
import kotlinx.coroutines.CancellationException
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
        /** The IPv4-pinned registration attempt did not connect, and no link turned up after it. */
        val noIpv4: Boolean = false,
        /** The answer never arrived and the server does not show the link, which settles neither way. */
        val outcomeUnknown: Boolean = false,
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

    /** The verdicts an attempt leaves, cleared together so none of them outlives what it described. */
    private fun State.withoutAttemptOutcome(): State = copy(
        errorCode = null,
        retryAfterSeconds = null,
        noIpv4 = false,
        outcomeUnknown = false,
    )

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
        _state.update { it.withoutAttemptOutcome().copy(selected = feederId) }
    }

    /** Entries keep arrival order behind the scan results, and repeats collapse. */
    fun addManual(feederId: String) {
        log(tag) { "addManual(...)" }
        val trimmed = feederId.trim()
        _state.update {
            it.withoutAttemptOutcome().copy(
                manual = (it.manual + trimmed).distinct(),
                selected = trimmed,
            )
        }
    }

    fun detect() = launch {
        if (!operation.tryLock()) {
            log(tag, WARN) { "detect(): another operation is running" }
            return@launch
        }
        log(tag) { "detect()" }
        _state.update { it.withoutAttemptOutcome().copy(isBusy = true, detectFailed = false) }
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

    /**
     * A registration can land and still fail on the way back, so the server's own view decides
     * whether it did. Only a fetch that answers and names [feederId] counts as a yes; every other
     * result is a no that the caller reports as an outcome it could not establish.
     */
    private suspend fun isLinkedAfterAttempt(feederId: String): Boolean = try {
        feederLinkRepo.reconcileRegistration(feederId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log(tag, WARN) { "Could not reconcile the registration: ${e.asLog()}" }
        false
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
            // This attempt's outcome is the answer now, the earlier scan failure is not
            it.withoutAttemptOutcome().copy(isBusy = true, detectFailed = false)
        }
        try {
            feederLinkRepo.register(feederId.trim())
            navUp()
        } catch (e: ServerApiException) {
            log(tag, WARN) { "Linking rejected: ${e.code}" }
            _state.update { it.copy(errorCode = e.code, retryAfterSeconds = e.retryAfterSeconds) }
        } catch (e: Ipv4UnreachableException) {
            // Raised for the pinned request only, so the unpinned session and access work around it
            // cannot produce this verdict. A retried connection can still have delivered an earlier
            // attempt, so the server is asked before the address family is blamed.
            log(tag, WARN) { "The pinned registration call did not connect: ${e.asLog()}" }
            if (isLinkedAfterAttempt(feederId)) navUp() else _state.update { it.copy(noIpv4 = true) }
        } catch (e: IOException) {
            // A timeout, a reset or a closed stream can all arrive after the server took the
            // registration, so none of them is a rejection on its own
            log(tag, WARN) { "Registration did not come back, reconciling: ${e.asLog()}" }
            if (isLinkedAfterAttempt(feederId)) navUp() else _state.update { it.copy(outcomeUnknown = true) }
        } finally {
            _state.update { it.copy(isBusy = false) }
            operation.unlock()
        }
    }
}
