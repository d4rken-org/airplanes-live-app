package eu.darken.apl.server.access

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import eu.darken.apl.common.coroutine.AppScope
import eu.darken.apl.common.datastore.createValue
import eu.darken.apl.common.datastore.value
import eu.darken.apl.common.debug.logging.Logging.Priority.ERROR
import eu.darken.apl.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.network.NetworkStateProvider
import eu.darken.apl.server.ServerAccessDataStore
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.ServerCodes
import eu.darken.apl.server.api.ServerEndpoint
import eu.darken.apl.server.api.UsageUpdate
import eu.darken.apl.server.session.SessionManager
import eu.darken.apl.server.session.SessionRevokedException
import eu.darken.apl.server.session.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccessRepo @Inject constructor(
    @param:AppScope private val appScope: CoroutineScope,
    @param:ServerAccessDataStore private val dataStore: DataStore<Preferences>,
    json: Json,
    private val endpoint: ServerEndpoint,
    private val sessionManager: SessionManager,
    private val serverClock: ServerClock,
    private val networkStateProvider: NetworkStateProvider,
) {

    private val persisted = dataStore.createValue<AccessState?>(
        key = "access.last",
        defaultValue = null,
        json = json,
        onErrorFallbackToDefault = true,
    )

    private val _state = MutableStateFlow<AccessState?>(null)
    val state: StateFlow<AccessState?> = _state.asStateFlow()

    private val refreshLock = Mutex()
    private var inFlight: Deferred<Unit>? = null
    private var lastAttemptElapsed: Long? = null

    /** When the newest successful attempt started, triggers older than that are answered by it. */
    private var lastCoveredElapsed: Long? = null
    private var pendingTrigger: Trigger? = null
    private var followUpJob: Job? = null
    private var lastThrottledElapsed: Long? = null
    private var retryJob: Job? = null
    private var resetJob: Job? = null
    private var retryWanted = false
    private var backoffMillis = INITIAL_BACKOFF_MS

    private data class Trigger(val reason: String, val elapsed: Long)

    private val loadJob = appScope.launch {
        persisted.value()?.let { _state.value = it }
    }

    /** The persisted policy has to be in [state] before anything judges what the app knows. */
    suspend fun awaitLoaded() = loadJob.join()

    init {
        appScope.launch {
            sessionManager.state.collect { session ->
                awaitLoaded()
                if (session !is SessionState.Active) return@collect
                if (_state.value?.installationId != session.installationId) {
                    // A policy fetched for a different installation says nothing about this one
                    _state.value = null
                    persisted.value(null)
                }
                refresh("session-active")
            }
        }
        appScope.launch {
            networkStateProvider.networkState.collect { network ->
                if (network.isInternetAvailable && retryWanted) refresh("network-available")
            }
        }
    }

    suspend fun refresh(reason: String) {
        val requestedAt = serverClock.elapsed()
        val job = refreshLock.withLock {
            inFlight?.takeIf { it.isActive }?.let { running ->
                // A fetch that started earlier cannot know about what triggered this one
                pendingTrigger = keepNewest(Trigger(reason, requestedAt))
                return@withLock running
            }
            inFlight = null

            val spacingLeft = lastAttemptElapsed?.let { MIN_SPACING_MS - (requestedAt - it) } ?: 0L
            if (spacingLeft > 0) {
                if (isCovered(requestedAt)) {
                    log(TAG, VERBOSE) { "refresh($reason) is already answered by the last refresh" }
                    return
                }
                log(TAG, VERBOSE) { "refresh($reason) deferred by ${spacingLeft}ms" }
                pendingTrigger = keepNewest(Trigger(reason, requestedAt))
                scheduleFollowUp(spacingLeft)
                return
            }

            lastAttemptElapsed = requestedAt
            appScope
                .async {
                    try {
                        attempt(reason, requestedAt)
                    } finally {
                        dispatchFollowUp()
                    }
                }
                .also { inFlight = it }
        }
        job.await()
    }

    /** Only the newest trigger has to survive, an attempt that answers it answers the older ones too. */
    private fun keepNewest(trigger: Trigger): Trigger =
        listOfNotNull(pendingTrigger, trigger).maxBy { it.elapsed }

    private fun isCovered(triggerElapsed: Long): Boolean =
        lastCoveredElapsed?.let { it >= triggerElapsed } == true

    /** A trigger the finished attempt could not answer gets its own refresh once spacing allows. */
    private suspend fun dispatchFollowUp() = refreshLock.withLock {
        inFlight = null
        val pending = pendingTrigger ?: return
        if (isCovered(pending.elapsed)) {
            pendingTrigger = null
            return
        }
        val spacingLeft = lastAttemptElapsed?.let { MIN_SPACING_MS - (serverClock.elapsed() - it) } ?: 0L
        scheduleFollowUp(spacingLeft.coerceAtLeast(0L))
    }

    /** Caller holds [refreshLock]. One deferred refresh at a time, further triggers ride along. */
    private fun scheduleFollowUp(delayMillis: Long) {
        if (followUpJob?.isActive == true) return
        // A failure retry already covers this and starting earlier would skip its backoff
        if (retryJob?.isActive == true) return
        followUpJob = appScope.launch {
            delay(delayMillis)
            val trigger = refreshLock.withLock {
                followUpJob = null
                pendingTrigger.also { pendingTrigger = null }
            } ?: return@launch
            refresh(trigger.reason)
        }
    }

    /** For triggers that can fire per response, like a restricted item in a batch. */
    suspend fun refreshThrottled(reason: String) {
        val last = lastThrottledElapsed
        if (last != null && serverClock.elapsed() - last < THROTTLE_SPACING_MS) return
        lastThrottledElapsed = serverClock.elapsed()
        refresh(reason)
    }

    fun applyUsage(update: UsageUpdate) {
        _state.update { current ->
            if (current == null) return@update null
            val usage = current.usage
            val bucket = update.bucket.uppercase()
            val currentAllowance = when (bucket) {
                BUCKET_VIEWING -> usage.viewing
                BUCKET_SEARCH -> usage.search
                BUCKET_WATCH -> usage.watch
                else -> return@update current
            }
            // A replayed batch carries older counters, usage never moves backwards within a period
            val accept = update.resetsAt > usage.resetsAt ||
                    (update.resetsAt == usage.resetsAt && update.allowance.used >= currentAllowance.used)
            if (!accept) return@update current

            val updated = when (bucket) {
                BUCKET_VIEWING -> usage.copy(resetsAt = update.resetsAt, viewing = update.allowance)
                BUCKET_SEARCH -> usage.copy(resetsAt = update.resetsAt, search = update.allowance)
                else -> usage.copy(resetsAt = update.resetsAt, watch = update.allowance)
            }
            current.copy(usage = updated)
        }
    }

    private suspend fun attempt(reason: String, startedAt: Long) {
        log(TAG) { "refresh($reason)" }
        try {
            val response = sessionManager.authed { endpoint.access(it) }
            val installationId = (sessionManager.state.value as? SessionState.Active)?.installationId
            if (installationId == null) log(TAG, WARN) { "Access fetched without a known installation id" }
            val fetched = AccessState.from(
                response = response,
                installationId = installationId ?: "",
                fetchedAt = serverClock.now(),
            )
            _state.value = fetched
            lastCoveredElapsed = startedAt
            persisted.value(fetched)
            retryWanted = false
            backoffMillis = INITIAL_BACKOFF_MS
            scheduleAllowanceReset(fetched)
        } catch (e: SessionRevokedException) {
            retryWanted = false
            log(TAG, WARN) { "Access unavailable, installation is revoked" }
        } catch (e: ServerApiException) {
            if (e.isRetryable) {
                scheduleRetry(reason, e.retryAfterSeconds?.times(1000))
            } else {
                log(TAG, ERROR) { "Access refresh failed: ${e.asLog()}" }
            }
        } catch (e: IOException) {
            scheduleRetry(reason, null)
        }
    }

    /** A server hint may stretch a wait but never shortens the backoff a repeated failure grew. */
    private fun scheduleRetry(reason: String, serverHintMillis: Long?) {
        retryWanted = true
        val wait = maxOf(backoffMillis, serverHintMillis ?: 0L).coerceAtLeast(MIN_SPACING_MS)
        backoffMillis = (backoffMillis * 2).coerceAtMost(MAX_BACKOFF_MS)
        log(TAG) { "Retrying access refresh in ${wait}ms" }
        retryJob?.cancel()
        retryJob = appScope.launch {
            delay(wait)
            val self = coroutineContext[Job]
            // Once the attempt runs it is represented by inFlight, only a waiting retry may hold follow-ups back
            refreshLock.withLock { if (retryJob === self) retryJob = null }
            refresh("$reason-retry")
        }
    }

    private fun scheduleAllowanceReset(current: AccessState) {
        resetJob?.cancel()
        resetJob = appScope.launch {
            val wait = current.resetsAt.toEpochMilli() - serverClock.now().toEpochMilli()
            if (wait > 0) delay(wait + RESET_GRACE_MS)
            refresh("allowance-reset")
        }
    }

    private val ServerApiException.isRetryable: Boolean
        get() = status >= 500 || code in setOf(
            ServerCodes.QUOTA_EXCEEDED,
            ServerCodes.INSTALLATION_RATE_EXCEEDED,
            ServerCodes.DATABASE_UNAVAILABLE,
        )

    companion object {
        private const val MIN_SPACING_MS = 1_500L
        private const val THROTTLE_SPACING_MS = 5 * 60 * 1000L
        private const val INITIAL_BACKOFF_MS = 5_000L
        private const val MAX_BACKOFF_MS = 5 * 60 * 1000L
        private const val RESET_GRACE_MS = 1_000L
        private const val BUCKET_VIEWING = "VIEWING"
        private const val BUCKET_SEARCH = "SEARCH"
        private const val BUCKET_WATCH = "WATCH"
        private val TAG = logTag("Server", "AccessRepo")
    }
}
