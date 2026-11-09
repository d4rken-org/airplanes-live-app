package eu.darken.apl.feeder.core.monitor

import eu.darken.apl.common.debug.logging.Logging.Priority.ERROR
import eu.darken.apl.common.debug.logging.Logging.Priority.INFO
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.feeder.core.FeederRepo
import eu.darken.apl.feeder.core.config.FeederSettings
import eu.darken.apl.feeder.core.server.FeederServerMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeederMonitor @Inject constructor(
    private val settings: FeederSettings,
    private val feederRepo: FeederRepo,
    private val notifications: FeederMonitorNotifications,
    private val serverMode: FeederServerMode,
) {
    suspend fun check() {
        log(TAG) { "check()" }

        // The server monitors the feeders, see FeederMonitorRunner
        if (serverMode.current().isActive) {
            log(TAG) { "check(): server mode, skipping" }
            return
        }

        try {
            feederRepo.refresh()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to refresh ${e.asLog()}" }
        }

        val offlineDevices = feederRepo.feeders.first()
            .filter { feederRepo.isOffline(it) }
            .onEach { log(TAG, INFO) { "Feeder has been offline for a while... $it" } }

        notifications.notifyOfOfflineDevices(offlineDevices)
    }

    companion object {
        private val TAG = logTag("Feeder", "Monitor")
    }
}
