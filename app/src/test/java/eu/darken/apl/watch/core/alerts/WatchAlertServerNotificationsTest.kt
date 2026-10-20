package eu.darken.apl.watch.core.alerts

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.watch.core.db.WatchDatabase
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import testhelper.coroutine.TestDispatcherProvider

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class WatchAlertServerNotificationsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val aircraftRepo = mockk<AircraftRepo>().apply {
        every { cache } returns flowOf(emptyMap())
    }

    private lateinit var db: WatchDatabase
    private lateinit var notifications: WatchAlertNotifications

    @Before
    fun setup() {
        context.deleteDatabase("watch")
        db = WatchDatabase(context)
        notifications = WatchAlertNotifications(
            context = context,
            notificationManager = notificationManager,
            dispatcherProvider = TestDispatcherProvider(),
            aircraftRepo = aircraftRepo,
        )
    }

    @After
    fun teardown() {
        context.deleteDatabase("watch")
    }

    private fun Notification.text() = extras.getCharSequence(Notification.EXTRA_TEXT).toString()

    @Test
    fun `a repeated alert of a watch replaces the one posted under its tag`() = runBlocking<Unit> {
        val watch = db.createAircraft("3C65A3", "")

        notifications.postAlert(watch, listOf("3C65A3"), 1)
        notifications.postAlert(watch, listOf("3C65A3"), 1)

        val shadow = shadowOf(notificationManager)
        shadow.size() shouldBe 1
        shadow.getNotification(watch.id, WatchAlertNotifications.SERVER_ALERT_ID).text() shouldBe
                "Aircraft 3C65A3 has been spotted"
    }

    @Test
    fun `an alert without cached aircraft is posted with the server's count`() = runBlocking<Unit> {
        val watch = db.createSquawk("7700", "")

        notifications.postAlert(watch, emptyList(), 3)

        shadowOf(notificationManager).getNotification(watch.id, WatchAlertNotifications.SERVER_ALERT_ID).text() shouldBe
                "Code 7700 is being transmitted by 3 aircraft."
    }

    @Test
    fun `the summary is posted under a tag of its own`() = runBlocking<Unit> {
        val watch = db.createAircraft("3C65A3", "")
        notifications.postAlert(watch, listOf("3C65A3"), 1)

        notifications.postSummary(sightings = 3, watches = 2)

        val shadow = shadowOf(notificationManager)
        shadow.size() shouldBe 2
        shadow.getNotification(WatchAlertNotifications.SERVER_SUMMARY_TAG, WatchAlertNotifications.SERVER_ALERT_ID).text() shouldBe
                "3 sightings across 2 watches"
    }
}
