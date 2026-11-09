package eu.darken.apl.feeder.core.monitor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.BigTextStyle
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.apl.R
import eu.darken.apl.common.BuildConfigWrap
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.easterEggProgressMsg
import eu.darken.apl.common.notifications.PendingIntentFlagCompat
import eu.darken.apl.common.time.durationText
import eu.darken.apl.feeder.core.Feeder
import eu.darken.apl.main.ui.MainActivity
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue

@Singleton
class FeederMonitorNotifications @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val notificationManager: NotificationManager,
) {

    private val builder: NotificationCompat.Builder
    private val openPi: PendingIntent

    init {
        NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.feeder_notification_channel_title),
            NotificationManager.IMPORTANCE_DEFAULT
        ).run { notificationManager.createNotificationChannel(this) }

        val openIntent = Intent(context, MainActivity::class.java)
        openPi = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntentFlagCompat.FLAG_IMMUTABLE
        )

        builder = NotificationCompat.Builder(context, CHANNEL_ID).apply {
            setChannelId(CHANNEL_ID)
            setContentIntent(openPi)
            priority = NotificationCompat.PRIORITY_DEFAULT
            setSmallIcon(R.drawable.ic_chili_alert_24)
            setContentTitle(context.getString(R.string.app_name))
            setContentText(context.getString(easterEggProgressMsg))
        }
    }

    fun notifyOfOfflineDevices(offlineFeeders: Collection<Feeder>) {
        log(TAG) { "notifyOfOfflineDevices($offlineFeeders)" }

        // A feeder that recovered, or whose check failed, loses its notification while others stay offline
        staleNotificationIds(offlineFeeders.map { it.id }).forEach { notificationManager.cancel(it) }

        offlineFeeders.forEach { feeder ->
            val notificationId = getNotificationIdForFeeder(feeder.id)

            val notification = builder.apply {
                clearActions()
                setContentTitle(context.getString(R.string.feeder_monitor_offline_title))

                val msgText = context.getString(
                    R.string.feeder_monitor_offline_message,
                    feeder.label
                )
                setContentText(msgText)
                setStyle(BigTextStyle().bigText(msgText))

                val ignoreIntent = FeederMonitorIgnoreReceiver.createIntent(
                    context,
                    feeder.id,
                    notificationId
                )
                val ignorePendingIntent = PendingIntent.getBroadcast(
                    context,
                    feeder.id.hashCode(), // Use feeder ID as request code to make PendingIntents unique
                    ignoreIntent,
                    PendingIntentFlagCompat.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

                addAction(
                    R.drawable.ic_outline_snooze_24,
                    context.getString(R.string.feeder_notification_snooze_action),
                    ignorePendingIntent
                )
            }.build()

            notificationManager.notify(notificationId, notification)
        }
    }

    fun clearOfflineNotifications() {
        log(TAG) { "clearOfflineNotifications()" }
        for (id in BASE_NOTIFICATION_ID until (BASE_NOTIFICATION_ID + MAX_NOTIFICATION_OFFSET)) {
            notificationManager.cancel(id)
        }
    }

    fun cancelNotification(notificationId: Int) {
        log(TAG) { "cancelNotification($notificationId)" }
        notificationManager.cancel(notificationId)
    }

    /** [offlineSince] is epoch millis. */
    fun notifyServerOffline(feederId: String, label: String, offlineSince: Long?) {
        log(TAG) { "notifyServerOffline($feederId, $offlineSince)" }
        val message = if (offlineSince != null) {
            context.getString(R.string.feeder_monitor_server_offline_message, label, offlineTimeText(offlineSince))
        } else {
            context.getString(R.string.feeder_monitor_server_offline_message_no_time, label)
        }
        val notification = serverBuilder(
            title = context.getString(R.string.feeder_monitor_server_offline_title),
            message = message,
            snooze = snoozeIntent(feederId, KIND_OFFLINE),
        ).build()
        notificationManager.notify(offlineTag(feederId), SERVER_OFFLINE_ID, notification)
    }

    fun notifyServerRecovered(feederId: String, label: String, offlineForMillis: Long?) {
        log(TAG) { "notifyServerRecovered($feederId, $offlineForMillis)" }
        val message = if (offlineForMillis != null) {
            val duration = durationText(Duration.ofMillis(offlineForMillis), context.resources.configuration.locales[0])
            context.getString(R.string.feeder_monitor_server_recovered_message, label, duration)
        } else {
            context.getString(R.string.feeder_monitor_server_recovered_message_no_time, label)
        }
        val notification = serverBuilder(
            title = context.getString(R.string.feeder_monitor_server_recovered_title),
            message = message,
            snooze = null,
        ).build()
        notificationManager.notify(offlineTag(feederId), SERVER_OFFLINE_ID, notification)
    }

    /** An [update] does not alert again while the earlier notification is still shown. */
    fun notifyServerProblems(feederId: String, label: String, problems: List<String>, update: Boolean = false) {
        log(TAG) { "notifyServerProblems($feederId, $problems, update=$update)" }
        val names = problems.map { problemName(it) }.distinct().joinToString(", ")
        val notification = serverBuilder(
            title = context.getString(R.string.feeder_monitor_server_problems_title),
            message = context.getString(R.string.feeder_monitor_server_problems_message, label, names),
            snooze = snoozeIntent(feederId, KIND_PROBLEMS),
        ).setOnlyAlertOnce(update).build()
        notificationManager.notify(problemsTag(feederId), SERVER_PROBLEMS_ID, notification)
    }

    fun cancelServerOffline(feederId: String) {
        log(TAG) { "cancelServerOffline($feederId)" }
        notificationManager.cancel(offlineTag(feederId), SERVER_OFFLINE_ID)
    }

    fun cancelServerProblems(feederId: String) {
        log(TAG) { "cancelServerProblems($feederId)" }
        notificationManager.cancel(problemsTag(feederId), SERVER_PROBLEMS_ID)
    }

    fun cancelServer(feederId: String) {
        cancelServerOffline(feederId)
        cancelServerProblems(feederId)
    }

    internal fun problemName(code: String): String = context.getString(
        when (code) {
            "mlat_missing" -> R.string.feeder_problem_mlat_missing
            "mlat_no_peers" -> R.string.feeder_problem_mlat_no_peers
            "mlat_bad_sync" -> R.string.feeder_problem_mlat_bad_sync
            "mlat_outliers" -> R.string.feeder_problem_mlat_outliers
            "deaf" -> R.string.feeder_problem_deaf
            "flapping" -> R.string.feeder_problem_flapping
            "duplicate_uuid" -> R.string.feeder_problem_duplicate_uuid
            else -> R.string.feeder_problem_other
        }
    )

    private fun offlineTimeText(at: Long): String {
        val flags = if (DateUtils.isToday(at)) {
            DateUtils.FORMAT_SHOW_TIME
        } else {
            DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH
        }
        return DateUtils.formatDateTime(context, at, flags)
    }

    private fun serverBuilder(title: String, message: String, snooze: PendingIntent?) =
        NotificationCompat.Builder(context, CHANNEL_ID).apply {
            setChannelId(CHANNEL_ID)
            setContentIntent(openPi)
            priority = NotificationCompat.PRIORITY_DEFAULT
            setSmallIcon(R.drawable.ic_chili_alert_24)
            setContentTitle(title)
            setContentText(message)
            setStyle(BigTextStyle().bigText(message))
            if (snooze != null) {
                addAction(
                    R.drawable.ic_outline_snooze_24,
                    context.getString(R.string.feeder_notification_snooze_action),
                    snooze,
                )
            }
        }

    private fun snoozeIntent(feederId: String, kind: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        "$feederId:$kind".hashCode(),
        FeederMonitorIgnoreReceiver.createServerIntent(context, feederId),
        PendingIntentFlagCompat.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    internal fun staleNotificationIds(offlineIds: Collection<String>): List<Int> {
        val current = offlineIds.map { getNotificationIdForFeeder(it) }.toSet()
        return (BASE_NOTIFICATION_ID until (BASE_NOTIFICATION_ID + MAX_NOTIFICATION_OFFSET)).filter { it !in current }
    }

    fun getNotificationIdForFeeder(feederId: String): Int {
        val prefix = if (feederId.length >= 4) feederId.substring(0, 4) else feederId
        val suffix = if (feederId.length > 4) feederId.substring(feederId.length - 4) else ""
        val combinedHash = (prefix + suffix).hashCode().absoluteValue
        return BASE_NOTIFICATION_ID + (combinedHash % MAX_NOTIFICATION_OFFSET)
    }

    companion object {
        val TAG = logTag("Feeder", "Monitor", "Notifications")
        private val CHANNEL_ID = "${BuildConfigWrap.APPLICATION_ID}.notification.channel.feeder.monitor"
        internal const val BASE_NOTIFICATION_ID = 1000
        internal const val MAX_NOTIFICATION_OFFSET = 50

        /** Server notifications are told apart by their tag, see [offlineTag] and [problemsTag]. */
        internal const val SERVER_OFFLINE_ID = 2000
        internal const val SERVER_PROBLEMS_ID = 2001
        private const val KIND_OFFLINE = "offline"
        private const val KIND_PROBLEMS = "problems"

        internal fun offlineTag(feederId: String) = "feeder:$feederId:$KIND_OFFLINE"
        internal fun problemsTag(feederId: String) = "feeder:$feederId:$KIND_PROBLEMS"
    }
}
