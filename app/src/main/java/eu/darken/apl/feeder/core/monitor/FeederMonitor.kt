package eu.darken.apl.feeder.core.monitor

import eu.darken.apl.account.core.NotLoggedInException
import eu.darken.apl.account.core.SessionExpiredException
import eu.darken.apl.common.datastore.value
import eu.darken.apl.common.debug.logging.Logging.Priority.ERROR
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.feeder.core.Feeder
import eu.darken.apl.feeder.core.FeederRepo
import eu.darken.apl.feeder.core.config.FeederSettings
import eu.darken.apl.feeder.core.stats.FeederMetricFamily
import eu.darken.apl.feeder.core.stats.FeederStatsRepo
import eu.darken.apl.feeder.core.stats.HealthObservation
import eu.darken.apl.feeder.core.stats.toHealthObservation
import eu.darken.apl.feeder.core.stats.family
import eu.darken.apl.common.coroutine.AppScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeederMonitor @Inject constructor(
    private val feederRepo: FeederRepo,
    private val feederStatsRepo: FeederStatsRepo,
    private val feederSettings: FeederSettings,
    private val notifications: FeederMonitorNotifications,
    @AppScope appScope: CoroutineScope,
) {

    // The periodic worker and manual triggers can overlap — one cycle at a time.
    private val mutex = Mutex()

    init {
        // Logout cleans up immediately — waiting for the next (network-constrained) worker run
        // would leave stale outage notifications on the lock screen indefinitely.
        feederRepo.isLoggedIn
            .onEach { loggedIn -> if (!loggedIn) mutex.withLock { cleanupLoggedOut() } }
            .launchIn(appScope)
    }

    suspend fun check() = mutex.withLock {
        log(TAG) { "check()" }

        if (!isSessionUsable()) return@withLock

        try {
            feederRepo.refresh()
        } catch (e: Exception) {
            // Don't notify from stale cache when we couldn't get fresh data.
            log(TAG, ERROR) { "Refresh failed, skipping check: ${e.asLog()}" }
            return@withLock
        }
        // refresh() reacts to a revoked session by clearing it rather than rethrowing — a cycle
        // that lost its session mid-way must stop instead of evaluating an empty feeder list.
        if (!isSessionUsable()) return@withLock

        val feeders = feederRepo.feeders.first()

        // Phase 1: connectivity transitions. Runs and persists to completion BEFORE the
        // network-heavy health phase, so a slow/failing detail endpoint can't starve outage
        // alerts (absent health observations carry previous health state forward).
        runPhase(feeders, health = emptyMap())

        // Phase 2: device health (opt-in). When disabled, DiagnosticsUnavailable retires any
        // previously shown warnings — turning the setting off self-heals within one cycle.
        val health = if (!feederSettings.deviceHealthWarningsEnabled.value()) {
            feeders.associate { it.id to (HealthObservation.DiagnosticsUnavailable as HealthObservation) }
        } else {
            collectHealth(feeders)
        }
        // The health pass can lose the session (401 during a detail fetch) — don't evaluate
        // observations gathered by a cycle whose session died under it.
        if (!isSessionUsable()) return@withLock
        runPhase(feeders, health)
    }

    private suspend fun collectHealth(feeders: List<Feeder>): Map<String, HealthObservation> = buildMap {
        for (feeder in feeders) {
            try {
                val device = feederStatsRepo.getDetail(feeder.id).live.family<FeederMetricFamily.Device>()
                put(feeder.id, device.toHealthObservation())
            } catch (e: Exception) {
                put(feeder.id, HealthObservation.Inconclusive)
                if (e.isSystemic()) {
                    // Auth/connectivity/throttling failure — the rest of the pass would fail the
                    // same way; feeders without an observation carry their previous state.
                    log(TAG, WARN) { "Aborting health pass after systemic failure: ${e.asLog()}" }
                    break
                }
                log(TAG, WARN) { "Health fetch failed for ${feeder.id}: ${e.asLog()}" }
            }
        }
    }

    private suspend fun runPhase(feeders: List<Feeder>, health: Map<String, HealthObservation>) {
        val state = feederSettings.monitorState.value()
        var muted = feederSettings.mutedFeeders.value()
        var result = FeederTransitionEngine.evaluate(state, feeders, muted, health)

        // A mute that raced this cycle wins. Re-evaluating (pure, cheap) instead of filtering
        // commands keeps the persisted state consistent with what was actually shown.
        val mutedNow = feederSettings.mutedFeeders.value()
        if (mutedNow != muted) {
            muted = mutedNow
            result = FeederTransitionEngine.evaluate(state, feeders, muted, health)
        }
        result.commands.forEach { notifications.execute(it) }
        // Commands BEFORE state (at-least-once): dying in between re-posts on the next cycle,
        // which the tag-based notify turns into a silent update instead of a duplicate.
        feederSettings.monitorState.value(result.nextState)
        // A mute can still land between the re-read and execute(); sweep so a just-muted feeder
        // never keeps a freshly posted notification.
        feederSettings.mutedFeeders.value().forEach { notifications.cancelAllFor(it) }
    }

    private suspend fun isSessionUsable(): Boolean {
        if (feederRepo.isLoggedIn.first()) return true
        cleanupLoggedOut()
        return false
    }

    private suspend fun cleanupLoggedOut() {
        log(TAG) { "Not logged in, clearing notifications and monitor state." }
        notifications.clearAll()
        feederSettings.monitorState.value(FeederMonitorState())
        feederSettings.mutedFeeders.value(emptySet())
    }

    private fun Exception.isSystemic(): Boolean = when (this) {
        is SessionExpiredException, is NotLoggedInException -> true
        is HttpException -> code() == 429 || code() >= 500
        is IOException -> true
        else -> false
    }

    companion object {
        private val TAG = logTag("Feeder", "Monitor")
    }
}
