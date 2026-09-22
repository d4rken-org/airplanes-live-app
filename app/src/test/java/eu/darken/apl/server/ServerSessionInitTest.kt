package eu.darken.apl.server

import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.server.session.SessionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.io.IOException

class ServerSessionInitTest : BaseTest() {

    private val sessionManager = mockk<SessionManager>()
    private val accessRepo = mockk<AccessRepo>(relaxed = true)
    private val serverClock = ServerClock(object : MonotonicClock {
        override fun elapsed(): Long = 0L
    })

    @Test
    fun `a failed session attempt still routes startup through the access refresh`() = runTest {
        every { accessRepo.state } returns MutableStateFlow<AccessState?>(null)
        coEvery { sessionManager.ensureSession() } throws IOException("offline")

        ServerSessionInit(
            appScope = backgroundScope,
            sessionManager = sessionManager,
            accessRepo = accessRepo,
            serverClock = serverClock,
        ).setup()
        // advanceUntilIdle() stops while only background work is queued, setup() is background work
        runCurrent()

        coVerify { accessRepo.refresh(any()) }
    }
}
