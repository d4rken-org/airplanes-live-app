package eu.darken.apl.server.status

import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.coroutine.AppScope
import eu.darken.apl.common.debug.logging.Logging.Priority.INFO
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.network.NetworkStateProvider
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.ServerCodes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ServerStatus {
    data object Ok : ServerStatus
    data class Maintenance(val until: Instant?) : ServerStatus
    data class Incident(val until: Instant?) : ServerStatus

    /** The server answers, but with errors it gave no reason for. */
    data object ServerProblem : ServerStatus

    /** The server can't be reached although the device's network is validated. */
    data object Unreachable : ServerStatus
}

/**
 * What the server's answers say about its health. Only fed with outcomes of requests that reached the
 * network; it sends nothing itself.
 *
 * [ServerStatus.ServerProblem] and [ServerStatus.Unreachable] are only shown after [PROMOTE_FAILURES]
 * failures or once [PROMOTE_AFTER_MS] passed since the first one, unless an answer cleared them meanwhile.
 */
@Singleton
class ServerStatusTracker @Inject constructor(
    private val networkStateProvider: NetworkStateProvider,
    private val clock: MonotonicClock,
    @param:AppScope private val appScope: CoroutineScope,
) {

    enum class Route {
        /** `/meta` never touches the database, its success says nothing about the data routes. */
        META,
        DATA,
    }

    private enum class PendingKind { SERVER_PROBLEM, UNREACHABLE }

    private data class Pending(val kind: PendingKind, val failures: Int, val deadline: Long)

    private val lock = Any()
    private var pending: Pending? = null
    private var deadlineJob: Job? = null

    private val _status = MutableStateFlow<ServerStatus>(ServerStatus.Ok)
    val status: StateFlow<ServerStatus> = _status.asStateFlow()

    private val _isTroubled = MutableStateFlow(false)

    /** True while [status] is not [ServerStatus.Ok] or a failure waits to be confirmed. */
    val isTroubled: StateFlow<Boolean> = _isTroubled.asStateFlow()

    private val _retryHint = MutableStateFlow<Duration?>(null)

    /** The server's own retry hint from its latest outage answer, null if it gave none or all is well. */
    val retryHint: StateFlow<Duration?> = _retryHint.asStateFlow()

    fun onSuccess(route: Route) = synchronized(lock) {
        when (route) {
            Route.DATA -> {
                pending = null
                show(ServerStatus.Ok)
            }

            Route.META -> clearByAnswer()
        }
        publish()
    }

    fun onErrorResponse(route: Route, error: ServerApiException) = synchronized(lock) {
        when {
            error.code == ServerCodes.SERVER_MAINTENANCE -> {
                _retryHint.value = error.serverRetryHint
                pending = null
                show(ServerStatus.Maintenance(error.until))
            }

            error.code == ServerCodes.SERVER_INCIDENT -> {
                _retryHint.value = error.serverRetryHint
                pending = null
                show(ServerStatus.Incident(error.until))
            }

            error.status >= 500 -> {
                _retryHint.value = error.serverRetryHint
                when (_status.value) {
                    is ServerStatus.Maintenance, is ServerStatus.Incident, ServerStatus.ServerProblem -> Unit
                    else -> countFailure(PendingKind.SERVER_PROBLEM)
                }
            }

            else -> clearByAnswer()
        }
        publish()
    }

    /** Without a validated network the failure is the device's, it is shown elsewhere. */
    suspend fun onConnectionFailure() {
        val network = networkStateProvider.networkState.first()
        if (network !is NetworkStateProvider.State.Modern || !network.isInternetAvailable) return
        synchronized(lock) {
            when (_status.value) {
                // A short restart of the server must not replace an announced maintenance
                is ServerStatus.Maintenance, is ServerStatus.Incident, ServerStatus.ServerProblem -> Unit
                else -> countFailure(PendingKind.UNREACHABLE)
            }
            publish()
        }
    }

    /** Any answer below 500 proves the server is reachable and serving, not that its data routes work. */
    private fun clearByAnswer() {
        when (_status.value) {
            is ServerStatus.Maintenance, is ServerStatus.Incident, ServerStatus.Unreachable -> show(ServerStatus.Ok)
            else -> Unit
        }
        if (pending?.kind == PendingKind.UNREACHABLE) pending = null
    }

    private fun countFailure(kind: PendingKind) {
        val now = clock.elapsed()
        val current = pending
        val next = if (current == null) {
            Pending(kind, failures = 1, deadline = now + PROMOTE_AFTER_MS)
        } else {
            val merged = if (current.kind == PendingKind.SERVER_PROBLEM) PendingKind.SERVER_PROBLEM else kind
            current.copy(kind = merged, failures = current.failures + 1)
        }
        if (next.failures >= PROMOTE_FAILURES || now >= next.deadline) {
            promote(next)
        } else {
            pending = next
            if (deadlineJob?.isActive != true) deadlineJob = appScope.launch { awaitDeadline() }
        }
    }

    private suspend fun awaitDeadline() {
        while (true) {
            val remaining = synchronized(lock) {
                val current = pending ?: return
                val left = current.deadline - clock.elapsed()
                if (left <= 0) {
                    promote(current)
                    publish()
                    return
                }
                left
            }
            delay(remaining)
        }
    }

    private fun promote(promoted: Pending) {
        pending = null
        show(
            when (promoted.kind) {
                PendingKind.SERVER_PROBLEM -> ServerStatus.ServerProblem
                PendingKind.UNREACHABLE -> ServerStatus.Unreachable
            }
        )
    }

    private fun show(status: ServerStatus) {
        if (_status.value != status) log(TAG, INFO) { "Server status: ${_status.value} -> $status" }
        _status.value = status
    }

    private fun publish() {
        if (pending == null) {
            deadlineJob?.cancel()
            deadlineJob = null
        }
        val troubled = pending != null || _status.value != ServerStatus.Ok
        if (!troubled) _retryHint.value = null
        _isTroubled.value = troubled
    }

    private val ServerApiException.serverRetryHint: Duration?
        get() = retryAfterSeconds?.let { Duration.ofSeconds(it) }

    companion object {
        const val PROMOTE_FAILURES = 2
        const val PROMOTE_AFTER_MS = 10_000L
        private val TAG = logTag("Server", "Status")
    }
}
