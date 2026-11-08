package eu.darken.apl.watch.core.history

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

    private fun build(rollups: List<WatchServerRollupEntity>) =
        WatchActivityTimeline.build(from, until, rollups, SERVER_WATCH, CURRENT_REVISION)

    @Test
    fun `one cell per hour from the start hour through the current one`() {
        val timeline = build(emptyList())

        timeline.from shouldBe at("06:00:00")
        timeline.cells shouldBe List(6) { Cell.UNKNOWN }
        timeline.hasData shouldBe false
    }

    @Test
    fun `a full zero is inactive, a partial or uncovered zero is not`() {
        val timeline = build(
            listOf(
                rollup("06:00:00", 0, "full"),
                rollup("07:00:00", 0, "partial"),
                rollup("07:00:00", 0, "full", source = "history"),
                rollup("08:00:00", 0, "partial"),
                rollup("09:00:00", 0, "none"),
            ),
        )

        timeline.cells shouldBe listOf(
            Cell.INACTIVE,
            // One source covered what the other did not
            Cell.INACTIVE,
            Cell.UNKNOWN,
            Cell.UNKNOWN,
            // No row: not monitored
            Cell.UNKNOWN,
            Cell.UNKNOWN,
        )
        timeline.counts shouldBe List(6) { 0 }
    }

    @Test
    fun `a positive count of any source wins and the count is the largest, never the sum`() {
        val timeline = build(
            listOf(
                rollup("06:00:00", 3, "partial", source = "history"),
                rollup("06:00:00", 1, "partial"),
                rollup("07:00:00", 4, "partial"),
                rollup("08:00:00", 0, "full"),
                rollup("09:00:00", 1, "full", source = "history"),
                rollup("09:00:00", 0, "full"),
            ),
        )

        timeline.cells.take(4) shouldBe listOf(Cell.ACTIVE, Cell.ACTIVE, Cell.INACTIVE, Cell.ACTIVE)
        timeline.counts shouldBe listOf(3, 4, 0, 1, 0, 0)
        // Active cells always carry a count
        timeline.cells.zip(timeline.counts).forEach { (cell, count) -> (cell == Cell.ACTIVE) shouldBe (count > 0) }
    }

    @Test
    fun `a rollup hour before the window does not land in its first cell`() {
        val timeline = build(listOf(rollup("05:00:00", 1, "full")))

        timeline.cells.first() shouldBe Cell.UNKNOWN
    }

    @Test
    fun `rollups of another revision or server watch are left out`() {
        val timeline = build(
            listOf(
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
        val timeline = build(
            listOf(
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
            rollups = listOf(rollup("07:00:00", 2, "full")),
            serverWatchId = null,
            serverRevision = null,
        )

        timeline.cells shouldBe List(6) { Cell.UNKNOWN }
    }

    @Test
    fun `merged hours hold one row per hour with the largest count and widest coverage`() {
        val merged = WatchActivityTimeline.mergeHours(
            rollups = listOf(
                rollup("06:00:00", 2, "full", source = "history"),
                rollup("06:00:00", 5, "partial"),
                rollup("07:00:00", 0, "none"),
                rollup("08:00:00", 0, "partial"),
                rollup("08:00:00", 0, "none", source = "history"),
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
