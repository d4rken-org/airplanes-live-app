package eu.darken.apl.feeder.core.monitor

import androidx.work.WorkManager
import eu.darken.apl.common.datastore.DataStoreValue
import eu.darken.apl.feeder.core.config.FeederSettings
import eu.darken.apl.feeder.core.server.FeederServerMode
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
    private val feederSettings = mockk<FeederSettings>().apply {
        val interval = mockk<DataStoreValue<Duration>>()
        every { interval.flow } returns flowOf(Duration.ofHours(1))
        every { feederMonitorInterval } returns interval
    }
    private val modeState = MutableStateFlow(FeederServerMode.State(access = null, isActive = false))
    private val serverMode = mockk<FeederServerMode>().apply {
        every { state } returns modeState
    }
    private val notifications = mockk<FeederMonitorNotifications>(relaxed = true)

    private fun TestScope.started() {
        FeederWorkerHelper(backgroundScope, workManager, monitor, feederSettings, serverMode, notifications).setup()
        runCurrent()
    }

    private fun TestScope.active(isActive: Boolean) {
        modeState.value = FeederServerMode.State(access = null, isActive = isActive)
        runCurrent()
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
