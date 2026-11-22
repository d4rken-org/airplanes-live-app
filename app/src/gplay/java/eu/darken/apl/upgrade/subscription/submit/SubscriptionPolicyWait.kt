package eu.darken.apl.upgrade.subscription.submit

import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.access.AccessRepo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the access policy may still predate what the server was told about a purchase. A policy
 * answers a submission only when its fetch started after the submission's answer arrived; a fetch
 * that was already running may finish later and still not know about it.
 */
@Singleton
class SubscriptionPolicyWait @Inject constructor(
    private val store: SubscriptionTokenStore,
    private val accessRepo: AccessRepo,
    private val serverClock: ServerClock,
) {

    private data class Marker(val installationId: String, val elapsed: Long)

    // Lost with the process: then any fetch started in this process is newer than the submission
    private val marker = MutableStateFlow<Marker?>(null)

    // Keeps a clear from interleaving between a new marker and the flag stored with it
    private val lock = Mutex()

    /** Stores [block]'s change together with the flag; call it once the server answered and before refreshing access. */
    suspend fun afterSubmission(
        installationId: String,
        block: (SubscriptionTokens) -> SubscriptionTokens,
    ): SubscriptionTokens = lock.withLock {
        marker.value = Marker(installationId, serverClock.elapsed())
        store.update { block(it).copy(awaitingPolicyFor = installationId) }
    }

    /**
     * The installation whose policy may still predate its last submission, null when none. Collecting
     * this also clears the stored flag once a policy answered it.
     */
    val awaitingFor: Flow<String?> = combine(store.tokens, marker, accessRepo.state) { tokens, _, _ ->
        tokens.awaitingPolicyFor
    }
        .map { awaiting ->
            when {
                awaiting == null -> null
                isAnswered(awaiting) -> {
                    clearIfAnswered(awaiting)
                    null
                }

                else -> awaiting
            }
        }
        .distinctUntilChanged()

    // Reads the newest marker and policy rather than the combined ones, which can lag behind each other
    private fun isAnswered(installationId: String): Boolean {
        val access = accessRepo.state.value ?: return false
        if (access.installationId != installationId) return false
        val fetchStarted = access.fetchStartedElapsed ?: return false
        val submitted = marker.value?.takeIf { it.installationId == installationId } ?: return true
        return fetchStarted >= submitted.elapsed
    }

    private suspend fun clearIfAnswered(installationId: String) = lock.withLock {
        if (!isAnswered(installationId)) return@withLock
        store.update { tokens ->
            if (tokens.awaitingPolicyFor == installationId) tokens.copy(awaitingPolicyFor = null) else tokens
        }
        log(TAG) { "The policy for $installationId was fetched after the last submission" }
    }

    companion object {
        private val TAG = logTag("Upgrade", "Subscription", "Submit", "PolicyWait")
    }
}
