package eu.darken.apl.feeder.core.monitor

import android.content.Context
import androidx.work.Operation
import androidx.work.WorkManager
import com.google.common.util.concurrent.ListenableFuture
import eu.darken.apl.feeder.core.config.FeederSettings
import eu.darken.apl.feeder.core.server.FeederServerMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.coroutine.TestDispatcherProvider
import java.io.IOException

class FeederWorkerHelperTest : BaseTest() {

    private val cancelled = mockk<Operation> {
        every { result } returns mockk<ListenableFuture<Operation.State.SUCCESS>> {
            every { isDone } returns true
            every { get() } returns mockk()
        }
    }
    private val workManager = mockk<WorkManager>(relaxed = true).apply {
        every { cancelUniqueWork(any()) } returns cancelled
    }
    private val context = mockk<Context>().apply {
        every { deleteDatabase(any()) } returns true
    }
    private val modeState = MutableStateFlow(FeederServerMode.State(access = null, isActive = false))
    private val serverMode = mockk<FeederServerMode>().apply {
        every { state } returns modeState
    }
    private val settings = mockk<FeederSettings>().apply {
        coEvery { removeLegacyMonitorKeys() } returns Unit
    }
    private val notifications = mockk<FeederMonitorNotifications>(relaxed = true)

    private fun TestScope.started() {
        FeederWorkerHelper(
            appScope = backgroundScope,
            context = context,
            workManager = workManager,
            dispatcherProvider = TestDispatcherProvider(),
            serverMode = serverMode,
            settings = settings,
            notifications = notifications,
        ).setup()
        runCurrent()
    }

    private fun TestScope.active(isActive: Boolean) {
        modeState.value = FeederServerMode.State(access = null, isActive = isActive)
        runCurrent()
    }

    @Test
    fun `starting removes what the app's own feeder checks left behind`() = runTest {
        started()

        verify(exactly = 1) { workManager.cancelUniqueWork("feeder.monitor.worker") }
        verify(exactly = 1) { context.deleteDatabase("feeder-stats") }
        coVerify(exactly = 1) { settings.removeLegacyMonitorKeys() }
        verify(exactly = 1) { notifications.clearOfflineNotifications() }
    }

    @Test
    fun `starting schedules no periodic work`() = runTest {
        started()

        verify(exactly = 0) { workManager.enqueueUniquePeriodicWork(any(), any(), any()) }
        verify(exactly = 0) { workManager.enqueue(any<androidx.work.WorkRequest>()) }
    }

    @Test
    fun `a failing cleanup step does not skip the others`() = runTest {
        coEvery { settings.removeLegacyMonitorKeys() } throws IOException("datastore unreadable")

        // An exception escaping the app scope crashes the app, here it fails the test
        started()

        verify(exactly = 1) { context.deleteDatabase("feeder-stats") }
        verify(exactly = 1) { notifications.clearOfflineNotifications() }
    }

    @Test
    fun `a failing worker cancel does not skip the others`() = runTest {
        every { workManager.cancelUniqueWork(any()) } throws IllegalStateException("not initialized")

        started()

        verify(exactly = 1) { context.deleteDatabase("feeder-stats") }
        coVerify(exactly = 1) { settings.removeLegacyMonitorKeys() }
        verify(exactly = 1) { notifications.clearOfflineNotifications() }
    }

    @Test
    fun `starting inactive cancels no server notifications`() = runTest {
        started()

        verify(exactly = 0) { notifications.cancelAllServer() }
    }

    @Test
    fun `server mode turning active cancels no server notifications`() = runTest {
        started()

        active(true)

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
