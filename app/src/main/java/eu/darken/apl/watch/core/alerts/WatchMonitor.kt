package eu.darken.apl.watch.core.alerts

import eu.darken.apl.common.datastore.value
import eu.darken.apl.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.db.AircraftDatabase
import eu.darken.apl.main.core.query.BatchResult
import eu.darken.apl.main.core.query.WatchOutcome
import eu.darken.apl.main.core.request.OperationFailedException
import eu.darken.apl.main.core.request.OperationStore
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.api.Allowance
import eu.darken.apl.server.api.ServerCodes
import eu.darken.apl.server.identity.newOperationId
import eu.darken.apl.watch.core.WatchId
import eu.darken.apl.watch.core.WatchRepo
import eu.darken.apl.watch.core.WatchRequestGate
import eu.darken.apl.watch.core.WatchSettings
import eu.darken.apl.watch.core.toDefinition
import eu.darken.apl.watch.core.db.WatchDatabase
import eu.darken.apl.watch.core.history.WatchHistoryRepo
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.Watch
import eu.darken.apl.watch.core.types.WatchCheckOutcome
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchMonitor @Inject constructor(
    private val settings: WatchSettings,
    private val watchRepo: WatchRepo,
    private val watchDb: WatchDatabase,
    private val historyRepo: WatchHistoryRepo,
    private val aircraftRepo: AircraftRepo,
    private val aircraftDatabase: AircraftDatabase,
    private val operationStore: OperationStore,
    private val serverClock: ServerClock,
    private val accessRepo: AccessRepo,
    private val notifications: WatchAlertNotifications,
    private val gate: WatchRequestGate,
) {

    enum class Trigger {
        PERIODIC,
        MANUAL,
        APP_START,
        SCREEN_OPEN,
        FOREGROUND,
        ;
    }

    data class CheckSummary(
        val evaluated: Int = 0,
        val inconclusive: Int = 0,
        val restricted: Int = 0,
        val exhausted: Int = 0,
        val remainingAllowance: Allowance? = null,
        val resetsAt: Instant? = null,
    )

    suspend fun check(trigger: Trigger): CheckSummary = gate.live {
        log(TAG) { "check($trigger)" }
        val watches = watchRepo.watches.first()
        val byId = watches.associateBy { it.id }
        var summary = CheckSummary()

        try {
            // Results the app paid for but never applied come first, they are free to replay
            val covered = mutableSetOf<WatchId>()
            operationStore.pending(OperationStore.Kind.WATCH, serverClock.now()).forEach { pending ->
                val revisions = pending.ownerRevisions
                if (revisions == null) {
                    // Sent before revisions were recorded, so what it evaluated cannot be told apart
                    // from an area the watch was moved away from since
                    log(TAG, WARN) { "Pending operation ${pending.operationId} has no revisions, discarding it" }
                    operationStore.complete(pending.operationId)
                    return@forEach
                }
                val result = try {
                    aircraftRepo.replayWatchOperation(pending)
                } catch (e: OperationFailedException) {
                    log(TAG, WARN) { "Pending operation ${pending.operationId} is gone: ${e.code}" }
                    operationStore.complete(pending.operationId)
                    if (e.code == ServerCodes.RESULT_TOO_LARGE) {
                        val outstanding = pending.ownerIds.mapNotNull { byId[it] }
                        if (outstanding.isNotEmpty()) {
                            summary = summary.splitAndRun(outstanding, byId)
                            covered.addAll(pending.ownerIds)
                        }
                    }
                    return@forEach
                }
                // A stored receipt is not the only way to get an old answer: re-sending the id can
                // have the server return the answer it kept, which evaluated nothing now
                val recovered = pending.resultJson != null || result.replayed
                val live = result.withoutStale(serverClock.now(), recovered)
                val applied = summary.apply(live, pending.ownerIds, revisions, byId)
                summary = applied.summary
                operationStore.complete(pending.operationId)
                // Only the watches this receipt still answered for are done for this round
                covered.addAll(applied.settled)
            }

            val outstanding = watches.filter { it.id !in covered }
            outstanding.chunked(AircraftRepo.MAX_BATCH_ITEMS).forEach { chunk ->
                summary = summary.run(chunk, byId)
            }
        } catch (e: Exception) {
            log(TAG, WARN) { "Watch check failed: ${e.asLog()}" }
            val now = serverClock.now()
            watches.forEach {
                watchDb.updateLastCheck(it.id, it.definitionRevision, now, WatchCheckOutcome.FAILED, e.javaClass.simpleName)
            }
            settings.lastCheck.value(now)
            throw e
        }

        settings.lastCheck.value(serverClock.now())
        cleanup()
        summary
    }

    private suspend fun CheckSummary.run(chunk: List<Watch>, byId: Map<WatchId, Watch>): CheckSummary {
        val definitions = chunk.map { it.toDefinition() }
        val ownerIds = chunk.map { it.id }
        val revisions = chunk.map { it.definitionRevision }
        var operationId = newOperationId()

        val results = try {
            aircraftRepo.checkWatches(
                listOf(AircraftRepo.Chunk(definitions, ownerIds, operationId, revisions))
            )
        } catch (e: OperationFailedException) {
            if (e.code == ServerCodes.RESULT_TOO_LARGE) return splitAndRun(chunk, byId)
            // The server cannot answer this id anymore, a fresh one costs the same evaluation
            log(TAG, WARN) { "Operation ${e.code}, resending under a new id" }
            operationId = newOperationId()
            aircraftRepo.checkWatches(listOf(AircraftRepo.Chunk(definitions, ownerIds, operationId, revisions)))
        }

        var summary = this
        results.forEach { summary = summary.apply(it, ownerIds, revisions, byId).summary }
        operationStore.complete(operationId)
        return summary
    }

    /** An answer that does not fit is asked for in halves, a single watch has nothing left to split. */
    private suspend fun CheckSummary.splitAndRun(chunk: List<Watch>, byId: Map<WatchId, Watch>): CheckSummary {
        val single = chunk.singleOrNull()
        if (single != null) {
            log(TAG, WARN) { "Result for ${single.id} is too large to deliver" }
            watchDb.updateLastCheck(
                single.id,
                single.definitionRevision,
                serverClock.now(),
                WatchCheckOutcome.FAILED,
                ServerCodes.RESULT_TOO_LARGE,
            )
            return this
        }
        log(TAG, WARN) { "Result too large for ${chunk.size} watches, halving the batch" }
        var summary = this
        chunk.chunked((chunk.size + 1) / 2).forEach { half -> summary = summary.run(half, byId) }
        return summary
    }

    /**
     * An answer recovered from before describes the moment it was produced, not this one.
     *
     * Observations are dropped once they expire, whatever route they arrived by. A refusal or a gap
     * in evidence is dropped whenever it was [recovered], because the circumstance behind it is
     * exactly what may have changed since; the watch is then evaluated again, which is a new check
     * and may cost what a check costs.
     */
    private fun BatchResult<WatchOutcome>.withoutStale(
        now: Instant,
        recovered: Boolean,
    ): BatchResult<WatchOutcome> = copy(
        outcomes = outcomes.map { outcome ->
            when (outcome) {
                is WatchOutcome.Matched -> outcome.takeIf { it.expiresAt?.isAfter(now) == true }
                is WatchOutcome.Absent -> outcome.takeIf { it.expiresAt?.isAfter(now) == true }
                else -> outcome.takeUnless { recovered }
            } ?: WatchOutcome.Expired
        },
    )

    /** [settled] are the watches this result answered for, a stale or expired outcome settles nothing. */
    private data class Applied(val summary: CheckSummary, val settled: Set<WatchId>)

    private suspend fun CheckSummary.apply(
        result: BatchResult<WatchOutcome>,
        ownerIds: List<String>,
        ownerRevisions: List<Long>,
        byId: Map<WatchId, Watch>,
    ): Applied {
        val now = serverClock.now()
        var evaluated = this.evaluated
        var inconclusive = this.inconclusive
        var restricted = this.restricted
        var exhausted = this.exhausted
        val settled = mutableSetOf<WatchId>()

        result.outcomes.forEachIndexed { index, outcome ->
            val watchId = ownerIds.getOrNull(index) ?: return@forEachIndexed
            val revision = ownerRevisions.getOrNull(index) ?: return@forEachIndexed
            // A watch may have been deleted while the operation was outstanding
            val watch = byId[watchId] ?: return@forEachIndexed

            when (outcome) {
                is WatchOutcome.Matched -> {
                    val application = watchDb.applyCheck(
                        watchId = watchId,
                        revision = revision,
                        at = now,
                        outcome = WatchCheckOutcome.MATCHED,
                        aircraftCount = outcome.totalMatching ?: outcome.aircraft.size,
                        seenHexes = outcome.aircraft.map { it.hex }.toSet(),
                        operationId = result.operationId,
                        registration = (watch as? AircraftWatch)?.let { aw ->
                            outcome.aircraft.firstOrNull { aw.matches(it) }?.registration
                        },
                    )
                    if (application != WatchDatabase.CheckApplication.Stale) {
                        evaluated++
                        settled.add(watchId)
                    }
                    // Only after the commit, an alert must not go out for a check that was rolled back
                    if (application is WatchDatabase.CheckApplication.Applied) {
                        notifyIfNewHit(watch, application.previousCount, outcome.aircraft)
                    }
                }

                is WatchOutcome.Absent -> {
                    val application = watchDb.applyCheck(
                        watchId = watchId,
                        revision = revision,
                        at = now,
                        outcome = WatchCheckOutcome.ABSENT,
                        aircraftCount = 0,
                        operationId = result.operationId,
                    )
                    if (application != WatchDatabase.CheckApplication.Stale) {
                        evaluated++
                        settled.add(watchId)
                    }
                }

                is WatchOutcome.Expired -> {
                    // Neither a result nor a failure: the watch is simply still due a check
                    log(TAG) { "Discarding an expired recovered result for $watchId" }
                }

                is WatchOutcome.Inconclusive -> {
                    // Missing evidence is not a disappearance, it must never write a zero
                    if (watchDb.updateLastCheck(watchId, revision, now, WatchCheckOutcome.INCONCLUSIVE, outcome.reason)) {
                        inconclusive++
                        settled.add(watchId)
                    }
                }

                is WatchOutcome.Rejected -> {
                    val state = when (outcome.code) {
                        ServerCodes.TIER_RESTRICTED -> WatchCheckOutcome.RESTRICTED
                        ServerCodes.DAILY_ALLOWANCE_EXHAUSTED -> WatchCheckOutcome.EXHAUSTED
                        else -> WatchCheckOutcome.INVALID
                    }
                    if (watchDb.updateLastCheck(watchId, revision, now, state, outcome.code)) {
                        settled.add(watchId)
                        when (state) {
                            WatchCheckOutcome.RESTRICTED -> restricted++
                            WatchCheckOutcome.EXHAUSTED -> exhausted++
                            else -> {}
                        }
                    }
                }
            }
        }

        // Both rejections say the stored policy is out of date, whatever it currently claims
        if (restricted > this.restricted || exhausted > this.exhausted) {
            accessRepo.refreshThrottled("watch-rejected")
        }

        val summary = copy(
            evaluated = evaluated,
            inconclusive = inconclusive,
            restricted = restricted,
            exhausted = exhausted,
            remainingAllowance = result.usage.allowance,
            resetsAt = Instant.ofEpochMilli(result.usage.resetsAt),
        )
        return Applied(summary, settled)
    }

    private suspend fun notifyIfNewHit(watch: Watch, previousCount: Int?, matches: List<Aircraft>) {
        when {
            !watch.isNotificationEnabled -> log(TAG, VERBOSE) { "Notifications are disabled for $watch" }
            previousCount == null -> log(TAG, VERBOSE) { "First ever hit for $watch, staying quiet" }
            previousCount != 0 -> log(TAG, VERBOSE) { "$watch was already tracking aircraft" }
            else -> {
                log(TAG) { "Notifying about $watch" }
                notifications.alert(watch, matches)
            }
        }
    }

    private suspend fun cleanup() {
        val lastCleanup = settings.lastCleanup.value()
        if (Duration.between(lastCleanup, Instant.now()) <= Duration.ofDays(1)) return
        try {
            historyRepo.cleanupOldChecks()
            backfillRegistrations()
            aircraftDatabase.evict(CACHE_RETENTION)
            settings.lastCleanup.update { Instant.now() }
        } catch (e: Exception) {
            log(TAG, WARN) { "Cleanup failed: $e" }
        }
    }

    /** Eviction drops what the cache knew about a watched aircraft, its name has to survive that. */
    private suspend fun backfillRegistrations() {
        val cache = aircraftRepo.cache.first()
        val registrations = watchRepo.watches.first()
            .filterIsInstance<AircraftWatch>()
            .associate { it.id to cache[it.hex.uppercase()]?.registration }
        watchDb.backfillRegistrations(registrations)
    }

    companion object {
        private val CACHE_RETENTION: Duration = Duration.ofDays(7)
        private val TAG = logTag("Watch", "Monitor")
    }
}
