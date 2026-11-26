package eu.darken.apl.server.status

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import eu.darken.apl.common.coroutine.AppScope
import eu.darken.apl.common.debug.logging.Logging.Priority.INFO
import eu.darken.apl.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.network.NetworkStateProvider
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.compat.AppCompatibility
import eu.darken.apl.server.compat.AppCompatibilityCheck
import eu.darken.apl.server.compat.CompatState
import eu.darken.apl.server.session.SessionManager
import eu.darken.apl.server.session.SessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * While the app is in the foreground, online and [ServerStatusTracker] sees trouble, asks the server's
 * `/meta` from time to time, so a recovery is noticed even when nothing else sends requests.
 */
@Singleton
class ServerStatusProbe internal constructor(
    private val appScope: CoroutineScope,
    private val foreground: Flow<Boolean>,
    private val networkStateProvider: NetworkStateProvider,
    private val tracker: ServerStatusTracker,
    private val compatibilityCheck: AppCompatibilityCheck,
    private val compatibility: AppCompatibility,
    private val sessionManager: SessionManager,
    private val accessRepo: AccessRepo,
) {

    @Inject constructor(
        @AppScope appScope: CoroutineScope,
        networkStateProvider: NetworkStateProvider,
        tracker: ServerStatusTracker,
        compatibilityCheck: AppCompatibilityCheck,
        compatibility: AppCompatibility,
        sessionManager: SessionManager,
        accessRepo: AccessRepo,
    ) : this(
        appScope = appScope,
        foreground = flow { emitAll(ProcessLifecycleOwner.get().lifecycle.currentStateFlow) }
            .map { it.isAtLeast(Lifecycle.State.STARTED) },
        networkStateProvider = networkStateProvider,
        tracker = tracker,
        compatibilityCheck = compatibilityCheck,
        compatibility = compatibility,
        sessionManager = sessionManager,
        accessRepo = accessRepo,
    )

    private var isInit = false

    /** Only touched by the probe loop, which runs one at a time. */
    private var probing: Deferred<Boolean>? = null

    fun setup() {
        log(TAG) { "setup()" }
        require(!isInit)
        isInit = true

        appScope.launch {
            combine(
                foreground.distinctUntilChanged(),
                networkStateProvider.networkState.map { it.isInternetAvailable }.distinctUntilChanged(),
                tracker.isTroubled,
            ) { inForeground, online, troubled -> inForeground && online && troubled }
                .distinctUntilChanged()
                .collectLatest { active -> if (active) probeLoop() }
        }
    }

    private suspend fun probeLoop() {
        log(TAG) { "Probing the server until it recovers" }
        var backoff = INITIAL_BACKOFF
        try {
            while (true) {
                val wait = maxOf(backoff, tracker.retryHint.value ?: Duration.ZERO)
                log(TAG, VERBOSE) { "Next probe in $wait" }
                delay(wait.toMillis())
                // Cancelling this loop must not cancel a probe, its answer may still be handed on
                val running = probing?.takeIf { it.isActive } ?: appScope.async { probe() }.also { probing = it }
                val answered = running.await()
                if (!answered) log(TAG) { "Probe got no answer" }
                backoff = backoff.multipliedBy(2).coerceAtMost(MAX_BACKOFF)
            }
        } finally {
            log(TAG) { "Probing stopped" }
        }
    }

    private suspend fun probe(): Boolean {
        val answered = try {
            compatibilityCheck.check(force = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, WARN) { "Probe failed: ${e.asLog()}" }
            false
        }
        if (!answered) return false

        if (compatibility.state.value is CompatState.UpdateRequired || sessionManager.state.value is SessionState.Revoked) {
            log(TAG, INFO) { "The server answered, data requests are off" }
            tracker.onServerAnsweredWithoutDataRoute()
        }

        // An authed request, its success is what shows the data routes work again
        appScope.launch {
            try {
                accessRepo.refresh(REASON)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(TAG, WARN) { "Access refresh failed: ${e.asLog()}" }
            }
        }
        return true
    }

    companion object {
        internal const val REASON = "server-status-probe"
        internal val INITIAL_BACKOFF: Duration = Duration.ofSeconds(15)
        internal val MAX_BACKOFF: Duration = Duration.ofSeconds(60)
        private val TAG = logTag("Server", "Status", "Probe")
    }
}
