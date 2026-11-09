package eu.darken.apl.feeder.core.server

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
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

    private val runner = FeederMonitorRunner(mode, sync, alerts)

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
}
