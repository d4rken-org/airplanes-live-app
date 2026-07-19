package eu.darken.apl.feeder.core.monitor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.BigTextStyle
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.apl.R
import eu.darken.apl.common.BuildConfigWrap
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.notifications.PendingIntentFlagCompat
import eu.darken.apl.feeder.core.Feeder
import eu.darken.apl.feeder.core.stats.HealthMetric
import eu.darken.apl.feeder.core.stats.HealthSeverity
import eu.darken.apl.feeder.core.stats.labelRes
import eu.darken.apl.main.ui.MainActivity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Feeder notifications, one per (feeder, kind) via Android's tagged notification API:
 * `notify(tag = "feeder:<id>", id = <kind>)`. Tags make IDs collision-free by construction —
 * no per-feeder ID allocation needed — and let recovery/mute cancel exactly one feeder's
 * notification of one kind.
 */
@Singleton
class FeederMonitorNotifications @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val notificationManager: NotificationManager,
) {

    init {
        NotificationChannel(
            CHANNEL_OFFLINE_ID,
            context.getString(R.string.feeder_notification_channel_title),
            NotificationManager.IMPORTANCE_DEFAULT
        ).run { notificationManager.createNotificationChannel(this) }
        NotificationChannel(
            CHANNEL_RECOVERY_ID,
            context.getString(R.string.feeder_notification_channel_recovery_title),
            NotificationManager.IMPORTANCE_LOW
        ).run { notificationManager.createNotificationChannel(this) }
        NotificationChannel(
            CHANNEL_HEALTH_ID,
            context.getString(R.string.feeder_notification_channel_health_title),
            NotificationManager.IMPORTANCE_DEFAULT
        ).run { notificationManager.createNotificationChannel(this) }
    }

    fun execute(command: FeederTransitionEngine.Command) {
        log(TAG) { "execute($command)" }
        when (command) {
            is FeederTransitionEngine.Command.ShowOffline -> notifyOffline(command.feeder)
            is FeederTransitionEngine.Command.ShowRecovered -> notifyRecovered(command.feeder)
            is FeederTransitionEngine.Command.ShowHealth -> notifyHealth(command.feeder, command.warnings)
            is FeederTransitionEngine.Command.CancelOffline ->
                notificationManager.cancel(tag(command.feederId), ID_OFFLINE)

            is FeederTransitionEngine.Command.CancelRecovery ->
                notificationManager.cancel(tag(command.feederId), ID_RECOVERY)

            is FeederTransitionEngine.Command.CancelHealth ->
                notificationManager.cancel(tag(command.feederId), ID_HEALTH)

            is FeederTransitionEngine.Command.CancelAll -> cancelAllFor(command.feederId)
        }
    }

    private fun notifyOffline(feeder: Feeder) {
        val text = context.getString(R.string.feeder_monitor_offline_message, feeder.label)
        val notification = builder(CHANNEL_OFFLINE_ID, feeder.id)
            .setContentTitle(context.getString(R.string.feeder_monitor_offline_title))
            .setContentText(text)
            .setStyle(BigTextStyle().bigText(text))
            .addAction(muteAction(feeder.id))
            .build()
        notificationManager.notify(tag(feeder.id), ID_OFFLINE, notification)
    }

    private fun notifyRecovered(feeder: Feeder) {
        val text = context.getString(R.string.feeder_monitor_recovered_message, feeder.label)
        val notification = builder(CHANNEL_RECOVERY_ID, feeder.id)
            .setContentTitle(context.getString(R.string.feeder_monitor_recovered_title))
            .setContentText(text)
            .setTimeoutAfter(RECOVERY_TIMEOUT_MS)
            .build()
        notificationManager.notify(tag(feeder.id), ID_RECOVERY, notification)
    }

    private fun notifyHealth(feeder: Feeder, warnings: Map<HealthMetric, HealthSeverity>) {
        val issues = warnings.entries.joinToString { (metric, severity) ->
            val label = context.getString(metric.labelRes())
            if (severity == HealthSeverity.CRIT) {
                context.getString(R.string.feeder_monitor_health_issue_critical, label)
            } else {
                label
            }
        }
        val text = context.getString(R.string.feeder_monitor_health_message, feeder.label, issues)
        val notification = builder(CHANNEL_HEALTH_ID, feeder.id)
            .setContentTitle(context.getString(R.string.feeder_monitor_health_title))
            .setContentText(text)
            .setStyle(BigTextStyle().bigText(text))
            .addAction(muteAction(feeder.id))
            .build()
        notificationManager.notify(tag(feeder.id), ID_HEALTH, notification)
    }

    fun cancelAllFor(feederId: String) {
        val tag = tag(feederId)
        notificationManager.cancel(tag, ID_OFFLINE)
        notificationManager.cancel(tag, ID_RECOVERY)
        notificationManager.cancel(tag, ID_HEALTH)
    }

    /**
     * Pre-tag builds posted untagged notifications with IDs 1000..1049; the tag-based cancel
     * paths can never reach those. One-time cleanup on app start after the upgrade.
     */
    fun cancelLegacyUntagged() {
        (LEGACY_BASE_ID until LEGACY_BASE_ID + LEGACY_RANGE).forEach { notificationManager.cancel(it) }
    }

    fun clearAll() {
        log(TAG) { "clearAll()" }
        notificationManager.activeNotifications
            .filter { it.tag?.startsWith(TAG_PREFIX) == true }
            .forEach { notificationManager.cancel(it.tag, it.id) }
    }

    private fun builder(channelId: String, feederId: String) =
        NotificationCompat.Builder(context, channelId).apply {
            priority = NotificationCompat.PRIORITY_DEFAULT
            setSmallIcon(R.drawable.ic_chili_alert_24)
            setOnlyAlertOnce(true)
            setAutoCancel(true)
            setContentIntent(showFeederIntent(feederId))
        }

    /**
     * Extras don't participate in PendingIntent equality — the unique data URI (plus a per-feeder
     * request code) keeps two feeders' intents from collapsing into one and cross-contaminating.
     */
    private fun showFeederIntent(feederId: String): PendingIntent = PendingIntent.getActivity(
        context,
        feederId.hashCode(),
        Intent(context, MainActivity::class.java).apply {
            action = SHOW_FEEDER_ACTION
            data = "apl://feeder/$feederId".toUri()
            putExtra(ARG_FEEDER_ID, feederId)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntentFlagCompat.FLAG_IMMUTABLE,
    )

    private fun muteAction(feederId: String): NotificationCompat.Action {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            feederId.hashCode(),
            Intent(context, FeederMuteActionReceiver::class.java).apply {
                action = ACTION_MUTE
                data = "apl://feeder/$feederId/mute".toUri()
                putExtra(ARG_FEEDER_ID, feederId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntentFlagCompat.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(
            R.drawable.ic_chili_alert_24,
            context.getString(R.string.feeder_monitor_mute_action),
            pendingIntent,
        ).build()
    }

    internal fun tag(feederId: String) = "$TAG_PREFIX$feederId"

    companion object {
        val TAG = logTag("Feeder", "Monitor", "Notifications")

        private val CHANNEL_OFFLINE_ID = "${BuildConfigWrap.APPLICATION_ID}.notification.channel.feeder.monitor"
        private val CHANNEL_RECOVERY_ID = "${BuildConfigWrap.APPLICATION_ID}.notification.channel.feeder.recovery"
        private val CHANNEL_HEALTH_ID = "${BuildConfigWrap.APPLICATION_ID}.notification.channel.feeder.health"

        private const val LEGACY_BASE_ID = 1000
        private const val LEGACY_RANGE = 50

        internal const val TAG_PREFIX = "feeder:"
        internal const val ID_OFFLINE = 1001
        internal const val ID_RECOVERY = 1002
        internal const val ID_HEALTH = 1003

        private const val RECOVERY_TIMEOUT_MS = 6 * 60 * 60 * 1000L

        val SHOW_FEEDER_ACTION = "${BuildConfigWrap.APPLICATION_ID}.feeder.show"
        val ARG_FEEDER_ID = "${BuildConfigWrap.APPLICATION_ID}.feeder.show.feederId"
        val ACTION_MUTE = "${BuildConfigWrap.APPLICATION_ID}.feeder.mute"
    }
}
