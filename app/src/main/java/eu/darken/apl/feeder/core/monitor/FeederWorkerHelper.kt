package eu.darken.apl.feeder.core.monitor

import android.content.Context
import androidx.work.WorkManager
import androidx.work.await
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.apl.common.coroutine.AppScope
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.Logging.Priority.ERROR
import eu.darken.apl.common.debug.logging.Logging.Priority.INFO
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.feeder.core.config.FeederSettings
import eu.darken.apl.feeder.core.server.FeederServerMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class FeederWorkerHelper @Inject constructor(
    @param:AppScope private val appScope: CoroutineScope,
    @param:ApplicationContext private val context: Context,
    private val workManager: WorkManager,
    private val dispatcherProvider: DispatcherProvider,
    private val serverMode: FeederServerMode,
    private val settings: FeederSettings,
    private val notifications: FeederMonitorNotifications,
) {

    private var isInit = false
    fun setup() {
        log(TAG) { "setup()" }
        require(!isInit)
        isInit = true

        // On every start, not once: restoring an old device backup brings the stats database back
        appScope.launch { removeLocalMonitoring() }

        // Not on an inactive start: that is also the state until the access is loaded.
        appScope.launch {
            var wasActive = false
            serverMode.state
                .map { it.isActive }
                .distinctUntilChanged()
                .collect { active ->
                    if (!active && wasActive) notifications.cancelAllServer()
                    wasActive = active
                }
        }
    }

    /** What app versions that checked the feeders themselves left behind. Each step runs even if another fails. */
    private suspend fun removeLocalMonitoring() {
        cleanupStep("cancel worker") {
            workManager.cancelUniqueWork(LEGACY_WORK_NAME).await()
        }
        cleanupStep("delete stats database") {
            val deleted = withContext(dispatcherProvider.IO) { context.deleteDatabase(LEGACY_STATS_DATABASE) }
            log(TAG, INFO) { "Legacy stats database deleted: $deleted" }
        }
        cleanupStep("remove settings") {
            settings.removeLegacyMonitorKeys()
        }
        cleanupStep("clear notifications") {
            notifications.clearOfflineNotifications()
        }
    }

    private suspend fun cleanupStep(name: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, ERROR) { "Cleanup step '$name' failed: ${e.asLog()}" }
        }
    }

    companion object {
        val TAG = logTag("Feeder", "Worker", "Helper")
        internal const val LEGACY_WORK_NAME = "feeder.monitor.worker"
        internal const val LEGACY_STATS_DATABASE = "feeder-stats"
    }
}
