package eu.darken.apl.map.core

import eu.darken.apl.server.api.Allowance
import eu.darken.apl.server.api.HistoryAircraftResponse
import eu.darken.apl.server.api.TrailPoint
import eu.darken.apl.server.api.UsageUpdate
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class AircraftHistoryTracksTest : BaseTest() {

    private fun points(vararg minutes: Int) = minutes.map { TrailPoint(50.0, 8.0, 35_000.0, it * 60_000L) }

    private fun response(points: List<TrailPoint>) = HistoryAircraftResponse(
        serverTime = 3_600_000,
        aircraftId = "a",
        from = 0,
        until = 3_600_000,
        maxGapMs = 300_000,
        points = points,
        category = "A3",
        usage = UsageUpdate("principal", "VIEWING", 0, Allowance(1, 0, 0, 1)),
    )

    @Test
    fun `one load per aircraft at a time`() {
        val tracks = AircraftHistoryTracks()

        tracks.begin("3C65A3").shouldNotBeNull()
        tracks.begin("3C65A3").shouldBeNull()
        tracks.begin("4B1805").shouldNotBeNull()
    }

    @Test
    fun `only the newest request counts, a failed or finished one frees the aircraft`() {
        val tracks = AircraftHistoryTracks()
        val first = tracks.begin("3C65A3")!!
        tracks.fail("3C65A3", first)
        val second = tracks.begin("3C65A3")!!

        tracks.complete("3C65A3", first) shouldBe false
        tracks.complete("3C65A3", second) shouldBe true
        tracks.begin("3C65A3").shouldNotBeNull()
    }

    @Test
    fun `only the newest points are kept, in order, and where the dropped ones end is known`() {
        val track = AircraftHistoryTracks(maxPoints = 3).prepare(response(points(5, 1, 4, 2, 3)))

        track.points.map { it.observedAt / 60_000L } shouldBe listOf(3L, 4L, 5L)
        track.omittedUntil shouldBe 3 * 60_000L
        track.category shouldBe "A3"
        AircraftHistoryTracks().prepare(response(points(1, 2))).omittedUntil shouldBe null
    }
}
