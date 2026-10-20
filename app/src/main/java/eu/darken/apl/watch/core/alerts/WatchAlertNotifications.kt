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
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.notifications.PendingIntentFlagCompat
import eu.darken.apl.common.planespotters.coil.PlanespottersImage
import eu.darken.apl.common.planespotters.toPlanespottersQuery
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.ui.MainActivity
import eu.darken.apl.watch.core.server.WatchServerAlerts
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.FlightWatch
import eu.darken.apl.watch.core.types.LocationWatch
import eu.darken.apl.watch.core.types.SquawkWatch
import eu.darken.apl.watch.core.types.Watch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue

@Singleton
class WatchAlertNotifications @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val notificationManager: NotificationManager,
    private val dispatcherProvider: DispatcherProvider,
    private val aircraftRepo: AircraftRepo,
) : WatchServerAlerts {

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
                    val msgText = context.resources.getQuantityString(
                        R.plurals.watch_notification_alert_squawk_msg,
                        aircrafts.size, watch.code, aircrafts.size
                    )
                    setContentText(msgText)
                }

                is LocationWatch -> {
                    setContentTitle(context.getString(R.string.watch_notification_alert_location_title))
                    val msgText = context.resources.getQuantityString(
                        R.plurals.watch_notification_alert_location_msg,
                        aircrafts.size, aircrafts.size, watch.label
                    )
                    setContentText(msgText)
                }
            }
        }.build()
        withContext(dispatcherProvider.Main) {
            notificationManager.notify(watch.notificationId, notification)
        }
    }

    /** The cache only makes the alert nicer, it is posted without it as well. */
    private suspend fun cachedAircraft(hex: String?): Aircraft? {
        if (hex == null) return null
        return try {
            val cache = aircraftRepo.cache.first()
            cache[hex] ?: cache[hex.uppercase()]
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, WARN) { "Aircraft cache unavailable: ${e.asLog()}" }
            null
        }
    }

    private suspend fun Aircraft.previewOrNull(): PlanespottersImage? = try {
        getPlanePreview()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log(TAG, WARN) { "No preview for $hex: ${e.asLog()}" }
        null
    }

    override suspend fun postAlert(watch: Watch, aircraftIds: List<String>, totalMatching: Int) {
        log(TAG) { "postAlert(watch=${watch.id}, aircraft=$aircraftIds, total=$totalMatching)" }
        val count = maxOf(totalMatching, aircraftIds.size)
        val aircraft = cachedAircraft(aircraftIds.firstOrNull())

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

            aircraft?.previewOrNull()?.let { setLargeIcon(it.raw) }

            when (watch) {
                is AircraftWatch -> {
                    setContentTitle(context.getString(R.string.watch_notification_alert_aircraft_title))
                    setContentText(
                        context.getString(
                            R.string.watch_notification_alert_aircraft_msg,
                            aircraft?.hex ?: aircraftIds.firstOrNull() ?: watch.hex,
                        )
                    )
                }

                is FlightWatch -> {
                    setContentTitle(context.getString(R.string.watch_notification_alert_flight_title))
                    setContentText(context.getString(R.string.watch_notification_alert_flight_msg, watch.callsign))
                }

                is SquawkWatch -> {
                    setContentTitle(context.getString(R.string.watch_notification_alert_squawk_title))
                    setContentText(
                        context.resources.getQuantityString(
                            R.plurals.watch_notification_alert_squawk_msg,
                            count, watch.code, count
                        )
                    )
                }

                is LocationWatch -> {
                    setContentTitle(context.getString(R.string.watch_notification_alert_location_title))
                    setContentText(
                        context.resources.getQuantityString(
                            R.plurals.watch_notification_alert_location_msg,
                            count, count, watch.label
                        )
                    )
                }
            }
        }.build()
        withContext(dispatcherProvider.Main) {
            notificationManager.notify(watch.id, SERVER_ALERT_ID, notification)
        }
    }

    override suspend fun postSummary(sightings: Int, watches: Int) {
        log(TAG) { "postSummary(sightings=$sightings, watches=$watches)" }
        val notification = getBuilder().apply {
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).apply { setAction(WATCH_LIST_SHOW_ACTION) },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntentFlagCompat.FLAG_IMMUTABLE
            )
            setContentIntent(pendingIntent)
            setContentTitle(context.getString(R.string.watch_notification_summary_title))
            setContentText(
                context.getString(
                    R.string.watch_notification_summary_msg,
                    context.resources.getQuantityString(R.plurals.watch_notification_summary_sightings, sightings, sightings),
                    context.resources.getQuantityString(R.plurals.watch_notification_summary_watches, watches, watches),
                )
            )
        }.build()
        withContext(dispatcherProvider.Main) {
            notificationManager.notify(SERVER_SUMMARY_TAG, SERVER_ALERT_ID, notification)
        }
    }

    companion object {
        val TAG = logTag("Watch", "Monitor", "Notifications")
        private val CHANNEL_ID = "${BuildConfigWrap.APPLICATION_ID}.notification.channel.watch.monitor"
        internal const val NOTIFICATION_ID_RANGE = 100

        /** Server alerts are told apart by their tag, the watch id or [SERVER_SUMMARY_TAG]. */
        internal const val SERVER_ALERT_ID = 200
        internal const val SERVER_SUMMARY_TAG = "watch.server.summary"
        val ALERT_SHOW_ACTION = "${BuildConfigWrap.APPLICATION_ID}.watch.alert.show"
        val ARG_WATCHID = "${BuildConfigWrap.APPLICATION_ID}.watch.alert.show.watchid"
        val WATCH_LIST_SHOW_ACTION = "${BuildConfigWrap.APPLICATION_ID}.watch.list.show"
    }
}
