package eu.darken.apl.feeder.core.monitor

import eu.darken.apl.common.debug.logging.Logging.Priority.ERROR
import eu.darken.apl.common.debug.logging.Logging.Priority.INFO
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.feeder.core.FeederRepo
import eu.darken.apl.feeder.core.FeederStatus
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeederMonitor @Inject constructor(
    private val feederRepo: FeederRepo,
    private val notifications: FeederMonitorNotifications,
) {
    suspend fun check() {
        log(TAG) { "check()" }

        if (!feederRepo.isLoggedIn.first()) {
            log(TAG) { "Not logged in, clearing notifications." }
            notifications.clearOfflineNotifications()
            return
        }

        try {
            feederRepo.refresh()
        } catch (e: Exception) {
            // Don't notify from stale cache when we couldn't get fresh data.
            log(TAG, ERROR) { "Refresh failed, skipping offline check: ${e.asLog()}" }
            return
        }

        val offlineDevices = feederRepo.feeders.first()
            .filter { it.status == FeederStatus.INACTIVE }
            .onEach { log(TAG, INFO) { "Feeder is inactive: $it" } }

        notifications.notifyOfOfflineDevices(offlineDevices)
    }

    companion object {
        private val TAG = logTag("Feeder", "Monitor")
    }
}
