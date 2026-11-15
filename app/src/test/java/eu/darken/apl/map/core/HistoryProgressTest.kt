package eu.darken.apl.map.core

import eu.darken.apl.map.core.HistoryProgress.Update
import eu.darken.apl.server.api.TrailPoint
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.LocalDate
import java.time.ZoneId

class HistoryProgressTest : BaseTest() {

    private val zone = ZoneId.of("UTC")
    private val date = LocalDate.of(2026, 10, 14)
    private val base = date.atStartOfDay(zone).toInstant().toEpochMilli() + 10 * 3_600_000L

    // One point a minute from 10:00 to 10:05 UTC
    private val timeline = HistoryTimeline(
        AircraftHistoryTracks.Track(
            points = (0..5).map { TrailPoint(50.0 + it * 0.1, 8.0, 35_000.0, base + it * 60_000L) },
            maxGapMs = 300_000,
            from = base - 3_600_000,
            until = base + 3_600_000,
        ),
        zone,
    )
    private val day = timeline.day(date)

    @Test
    fun `the first cursor settles, moving on marks what was passed, standing still does nothing`() {
        val progress = HistoryProgress()

        progress.next(day, base + 30_000, settle = false) shouldBe Update.Settle(1)
        progress.next(day, base + 30_000, settle = false) shouldBe Update.None
        progress.next(day, base + 90_000, settle = false) shouldBe Update.Mark(1, day.passed(base + 30_000, base + 90_000))
        progress.next(day, base + 150_000, settle = false) shouldBe Update.Mark(1, day.passed(base + 90_000, base + 150_000))
    }

    @Test
    fun `going back, settling elsewhere or another day starts a new epoch`() {
        val progress = HistoryProgress()
        progress.next(day, base + 30_000, settle = true) shouldBe Update.Settle(1)
        progress.next(day, base + 150_000, settle = false).shouldBeInstanceOf<Update.Mark>()

        // A seek back
        progress.next(day, base + 60_000, settle = true) shouldBe Update.Settle(2)
        progress.next(day, base + 120_000, settle = false).shouldBeInstanceOf<Update.Mark>()
        // Paused where playback had got to: drawn already, but split there exactly
        progress.next(day, base + 120_000, settle = true) shouldBe Update.Settle(3)
        progress.next(day, base + 120_000, settle = true) shouldBe Update.None

        val nextDay = timeline.day(date.plusDays(1))
        progress.next(nextDay, nextDay.start, settle = true) shouldBe Update.Settle(4)
    }

    @Test
    fun `layers written anew settle again in a fresh epoch, so marks left on the map do not count`() {
        val progress = HistoryProgress()
        progress.next(day, base, settle = true) shouldBe Update.Settle(1)
        progress.next(day, base + 240_000, settle = false).shouldBeInstanceOf<Update.Mark>()

        // E.g. the map stopped drawing and started again over the same sources
        progress.reset()
        progress.next(day, base, settle = true) shouldBe Update.Settle(2)
    }

    @Test
    fun `without a history the layers are cleared once`() {
        val progress = HistoryProgress()
        progress.next(null, null, settle = true) shouldBe Update.Clear
        progress.next(null, null, settle = true) shouldBe Update.None

        progress.next(day, base, settle = true) shouldBe Update.Settle(1)
        progress.next(null, null, settle = true) shouldBe Update.Clear
    }
}
