package eu.darken.apl.map.core

import eu.darken.apl.server.api.HistoryHourStatus
import eu.darken.apl.server.api.HistoryRunSpan
import eu.darken.apl.server.api.TrailPoint
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class HistoryTimelineTest : BaseTest() {

    private val berlin = ZoneId.of("Europe/Berlin")
    private val day = LocalDate.of(2026, 10, 14)

    private fun at(hour: Int, minute: Int, date: LocalDate = day, zone: ZoneId = berlin): Long =
        LocalDateTime.of(date, java.time.LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()

    private fun point(time: Long, lat: Double = 50.0, lon: Double = 8.0, altitude: Double? = 35_000.0) =
        TrailPoint(latitude = lat, longitude = lon, altitudeFeet = altitude, observedAt = time)

    private fun timeline(
        points: List<TrailPoint>,
        from: Long = at(0, 0, day.minusDays(1)),
        until: Long = at(0, 0, day.plusDays(2)),
        runs: List<HistoryRunSpan> = emptyList(),
        hours: List<HistoryHourStatus> = emptyList(),
        omittedUntil: Long? = null,
        zone: ZoneId = berlin,
    ) = HistoryTimeline(
        AircraftHistoryTracks.Track(points, 300_000L, from, until, omittedUntil, runs, hours),
        zone,
    )

    @Test
    fun `an edge across midnight is cut there, into both days`() {
        val timeline = timeline(listOf(point(at(23, 58), lat = 50.0), point(at(0, 2, day.plusDays(1)), lat = 50.4)))

        val first = timeline.day(day).edges.single()
        first.end shouldBe at(0, 0, day.plusDays(1))
        first.endPoint.latitude shouldBe (50.2 plusOrMinus 1e-9)
        val second = timeline.day(day.plusDays(1)).edges.single()
        second.start shouldBe at(0, 0, day.plusDays(1))
        second.startPoint.latitude shouldBe (50.2 plusOrMinus 1e-9)
    }

    @Test
    fun `the day the clocks go back has 25 hours, labelled apart by offset`() {
        val fallBack = LocalDate.of(2026, 10, 25)
        val timeline = timeline(listOf(point(at(12, 0, fallBack))), from = at(0, 0, fallBack), until = at(0, 0, fallBack.plusDays(1)))

        val d = timeline.day(fallBack)
        (d.end - d.start) shouldBe 25 * 3_600_000L
    }

    @Test
    fun `gaps longer than the server's limit split stretches, a point alone is a sighting`() {
        val timeline = timeline(
            listOf(at(10, 0), at(10, 1), at(10, 2), at(10, 30), at(11, 0), at(11, 1)).map { point(it) }
        )

        val d = timeline.day(day)
        d.stretches.map { it.start to it.end } shouldBe listOf(at(10, 0) to at(10, 2), at(11, 0) to at(11, 1))
        d.sightings.map { it.observedAt } shouldBe listOf(at(10, 30))
    }

    @Test
    fun `positions are interpolated on an edge, shown briefly around a sighting, absent in a gap`() {
        val timeline = timeline(
            listOf(point(at(10, 0), lat = 50.0, altitude = 30_000.0), point(at(10, 2), lat = 50.2, altitude = 32_000.0), point(at(10, 30)))
        )

        val mid = timeline.positionAt(at(10, 1))!!
        mid.point.latitude shouldBe (50.1 plusOrMinus 1e-9)
        mid.point.altitudeFeet shouldBe (31_000.0 plusOrMinus 1e-6)
        mid.trackDegrees!!.toDouble() shouldBe (0.0 plusOrMinus 0.01)
        // 0.2 degrees of latitude in two minutes
        mid.speedKnots shouldBe 360

        timeline.positionAt(at(10, 15)) shouldBe null
        timeline.positionAt(at(10, 30) + 30_000)!!.trackDegrees shouldBe null
    }

    @Test
    fun `across the antimeridian the short way is taken`() {
        val timeline = timeline(listOf(point(at(10, 0), lon = 179.5), point(at(10, 2), lon = -179.5)))

        val mid = timeline.positionAt(at(10, 1))!!.point.longitude
        kotlin.math.abs(mid) shouldBe (180.0 plusOrMinus 1e-9)
    }

    @Test
    fun `only the requested range counts, the neighbours outside it just shape the edges`() {
        val from = at(10, 1)
        val timeline = timeline(listOf(point(at(10, 0), lat = 50.0), point(at(10, 2), lat = 50.2), point(at(9, 0) - 1)), from = from)

        val d = timeline.day(day)
        d.edges.single().start shouldBe from
        d.edges.single().startPoint.latitude shouldBe (50.1 plusOrMinus 1e-9)
        d.sightings shouldBe emptyList()
    }

    @Test
    fun `stretches carry the callsign they mostly had`() {
        val timeline = timeline(
            listOf(at(10, 0), at(10, 4), at(10, 8), at(10, 12)).map { point(it) },
            runs = listOf(
                HistoryRunSpan("callsign", "DLH1", at(10, 0), at(10, 3)),
                HistoryRunSpan("callsign", "DLH2", at(10, 3), at(10, 12)),
                HistoryRunSpan("squawk", "1000", at(10, 0), at(10, 12)),
            ),
        )

        timeline.day(day).stretches.single().callsign shouldBe "DLH2"
        timeline.callsignAt(at(10, 1)) shouldBe "DLH1"
        timeline.callsignAt(at(11, 0)) shouldBe null
    }

    @Test
    fun `what could not be seen is told apart`() {
        val from = at(6, 0)
        val until = at(18, 0)
        val timeline = timeline(
            listOf(point(at(12, 0))),
            from = from,
            until = until,
            hours = listOf(
                HistoryHourStatus(at(8, 0), HistoryHourStatus.COVERAGE_NONE),
                HistoryHourStatus(at(9, 0), HistoryHourStatus.COVERAGE_PARTIAL),
                HistoryHourStatus(at(10, 0), HistoryHourStatus.COVERAGE_FULL),
            ),
            omittedUntil = at(7, 0),
        )

        timeline.day(day).spans.map { Triple(it.kind, it.start, it.end) } shouldBe listOf(
            Triple(HistoryTimeline.SpanKind.PARTIAL, at(9, 0), at(10, 0)),
            Triple(HistoryTimeline.SpanKind.NOT_RECORDED, at(8, 0), at(9, 0)),
            Triple(HistoryTimeline.SpanKind.OMITTED, from, at(7, 0)),
            Triple(HistoryTimeline.SpanKind.OUTSIDE, at(0, 0), from),
            Triple(HistoryTimeline.SpanKind.OUTSIDE, until, at(0, 0, day.plusDays(1))),
        )
    }

    @Test
    fun `server hours land on the local clock of a half-hour zone`() {
        val kolkata = ZoneId.of("Asia/Kolkata")
        val utcHour = LocalDateTime.of(day, java.time.LocalTime.of(0, 0)).atZone(ZoneId.of("UTC")).toInstant().toEpochMilli()
        val timeline = timeline(
            listOf(point(utcHour + 7_200_000)),
            from = at(0, 0, zone = kolkata),
            until = at(0, 0, day.plusDays(1), zone = kolkata),
            hours = listOf(HistoryHourStatus(utcHour, HistoryHourStatus.COVERAGE_NONE)),
            zone = kolkata,
        )

        val gap = timeline.day(day).spans.single { it.kind == HistoryTimeline.SpanKind.NOT_RECORDED }
        gap.start shouldBe at(5, 30, zone = kolkata)
        gap.end shouldBe at(6, 30, zone = kolkata)
    }

    @Test
    fun `playback jumps gaps, rests on sightings and stops where the day's positions end`() {
        val timeline = timeline(
            listOf(at(10, 0), at(10, 1), at(10, 30), at(11, 0), at(11, 1)).map { point(it) }
        )
        val d = timeline.day(day)

        timeline.advance(d, at(10, 0), 30_000) shouldBe HistoryTimeline.Step(at(10, 0) + 30_000, playing = true)
        // A large step does not skip the sighting in the gap
        timeline.advance(d, at(10, 0), 3_600_000) shouldBe
                HistoryTimeline.Step(at(10, 30), playing = true, holdMs = HistoryTimeline.SIGHTING_HOLD_MS)
        // Gaps take no time, the rest of the step goes on after them
        timeline.advance(d, at(10, 30), 30_000) shouldBe HistoryTimeline.Step(at(11, 0) + 30_000, playing = true)
        timeline.advance(d, at(11, 0), 3_600_000) shouldBe HistoryTimeline.Step(at(11, 1), playing = false)

        timeline.playFrom(d, at(11, 1)) shouldBe at(10, 0)
        timeline.playFrom(d, at(10, 15)) shouldBe at(10, 15)
    }

    @Test
    fun `playback into the next day stops before its midnight and plays again from the day's start`() {
        val timeline = timeline(
            listOf(point(at(22, 0)), point(at(22, 1)), point(at(23, 58)), point(at(0, 2, day.plusDays(1))))
        )
        val d = timeline.day(day)

        val end = timeline.advance(d, at(23, 58), 3_600_000)
        end shouldBe HistoryTimeline.Step(d.end - 1, playing = false)
        timeline.localDate(end.cursor) shouldBe day
        timeline.playFrom(d, end.cursor) shouldBe at(22, 0)
    }

    @Test
    fun `days span the range in local time, the newest positions open it`() {
        val timeline = timeline(
            listOf(point(at(10, 0)), point(at(10, 1)), point(at(9, 0, day.plusDays(1))), point(at(9, 1, day.plusDays(1)))),
            from = at(12, 0, day.minusDays(1)),
            until = at(12, 0, day.plusDays(1)),
        )

        timeline.days shouldBe listOf(day.minusDays(1), day, day.plusDays(1))
        timeline.latestStart() shouldBe at(9, 0, day.plusDays(1))
        timeline(emptyList()).latestStart() shouldBe null
    }
}
