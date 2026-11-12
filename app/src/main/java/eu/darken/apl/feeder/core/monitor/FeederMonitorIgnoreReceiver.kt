package eu.darken.apl.feeder.core.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.apl.common.coroutine.AppScope
import eu.darken.apl.common.debug.logging.Logging.Priority.INFO
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.feeder.core.server.FeederServerAlerts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FeederMonitorIgnoreReceiver : BroadcastReceiver() {

    @Inject @AppScope lateinit var appScope: CoroutineScope
    @Inject lateinit var serverAlerts: FeederServerAlerts

    override fun onReceive(context: Context, intent: Intent) {
        log(TAG, INFO) { "onReceive(context=$context, intent=$intent)" }

        val feederMonitorId = intent.getStringExtra(EXTRA_FEEDER_MONITOR_ID)
        if (feederMonitorId == null) {
            // From a notification posted by an app version that checked the feeders itself
            log(TAG, WARN) { "Ignoring intent without a feeder monitor ID: ${intent.extras?.keySet()}" }
            return
        }

        val pendingResult = goAsync()
        appScope.launch {
            try {
                serverAlerts.snooze(feederMonitorId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private val TAG = logTag("Feeder", "Monitor", "IgnoreReceiver")
        const val EXTRA_FEEDER_MONITOR_ID = "eu.darken.apl.feeder.core.monitor.EXTRA_FEEDER_MONITOR_ID"

        /** For a notification from the server's monitoring, [feederId] in its canonical form. */
        fun createServerIntent(context: Context, feederId: String): Intent =
            Intent(context, FeederMonitorIgnoreReceiver::class.java).putExtra(EXTRA_FEEDER_MONITOR_ID, feederId)
    }
}
