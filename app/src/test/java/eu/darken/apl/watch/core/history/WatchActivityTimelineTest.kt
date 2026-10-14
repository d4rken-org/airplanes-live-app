package eu.darken.apl.watch.core.history

import eu.darken.apl.watch.core.db.history.WatchHistoryHourEntity
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
}
