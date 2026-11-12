package eu.darken.apl.feeder.core.monitor

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import eu.darken.apl.feeder.core.server.FeederServerMode
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Duration

class FeederWorkerHelperTest : BaseTest() {

    private val workManager = mockk<WorkManager>(relaxed = true)
    private val monitor = mockk<FeederMonitor>().apply {
        coEvery { check() } returns Unit
    }
    private val modeState = MutableStateFlow(FeederServerMode.State(access = null, isActive = false))
    private val serverMode = mockk<FeederServerMode>().apply {
        every { state } returns modeState
    }
    private val notifications = mockk<FeederMonitorNotifications>(relaxed = true)

    private fun TestScope.started() {
        FeederWorkerHelper(backgroundScope, workManager, monitor, serverMode, notifications).setup()
        runCurrent()
    }

    private fun TestScope.active(isActive: Boolean) {
        modeState.value = FeederServerMode.State(access = null, isActive = isActive)
        runCurrent()
    }

    @Test
    fun `the fallback polling runs hourly`() = runTest {
        val request = slot<PeriodicWorkRequest>()

        started()

        verify(exactly = 1) {
            workManager.enqueueUniquePeriodicWork("feeder.monitor.worker", ExistingPeriodicWorkPolicy.UPDATE, capture(request))
        }
        request.captured.workSpec.intervalDuration shouldBe Duration.ofMinutes(60).toMillis()
    }

    @Test
    fun `starting inactive cancels nothing`() = runTest {
        started()

        verify(exactly = 0) { notifications.cancelAllServer() }
        verify(exactly = 0) { notifications.clearOfflineNotifications() }
    }

    @Test
    fun `server mode turning active clears the app's own notifications`() = runTest {
        started()

        active(true)

        verify(exactly = 1) { notifications.clearOfflineNotifications() }
        verify(exactly = 0) { notifications.cancelAllServer() }
    }

    @Test
    fun `server mode turning inactive cancels every server notification`() = runTest {
        started()
        active(true)

        active(false)

        verify(exactly = 1) { notifications.cancelAllServer() }
    }
}
