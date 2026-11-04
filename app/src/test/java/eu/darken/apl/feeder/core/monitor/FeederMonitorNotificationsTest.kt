package eu.darken.apl.feeder.core.monitor

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import eu.darken.apl.common.debug.logging.log
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldNotBe
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkStatic
import org.junit.Before
import org.junit.Test

class FeederMonitorNotificationsTest {

    @MockK lateinit var context: Context
    @MockK lateinit var notificationManager: NotificationManager

    private lateinit var notifications: FeederMonitorNotifications

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        every { context.getString(any()) } returns "test"
        every { context.getString(any(), any()) } returns "test"
        every { notificationManager.createNotificationChannel(any()) } returns Unit

        mockkStatic(PendingIntent::class)
        val pendingIntent = mockk<PendingIntent>()
        every {
            PendingIntent.getActivity(
                any(),
                any(),
                any(),
                any()
            )
        } returns pendingIntent

        notifications = FeederMonitorNotifications(context, notificationManager)
    }

    @Test
    fun `getNotificationIdForFeeder generates different IDs for different feeders`() {
        val id1 = "feeder1234"
        val id2 = "feeder1235"
        val id3 = "feeder1236"
        val id4 = "feeder1237"
        val id5 = "feeder1238"

        val notificationId1 = notifications.getNotificationIdForFeeder(id1)
        val notificationId2 = notifications.getNotificationIdForFeeder(id2)
        val notificationId3 = notifications.getNotificationIdForFeeder(id3)
        val notificationId4 = notifications.getNotificationIdForFeeder(id4)
        val notificationId5 = notifications.getNotificationIdForFeeder(id5)

        val allIds = setOf(notificationId1, notificationId2, notificationId3, notificationId4, notificationId5)
        allIds shouldHaveSize 5

        // Check that all IDs are within the expected range
        allIds.forEach { id ->
            id shouldBeGreaterThanOrEqualTo FeederMonitorNotifications.BASE_NOTIFICATION_ID
            id shouldBeLessThan (FeederMonitorNotifications.BASE_NOTIFICATION_ID + FeederMonitorNotifications.MAX_NOTIFICATION_OFFSET)
        }
    }

    @Test
    fun `getNotificationIdForFeeder handles short feeder IDs`() {
        val shortId1 = "abc"
        val shortId2 = "def"

        val notificationId1 = notifications.getNotificationIdForFeeder(shortId1)
        val notificationId2 = notifications.getNotificationIdForFeeder(shortId2)

        notificationId1 shouldNotBe notificationId2

        notificationId1 shouldBeGreaterThanOrEqualTo FeederMonitorNotifications.BASE_NOTIFICATION_ID
        notificationId1 shouldBeLessThan (FeederMonitorNotifications.BASE_NOTIFICATION_ID + FeederMonitorNotifications.MAX_NOTIFICATION_OFFSET)
        notificationId2 shouldBeGreaterThanOrEqualTo FeederMonitorNotifications.BASE_NOTIFICATION_ID
        notificationId2 shouldBeLessThan (FeederMonitorNotifications.BASE_NOTIFICATION_ID + FeederMonitorNotifications.MAX_NOTIFICATION_OFFSET)
    }

    @Test
    fun `getNotificationIdForFeeder handles UUID feeder IDs`() {
        val uuidId1 = "123e4567-e89b-12d3-a456-426614174000"
        val uuidId2 = "00112233-4455-6677-8899-aabbccddeeff"
        val uuidId3 = "f81d4fae-7dec-11d0-a765-00a0c91e6bf6"
        val uuidId4 = "550e8400-e29b-41d4-a716-446655440000"
        val uuidId5 = "6ba7b810-9dad-11d1-80b4-00c04fd430c8"

        val notificationId1 = notifications.getNotificationIdForFeeder(uuidId1)
        val notificationId2 = notifications.getNotificationIdForFeeder(uuidId2)
        val notificationId3 = notifications.getNotificationIdForFeeder(uuidId3)
        val notificationId4 = notifications.getNotificationIdForFeeder(uuidId4)
        val notificationId5 = notifications.getNotificationIdForFeeder(uuidId5)

        val allIds = setOf(notificationId1, notificationId2, notificationId3, notificationId4, notificationId5)
        allIds shouldHaveSize 5

        // Check that all IDs are within the expected range
        allIds.forEach { id ->
            id shouldBeGreaterThanOrEqualTo FeederMonitorNotifications.BASE_NOTIFICATION_ID
            id shouldBeLessThan (FeederMonitorNotifications.BASE_NOTIFICATION_ID + FeederMonitorNotifications.MAX_NOTIFICATION_OFFSET)
        }

        log { "UUID1: $uuidId1 -> ID: $notificationId1" }
        log { "UUID2: $uuidId2 -> ID: $notificationId2" }
        log { "UUID3: $uuidId3 -> ID: $notificationId3" }
        log { "UUID4: $uuidId4 -> ID: $notificationId4" }
        log { "UUID5: $uuidId5 -> ID: $notificationId5" }
    }

    @Test
    fun `a feeder that left the offline set loses its notification, the remaining one keeps it`() {
        val stillOffline = notifications.getNotificationIdForFeeder("feeder-b")
        val recovered = notifications.getNotificationIdForFeeder("feeder-a")
        recovered shouldNotBe stillOffline

        val stale = notifications.staleNotificationIds(listOf("feeder-b"))

        stale shouldContain recovered
        stale shouldNotContain stillOffline
    }

    @Test
    fun `no offline feeders clears every feeder notification`() {
        notifications.staleNotificationIds(emptyList()) shouldHaveSize FeederMonitorNotifications.MAX_NOTIFICATION_OFFSET
    }
}
