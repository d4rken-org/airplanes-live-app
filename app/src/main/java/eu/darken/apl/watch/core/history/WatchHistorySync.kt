package eu.darken.apl.watch.core.history

import eu.darken.apl.common.datastore.value
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.main.core.request.OperationFailedException
import eu.darken.apl.main.core.request.OperationYieldedException
import eu.darken.apl.main.core.request.OperationStore
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.ServerJson
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.api.HistoryCursor
import eu.darken.apl.server.api.HistoryWatchDefinition
import eu.darken.apl.server.api.HistoryWatchRequest
import eu.darken.apl.server.api.HistoryWatchResponse
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.ServerCodes
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
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
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

        /** The allowance counters are from a period that already ended, fresh ones come first. */
        data class StaleAllowance(val resetAt: Instant) : Page
    }

    private data class Candidate(
        val watch: Watch,
        val sync: WatchHistorySyncEntity?,
        val from: Instant,
        val until: Instant,
        val cursor: HistoryCursor?,
    )

    private val running = AtomicBoolean(false)
    private val rerunRequested = AtomicBoolean(false)

    /** A run in this process; work that appears meanwhile is left to a rerun right after it. */
    val isRunning: Boolean
        get() = running.get()

    fun requestRerun() = rerunRequested.set(true)

    /** Whether something asked for another run while this one was going. */
    fun consumeRerun(): Boolean = rerunRequested.getAndSet(false)

    suspend fun run(): Result {
        running.set(true)
        try {
            return drain()
        } finally {
            running.set(false)
        }
    }

    private suspend fun drain(): Result {
        accessRepo.awaitLoaded()
        val access = accessRepo.state.value
        if (access == null || !access.offersWatchHistory) {
            log(TAG) { "run(): the server offers no watch history to this installation" }
            return Result.Done(null)
        }

        operationStore.pending(OperationStore.Kind.WATCH_HISTORY, serverClock.now()).forEach { pending ->
            yielding { replay(pending) } ?: return Result.Yielded
        }

        repeat(MAX_PAGES_PER_RUN) {
            when (val page = yielding { page() }) {
                null -> return Result.Yielded
                is Page.NothingDue -> return Result.Done(page.at)
                is Page.StaleAllowance -> {
                    // Outside the gate: a live check must not wait for an access request
                    accessRepo.refreshThrottled("history-allowance")
                    return Result.Done(maxOf(page.resetAt, serverClock.now()).plus(RESET_GRACE))
                }
                Page.Sent -> {}
            }
        }
        return Result.Done(serverClock.now())
    }

    /** Null when a live check is waiting; a page cut short that way stays pending for its replay. */
    private suspend fun <T> yielding(block: suspend () -> T): T? = try {
        gate.history(block)
    } catch (e: OperationYieldedException) {
        null
    }

    private suspend fun replay(pending: OperationStore.PendingOperation) {
        val revisions = pending.ownerRevisions
        if (revisions == null) {
            log(TAG, WARN) { "Pending history ${pending.operationId} has no revisions, discarding it" }
            operationStore.complete(pending.operationId)
            return
        }
        // Finalized before a crash kept the row from being retired, sending it again would be wasted
        if (watchDb.history.isApplied(pending.operationId)) {
            operationStore.complete(pending.operationId)
            return
        }
        val request = try {
            json.decodeFromString(HistoryWatchRequest.serializer(), pending.requestJson)
        } catch (e: SerializationException) {
            log(TAG, WARN) { "Pending history ${pending.operationId} cannot be read, starting its watches over" }
            deferAndRetire(pending.operationId, pending.ownerIds, revisions, WatchHistoryPlan.TOP_UP_INTERVAL, startOver = true)
            return
        }
        val response = try {
            aircraftRepo.replayHistoryWatches(pending, abandonRetry = { gate.isLiveWaiting })
        } catch (e: OperationFailedException) {
            log(TAG, WARN) { "Pending history ${pending.operationId} is gone: ${e.code}" }
            deferAndRetire(pending.operationId, pending.ownerIds, revisions, RETRY_DELAY, startOver = false)
            return
        } catch (e: ServerApiException) {
            if (!e.resetsHistoryBatch) throw e
            log(TAG, WARN) { "Pending history ${pending.operationId} was refused: ${e.code}" }
            deferAndRetire(pending.operationId, pending.ownerIds, revisions, WatchHistoryPlan.TOP_UP_INTERVAL, startOver = true)
            return
        } catch (e: SerializationException) {
            // Only a stored answer that no longer reads is this operation's own fault
            if (pending.resultJson == null) throw e
            log(TAG, WARN) { "Stored history answer ${pending.operationId} cannot be read, starting over" }
            deferAndRetire(pending.operationId, pending.ownerIds, revisions, WatchHistoryPlan.TOP_UP_INTERVAL, startOver = true)
            return
        }
        // The tier this was evaluated under is not known anymore
        apply(request, pending.ownerIds, revisions, response, evaluatedTier = null)
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
            // A continuation that does not read anymore is dropped, the watch starts a fresh range
            val cursor = sync?.cursorJson?.let {
                try {
                    json.decodeFromString(HistoryCursor.serializer(), it)
                } catch (e: SerializationException) {
                    log(TAG, WARN) { "Stored cursor of ${watch.id} cannot be read, starting over" }
                    null
                }
            }
            val candidate = if (cursor != null && sync.windowFrom != null && sync.windowUntil != null) {
                Candidate(watch, sync, sync.windowFrom, sync.windowUntil, cursor)
            } else {
                val (retainedFrom, retainedUntil) = WatchHistoryPlan.retainedWindow(now)
                val cached = watchDb.history.getHours(watch.id, retainedFrom, retainedUntil)
                Candidate(watch, sync, WatchHistoryPlan.evaluationStart(now, cached), retainedUntil, null)
            }
            candidates.add(candidate)
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
            return if (access.resetsAt > now) {
                Page.NothingDue(access.resetsAt.plus(RESET_GRACE))
            } else {
                Page.StaleAllowance(access.resetsAt)
            }
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
                    cursor = candidate.cursor,
                )
            },
        )
        val ownerIds = batch.map { it.watch.id }
        val revisions = batch.map { it.watch.definitionRevision }
        log(TAG) { "page(): ${batch.size} watches, ${oldest.from} to ${oldest.until}" }

        val response = try {
            aircraftRepo.historyWatches(request, ownerIds, revisions, abandonRetry = { gate.isLiveWaiting })
        } catch (e: OperationFailedException) {
            // The runner retired the id; the cursors stand and are asked for again after a pause
            log(TAG, WARN) { "History page ${request.operationId} failed: ${e.code}" }
            deferAndRetire(request.operationId, ownerIds, revisions, RETRY_DELAY, startOver = false)
            return Page.Sent
        } catch (e: ServerApiException) {
            if (!e.resetsHistoryBatch) throw e
            // Refused as a whole: these watches start over later, the others go on now
            log(TAG, WARN) { "History page ${request.operationId} was refused: ${e.code}" }
            deferAndRetire(request.operationId, ownerIds, revisions, WatchHistoryPlan.TOP_UP_INTERVAL, startOver = true)
            return Page.Sent
        }
        apply(request, ownerIds, revisions, response, evaluatedTier = access.tier.name)
        operationStore.complete(request.operationId)
        return Page.Sent
    }

    /**
     * Postpones watches the server gave no usable answer for, so the others are not stuck behind
     * them. The operation is finalized with it: a replay of the id afterwards is skipped.
     */
    private suspend fun deferAndRetire(
        operationId: String,
        ownerIds: List<String>,
        revisions: List<Long>,
        delay: Duration,
        startOver: Boolean,
    ) {
        val now = serverClock.now()
        val until = now.plus(delay)
        watchDb.applyHistory(operationId, now) {
            ownerIds.forEachIndexed { index, watchId ->
                val revision = revisions.getOrNull(index) ?: return@forEachIndexed
                if (revisionOf(watchId) != revision) return@forEachIndexed
                val current = syncOf(watchId)?.takeIf { it.definitionRevision == revision }
                    ?: WatchHistorySyncEntity(watchId, revision, nextEligibleAt = now, updatedAt = now)
                val deferred = current.copy(nextEligibleAt = until, updatedAt = now)
                writeSync(if (startOver) deferred.copy(windowFrom = null, windowUntil = null, cursorJson = null) else deferred)
            }
        }
        operationStore.complete(operationId)
    }

    private suspend fun apply(
        request: HistoryWatchRequest,
        ownerIds: List<String>,
        revisions: List<Long>,
        response: HistoryWatchResponse,
        evaluatedTier: String?,
    ) {
        val now = serverClock.now()
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
                writeSync(WatchHistoryPlan.next(current, outcome, cursorJson, from, until, now, evaluatedTier, resetsAt))
            }
        }
        if (!applied) log(TAG) { "History ${response.operationId} was applied before" }
    }

    /** Only a malformed request is these watches' fault; anything else concerns the whole installation. */
    private val ServerApiException.resetsHistoryBatch: Boolean
        get() = status == 400 && code == ServerCodes.INVALID_REQUEST

    private suspend fun checkInterval(): Duration = WatchWorkerHelper.effectiveInterval(
        watchSettings.watchMonitorInterval.value(),
        upgradeRepo.isProNow(),
    )

    companion object {
        /** Bounds one run; a watch whose answers keep asking for another page cannot spin forever. */
        private const val MAX_PAGES_PER_RUN = 20
        private val RETRY_DELAY: Duration = Duration.ofMinutes(15)

        /** Lands after the access refresh that a reset triggers. */
        private val RESET_GRACE: Duration = Duration.ofMinutes(1)
        private val TAG = logTag("Watch", "History", "Sync")
    }
}
