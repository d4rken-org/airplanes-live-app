package eu.darken.apl.ar.ui

import eu.darken.apl.main.core.AircraftRepo.ViewingState.Reason
import eu.darken.apl.map.ui.ViewingMessage
import eu.darken.apl.server.status.ServerStatus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Instant

class ArViewingMessageTest : BaseTest() {

    private val until = Instant.parse("2026-11-26T14:35:00Z")

    private val troubled = listOf(
        ServerStatus.Maintenance(until),
        ServerStatus.Maintenance(null),
        ServerStatus.Incident(until),
        ServerStatus.Incident(null),
        ServerStatus.ServerProblem,
        ServerStatus.Unreachable,
    )

    @Test
    fun `missing or aging data names the server's trouble`() {
        for (status in troubled) {
            arViewingMessage(Reason.Offline, stale = false, status) shouldBe ViewingMessage.Server(status)
            arViewingMessage(Reason.AccessUnknown, stale = false, status) shouldBe ViewingMessage.Server(status)
            arViewingMessage(waiting = null, stale = true, status) shouldBe ViewingMessage.Server(status)
        }
    }

    @Test
    fun `while the server is fine aging positions are a connection problem`() {
        arViewingMessage(waiting = null, stale = true, ServerStatus.Ok) shouldBe ViewingMessage.ConnectionProblem
    }

    @Test
    fun `while the server is fine missing data stays unavailable`() {
        arViewingMessage(Reason.Offline, stale = false, ServerStatus.Ok) shouldBe ViewingMessage.Unavailable
        arViewingMessage(Reason.AccessUnknown, stale = false, ServerStatus.Ok) shouldBe null
        arViewingMessage(waiting = null, stale = false, ServerStatus.Ok) shouldBe null
    }

    @Test
    fun `feature reasons beat any server status`() {
        val resetsAt = Instant.parse("2026-11-27T00:00:00Z")
        for (status in troubled + ServerStatus.Ok) {
            arViewingMessage(Reason.Exhausted(resetsAt), stale = false, status) shouldBe
                ViewingMessage.AllowanceUsedUp(resetsAt)
            arViewingMessage(Reason.Restricted, stale = false, status) shouldBe ViewingMessage.Unavailable
            arViewingMessage(Reason.Revoked, stale = false, status) shouldBe ViewingMessage.Unavailable
            arViewingMessage(Reason.UpdateRequired, stale = false, status) shouldBe null
        }
    }
}
