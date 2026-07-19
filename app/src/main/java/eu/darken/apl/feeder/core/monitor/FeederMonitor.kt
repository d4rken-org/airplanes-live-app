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
import eu.darken.apl.feeder.core.stats.FeederHealthThresholds
import eu.darken.apl.feeder.core.stats.FeederMetricFamily
import eu.darken.apl.feeder.core.stats.FeederStatsRepo
import eu.darken.apl.feeder.core.stats.HealthObservation
import eu.darken.apl.feeder.core.stats.family
import kotlinx.coroutines.flow.first
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
) {

    // The periodic worker and manual triggers can overlap — one cycle at a time.
    private val mutex = Mutex()

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
        runPhase(feeders, health)
    }

    private suspend fun collectHealth(feeders: List<Feeder>): Map<String, HealthObservation> = buildMap {
        for (feeder in feeders) {
            try {
                val device = feederStatsRepo.getDetail(feeder.id).live.family<FeederMetricFamily.Device>()
                put(
                    feeder.id,
                    if (device == null) {
                        HealthObservation.DiagnosticsUnavailable
                    } else {
                        HealthObservation.Evaluated(FeederHealthThresholds.warnings(device))
                    },
                )
            } catch (e: Exception) {
                put(feeder.id, HealthObservation.FetchFailed)
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
        val muted = feederSettings.mutedFeeders.value()
        val result = FeederTransitionEngine.evaluate(state, feeders, muted, health)

        // A mute that raced this cycle wins: re-read right before showing anything.
        val mutedNow = feederSettings.mutedFeeders.value()
        result.commands.forEach { command ->
            val showFor = when (command) {
                is FeederTransitionEngine.Command.ShowOffline -> command.feeder.id
                is FeederTransitionEngine.Command.ShowRecovered -> command.feeder.id
                is FeederTransitionEngine.Command.ShowHealth -> command.feeder.id
                else -> null
            }
            if (showFor != null && showFor in mutedNow) return@forEach
            notifications.execute(command)
        }
        // Commands BEFORE state (at-least-once): dying in between re-posts on the next cycle,
        // which the tag-based notify turns into a silent update instead of a duplicate.
        feederSettings.monitorState.value(result.nextState)
    }

    private suspend fun isSessionUsable(): Boolean {
        if (feederRepo.isLoggedIn.first()) return true
        log(TAG) { "Not logged in, clearing notifications and monitor state." }
        notifications.clearAll()
        feederSettings.monitorState.value(FeederMonitorState())
        feederSettings.mutedFeeders.value(emptySet())
        return false
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
