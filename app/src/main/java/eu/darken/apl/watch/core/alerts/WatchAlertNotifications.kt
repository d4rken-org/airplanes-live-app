package eu.darken.apl.watch.core.alerts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.apl.R
import eu.darken.apl.common.BuildConfigWrap
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.notifications.PendingIntentFlagCompat
import eu.darken.apl.common.planespotters.coil.PlanespottersImage
import eu.darken.apl.common.planespotters.toPlanespottersQuery
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.ui.MainActivity
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.FlightWatch
import eu.darken.apl.watch.core.types.LocationWatch
import eu.darken.apl.watch.core.types.SquawkWatch
import eu.darken.apl.watch.core.types.Watch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue

@Singleton
class WatchAlertNotifications @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val notificationManager: NotificationManager,
    private val dispatcherProvider: DispatcherProvider,
) {

    init {
        NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.watch_notification_channel_title),
            NotificationManager.IMPORTANCE_DEFAULT
        ).run { notificationManager.createNotificationChannel(this) }
    }

    private fun getBuilder() = NotificationCompat.Builder(context, CHANNEL_ID).apply {
        val openIntent = Intent(context, MainActivity::class.java)
        val openPi = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntentFlagCompat.FLAG_IMMUTABLE
        )

        setChannelId(CHANNEL_ID)
        setContentIntent(openPi)
        priority = NotificationCompat.PRIORITY_DEFAULT
        setSmallIcon(R.drawable.ic_watchlist_24)
        setContentTitle(context.getString(R.string.app_name))
        setContentText(context.getString(R.string.watch_notification_channel_title))
        setAutoCancel(true)
    }

    private suspend fun Aircraft.getPlanePreview(): PlanespottersImage? = withContext(dispatcherProvider.IO) {
        val request = ImageRequest.Builder(context).apply {
            data(this@getPlanePreview.toPlanespottersQuery())
        }.build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: return@withContext null
        result.image.asDrawable(context.resources) as? PlanespottersImage
    }

    private val Watch.notificationId: Int
        get() = NOTIFICATION_ID_RANGE + (id.hashCode().absoluteValue % 100)


    suspend fun alert(watch: Watch, aircrafts: Collection<Aircraft>) {
        log(TAG) { "show(watch=$watch, aircraft=${aircrafts.size})" }

        val notification = getBuilder().apply {
            val pendingIntent = PendingIntent.getActivity(
                context,
                watch.id.hashCode(),
                Intent(context, MainActivity::class.java).apply {
                    setAction(ALERT_SHOW_ACTION)
                    putExtra(ARG_WATCHID, watch.id)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntentFlagCompat.FLAG_IMMUTABLE
            )
            setContentIntent(pendingIntent)

            val aircraft = aircrafts.first()
            aircraft.getPlanePreview()?.let { setLargeIcon(it.raw) }

            when (watch) {
                is AircraftWatch -> {
                    setContentTitle(context.getString(R.string.watch_notification_alert_aircraft_title))
                    val msgText = context.getString(
                        R.string.watch_notification_alert_aircraft_msg,
                        aircraft.hex
                    )
                    setContentText(msgText)
                }

                is FlightWatch -> {
                    setContentTitle(context.getString(R.string.watch_notification_alert_flight_title))
                    val msgText = context.getString(
                        R.string.watch_notification_alert_flight_msg,
                        aircrafts.first().hex
                    )
                    setContentText(msgText)
                }

                is SquawkWatch -> {
                    setContentTitle(context.getString(R.string.watch_notification_alert_squawk_title))
                    val msgText = context.getString(
                        R.string.watch_notification_alert_squawk_msg,
                        watch.code, aircrafts.size
                    )
                    setContentText(msgText)
                }

                is LocationWatch -> {
                    setContentTitle(context.getString(R.string.watch_notification_alert_location_title))
                    val msgText = context.getString(
                        R.string.watch_notification_alert_location_msg,
                        aircrafts.size, watch.label
                    )
                    setContentText(msgText)
                }
            }
        }.build()
        withContext(dispatcherProvider.Main) {
            notificationManager.notify(watch.notificationId, notification)
        }
    }

    companion object {
        val TAG = logTag("Watch", "Monitor", "Notifications")
        private val CHANNEL_ID = "${BuildConfigWrap.APPLICATION_ID}.notification.channel.watch.monitor"
        internal const val NOTIFICATION_ID_RANGE = 100
        val ALERT_SHOW_ACTION = "${BuildConfigWrap.APPLICATION_ID}.watch.alert.show"
        val ARG_WATCHID = "${BuildConfigWrap.APPLICATION_ID}.watch.alert.show.watchid"
    }
}
