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
import eu.darken.apl.server.api.Allowance
import eu.darken.apl.server.api.ServerCodes
import eu.darken.apl.server.api.WatchDefinition
import eu.darken.apl.server.identity.newOperationId
import eu.darken.apl.watch.core.WatchId
import eu.darken.apl.watch.core.WatchRepo
import eu.darken.apl.watch.core.WatchSettings
import eu.darken.apl.watch.core.db.WatchDatabase
import eu.darken.apl.watch.core.history.WatchHistoryRepo
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.FlightWatch
import eu.darken.apl.watch.core.types.LocationWatch
import eu.darken.apl.watch.core.types.SquawkWatch
import eu.darken.apl.watch.core.types.Watch
import eu.darken.apl.watch.core.types.WatchCheckOutcome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val notifications: WatchAlertNotifications,
) {
    private val mutex = Mutex()

    enum class Trigger {
        PERIODIC,
        MANUAL,
        APP_START,
        SCREEN_OPEN,
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

    suspend fun check(trigger: Trigger): CheckSummary = mutex.withLock {
        log(TAG) { "check($trigger)" }
        val watches = watchRepo.watches.first()
        val byId = watches.associateBy { it.id }
        var summary = CheckSummary()

        try {
            // Results the app paid for but never applied come first, they are free to replay
            val covered = mutableSetOf<WatchId>()
            operationStore.pending(OperationStore.Kind.WATCH, serverClock.now()).forEach { pending ->
                val result = try {
                    aircraftRepo.replayWatchOperation(pending)
                } catch (e: OperationFailedException) {
                    log(TAG, WARN) { "Pending operation ${pending.operationId} is gone: ${e.code}" }
                    operationStore.complete(pending.operationId)
                    return@forEach
                }
                summary = summary.apply(result, pending.ownerIds, byId)
                operationStore.complete(pending.operationId)
                covered.addAll(pending.ownerIds)
            }

            val outstanding = watches.filter { it.id !in covered }
            outstanding.chunked(AircraftRepo.MAX_BATCH_ITEMS).forEach { chunk ->
                summary = summary.run(chunk, byId)
            }
        } catch (e: Exception) {
            log(TAG, WARN) { "Watch check failed: ${e.asLog()}" }
            val now = serverClock.now()
            watches.forEach { watchDb.updateLastCheck(it.id, now, WatchCheckOutcome.FAILED, e.javaClass.simpleName) }
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
        var operationId = newOperationId()

        val results = try {
            aircraftRepo.checkWatches(
                listOf(AircraftRepo.Chunk(definitions, ownerIds, operationId))
            )
        } catch (e: OperationFailedException) {
            // The server cannot answer this id anymore, a fresh one costs the same evaluation
            log(TAG, WARN) { "Operation ${e.code}, resending under a new id" }
            operationId = newOperationId()
            aircraftRepo.checkWatches(listOf(AircraftRepo.Chunk(definitions, ownerIds, operationId)))
        }

        var summary = this
        results.forEach { summary = summary.apply(it, ownerIds, byId) }
        operationStore.complete(operationId)
        return summary
    }

    private suspend fun CheckSummary.apply(
        result: BatchResult<WatchOutcome>,
        ownerIds: List<String>,
        byId: Map<WatchId, Watch>,
    ): CheckSummary {
        val now = serverClock.now()
        var evaluated = this.evaluated
        var inconclusive = this.inconclusive
        var restricted = this.restricted
        var exhausted = this.exhausted

        result.outcomes.forEachIndexed { index, outcome ->
            val watchId = ownerIds.getOrNull(index) ?: return@forEachIndexed
            // A watch may have been deleted while the operation was outstanding
            val watch = byId[watchId] ?: return@forEachIndexed

            when (outcome) {
                is WatchOutcome.Matched -> {
                    evaluated++
                    val previous = historyRepo.getLastCheck(watchId)
                    val hexes = outcome.aircraft.map { it.hex }.toSet()
                    val inserted = historyRepo.addCheck(
                        watchId = watchId,
                        aircraftCount = outcome.totalMatching ?: outcome.aircraft.size,
                        seenHexes = hexes,
                        operationId = result.operationId,
                    )
                    watchDb.updateLastCheck(watchId, now, WatchCheckOutcome.MATCHED)
                    if (inserted) notifyIfNewHit(watch, previous?.aircraftCount, outcome.aircraft)
                }

                is WatchOutcome.Absent -> {
                    evaluated++
                    historyRepo.addCheck(
                        watchId = watchId,
                        aircraftCount = 0,
                        operationId = result.operationId,
                    )
                    watchDb.updateLastCheck(watchId, now, WatchCheckOutcome.ABSENT)
                }

                is WatchOutcome.Inconclusive -> {
                    // Missing evidence is not a disappearance, it must never write a zero
                    inconclusive++
                    watchDb.updateLastCheck(watchId, now, WatchCheckOutcome.INCONCLUSIVE, outcome.reason)
                }

                is WatchOutcome.Rejected -> {
                    val state = when (outcome.code) {
                        ServerCodes.TIER_RESTRICTED -> {
                            restricted++
                            WatchCheckOutcome.RESTRICTED
                        }

                        ServerCodes.DAILY_ALLOWANCE_EXHAUSTED -> {
                            exhausted++
                            WatchCheckOutcome.EXHAUSTED
                        }

                        else -> WatchCheckOutcome.INVALID
                    }
                    watchDb.updateLastCheck(watchId, now, state, outcome.code)
                }
            }
        }

        return copy(
            evaluated = evaluated,
            inconclusive = inconclusive,
            restricted = restricted,
            exhausted = exhausted,
            remainingAllowance = result.usage.allowance,
            resetsAt = Instant.ofEpochMilli(result.usage.resetsAt),
        )
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

    private fun Watch.toDefinition(): WatchDefinition = when (this) {
        is AircraftWatch -> WatchDefinition(type = "hex", value = hex.uppercase())
        is FlightWatch -> WatchDefinition(type = "callsign", value = callsign.uppercase())
        is SquawkWatch -> WatchDefinition(type = "squawk", value = code)
        is LocationWatch -> WatchDefinition(
            type = "location",
            latitude = latitude,
            longitude = longitude,
            radiusKm = radiusInMeters / 1000.0,
        )
    }

    private suspend fun cleanup() {
        val lastCleanup = settings.lastCleanup.value()
        if (Duration.between(lastCleanup, Instant.now()) <= Duration.ofDays(1)) return
        try {
            historyRepo.cleanupOldChecks()
            aircraftDatabase.evict(CACHE_RETENTION)
            settings.lastCleanup.update { Instant.now() }
        } catch (e: Exception) {
            log(TAG, WARN) { "Cleanup failed: $e" }
        }
    }

    companion object {
        private val CACHE_RETENTION: Duration = Duration.ofDays(7)
        private val TAG = logTag("Watch", "Monitor")
    }
}
