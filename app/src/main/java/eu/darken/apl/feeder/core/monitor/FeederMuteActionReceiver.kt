package eu.darken.apl.feeder.core.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.apl.common.coroutine.AppScope
import eu.darken.apl.common.debug.logging.Logging.Priority.ERROR
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Handles the "Mute" action on feeder notifications. Not exported — only our own PendingIntents. */
@AndroidEntryPoint
class FeederMuteActionReceiver : BroadcastReceiver() {

    @Inject lateinit var muteController: FeederMuteController

    @Inject @AppScope lateinit var appScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        log(TAG) { "onReceive($intent)" }
        if (intent.action != FeederMonitorNotifications.ACTION_MUTE) return
        val feederId = intent.getStringExtra(FeederMonitorNotifications.ARG_FEEDER_ID) ?: return

        // onReceive must not block; goAsync keeps the process alive until finish().
        val pending = goAsync()
        appScope.launch {
            try {
                muteController.setMuted(feederId, muted = true)
            } catch (e: Exception) {
                log(TAG, ERROR) { "Failed to mute $feederId: ${e.asLog()}" }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val TAG = logTag("Feeder", "Monitor", "MuteReceiver")
    }
}
