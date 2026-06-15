package eu.darken.apl.feeder.core.monitor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.BigTextStyle
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.apl.R
import eu.darken.apl.common.BuildConfigWrap
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.easterEggProgressMsg
import eu.darken.apl.common.notifications.PendingIntentFlagCompat
import eu.darken.apl.feeder.core.Feeder
import eu.darken.apl.main.ui.MainActivity
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue

@Singleton
class FeederMonitorNotifications @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val notificationManager: NotificationManager,
) {

    private val builder: NotificationCompat.Builder

    init {
        NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.feeder_notification_channel_title),
            NotificationManager.IMPORTANCE_DEFAULT
        ).run { notificationManager.createNotificationChannel(this) }

        val openIntent = Intent(context, MainActivity::class.java)
        val openPi = PendingIntent.getActivity(
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

        if (offlineFeeders.isEmpty()) {
            clearOfflineNotifications()
            return
        }

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
    }
}
