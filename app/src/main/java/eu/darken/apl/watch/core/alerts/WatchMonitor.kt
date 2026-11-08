package eu.darken.apl.watch.core.alerts

import eu.darken.apl.common.datastore.value
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.main.core.db.AircraftDatabase
import eu.darken.apl.server.ServerClock
import eu.darken.apl.watch.core.WatchRepo
import eu.darken.apl.watch.core.WatchSettings
import eu.darken.apl.watch.core.db.WatchDatabase
import eu.darken.apl.watch.core.history.WatchHistoryRepo
import eu.darken.apl.watch.core.server.WatchListSync
import eu.darken.apl.watch.core.server.WatchListSyncScheduler
import eu.darken.apl.watch.core.server.WatchServerMode
import eu.darken.apl.watch.core.types.AircraftWatch
import kotlinx.coroutines.CancellationException
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
    private val serverClock: ServerClock,
    private val serverMode: WatchServerMode,
    private val watchListSync: WatchListSync,
    private val syncScheduler: WatchListSyncScheduler,
) {

    enum class Trigger {
        PERIODIC,
        MANUAL,
        APP_START,
        SCREEN_OPEN,
        FOREGROUND,
        ;
    }

    /**
     * Syncs the server's watch list, which is what monitors the watches. A failed sync is thrown, the
     * caller decides whether it is worth another attempt.
     */
    suspend fun check(trigger: Trigger) {
        log(TAG) { "check($trigger)" }
        cleanup()
        if (!serverMode.current().isActive) {
            log(TAG) { "check($trigger): no server watch list, nothing to sync" }
            return
        }
        val result = watchListSync.run("monitor-${trigger.name.lowercase()}", periodic = trigger == Trigger.PERIODIC)
        syncScheduler.afterRun(result)
        if (result is WatchListSync.Result.Done) settings.lastCheck.value(serverClock.now())
    }

    private suspend fun cleanup() {
        val lastCleanup = settings.lastCleanup.value()
        if (Duration.between(lastCleanup, Instant.now()) <= Duration.ofDays(1)) return
        try {
            historyRepo.cleanupOldChecks()
            backfillRegistrations()
            aircraftDatabase.evict(CACHE_RETENTION)
            settings.lastCleanup.update { Instant.now() }
        } catch (e: CancellationException) {
            throw e
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
