package eu.darken.apl.watch.core.alerts

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.watch.core.db.WatchDatabase
import io.kotest.matchers.nulls.shouldBeNull
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
import java.time.Instant

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

    private fun Notification.openIntent() = shadowOf(contentIntent).savedIntent

    private fun posted(tag: String) = shadowOf(notificationManager).getNotification(tag, WatchAlertNotifications.SERVER_ALERT_ID)

    private suspend fun location() = db.createLocation(
        latitude = 52.5,
        longitude = 13.4,
        radiusInMeters = 25_000f,
        minAltitudeFt = null,
        maxAltitudeFt = null,
        label = "Berlin",
        note = "",
    )

    @Test
    fun `a repeated alert of a watch replaces the one posted under its tag`() = runBlocking<Unit> {
        val watch = db.createAircraft("3C65A3", "")

        notifications.postAlert(watch, listOf("3C65A3"), 1, null)
        notifications.postAlert(watch, listOf("3C65A3"), 1, null)

        val shadow = shadowOf(notificationManager)
        shadow.size() shouldBe 1
        shadow.getNotification(watch.id, WatchAlertNotifications.SERVER_ALERT_ID).text() shouldBe
                "Aircraft 3C65A3 has been spotted"
    }

    @Test
    fun `an alert without cached aircraft is posted with the server's count`() = runBlocking<Unit> {
        val watch = db.createSquawk("7700", "")

        notifications.postAlert(watch, emptyList(), 3, null)

        shadowOf(notificationManager).getNotification(watch.id, WatchAlertNotifications.SERVER_ALERT_ID).text() shouldBe
                "Code 7700 is being transmitted by 3 aircraft."
    }

    @Test
    fun `the summary is posted under a tag of its own`() = runBlocking<Unit> {
        val watch = db.createAircraft("3C65A3", "")
        notifications.postAlert(watch, listOf("3C65A3"), 1, null)

        notifications.postSummary(sightings = 3, watches = 2)

        val shadow = shadowOf(notificationManager)
        shadow.size() shouldBe 2
        shadow.getNotification(WatchAlertNotifications.SERVER_SUMMARY_TAG, WatchAlertNotifications.SERVER_ALERT_ID).text() shouldBe
                "3 sightings across 2 watches"
    }

    @Test
    fun `a location alert names its aircraft by the uppercase hex`() = runBlocking<Unit> {
        val watch = location()

        notifications.postAlert(watch, listOf("3c65a3"), 1, null)

        posted(watch.id).text() shouldBe "3C65A3 near Berlin"
    }

    @Test
    fun `a location alert with several aircraft names the first and counts the rest`() = runBlocking<Unit> {
        val watch = location()

        notifications.postAlert(watch, listOf("3c65a3", "4b1814"), 4, null)

        posted(watch.id).text() shouldBe "3C65A3 and 3 more near Berlin"
    }

    @Test
    fun `a location alert without aircraft ids keeps the count`() = runBlocking<Unit> {
        val watch = location()

        notifications.postAlert(watch, emptyList(), 2, null)

        posted(watch.id).text() shouldBe "2 aircraft spotted near Berlin"
    }

    @Test
    fun `a location alert opens its first aircraft at the time it was seen`() = runBlocking<Unit> {
        val watch = location()
        val observedAt = Instant.parse("2026-11-18T10:15:30Z")

        notifications.postAlert(watch, listOf("3c65a3", "4b1814"), 2, observedAt)

        posted(watch.id).openIntent().apply {
            action shouldBe WatchAlertNotifications.ALERT_SHOW_ACTION
            getStringExtra(WatchAlertNotifications.ARG_WATCHID) shouldBe watch.id
            getStringExtra(WatchAlertNotifications.ARG_AIRCRAFT_HEX) shouldBe "3C65A3"
            getLongExtra(WatchAlertNotifications.ARG_OBSERVED_AT, -1L) shouldBe observedAt.toEpochMilli()
        }
    }

    @Test
    fun `a location alert without a known time carries no time`() = runBlocking<Unit> {
        val watch = location()

        notifications.postAlert(watch, listOf("3c65a3"), 1, null)

        posted(watch.id).openIntent().apply {
            getStringExtra(WatchAlertNotifications.ARG_AIRCRAFT_HEX) shouldBe "3C65A3"
            hasExtra(WatchAlertNotifications.ARG_OBSERVED_AT) shouldBe false
        }
    }

    @Test
    fun `other alerts carry no aircraft to open`() = runBlocking<Unit> {
        val aircraft = db.createAircraft("3C65A3", "")
        val squawk = db.createSquawk("7700", "")
        val observedAt = Instant.parse("2026-11-18T10:15:30Z")

        notifications.postAlert(aircraft, listOf("3c65a3"), 1, observedAt)
        notifications.postAlert(squawk, listOf("3c65a3"), 1, observedAt)

        listOf(aircraft.id, squawk.id).forEach { tag ->
            posted(tag).openIntent().apply {
                getStringExtra(WatchAlertNotifications.ARG_WATCHID) shouldBe tag
                getStringExtra(WatchAlertNotifications.ARG_AIRCRAFT_HEX).shouldBeNull()
                hasExtra(WatchAlertNotifications.ARG_OBSERVED_AT) shouldBe false
            }
        }
    }
}
