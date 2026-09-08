package eu.darken.apl.watch.core.alerts

import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import eu.darken.apl.common.coroutine.AppScope
import eu.darken.apl.common.datastore.value
import eu.darken.apl.common.debug.logging.Logging.Priority.ERROR
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.watch.core.WatchSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class WatchWorkerHelper @Inject constructor(
    @param:AppScope private val appScope: CoroutineScope,
    private val workManager: WorkManager,
    private val monitor: WatchMonitor,
    private val watchSettings: WatchSettings,
) {

    private var isInit = false
    fun setup() {
        log(TAG) { "setup()" }
        require(!isInit)
        isInit = true

        appScope.launch {
            updateWorker()
            triggerIfStale()
        }
    }

    /** A cold start must not spend an evaluation when the last check is still recent. */
    private suspend fun triggerIfStale() {
        val lastCheck = watchSettings.lastCheck.value()
        val age = Duration.between(lastCheck, Instant.now())
        if (age < WatchSettings.FOREGROUND_CHECK_MAX_AGE) {
            log(TAG) { "Last check was ${age.toMinutes()}min ago, not checking on start" }
            return
        }
        try {
            monitor.check(WatchMonitor.Trigger.APP_START)
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to refresh: ${e.asLog()}" }
        }
    }

    suspend fun updateWorker() {
        val configured = watchSettings.watchMonitorInterval.value()
        val interval = maxOf(configured, WatchSettings.MIN_CHECK_INTERVAL)
        log(TAG) { "updateWorker() to $interval" }

        val workRequest = PeriodicWorkRequestBuilder<WatchWorker>(
            interval,
            Duration.ofMinutes(10)
        ).apply {
            setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            setInputData(Data.Builder().build())
        }.build()

        val operation = workManager.enqueueUniquePeriodicWork(
            "alerts.monitor.worker",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest,
        )

        operation.await()
    }

    companion object {
        val TAG = logTag("Watch", "Worker", "Helper")
    }
}