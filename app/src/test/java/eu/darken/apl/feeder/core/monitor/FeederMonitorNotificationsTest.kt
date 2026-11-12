package eu.darken.apl.feeder.core.monitor

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class FeederMonitorNotificationsTest {

    @MockK lateinit var context: Context
    @MockK(relaxUnitFun = true) lateinit var notificationManager: NotificationManager

    private lateinit var notifications: FeederMonitorNotifications

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        every { context.getString(any()) } returns "test"
        every { context.getString(any(), any()) } returns "test"

        mockkStatic(PendingIntent::class)
        every { PendingIntent.getActivity(any(), any(), any(), any()) } returns mockk()

        notifications = FeederMonitorNotifications(context, notificationManager)
    }

    @Test
    fun `clearing the legacy offline notifications cancels every id they could have used`() {
        notifications.clearOfflineNotifications()

        // The ids the app's own feeder checks posted their untagged notifications under
        (1000 until 1050).forEach { id -> verify(exactly = 1) { notificationManager.cancel(id) } }
        verify(exactly = 50) { notificationManager.cancel(any<Int>()) }
        verify(exactly = 0) { notificationManager.cancel(any<String>(), any()) }
    }
}
