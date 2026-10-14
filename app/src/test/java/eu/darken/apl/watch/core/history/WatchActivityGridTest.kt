package eu.darken.apl.watch.core.history

import eu.darken.apl.watch.core.history.WatchActivityGrid.State
import eu.darken.apl.watch.core.history.WatchActivityTimeline.Cell
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class WatchActivityGridTest : BaseTest() {

    private val utc = ZoneOffset.UTC
    private val berlin = ZoneId.of("Europe/Berlin")
    private val kolkata = ZoneId.of("Asia/Kolkata")

    /** A timeline as the repo would build it for a grid at [now], every hour [fill] unless listed in [cells]. */
    private fun timeline(
        now: Instant,
        zone: ZoneId,
        fill: Cell = Cell.INACTIVE,
        cells: Map<String, Pair<Cell, Int>> = emptyMap(),
    ): WatchActivityTimeline {
        val start = WatchHistoryPlan.hourOf(WatchActivityGrid.startOf(now, zone))
        val size = Duration.between(start, now).toHours().toInt() + 1
        val byHour = cells.mapKeys { Instant.parse(it.key) }
        val built = List(size) { index -> byHour[start.plus(Duration.ofHours(index.toLong()))] ?: (fill to 0) }
        return WatchActivityTimeline(start, built.map { it.first }, built.map { it.second })
    }

    private fun WatchActivityGrid.slot(date: String, hour: Int) =
        days.single { it.date == LocalDate.parse(date) }.slots[hour]

    @Test
    fun `seven local days with today last and later hours not yet there`() {
        val now = Instant.parse("2026-10-14T10:20:00Z")
        val grid = WatchActivityGrid.build(
            timeline(now, utc, cells = mapOf("2026-10-08T00:00:00Z" to (Cell.ACTIVE to 4))),
            utc,
            now,
        )

        grid.days.map { it.date } shouldBe (8..14).map { LocalDate.of(2026, 10, it) }
        grid.days.forEach { it.slots.size shouldBe 24 }
        grid.slot("2026-10-08", 0) shouldBe WatchActivityGrid.Slot(State.ACTIVE, 4)
        grid.slot("2026-10-14", 10).state shouldBe State.INACTIVE
        grid.slot("2026-10-14", 11).state shouldBe State.NOT_YET
        grid.slot("2026-10-14", 23).state shouldBe State.NOT_YET
        grid.maxCount shouldBe 4
        grid.hasData shouldBe true
    }

    @Test
    fun `nothing known is no data`() {
        val now = Instant.parse("2026-10-14T10:20:00Z")
        val grid = WatchActivityGrid.build(timeline(now, utc, fill = Cell.UNKNOWN), utc, now)

        grid.hasData shouldBe false
        grid.maxCount shouldBe 0
        grid.slot("2026-10-14", 9).state shouldBe State.UNKNOWN
    }

    @Test
    fun `half hour zones place an hour where it starts, also before midnight`() {
        val now = Instant.parse("2026-10-14T10:20:00Z") // 15:50 in Kolkata
        val grid = WatchActivityGrid.build(
            timeline(now, kolkata, cells = mapOf("2026-10-12T18:00:00Z" to (Cell.ACTIVE to 1))),
            kolkata,
            now,
        )

        // 18:00Z is 23:30 local, a sighting at 18:45Z (00:15 local) lands the evening before
        grid.slot("2026-10-12", 23).state shouldBe State.ACTIVE
        grid.slot("2026-10-13", 0).state shouldBe State.INACTIVE
        grid.slot("2026-10-14", 15).state shouldBe State.INACTIVE
        grid.slot("2026-10-14", 16).state shouldBe State.NOT_YET
    }

    @Test
    fun `the hour skipped when clocks go forward stays unknown`() {
        val now = Instant.parse("2026-03-29T10:00:00Z")
        val grid = WatchActivityGrid.build(timeline(now, berlin, fill = Cell.ACTIVE), berlin, now)

        grid.slot("2026-03-29", 1).state shouldBe State.ACTIVE
        grid.slot("2026-03-29", 2).state shouldBe State.UNKNOWN
        grid.slot("2026-03-29", 3).state shouldBe State.ACTIVE
    }

    @Test
    fun `the hour repeated when clocks go back keeps the stronger evidence`() {
        val now = Instant.parse("2026-10-25T12:00:00Z")
        val grid = WatchActivityGrid.build(
            timeline(
                now,
                berlin,
                cells = mapOf(
                    "2026-10-25T00:00:00Z" to (Cell.ACTIVE to 2), // 02:00 summer time
                    "2026-10-25T01:00:00Z" to (Cell.INACTIVE to 0), // 02:00 winter time
                ),
            ),
            berlin,
            now,
        )

        grid.slot("2026-10-25", 2) shouldBe WatchActivityGrid.Slot(State.ACTIVE, 2)
        grid.slot("2026-10-25", 3).state shouldBe State.INACTIVE
    }

    @Test
    fun `a repeated hour already seen is not waiting for its second run`() {
        val now = Instant.parse("2026-10-25T00:30:00Z") // 02:30, the first time
        val grid = WatchActivityGrid.build(
            timeline(now, berlin, cells = mapOf("2026-10-25T00:00:00Z" to (Cell.ACTIVE to 1))),
            berlin,
            now,
        )

        grid.slot("2026-10-25", 2).state shouldBe State.ACTIVE
        grid.slot("2026-10-25", 3).state shouldBe State.NOT_YET
    }

    @Test
    fun `a new local day drops the oldest one`() {
        val before = Instant.parse("2026-10-14T23:59:00Z")
        val after = Instant.parse("2026-10-15T00:00:30Z")
        val data = timeline(after, utc, cells = mapOf("2026-10-09T05:00:00Z" to (Cell.ACTIVE to 1)))

        WatchActivityGrid.startOf(before, utc) shouldBe Instant.parse("2026-10-08T00:00:00Z")
        WatchActivityGrid.startOf(after, utc) shouldBe Instant.parse("2026-10-09T00:00:00Z")

        val grid = WatchActivityGrid.build(data, utc, after)
        grid.days.first().date shouldBe LocalDate.of(2026, 10, 9)
        grid.slot("2026-10-09", 5).state shouldBe State.ACTIVE
        grid.slot("2026-10-15", 0).state shouldBe State.INACTIVE
        grid.slot("2026-10-15", 1).state shouldBe State.NOT_YET
    }

    @Test
    fun `refreshes at the next UTC or local hour, whichever comes first`() {
        WatchActivityGrid.nextRefresh(Instant.parse("2026-10-14T10:20:00Z"), utc) shouldBe
            Instant.parse("2026-10-14T11:00:00Z")
        WatchActivityGrid.nextRefresh(Instant.parse("2026-10-14T10:20:00Z"), kolkata) shouldBe
            Instant.parse("2026-10-14T10:30:00Z")
        WatchActivityGrid.nextRefresh(Instant.parse("2026-10-14T10:40:00Z"), kolkata) shouldBe
            Instant.parse("2026-10-14T11:00:00Z")
    }

    @Test
    fun `refresh stays ahead across a half hour clock change`() {
        val lordHowe = ZoneId.of("Australia/Lord_Howe")
        // Clocks there go from 02:00 to 02:30 on 2026-10-04, which is 15:30Z the day before
        var now = Instant.parse("2026-10-03T13:10:00Z")
        repeat(6) {
            val next = WatchActivityGrid.nextRefresh(now, lordHowe)
            (next > now) shouldBe true
            (next <= now.plus(Duration.ofHours(1))) shouldBe true
            now = next
        }
    }
}
