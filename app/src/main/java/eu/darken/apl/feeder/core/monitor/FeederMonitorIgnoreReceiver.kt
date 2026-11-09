package eu.darken.apl.feeder.core.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.apl.common.coroutine.AppScope
import eu.darken.apl.common.debug.logging.Logging.Priority.INFO
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.feeder.core.FeederRepo
import eu.darken.apl.feeder.core.ReceiverId
import eu.darken.apl.feeder.core.server.FeederServerAlerts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class FeederMonitorIgnoreReceiver : BroadcastReceiver() {

    @Inject @AppScope lateinit var appScope: CoroutineScope
    @Inject lateinit var feederRepo: FeederRepo
    @Inject lateinit var notifications: FeederMonitorNotifications
    @Inject lateinit var feederMonitor: FeederMonitor
    @Inject lateinit var serverAlerts: FeederServerAlerts

    override fun onReceive(context: Context, intent: Intent) {
        log(TAG, INFO) { "onReceive(context=$context, intent=$intent)" }

        val feederMonitorId = intent.getStringExtra(EXTRA_FEEDER_MONITOR_ID)
        if (feederMonitorId != null) {
            val pendingResult = goAsync()
            appScope.launch {
                try {
                    serverAlerts.snooze(feederMonitorId)
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        val receiverId = intent.getStringExtra(EXTRA_RECEIVER_ID)
        if (receiverId == null) {
            log(TAG) { "Missing receiver ID in intent extras" }
            return
        }

        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (notificationId == -1) {
            log(TAG) { "Missing notification ID in intent extras, falling back to old behavior" }
            return
        }

        val pendingResult = goAsync()
        appScope.launch {
            try {
                log(TAG) { "Setting offlineCheckSnoozedAt for $receiverId" }
                feederRepo.setOfflineCheckSnoozedAt(receiverId, Instant.now())

                log(TAG) { "Canceling notification with ID $notificationId" }
                notifications.cancelNotification(notificationId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private val TAG = logTag("Feeder", "Monitor", "IgnoreReceiver")
        const val EXTRA_RECEIVER_ID = "eu.darken.apl.feeder.core.monitor.EXTRA_RECEIVER_ID"
        const val EXTRA_NOTIFICATION_ID = "eu.darken.apl.feeder.core.monitor.EXTRA_NOTIFICATION_ID"
        const val EXTRA_FEEDER_MONITOR_ID = "eu.darken.apl.feeder.core.monitor.EXTRA_FEEDER_MONITOR_ID"

        fun createIntent(context: Context, receiverId: ReceiverId, notificationId: Int): Intent {
            return Intent(context, FeederMonitorIgnoreReceiver::class.java).apply {
                putExtra(EXTRA_RECEIVER_ID, receiverId)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
        }

        /** For a notification from the server's monitoring, [feederId] in its canonical form. */
        fun createServerIntent(context: Context, feederId: String): Intent =
            Intent(context, FeederMonitorIgnoreReceiver::class.java).putExtra(EXTRA_FEEDER_MONITOR_ID, feederId)
    }
}
