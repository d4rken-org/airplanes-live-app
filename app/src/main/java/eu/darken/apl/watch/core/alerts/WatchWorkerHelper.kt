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
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.main.core.BackgroundSync
import eu.darken.apl.main.core.BackgroundSyncLevel
import eu.darken.apl.watch.core.WatchSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
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
    private val backgroundSync: BackgroundSync,
) {

    private var isInit = false
    fun setup() {
        log(TAG) { "setup()" }
        require(!isInit)
        isInit = true

        // An older version enqueued history work under this name, which no worker runs anymore
        workManager.cancelUniqueWork(RETIRED_HISTORY_WORK_NAME)

        appScope.launch { triggerIfStale() }
        appScope.launch {
            // Gaining or losing Pro changes the level in effect, without waiting for the next app start
            backgroundSync.level.collectLatest { level -> schedule(level) }
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

    /** Gives up after the last retry, the next level change schedules again. */
    private suspend fun schedule(level: BackgroundSyncLevel) {
        var attempt = 0
        while (true) {
            try {
                updateWorker(level)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val wait = RETRY_DELAYS.getOrNull(attempt++)
                if (wait == null) {
                    log(TAG, ERROR) { "Scheduling $level failed, giving up: ${e.asLog()}" }
                    return
                }
                log(TAG, WARN) { "Scheduling $level failed, retrying in $wait: ${e.asLog()}" }
                delay(wait.toMillis())
            }
        }
    }

    private suspend fun updateWorker(level: BackgroundSyncLevel) {
        val interval = level.interval
        log(TAG) { "updateWorker() to $interval ($level)" }

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
        internal const val RETIRED_HISTORY_WORK_NAME = "watch.history.worker"

        internal val RETRY_DELAYS: List<Duration> = listOf(
            Duration.ofSeconds(10),
            Duration.ofMinutes(1),
            Duration.ofMinutes(5),
        )

        val TAG = logTag("Watch", "Worker", "Helper")
    }
}
