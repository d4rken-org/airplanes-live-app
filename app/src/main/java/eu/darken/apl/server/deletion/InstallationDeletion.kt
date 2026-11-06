package eu.darken.apl.server.deletion

import eu.darken.apl.common.coroutine.AppScope
import eu.darken.apl.common.debug.logging.Logging.Priority.ERROR
import eu.darken.apl.common.debug.logging.Logging.Priority.INFO
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.ServerCodes
import eu.darken.apl.server.api.ServerEndpoint
import eu.darken.apl.server.session.SessionManager
import eu.darken.apl.server.session.SessionRevokedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

sealed interface DeletionResult {
    data object Deleted : DeletionResult

    /** The server data is gone, the device data is not. */
    data class WipeFailed(val cause: Throwable?) : DeletionResult

    data class Refused(val reason: Reason, val installationId: String?) : DeletionResult {
        enum class Reason { RESTRICTED, REVOKED }
    }

    data object UpdateRequired : DeletionResult

    /** The server may or may not have deleted the data. */
    data class NotConfirmed(val cause: Throwable, val retryAfterSeconds: Long?) : DeletionResult
}

/** Deletes the installation's server data, then wipes everything on the device. */
@Singleton
class InstallationDeletion internal constructor(
    private val appScope: CoroutineScope,
    private val stateStore: DeletionStateStore,
    private val requestGate: ServerRequestGate,
    private val sessionManager: SessionManager,
    private val endpoint: ServerEndpoint,
    private val wiper: LocalDataWiper,
    private val fallbackInstallationId: suspend () -> String?,
) {
    @Inject constructor(
        @AppScope appScope: CoroutineScope,
        stateStore: DeletionStateStore,
        requestGate: ServerRequestGate,
        sessionManager: SessionManager,
        endpoint: ServerEndpoint,
        wiper: LocalDataWiper,
        accessRepo: AccessRepo,
    ) : this(
        appScope = appScope,
        stateStore = stateStore,
        requestGate = requestGate,
        sessionManager = sessionManager,
        endpoint = endpoint,
        wiper = wiper,
        fallbackInstallationId = {
            accessRepo.awaitLoaded()
            accessRepo.state.value?.installationId
        },
    )

    private val mutex = Mutex()

    private val launchState: Deferred<DeletionState> = appScope.async { stateStore.current() }
    private val launchPromptTaken = AtomicBoolean(false)

    val state: Flow<DeletionState> = stateStore.state

    /** Runs in the app scope, a caller that stops waiting does not interrupt it. */
    suspend fun delete(): DeletionResult = appScope.async {
        mutex.withLock {
            // Writes of this process must not be mistaken for the state it started with
            launchState.await()
            requestGate.deletionOperation { deleteLocked() }
        }
    }.await()

    /** Forgets a deletion whose outcome is unknown. A confirmed one still has to be wiped. */
    suspend fun cancelPending() {
        mutex.withLock {
            launchState.await()
            if (stateStore.current() == DeletionState.REQUEST_PENDING) {
                log(TAG, INFO) { "cancelPending(): dropping the unconfirmed deletion" }
                stateStore.set(DeletionState.NONE)
            }
        }
    }

    /** The deletion state this process started with, once per process, null when there was none. */
    suspend fun takeLaunchPrompt(): DeletionState? {
        val start = launchState.await()
        if (start == DeletionState.NONE) return null
        return if (launchPromptTaken.compareAndSet(false, true)) start else null
    }

    private suspend fun deleteLocked(): DeletionResult {
        if (stateStore.current() == DeletionState.WIPE_PENDING) {
            log(TAG, INFO) { "delete(): server deletion already confirmed, wiping again" }
        } else {
            requestServerDeletion()?.let { return it }
        }
        return wipeLocal()
    }

    /** Null once the server confirmed the deletion. */
    private suspend fun requestServerDeletion(): DeletionResult? {
        var installationId = sessionManager.storedInstallationId() ?: fallbackInstallationId()
        try {
            stateStore.set(DeletionState.REQUEST_PENDING)
            sessionManager.refreshNow()
            sessionManager.authedAs { id, token ->
                installationId = id
                endpoint.deleteInstallation(token)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SessionRevokedException) {
            log(TAG, WARN) { "delete(): installation is revoked" }
            stateStore.set(DeletionState.NONE)
            return DeletionResult.Refused(DeletionResult.Refused.Reason.REVOKED, installationId)
        } catch (e: ServerApiException) {
            log(TAG, WARN) { "delete(): server answered ${e.code} (HTTP ${e.status})" }
            return when (e.code) {
                ServerCodes.INSTALLATION_REVOKED -> {
                    stateStore.set(DeletionState.NONE)
                    DeletionResult.Refused(DeletionResult.Refused.Reason.REVOKED, installationId)
                }

                ServerCodes.INSTALLATION_RESTRICTED -> {
                    stateStore.set(DeletionState.NONE)
                    DeletionResult.Refused(DeletionResult.Refused.Reason.RESTRICTED, installationId)
                }

                ServerCodes.APP_UPDATE_REQUIRED -> {
                    stateStore.set(DeletionState.NONE)
                    DeletionResult.UpdateRequired
                }

                else -> DeletionResult.NotConfirmed(e, e.retryAfterSeconds)
            }
        } catch (e: Exception) {
            log(TAG, WARN) { "delete(): outcome unknown: ${e.asLog()}" }
            return DeletionResult.NotConfirmed(e, null)
        }
        log(TAG, INFO) { "delete(): server confirmed the deletion" }
        return null
    }

    private suspend fun wipeLocal(): DeletionResult {
        val cause = try {
            stateStore.set(DeletionState.WIPE_PENDING)
            sessionManager.resetIdentity()
            if (wiper.wipe()) return DeletionResult.Deleted
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e
        }
        log(TAG, ERROR) { "delete(): wiping the device data failed${cause?.let { ": ${it.asLog()}" } ?: ""}" }
        return DeletionResult.WipeFailed(cause)
    }

    companion object {
        private val TAG = logTag("Server", "Deletion")
    }
}
