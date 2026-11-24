package eu.darken.apl.watch.ui.chart

import eu.darken.apl.common.chart.ChartPoint
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class WatchDailyPeaksTest : BaseTest() {

    private val berlin = ZoneId.of("Europe/Berlin")

    private fun local(text: String): Instant = LocalDateTime.parse(text).atZone(berlin).toInstant()

    private fun point(at: Instant, value: Double) = ChartPoint(at, value)

    private fun point(local: String, value: Double) = ChartPoint(local(local), value)

    private fun build(counts: List<ChartPoint>, now: Instant) = WatchDailyPeaks.build(counts, now, berlin)

    private fun WatchDailyPeaks.peakOn(date: String): Double? =
        days.single { it.date == LocalDate.parse(date) }.peak

    @Test
    fun `window covers exactly 30 local dates ending today`() {
        val result = build(emptyList(), local("2026-01-15T13:00"))

        result.days.size shouldBe 30
        result.days.first().date shouldBe LocalDate.parse("2025-12-17")
        result.days.last().date shouldBe LocalDate.parse("2026-01-15")
        result.todayIndex shouldBe 29
        result.hasValues shouldBe false
        result.busiestHour shouldBe null
    }

    @Test
    fun `spring DST day groups its 23 hours`() {
        val dayStart = Instant.parse("2026-03-28T23:00:00Z")
        val hours = (0 until 23).map { point(dayStart.plus(Duration.ofHours(it.toLong())), it.toDouble()) }
        hours.first().timestamp shouldBe local("2026-03-29T00:00")
        hours.last().timestamp shouldBe local("2026-03-29T23:00")
        val counts = listOf(point(Instant.parse("2026-03-28T22:59:00Z"), 50.0)) +
            hours +
            point(Instant.parse("2026-03-29T22:00:00Z"), 99.0)

        val result = build(counts, Instant.parse("2026-03-30T10:00:00Z"))

        result.peakOn("2026-03-28") shouldBe 50.0
        result.peakOn("2026-03-29") shouldBe 22.0
        result.peakOn("2026-03-30") shouldBe 99.0
    }

    @Test
    fun `autumn DST day groups its 25 hours`() {
        val dayStart = Instant.parse("2026-10-24T22:00:00Z")
        val hours = (0 until 25).map { point(dayStart.plus(Duration.ofHours(it.toLong())), it.toDouble()) }
        hours.first().timestamp shouldBe local("2026-10-25T00:00")
        hours.last().timestamp shouldBe local("2026-10-25T23:00")
        val counts = listOf(point(Instant.parse("2026-10-24T21:00:00Z"), 77.0)) +
            hours +
            point(Instant.parse("2026-10-25T23:00:00Z"), 99.0)

        val result = build(counts, Instant.parse("2026-10-26T12:00:00Z"))

        result.peakOn("2026-10-24") shouldBe 77.0
        result.peakOn("2026-10-25") shouldBe 24.0
        result.peakOn("2026-10-26") shouldBe 99.0
    }

    @Test
    fun `a day without points is null and a partial today has its peak so far`() {
        val counts = listOf(
            point("2026-01-13T08:00", 4.0),
            point("2026-01-15T09:00", 3.0),
            point("2026-01-15T13:00", 6.0),
        )

        val result = build(counts, local("2026-01-15T13:30"))

        result.peakOn("2026-01-13") shouldBe 4.0
        result.peakOn("2026-01-14") shouldBe null
        result.peakOn("2026-01-15") shouldBe 6.0
    }

    @Test
    fun `today without points is null`() {
        val counts = listOf(
            point("2026-01-14T22:00", 2.0),
            point("2026-01-14T23:00", 5.0),
        )

        val result = build(counts, local("2026-01-15T00:30"))

        result.days.last().date shouldBe LocalDate.parse("2026-01-15")
        result.days.last().peak shouldBe null
        result.peakOn("2026-01-14") shouldBe 5.0
    }

    @Test
    fun `an all-zero day between missing days is zero, not missing`() {
        val counts = listOf(
            point("2026-01-11T10:00", 3.0),
            point("2026-01-13T10:00", 0.0),
            point("2026-01-13T11:00", 0.0),
            point("2026-01-15T10:00", 1.0),
        )

        val result = build(counts, local("2026-01-15T12:00"))

        result.peakOn("2026-01-12") shouldBe null
        result.peakOn("2026-01-13") shouldBe 0.0
        result.peakOn("2026-01-14") shouldBe null
    }

    @Test
    fun `points on only one day leave all other days empty`() {
        val busiest = point("2026-01-10T12:00", 5.0)
        val counts = listOf(point("2026-01-10T11:00", 1.0), busiest, point("2026-01-10T13:00", 2.0))

        val result = build(counts, local("2026-01-15T12:00"))

        result.days.filter { it.peak != null }.map { it.date } shouldBe listOf(LocalDate.parse("2026-01-10"))
        result.peakOn("2026-01-10") shouldBe 5.0
        result.busiestHour shouldBe busiest
    }

    @Test
    fun `points only outside the window give no days and no busiest hour`() {
        val counts = listOf(
            point("2025-12-16T12:00", 9.0),
            point("2025-12-16T23:00", 4.0),
            point("2026-01-16T01:00", 7.0),
        )

        val result = build(counts, local("2026-01-15T12:00"))

        result.days.all { it.peak == null } shouldBe true
        result.hasValues shouldBe false
        result.busiestHour shouldBe null
    }

    @Test
    fun `a busier hour on the dropped 31st date is ignored`() {
        val inside = point("2025-12-17T00:00", 10.0)
        val counts = listOf(point("2025-12-16T23:00", 500.0), inside)

        val result = build(counts, local("2026-01-15T12:00"))

        result.busiestHour shouldBe inside
        result.peakOn("2025-12-17") shouldBe 10.0
    }

    @Test
    fun `equal maxima pick the most recent hour`() {
        val latest = point("2026-01-12T18:00", 8.0)
        val counts = listOf(
            latest,
            point("2026-01-05T10:00", 8.0),
            point("2026-01-12T09:00", 8.0),
            point("2026-01-14T10:00", 2.0),
        )

        val result = build(counts, local("2026-01-15T12:00"))

        result.busiestHour shouldBe latest
    }

    @Test
    fun `passing local midnight shifts the window by one date`() {
        val oldest = point("2025-12-17T10:00", 7.0)
        val recent = point("2026-01-15T20:00", 5.0)
        val counts = listOf(oldest, recent)

        val before = build(counts, local("2026-01-15T23:30"))
        val after = build(counts, local("2026-01-16T00:30"))

        before.days.first() shouldBe WatchDailyPeaks.Day(LocalDate.parse("2025-12-17"), 7.0)
        before.busiestHour shouldBe oldest

        after.days.first().date shouldBe LocalDate.parse("2025-12-18")
        after.days.last() shouldBe WatchDailyPeaks.Day(LocalDate.parse("2026-01-16"), null)
        after.days.dropLast(1) shouldBe before.days.drop(1)
        after.peakOn("2026-01-15") shouldBe 5.0
        after.busiestHour shouldBe recent
    }
}
