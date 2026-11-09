package eu.darken.apl.feeder.core.server

import eu.darken.apl.feeder.core.monitor.FeederMonitorNotifications
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.io.IOException

class FeederMonitorRunnerTest : BaseTest() {

    private val mode = mockk<FeederServerMode>()
    private val sync = mockk<FeederMonitorSync>()
    private val alerts = mockk<FeederServerAlerts>().apply {
        coEvery { deliver() } returns Unit
    }

    private val store = mockk<FeederServerStore>().apply {
        coEvery { current() } returns FeederServerState(installationId = INSTALLATION_A)
    }
    private val notifications = mockk<FeederMonitorNotifications>(relaxed = true)

    private val runner = FeederMonitorRunner(mode, sync, store, alerts, notifications)

    private fun active(isActive: Boolean) {
        coEvery { mode.current() } returns FeederServerMode.State(access = null, isActive = isActive)
    }

    @Test
    fun `outside server mode nothing runs`() = runBlocking<Unit> {
        active(false)

        runner.run("test") shouldBe FeederMonitorSync.Result.Inactive

        coVerify(exactly = 0) { sync.run(any(), any()) }
        coVerify(exactly = 0) { alerts.deliver() }
    }

    @Test
    fun `a run syncs, then delivers`() = runBlocking<Unit> {
        active(true)
        val done = FeederMonitorSync.Result.Done(moreWork = true)
        coEvery { sync.run("test", true) } returns done

        runner.run("test", foreground = true) shouldBe done

        coVerifyOrder {
            sync.run("test", true)
            alerts.deliver()
        }
    }

    @Test
    fun `a run that stays with its installation leaves the notifications`() = runBlocking<Unit> {
        active(true)
        coEvery { sync.run(any(), any()) } returns FeederMonitorSync.Result.Done(moreWork = false)

        runner.run("test")

        verify(exactly = 0) { notifications.cancelAllServer() }
    }

    @Test
    fun `an aborted run cancels every server notification before it delivers`() = runBlocking<Unit> {
        active(true)
        coEvery { store.current() } returnsMany listOf(
            FeederServerState(installationId = INSTALLATION_A),
            FeederServerState(),
        )
        coEvery { sync.run(any(), any()) } returns FeederMonitorSync.Result.Aborted

        runner.run("test") shouldBe FeederMonitorSync.Result.Aborted

        coVerifyOrder {
            notifications.cancelAllServer()
            alerts.deliver()
        }
    }

    @Test
    fun `a run that starts over for another installation cancels every server notification`() = runBlocking<Unit> {
        active(true)
        coEvery { store.current() } returnsMany listOf(
            FeederServerState(installationId = INSTALLATION_A),
            FeederServerState(installationId = INSTALLATION_B),
        )
        coEvery { sync.run(any(), any()) } returns FeederMonitorSync.Result.Done(moreWork = false)

        runner.run("test")

        verify(exactly = 1) { notifications.cancelAllServer() }
    }

    @Test
    fun `a failed sync still delivers and passes its exception on`() = runBlocking<Unit> {
        active(true)
        coEvery { sync.run(any(), any()) } throws IOException("offline")

        shouldThrow<IOException> { runner.run("test") }

        coVerify(exactly = 1) { alerts.deliver() }
    }

    @Test
    fun `a failed delivery after a failed sync does not hide the sync's exception`() = runBlocking<Unit> {
        active(true)
        coEvery { sync.run(any(), any()) } throws IOException("offline")
        coEvery { alerts.deliver() } throws IllegalStateException("broken")

        val e = shouldThrow<IOException> { runner.run("test") }

        e.suppressed.single()::class shouldBe IllegalStateException::class
    }

    companion object {
        private const val INSTALLATION_A = "installation-a"
        private const val INSTALLATION_B = "installation-b"
    }
}
