package eu.darken.apl.main.ui.navigation

import eu.darken.apl.server.status.PlannedMaintenance
import eu.darken.apl.server.status.ServerStatus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Instant

class StatusSlotStateTest : BaseTest() {

    private val until = Instant.parse("2026-11-26T14:35:00Z")
    private val notice = PlannedMaintenance(
        id = "notice-1",
        startsAt = Instant.parse("2026-12-01T02:00:00Z"),
        endsAt = Instant.parse("2026-12-01T04:00:00Z"),
    )

    private val serverStatuses = listOf(
        ServerStatus.Ok,
        ServerStatus.Maintenance(until),
        ServerStatus.Maintenance(null),
        ServerStatus.Incident(until),
        ServerStatus.Incident(null),
        ServerStatus.ServerProblem,
        ServerStatus.Unreachable,
    )

    @Test
    fun `nothing to say hides the slot`() {
        StatusSlotState.of(ServerStatus.Ok, upcoming = null) shouldBe StatusSlotState.Hidden
    }

    @Test
    fun `an announced maintenance shows while the server is fine`() {
        StatusSlotState.of(ServerStatus.Ok, upcoming = notice) shouldBe StatusSlotState.Planned(notice)
    }

    @Test
    fun `any server trouble beats the announcement`() {
        listOf(null, notice).forEach { upcoming ->
            StatusSlotState.of(ServerStatus.Maintenance(until), upcoming) shouldBe StatusSlotState.Maintenance(until)
            StatusSlotState.of(ServerStatus.Maintenance(null), upcoming) shouldBe StatusSlotState.Maintenance(null)
            StatusSlotState.of(ServerStatus.Incident(until), upcoming) shouldBe StatusSlotState.Incident(until)
            StatusSlotState.of(ServerStatus.Incident(null), upcoming) shouldBe StatusSlotState.Incident(null)
            StatusSlotState.of(ServerStatus.ServerProblem, upcoming) shouldBe StatusSlotState.ServerProblem
            StatusSlotState.of(ServerStatus.Unreachable, upcoming) shouldBe StatusSlotState.Unreachable
        }
    }

    @Test
    fun `offline beats everything`() {
        serverStatuses.forEach { status ->
            listOf(null, notice).forEach { upcoming ->
                StatusSlotState.of(status, upcoming).shownWhen(isInternetAvailable = false) shouldBe
                        StatusSlotState.Offline
            }
        }
    }

    @Test
    fun `online shows the server's state as it is`() {
        serverStatuses.forEach { status ->
            listOf(null, notice).forEach { upcoming ->
                val state = StatusSlotState.of(status, upcoming)
                state.shownWhen(isInternetAvailable = true) shouldBe state
            }
        }
    }
}
