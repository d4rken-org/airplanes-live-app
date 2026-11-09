package eu.darken.apl.feeder.core.monitor

import eu.darken.apl.feeder.core.FeederRepo
import eu.darken.apl.feeder.core.config.FeederSettings
import eu.darken.apl.feeder.core.server.FeederServerMode
import io.mockk.coEvery
import io.mockk.confirmVerified
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class FeederMonitorTest : BaseTest() {

    private val settings = mockk<FeederSettings>()
    private val feederRepo = mockk<FeederRepo>()
    private val notifications = mockk<FeederMonitorNotifications>()
    private val serverMode = mockk<FeederServerMode>()

    private val monitor = FeederMonitor(settings, feederRepo, notifications, serverMode)

    @Test
    fun `in server mode the check neither polls the feeders nor touches notifications`() = runBlocking {
        coEvery { serverMode.current() } returns FeederServerMode.State(access = null, isActive = true)

        monitor.check()

        confirmVerified(feederRepo, notifications, settings)
    }
}
