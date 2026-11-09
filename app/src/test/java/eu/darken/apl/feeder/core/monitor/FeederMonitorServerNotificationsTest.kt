package eu.darken.apl.feeder.core.monitor

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class FeederMonitorServerNotificationsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val notifications = FeederMonitorNotifications(context, notificationManager)
    private val shadow = shadowOf(notificationManager)

    private fun Notification.text() = extras.getCharSequence(Notification.EXTRA_TEXT).toString()
    private fun Notification.title() = extras.getCharSequence(Notification.EXTRA_TITLE).toString()

    private fun offline(feederId: String = ID_1) =
        shadow.getNotification(FeederMonitorNotifications.offlineTag(feederId), FeederMonitorNotifications.SERVER_OFFLINE_ID)

    private fun problems(feederId: String = ID_1) =
        shadow.getNotification(FeederMonitorNotifications.problemsTag(feederId), FeederMonitorNotifications.SERVER_PROBLEMS_ID)

    @Test
    fun `the recovery replaces the offline notification of its feeder`() {
        notifications.notifyServerOffline(ID_1, "Roof", 1_760_000_000_000L)
        offline().title() shouldBe "Feeder offline"
        offline().text() shouldStartWith "Roof has not sent data to airplanes.live since "

        notifications.notifyServerRecovered(ID_1, "Roof", 3 * 3_600_000L)

        shadow.size() shouldBe 1
        offline().title() shouldBe "Feeder back online"
        offline().text() shouldBe "Roof is back online after 3 hours"
    }

    @Test
    fun `offline and recovered without known times still read`() {
        notifications.notifyServerOffline(ID_1, "Roof", null)
        offline().text() shouldBe "Roof is not sending data to airplanes.live"

        notifications.notifyServerRecovered(ID_1, "Roof", null)
        offline().text() shouldBe "Roof is back online"
    }

    @Test
    fun `problems have their own notification with readable names, an unknown code by a generic one`() {
        notifications.notifyServerOffline(ID_1, "Roof", null)
        notifications.notifyServerProblems(ID_1, "Roof", listOf("mlat_no_peers", "flapping", "antenna_on_fire"))

        shadow.size() shouldBe 2
        problems().title() shouldBe "Feeder problems"
        problems().text() shouldBe "Roof: no MLAT peers, reconnecting repeatedly, another problem"
    }

    @Test
    fun `every known problem code has its own name`() {
        val codes = listOf("mlat_missing", "mlat_no_peers", "mlat_bad_sync", "mlat_outliers", "deaf", "flapping", "duplicate_uuid")
        val names = codes.map { notifications.problemName(it) }

        names.toSet().size shouldBe codes.size
        names.forEach { it shouldNotBe notifications.problemName("something_new") }
    }

    @Test
    fun `cancelling one feeder leaves the other's notifications`() {
        notifications.notifyServerOffline(ID_1, "Roof", null)
        notifications.notifyServerProblems(ID_1, "Roof", listOf("deaf"))
        notifications.notifyServerOffline(ID_2, "Garden", null)

        notifications.cancelServer(ID_1)

        shadow.size() shouldBe 1
        offline(ID_2).text() shouldBe "Garden is not sending data to airplanes.live"
    }

    @Test
    fun `the snooze actions name the feeder and differ per feeder and kind`() {
        notifications.notifyServerOffline(ID_1, "Roof", null)
        notifications.notifyServerProblems(ID_1, "Roof", listOf("deaf"))
        notifications.notifyServerProblems(ID_2, "Garden", listOf("deaf"))

        val intents = listOf(offline(ID_1), problems(ID_1), problems(ID_2)).map { shadowOf(it.actions.single().actionIntent) }

        intents.map { it.savedIntent.getStringExtra(FeederMonitorIgnoreReceiver.EXTRA_FEEDER_MONITOR_ID) } shouldBe
                listOf(ID_1, ID_1, ID_2)
        intents.map { it.requestCode }.toSet().size shouldBe 3
    }

    @Test
    fun `a recovery has no snooze action`() {
        notifications.notifyServerRecovered(ID_1, "Roof", 60_000L)

        (offline().actions?.size ?: 0) shouldBe 0
    }

    companion object {
        private const val ID_1 = "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d"
        private const val ID_2 = "11111111-2222-4333-8444-555555555555"
    }
}
