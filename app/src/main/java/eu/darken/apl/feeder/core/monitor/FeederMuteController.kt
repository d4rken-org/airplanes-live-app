package eu.darken.apl.feeder.core.monitor

import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.feeder.core.config.FeederSettings
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single write path for per-feeder notification muting, shared by the notification action,
 * the action sheet and the detail screen — so muting always also takes down whatever that feeder
 * is currently showing.
 */
@Singleton
class FeederMuteController @Inject constructor(
    private val feederSettings: FeederSettings,
    private val notifications: FeederMonitorNotifications,
) {

    val mutedFeeders: Flow<Set<String>> = feederSettings.mutedFeeders.flow

    suspend fun setMuted(feederId: String, muted: Boolean) {
        log(TAG) { "setMuted($feederId, $muted)" }
        feederSettings.mutedFeeders.update { if (muted) it + feederId else it - feederId }
        if (muted) notifications.cancelAllFor(feederId)
    }

    companion object {
        private val TAG = logTag("Feeder", "Monitor", "Mute")
    }
}
