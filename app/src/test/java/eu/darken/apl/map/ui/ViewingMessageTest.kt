package eu.darken.apl.map.ui

import eu.darken.apl.main.core.AircraftRepo.ViewingState.Reason
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.status.ServerStatus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.io.IOException
import java.time.Instant

class ViewingMessageTest : BaseTest() {

    private val until = Instant.parse("2026-11-26T14:35:00Z")

    private val troubled = listOf(
        ServerStatus.Maintenance(until),
        ServerStatus.Maintenance(null),
        ServerStatus.Incident(until),
        ServerStatus.Incident(null),
        ServerStatus.ServerProblem,
        ServerStatus.Unreachable,
    )

    private val allStatuses = troubled + ServerStatus.Ok

    @Test
    fun `with the bar visible, aging positions only say they may be outdated`() {
        for (status in troubled) {
            viewingMessage(waiting = null, stale = true, status, barVisible = true) shouldBe
                ViewingMessage.PositionsOutdated
        }
    }

    @Test
    fun `with the bar visible, missing data only says it is unavailable`() {
        for (status in troubled) {
            for (reason in listOf(Reason.Offline, Reason.AccessUnknown)) {
                viewingMessage(reason, stale = false, status, barVisible = true) shouldBe ViewingMessage.Unavailable
            }
        }
    }

    @Test
    fun `without the bar the server's reason is shown for aging and missing data`() {
        for (status in troubled) {
            viewingMessage(waiting = null, stale = true, status, barVisible = false) shouldBe
                ViewingMessage.Server(status)
            viewingMessage(Reason.Offline, stale = false, status, barVisible = false) shouldBe
                ViewingMessage.Server(status)
            viewingMessage(Reason.AccessUnknown, stale = false, status, barVisible = false) shouldBe
                ViewingMessage.Server(status)
        }
    }

    @Test
    fun `unknown access says nothing while the server is fine`() {
        for (barVisible in listOf(true, false)) {
            viewingMessage(Reason.AccessUnknown, stale = false, ServerStatus.Ok, barVisible) shouldBe null
        }
    }

    @Test
    fun `unknown access says something while the server is in trouble`() {
        viewingMessage(Reason.AccessUnknown, stale = false, ServerStatus.Unreachable, barVisible = true) shouldBe
            ViewingMessage.Unavailable
        viewingMessage(Reason.AccessUnknown, stale = false, ServerStatus.Unreachable, barVisible = false) shouldBe
            ViewingMessage.Server(ServerStatus.Unreachable)
    }

    @Test
    fun `feature reasons beat any server status`() {
        val resetsAt = Instant.parse("2026-11-27T00:00:00Z")
        for (status in allStatuses) {
            for (barVisible in listOf(true, false)) {
                viewingMessage(Reason.Exhausted(resetsAt), stale = false, status, barVisible) shouldBe
                    ViewingMessage.AllowanceUsedUp(resetsAt)
                viewingMessage(Reason.Restricted, stale = false, status, barVisible) shouldBe ViewingMessage.Unavailable
                viewingMessage(Reason.Revoked, stale = false, status, barVisible) shouldBe ViewingMessage.Unavailable
                viewingMessage(Reason.UpdateRequired, stale = false, status, barVisible) shouldBe null
            }
        }
    }

    @Test
    fun `while the server is fine the texts stay as they were`() {
        for (barVisible in listOf(true, false)) {
            viewingMessage(waiting = null, stale = true, ServerStatus.Ok, barVisible) shouldBe
                ViewingMessage.ConnectionProblem
            viewingMessage(Reason.Offline, stale = false, ServerStatus.Ok, barVisible) shouldBe
                ViewingMessage.Unavailable
        }
    }

    @Test
    fun `current data says nothing whatever the server status`() {
        for (status in allStatuses) {
            for (barVisible in listOf(true, false)) {
                viewingMessage(waiting = null, stale = false, status, barVisible) shouldBe null
            }
        }
    }

    @Test
    fun `leaving fullscreen swaps the server's reason for the freshness text`() {
        val status = ServerStatus.Maintenance(until)
        viewingMessage(waiting = null, stale = true, status, barVisible = false) shouldBe ViewingMessage.Server(status)
        viewingMessage(waiting = null, stale = true, status, barVisible = true) shouldBe ViewingMessage.PositionsOutdated

        viewingMessage(Reason.Offline, stale = false, status, barVisible = false) shouldBe ViewingMessage.Server(status)
        viewingMessage(Reason.Offline, stale = false, status, barVisible = true) shouldBe ViewingMessage.Unavailable
    }

    @Test
    fun `a rate limit leaves the positions current, other failures leave them aging`() {
        isStaleAfter(null) shouldBe false
        isStaleAfter(ServerApiException(code = "rate_limited", status = 429)) shouldBe false
        isStaleAfter(ServerApiException(code = "internal", status = 500)) shouldBe true
        isStaleAfter(IOException("timeout")) shouldBe true
    }
}
