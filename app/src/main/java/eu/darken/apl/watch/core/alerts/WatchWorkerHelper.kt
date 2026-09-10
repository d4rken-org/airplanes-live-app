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
import eu.darken.apl.upgrade.UpgradeRepo
import eu.darken.apl.upgrade.isProNow
import eu.darken.apl.watch.core.WatchSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    private val upgradeRepo: UpgradeRepo,
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
        appScope.launch {
            // Linking or losing a feeder changes the floor, without waiting for the next app start
            upgradeRepo.upgradeInfo
                .map { it.isSettled && it.isPro }
                .distinctUntilChanged()
                .collect { updateWorker() }
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
        val interval = effectiveInterval(configured, upgradeRepo.isProNow())
        log(TAG) { "updateWorker() to $interval (configured $configured)" }

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
        fun minInterval(isPro: Boolean): Duration =
            if (isPro) WatchSettings.PRO_MIN_CHECK_INTERVAL else WatchSettings.FREE_MIN_CHECK_INTERVAL

        /**
         * The floor the tier allows, applied to the stored preference without rewriting it, so a
         * user who set 15 minutes gets them back the moment their feeder is linked again.
         */
        fun effectiveInterval(configured: Duration, isPro: Boolean): Duration =
            maxOf(configured, minInterval(isPro))

        val TAG = logTag("Watch", "Worker", "Helper")
    }
}