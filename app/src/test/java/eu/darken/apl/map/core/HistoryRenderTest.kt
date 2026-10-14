package eu.darken.apl.map.core

import eu.darken.apl.server.api.TrailPoint
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class HistoryRenderTest : BaseTest() {

    private fun point(minute: Int, altitude: Double? = 35_000.0, lat: Double = minute.toDouble()) =
        TrailPoint(latitude = lat, longitude = 8.0, altitudeFeet = altitude, observedAt = minute * 60_000L)

    @Test
    fun `a gap longer than the server's limit breaks the line`() {
        val render = HistoryRender.build(
            points = listOf(point(0), point(1), point(2), point(10), point(11)),
            maxGapMs = 300_000L,
        )

        render.lines.map { it.latitudes } shouldBe listOf(listOf(0.0, 1.0, 2.0), listOf(10.0, 11.0))
        render.dots shouldBe emptyList()
    }

    @Test
    fun `a sighting without neighbours is a dot`() {
        val render = HistoryRender.build(
            points = listOf(point(0), point(20), point(40), point(41)),
            maxGapMs = 300_000L,
        )

        render.dots.map { it.latitude } shouldBe listOf(0.0, 20.0)
        render.lines.single().latitudes shouldBe listOf(40.0, 41.0)
    }

    @Test
    fun `lines split where the altitude band changes and stay joined`() {
        // Each edge takes the altitude of the point it leads to, like the live trail
        val render = HistoryRender.build(
            points = listOf(
                point(0, 500.0),
                point(1, 1_500.0),
                point(2, 1_900.0),
                point(3, 2_500.0),
                point(4, 3_900.0),
            ),
            maxGapMs = 300_000L,
        )

        render.lines.map { it.latitudes to it.altitudeFeet } shouldBe listOf(
            listOf(0.0, 1.0, 2.0) to 1_000.0,
            listOf(2.0, 3.0, 4.0) to 3_000.0,
        )
    }

    @Test
    fun `an unknown altitude is its own band`() {
        val render = HistoryRender.build(
            points = listOf(point(0), point(1, altitude = null), point(2, altitude = null), point(3)),
            maxGapMs = 300_000L,
        )

        render.lines.map { it.altitudeFeet } shouldBe listOf(null, 35_000.0)
        render.lines.first().latitudes shouldBe listOf(0.0, 1.0, 2.0)
    }

    @Test
    fun `nothing after the live trail's start is drawn`() {
        val render = HistoryRender.build(
            points = listOf(point(0), point(1), point(2), point(3)),
            maxGapMs = 300_000L,
            until = point(2),
        )

        render.lines.single().latitudes shouldBe listOf(0.0, 1.0, 2.0)
    }

    @Test
    fun `the history ends where the live trail starts, also between its own points`() {
        val liveStart = TrailPoint(latitude = 2.5, longitude = 8.0, altitudeFeet = 35_000.0, observedAt = 150_000L)

        val render = HistoryRender.build(
            points = listOf(point(0), point(1), point(2), point(3)),
            maxGapMs = 300_000L,
            until = liveStart,
        )

        render.lines.single().latitudes shouldBe listOf(0.0, 1.0, 2.0, 2.5)
    }

    @Test
    fun `a live trail starting after a long gap is not joined`() {
        val render = HistoryRender.build(
            points = listOf(point(0), point(1), point(20)),
            maxGapMs = 300_000L,
            until = point(10),
        )

        render.lines.single().latitudes shouldBe listOf(0.0, 1.0)
        render.dots shouldBe emptyList()
    }

    @Test
    fun `no points draw nothing`() {
        HistoryRender.build(emptyList(), maxGapMs = 300_000L) shouldBe HistoryRender(emptyList(), emptyList())
    }
}
