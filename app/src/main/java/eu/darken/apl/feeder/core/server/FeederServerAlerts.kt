package eu.darken.apl.feeder.core.server

import eu.darken.apl.common.coroutine.AppScope
import eu.darken.apl.common.datastore.value
import eu.darken.apl.common.debug.logging.Logging.Priority.INFO
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.feeder.core.config.FeederSettings
import eu.darken.apl.feeder.core.monitor.FeederMonitorNotifications
import eu.darken.apl.feeder.core.server.FeederAlertPlanner.Action
import eu.darken.apl.server.ServerClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Posts what [FeederAlertPlanner] decides for the stored server state. */
@Singleton
class FeederServerAlerts @Inject constructor(
    @param:AppScope private val appScope: CoroutineScope,
    private val store: FeederServerStore,
    private val feederSettings: FeederSettings,
    private val notifications: FeederMonitorNotifications,
    private val serverClock: ServerClock,
    private val serverMode: FeederServerMode,
) {

    private val lock = Mutex()

    private var isInit = false

    /**
     * Delivers on start and whenever the problems that may be notified change: a feeder's problem
     * categories or the problem alerts switch. Snoozes and other feeder edits don't count, a delivery
     * clears snoozes itself. Works on the stored server state only, a failed delivery waits for the next
     * change or monitoring run.
     */
    fun setup() {
        log(TAG) { "setup()" }
        require(!isInit)
        isInit = true

        val categories = feederSettings.feederGroup.flow.map { group ->
            group.configs.effectiveByFeederMonitorId().mapValues { it.value.problemCategories }
        }

        appScope.launch {
            // Not collectLatest: a newer change must not cancel a delivery between posting and recording
            combine(categories, feederSettings.notifyProblems.flow) { c, enabled -> c to enabled }
                .distinctUntilChanged()
                .collect { reconcile() }
        }
    }

    private suspend fun reconcile() {
        try {
            if (!serverMode.current().isActive) return
            deliver()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, WARN) { "reconcile() failed: ${e.asLog()}" }
        }
    }

    /**
     * Posts before it records what was notified: a run cut short in between posts the same notification
     * again under the same tag, which replaces it.
     */
    suspend fun deliver(): Unit = lock.withLock {
        val state = store.current()
        if (state.installationId == null) return@withLock
        val plan = FeederAlertPlanner.plan(
            state = state,
            configs = feederSettings.feederGroup.value().configs,
            problemAlertsEnabled = feederSettings.notifyProblems.value(),
            nowMillis = serverClock.now().toEpochMilli(),
        )
        if (plan.actions.isNotEmpty()) log(TAG, INFO) { "deliver(): ${plan.actions}" }

        plan.actions.forEach { action ->
            when (action) {
                is Action.PostOffline -> notifications.notifyServerOffline(action.feederId, action.label, action.offlineSince)
                is Action.PostRecovered -> notifications.notifyServerRecovered(action.feederId, action.label, action.offlineForMillis)
                is Action.PostProblems -> notifications.notifyServerProblems(
                    action.feederId,
                    action.label,
                    action.problems,
                    action.update,
                )

                is Action.CancelOffline -> notifications.cancelServerOffline(action.feederId)
                is Action.CancelProblems -> notifications.cancelServerProblems(action.feederId)
            }
        }

        if (plan.snoozeClears.isNotEmpty()) {
            log(TAG) { "deliver(): clearing the snooze of ${plan.snoozeClears}" }
            setSnoozedAt(plan.snoozeClears, null)
        }

        if (plan.notified != state.notified || plan.notifiedInitialized != state.notifiedInitialized) {
            store.update { s ->
                // Dropped or taken over by another installation meanwhile, the notified state was for this one
                if (s.installationId != state.installationId) {
                    s
                } else {
                    s.copy(notified = plan.notified, notifiedInitialized = plan.notifiedInitialized)
                }
            }
        }
    }

    /** Snoozes every local feeder with this canonical [feederId] until it sends data again. */
    suspend fun snooze(feederId: String) {
        log(TAG, INFO) { "snooze($feederId)" }
        setSnoozedAt(setOf(feederId), Instant.now())
        notifications.cancelServer(feederId)
    }

    private suspend fun setSnoozedAt(feederIds: Set<String>, snoozedAt: Instant?) {
        feederSettings.feederGroup.update { group ->
            group.copy(
                configs = group.configs.map { config ->
                    if (config.receiverId.toFeederMonitorId() in feederIds) config.copy(offlineCheckSnoozedAt = snoozedAt) else config
                }.toSet()
            )
        }
    }

    companion object {
        private val TAG = logTag("Feeder", "Server", "Alerts")
    }
}
