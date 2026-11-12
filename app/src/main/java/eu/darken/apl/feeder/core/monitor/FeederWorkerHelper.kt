package eu.darken.apl.feeder.core.monitor

import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import eu.darken.apl.common.coroutine.AppScope
import eu.darken.apl.common.debug.logging.Logging.Priority.ERROR
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.feeder.core.config.FeederSettings
import eu.darken.apl.feeder.core.server.FeederServerMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class FeederWorkerHelper @Inject constructor(
    @param:AppScope private val appScope: CoroutineScope,
    private val workManager: WorkManager,
    private val monitor: FeederMonitor,
    private val serverMode: FeederServerMode,
    private val notifications: FeederMonitorNotifications,
) {

    private var isInit = false
    fun setup() {
        log(TAG) { "setup()" }
        require(!isInit)
        isInit = true

        appScope.launch { updateWorker() }

        // Once the other side took over, nothing replaces or cancels what was posted before.
        // Not on an inactive start: that is also the state until the access is loaded.
        appScope.launch {
            var wasActive = false
            serverMode.state
                .map { it.isActive }
                .distinctUntilChanged()
                .collect { active ->
                    when {
                        active -> notifications.clearOfflineNotifications()
                        wasActive -> notifications.cancelAllServer()
                    }
                    wasActive = active
                }
        }

        triggerNow()
    }

    fun triggerNow() {
        log(TAG) { "runNow()" }
        appScope.launch {
            try {
                monitor.check()
            } catch (e: Exception) {
                log(TAG, ERROR) { "Failed to refresh: ${e.asLog()}" }
            }
        }
    }

    suspend fun updateWorker() {
        val interval = FeederSettings.CHECK_INTERVAL
        log(TAG) { "updateWorker() to $interval" }

        val workRequest = PeriodicWorkRequestBuilder<FeederMonitorWorker>(
            interval,
            Duration.ofMinutes(10)
        ).apply {
            setInputData(Data.Builder().build())
        }.build()

        val operation = workManager.enqueueUniquePeriodicWork(
            "feeder.monitor.worker",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest,
        )

        operation.await()
    }

    companion object {
        val TAG = logTag("Feeder", "Worker", "Helper")
    }
}