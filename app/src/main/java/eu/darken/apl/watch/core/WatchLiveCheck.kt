package eu.darken.apl.watch.core

import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.main.core.query.WatchOutcome
import eu.darken.apl.main.core.request.OperationStore
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.api.ServerCodes
import eu.darken.apl.server.identity.newOperationId
import eu.darken.apl.watch.core.types.Watch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * What a watch matches at this moment, to show it. Nothing is recorded as one of the watch's checks:
 * for a watch the server holds, its watch list stays the source of the watch's history and alerts.
 */
class WatchLiveCheck @Inject constructor(
    private val aircraftRepo: AircraftRepo,
    private val operationStore: OperationStore,
    private val gate: WatchRequestGate,
    private val accessRepo: AccessRepo,
    private val serverClock: ServerClock,
) {

    suspend fun check(watch: Watch): WatchOutcome {
        log(TAG) { "check(${watch.id})" }
        val operationId = newOperationId()
        val chunk = AircraftRepo.Chunk(
            items = listOf(watch.toDefinition()),
            ownerIds = listOf(watch.id),
            operationId = operationId,
            ownerRevisions = listOf(watch.definitionRevision),
        )
        val outcome = gate.live {
            // A process that died during a check left its operation behind, nobody is waiting for that answer
            operationStore.pending(OperationStore.Kind.WATCH_LIVE, serverClock.now()).forEach {
                operationStore.complete(it.operationId)
            }
            try {
                aircraftRepo.checkWatches(listOf(chunk), OperationStore.Kind.WATCH_LIVE).single().outcomes.single()
            } finally {
                withContext(NonCancellable) { operationStore.complete(operationId) }
            }
        }
        // Both say the stored policy is out of date, as for the monitor's own checks
        if (outcome is WatchOutcome.Rejected && outcome.code in STALE_POLICY_CODES) {
            accessRepo.refreshThrottled("watch-live-rejected")
        }
        return outcome
    }

    companion object {
        private val STALE_POLICY_CODES = setOf(ServerCodes.TIER_RESTRICTED, ServerCodes.DAILY_ALLOWANCE_EXHAUSTED)
        private val TAG = logTag("Watch", "LiveCheck")
    }
}
