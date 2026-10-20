package eu.darken.apl.watch.core.history

import eu.darken.apl.watch.core.db.history.WatchHistoryHourEntity
import eu.darken.apl.watch.core.db.server.WatchServerRollupEntity
import eu.darken.apl.watch.core.history.WatchActivityTimeline.Cell
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Instant

class WatchActivityTimelineTest : BaseTest() {

    private val from = Instant.parse("2026-10-13T06:20:00Z")
    private val until = Instant.parse("2026-10-13T11:10:00Z")

    private fun at(time: String) = Instant.parse("2026-10-13T${time}Z")
    private fun check(time: String, count: Int) = WatchActivityCheck(at(time), count)
    private fun hour(time: String, count: Int, coverage: String) =
        WatchHistoryHourEntity("w1", at(time), count, coverage, isFinal = true)

    private fun rollup(
        time: String,
        count: Int,
        coverage: String,
        revision: Long = CURRENT_REVISION,
        source: String = "live",
        serverWatchId: String = SERVER_WATCH,
    ) = WatchServerRollupEntity(
        serverWatchId = serverWatchId,
        revision = revision,
        hour = at(time),
        source = source,
        watchId = "w1",
        installationId = "installation-a",
        seq = 1,
        evaluationBasis = "recent_messages",
        aircraftCount = count,
        coverage = coverage,
        isFinal = true,
    )

    private fun buildWithRollups(
        checks: List<WatchActivityCheck> = emptyList(),
        hours: List<WatchHistoryHourEntity> = emptyList(),
        rollups: List<WatchServerRollupEntity>,
    ) = WatchActivityTimeline.build(from, until, checks, hours, rollups, SERVER_WATCH, CURRENT_REVISION)

    @Test
    fun `one cell per hour from the start hour through the current one`() {
        val timeline = WatchActivityTimeline.build(from, until, emptyList(), emptyList())

        timeline.from shouldBe at("06:00:00")
        timeline.cells shouldBe List(6) { Cell.UNKNOWN }
        timeline.hasData shouldBe false
    }

    @Test
    fun `cells combine checks and the server's hours`() {
        val timeline = WatchActivityTimeline.build(
            from,
            until,
            checks = listOf(
                check("07:15:00", 0),
                check("08:05:00", 0),
                check("08:45:00", 2),
                check("10:30:00", 0),
            ),
            hours = listOf(
                hour("06:00:00", 3, "partial"),
                hour("07:00:00", 0, "full"),
                hour("09:00:00", 0, "full"),
                hour("10:00:00", 0, "partial"),
            ),
        )

        timeline.cells shouldBe listOf(
            // A sighting counts under any coverage
            Cell.ACTIVE,
            // The server covered the whole hour, the check's zero only sampled it
            Cell.INACTIVE,
            Cell.ACTIVE,
            Cell.INACTIVE,
            // A partial zero says no more than the check did
            Cell.SAMPLED_QUIET,
            Cell.UNKNOWN,
        )
    }

    @Test
    fun `a check's sighting is not hidden by the server's zero`() {
        val timeline = WatchActivityTimeline.build(
            from,
            until,
            checks = listOf(check("09:00:30", 1)),
            hours = listOf(hour("09:00:00", 0, "full")),
        )

        timeline.cells[3] shouldBe Cell.ACTIVE
    }

    @Test
    fun `counts take the most aircraft either source reported`() {
        val timeline = WatchActivityTimeline.build(
            from,
            until,
            checks = listOf(
                check("06:30:00", 2),
                check("07:10:00", 3),
                check("07:40:00", 1),
                check("08:30:00", 1),
            ),
            hours = listOf(
                hour("06:00:00", 5, "full"),
                // Still filling up, it knows fewer than the check saw
                hour("07:00:00", 1, "partial"),
                hour("08:00:00", 0, "full"),
                hour("09:00:00", 0, "full"),
            ),
        )

        timeline.counts shouldBe listOf(5, 3, 1, 0, 0, 0)
        // Active cells always carry a count, the server's zero does not hide the check's sighting
        timeline.cells.zip(timeline.counts).forEach { (cell, count) -> (cell == Cell.ACTIVE) shouldBe (count > 0) }
    }

    @Test
    fun `a single check is enough to show`() {
        val timeline = WatchActivityTimeline.build(from, until, listOf(check("11:05:00", 0)), emptyList())

        timeline.hasData shouldBe true
        timeline.cells.last() shouldBe Cell.SAMPLED_QUIET
    }

    @Test
    fun `a check before the window does not land in its first hour`() {
        val timeline = WatchActivityTimeline.build(from, until, listOf(check("05:40:00", 1)), emptyList())

        timeline.cells.first() shouldBe Cell.UNKNOWN
    }

    @Test
    fun `a full zero of any hour source is inactive, a partial or uncovered zero is not`() {
        val timeline = buildWithRollups(
            checks = listOf(check("08:20:00", 0)),
            hours = listOf(hour("07:00:00", 0, "partial")),
            rollups = listOf(
                rollup("06:00:00", 0, "full"),
                rollup("07:00:00", 0, "full"),
                rollup("08:00:00", 0, "partial"),
                rollup("09:00:00", 0, "partial"),
                rollup("10:00:00", 0, "none"),
            ),
        )

        timeline.cells shouldBe listOf(
            Cell.INACTIVE,
            // The rollup covered what the history did not
            Cell.INACTIVE,
            // The check sampled it, the partial rollup adds nothing
            Cell.SAMPLED_QUIET,
            Cell.UNKNOWN,
            Cell.UNKNOWN,
            // No row: not monitored
            Cell.UNKNOWN,
        )
        timeline.counts shouldBe List(6) { 0 }
    }

    @Test
    fun `a positive count of any source wins and the count is the largest, never the sum`() {
        val timeline = buildWithRollups(
            checks = listOf(check("08:10:00", 2)),
            hours = listOf(hour("06:00:00", 2, "partial")),
            rollups = listOf(
                rollup("06:00:00", 3, "partial", source = "history"),
                rollup("06:00:00", 1, "partial"),
                rollup("07:00:00", 4, "partial"),
                rollup("08:00:00", 0, "full"),
                rollup("09:00:00", 1, "full", source = "history"),
                rollup("09:00:00", 0, "full"),
            ),
        )

        timeline.cells.take(4) shouldBe listOf(Cell.ACTIVE, Cell.ACTIVE, Cell.ACTIVE, Cell.ACTIVE)
        timeline.counts shouldBe listOf(3, 4, 2, 1, 0, 0)
    }

    @Test
    fun `rollups of another revision or server watch are left out`() {
        val timeline = buildWithRollups(
            rollups = listOf(
                rollup("06:00:00", 4, "full", revision = CURRENT_REVISION - 1),
                rollup("07:00:00", 0, "full", revision = CURRENT_REVISION - 1),
                rollup("08:00:00", 4, "full", serverWatchId = "server-other"),
            ),
        )

        timeline.cells shouldBe List(6) { Cell.UNKNOWN }
        timeline.counts shouldBe List(6) { 0 }
    }

    @Test
    fun `in the hour of an edit only the current revision's rows count`() {
        val timeline = buildWithRollups(
            rollups = listOf(
                rollup("07:00:00", 3, "partial", revision = CURRENT_REVISION - 1),
                rollup("07:00:00", 0, "partial"),
                rollup("08:00:00", 0, "full", revision = CURRENT_REVISION - 1),
                rollup("08:00:00", 1, "partial"),
            ),
        )

        timeline.cells.subList(1, 3) shouldBe listOf(Cell.UNKNOWN, Cell.ACTIVE)
        timeline.counts.subList(1, 3) shouldBe listOf(0, 1)
    }

    @Test
    fun `without a server watch rollups are ignored`() {
        val timeline = WatchActivityTimeline.build(
            from,
            until,
            checks = emptyList(),
            hours = listOf(hour("06:00:00", 0, "full")),
            rollups = listOf(rollup("07:00:00", 2, "full")),
            serverWatchId = null,
            serverRevision = null,
        )

        timeline.cells.take(2) shouldBe listOf(Cell.INACTIVE, Cell.UNKNOWN)
    }

    @Test
    fun `merged hours hold one row per hour with the largest count and widest coverage`() {
        val merged = WatchActivityTimeline.mergeHours(
            hours = listOf(hour("06:00:00", 2, "partial"), hour("08:00:00", 0, "none")),
            rollups = listOf(
                rollup("06:00:00", 5, "partial", source = "history"),
                rollup("06:00:00", 0, "full"),
                rollup("07:00:00", 0, "none"),
                rollup("08:00:00", 0, "partial"),
                rollup("08:00:00", 9, "full", revision = CURRENT_REVISION - 1),
            ),
            serverWatchId = SERVER_WATCH,
            serverRevision = CURRENT_REVISION,
        )

        merged.map { Triple(it.hour, it.aircraftCount, it.coverage) } shouldBe listOf(
            Triple(at("06:00:00"), 5, "full"),
            Triple(at("07:00:00"), 0, "none"),
            Triple(at("08:00:00"), 0, "partial"),
        )
    }

    companion object {
        private const val SERVER_WATCH = "server-1"
        private const val CURRENT_REVISION = 2L
    }
}
