package eu.darken.apl.watch.core.history

import eu.darken.apl.common.datastore.value
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.main.core.request.OperationFailedException
import eu.darken.apl.main.core.request.OperationStore
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.ServerJson
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.server.api.HistoryCursor
import eu.darken.apl.server.api.HistoryWatchDefinition
import eu.darken.apl.server.api.HistoryWatchRequest
import eu.darken.apl.server.api.HistoryWatchResponse
import eu.darken.apl.server.identity.newOperationId
import eu.darken.apl.upgrade.UpgradeRepo
import eu.darken.apl.upgrade.isProNow
import eu.darken.apl.watch.core.WatchRepo
import eu.darken.apl.watch.core.WatchRequestGate
import eu.darken.apl.watch.core.WatchSettings
import eu.darken.apl.watch.core.alerts.WatchWorkerHelper
import eu.darken.apl.watch.core.db.WatchDatabase
import eu.darken.apl.watch.core.db.history.WatchHistorySyncEntity
import eu.darken.apl.watch.core.toDefinition
import eu.darken.apl.watch.core.types.Watch
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fills each watch's activity from the server's history, one page at a time and never ahead of a
 * live check. Only one page is ever outstanding, and an unapplied one is finished first, so an
 * older page can never land after a newer one for the same watch.
 */
@Singleton
class WatchHistorySync @Inject constructor(
    private val watchRepo: WatchRepo,
    private val watchDb: WatchDatabase,
    private val aircraftRepo: AircraftRepo,
    private val operationStore: OperationStore,
    private val accessRepo: AccessRepo,
    private val serverClock: ServerClock,
    private val gate: WatchRequestGate,
    private val watchSettings: WatchSettings,
    private val upgradeRepo: UpgradeRepo,
    @param:ServerJson private val json: Json,
) {

    sealed interface Result {
        /** Nothing left until [at]; null when only a new watch or a tier change can create work. */
        data class Done(val at: Instant?) : Result

        /** A live check is waiting, the remaining pages run after it. */
        data object Yielded : Result
    }

    private sealed interface Page {
        data object Sent : Page
        data class NothingDue(val at: Instant?) : Page
    }

    private data class Candidate(
        val watch: Watch,
        val sync: WatchHistorySyncEntity?,
        val from: Instant,
        val until: Instant,
        val cursorJson: String?,
    )

    suspend fun run(): Result {
        accessRepo.awaitLoaded()
        val access = accessRepo.state.value
        if (access == null || !access.offersWatchHistory) {
            log(TAG) { "run(): the server offers no watch history to this installation" }
            return Result.Done(null)
        }

        operationStore.pending(OperationStore.Kind.WATCH_HISTORY, serverClock.now()).forEach { pending ->
            gate.history { replay(pending) } ?: return Result.Yielded
        }

        repeat(MAX_PAGES_PER_RUN) {
            when (val page = gate.history { page() }) {
                null -> return Result.Yielded
                is Page.NothingDue -> return Result.Done(page.at)
                Page.Sent -> {}
            }
        }
        return Result.Done(serverClock.now())
    }

    private suspend fun replay(pending: OperationStore.PendingOperation) {
        val revisions = pending.ownerRevisions
        if (revisions == null) {
            log(TAG, WARN) { "Pending history ${pending.operationId} has no revisions, discarding it" }
            operationStore.complete(pending.operationId)
            return
        }
        val request = json.decodeFromString(HistoryWatchRequest.serializer(), pending.requestJson)
        val response = try {
            aircraftRepo.replayHistoryWatches(pending)
        } catch (e: OperationFailedException) {
            // Nothing was applied, the watches' cursors still stand and the next page uses a new id
            log(TAG, WARN) { "Pending history ${pending.operationId} is gone: ${e.code}" }
            return
        }
        apply(request, pending.ownerIds, revisions, response)
        operationStore.complete(pending.operationId)
    }

    private suspend fun page(): Page {
        val access = accessRepo.state.value ?: return Page.NothingDue(null)
        if (!access.offersWatchHistory) return Page.NothingDue(null)
        val now = serverClock.now()
        val watches = watchRepo.watches.first()
        val syncs = watchDb.history.getAllSync().associateBy { it.watchId }

        val candidates = mutableListOf<Candidate>()
        var nextAt: Instant? = null
        for (watch in watches) {
            val sync = syncs[watch.id]?.takeIf { it.definitionRevision == watch.definitionRevision }
            if (!access.allowsWatchType(watch.toDefinition().type)) continue
            // Refused under this tier: only a different tier is worth asking again
            if (sync?.restrictedTier == access.tier.name) continue
            val eligibleAt = sync?.nextEligibleAt ?: now
            if (eligibleAt > now) {
                nextAt = minOf(nextAt ?: eligibleAt, eligibleAt)
                continue
            }
            val window = if (sync?.cursorJson != null && sync.windowFrom != null && sync.windowUntil != null) {
                Triple(sync.windowFrom, sync.windowUntil, sync.cursorJson)
            } else {
                val (retainedFrom, retainedUntil) = WatchHistoryPlan.retainedWindow(now)
                val cached = watchDb.history.getHours(watch.id, retainedFrom, retainedUntil)
                Triple(WatchHistoryPlan.evaluationStart(now, cached), retainedUntil, null)
            }
            candidates.add(Candidate(watch, sync, window.first, window.second, window.third))
        }
        if (candidates.isEmpty()) return Page.NothingDue(nextAt)

        val spendable = WatchHistoryPlan.spendable(
            remaining = access.usage.watch.remaining,
            watchCount = watches.size,
            now = now,
            resetsAt = access.resetsAt,
            checkInterval = checkInterval(),
        )
        if (spendable <= 0) {
            log(TAG) { "page(): allowance is kept for live checks until ${access.resetsAt}" }
            return Page.NothingDue(access.resetsAt)
        }

        // Watches waiting longest go first, a page shares one range so a group is one request
        val oldest = candidates.minBy { it.sync?.updatedAt ?: Instant.EPOCH }
        val batch = candidates
            .filter { it.from == oldest.from && it.until == oldest.until }
            .sortedBy { it.sync?.updatedAt ?: Instant.EPOCH }
            .take(minOf(spendable, AircraftRepo.MAX_BATCH_ITEMS))

        val request = HistoryWatchRequest(
            operationId = newOperationId(),
            from = oldest.from.toEpochMilli(),
            until = oldest.until.toEpochMilli(),
            watches = batch.map { candidate ->
                val definition = candidate.watch.toDefinition()
                HistoryWatchDefinition(
                    type = definition.type,
                    value = definition.value,
                    latitude = definition.latitude,
                    longitude = definition.longitude,
                    radiusKm = definition.radiusKm,
                    cursor = candidate.cursorJson?.let { json.decodeFromString(HistoryCursor.serializer(), it) },
                )
            },
        )
        val ownerIds = batch.map { it.watch.id }
        val revisions = batch.map { it.watch.definitionRevision }
        log(TAG) { "page(): ${batch.size} watches, ${oldest.from} to ${oldest.until}" }

        val response = try {
            aircraftRepo.historyWatches(request, ownerIds, revisions)
        } catch (e: OperationFailedException) {
            // Nothing was applied and the cursors still stand; trying again is left to the next run
            log(TAG, WARN) { "History page ${request.operationId} failed: ${e.code}" }
            return Page.NothingDue(now.plus(RETRY_DELAY))
        }
        apply(request, ownerIds, revisions, response)
        operationStore.complete(request.operationId)
        return Page.Sent
    }

    private suspend fun apply(
        request: HistoryWatchRequest,
        ownerIds: List<String>,
        revisions: List<Long>,
        response: HistoryWatchResponse,
    ) {
        val now = serverClock.now()
        val access: AccessState? = accessRepo.state.value
        val tier = access?.tier?.name ?: AccessState.Tier.FREE.name
        val resetsAt = Instant.ofEpochMilli(response.usage.resetsAt)
        val from = Instant.ofEpochMilli(request.from)
        val until = Instant.ofEpochMilli(request.until)

        val applied = watchDb.applyHistory(response.operationId, now) {
            response.outcomes.forEach { outcome ->
                val watchId = ownerIds.getOrNull(outcome.index) ?: return@forEach
                val revision = revisions.getOrNull(outcome.index) ?: return@forEach
                // Moved or deleted since the request was built: this answer is for something else
                if (revisionOf(watchId) != revision) return@forEach
                val current = syncOf(watchId)?.takeIf { it.definitionRevision == revision }
                    ?: WatchHistorySyncEntity(watchId, revision, nextEligibleAt = now, updatedAt = now)
                if (outcome.status == WatchHistoryPlan.STATUS_ANSWERED) {
                    writeHours(WatchHistoryPlan.hours(watchId, outcome))
                }
                val cursorJson = outcome.computedUntil?.let { json.encodeToString(HistoryCursor.serializer(), it) }
                writeSync(WatchHistoryPlan.next(current, outcome, cursorJson, from, until, now, tier, resetsAt))
            }
        }
        if (!applied) log(TAG) { "History ${response.operationId} was applied before" }
    }

    private suspend fun checkInterval(): Duration = WatchWorkerHelper.effectiveInterval(
        watchSettings.watchMonitorInterval.value(),
        upgradeRepo.isProNow(),
    )

    companion object {
        /** Bounds one run; a watch whose answers keep asking for another page cannot spin forever. */
        private const val MAX_PAGES_PER_RUN = 20
        private val RETRY_DELAY: Duration = Duration.ofMinutes(15)
        private val TAG = logTag("Watch", "History", "Sync")
    }
}
