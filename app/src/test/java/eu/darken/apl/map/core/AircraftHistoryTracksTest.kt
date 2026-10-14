package eu.darken.apl.map.core

import eu.darken.apl.server.api.TrailPoint
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class AircraftHistoryTracksTest : BaseTest() {

    private fun points(vararg minutes: Int) = minutes.map { TrailPoint(50.0, 8.0, 35_000.0, it * 60_000L) }

    @Test
    fun `one load per aircraft at a time`() {
        val tracks = AircraftHistoryTracks()

        tracks.begin("3C65A3").shouldNotBeNull()
        tracks.begin("3C65A3").shouldBeNull()
        tracks.begin("4B1805").shouldNotBeNull()
        tracks.status("3C65A3") shouldBe AircraftHistoryTracks.Status(loading = true, loaded = false)
    }

    @Test
    fun `only the newest request is applied`() {
        val tracks = AircraftHistoryTracks()
        val first = tracks.begin("3C65A3")!!
        tracks.fail("3C65A3", first)
        val second = tracks.begin("3C65A3")!!

        tracks.complete("3C65A3", first, points(1), 300_000L) shouldBe false
        tracks.complete("3C65A3", second, points(2), 300_000L) shouldBe true
        tracks.track("3C65A3")!!.points.single().observedAt shouldBe 2 * 60_000L
    }

    @Test
    fun `a failed reload keeps what was shown`() {
        val tracks = AircraftHistoryTracks()
        val first = tracks.begin("3C65A3")!!
        tracks.complete("3C65A3", first, points(1, 2), 300_000L)
        val reload = tracks.begin("3C65A3")!!

        tracks.fail("3C65A3", reload)

        tracks.track("3C65A3")!!.points.size shouldBe 2
        tracks.status("3C65A3") shouldBe AircraftHistoryTracks.Status(loading = false, loaded = true)
    }

    @Test
    fun `the aircraft used longest ago is dropped first`() {
        val tracks = AircraftHistoryTracks(maxAircraft = 2)
        listOf("A", "B").forEach { hex -> tracks.complete(hex, tracks.begin(hex)!!, points(1), 300_000L) }
        tracks.touch("A")

        tracks.complete("C", tracks.begin("C")!!, points(1), 300_000L)

        tracks.track("A").shouldNotBeNull()
        tracks.track("B").shouldBeNull()
        tracks.track("C").shouldNotBeNull()
    }

    @Test
    fun `dropping a result does not forget a load in flight`() {
        val tracks = AircraftHistoryTracks(maxAircraft = 1)
        tracks.complete("A", tracks.begin("A")!!, points(1), 300_000L)
        val reload = tracks.begin("A")!!
        tracks.complete("B", tracks.begin("B")!!, points(1), 300_000L)

        tracks.track("A").shouldBeNull()
        tracks.begin("A").shouldBeNull()
        tracks.complete("A", reload, points(3), 300_000L) shouldBe true
        tracks.track("A").shouldNotBeNull()
    }

    @Test
    fun `only the newest points are kept, in order`() {
        val tracks = AircraftHistoryTracks(maxPoints = 3)

        tracks.complete("A", tracks.begin("A")!!, tracks.prepare(points(5, 1, 4, 2, 3)), 300_000L)

        tracks.track("A")!!.points.map { it.observedAt / 60_000L } shouldBe listOf(3L, 4L, 5L)
    }

    @Test
    fun `the aircraft on screen is never dropped to make room`() {
        val tracks = AircraftHistoryTracks(maxAircraft = 2)
        tracks.complete("A", tracks.begin("A")!!, points(1), 300_000L)

        listOf("B", "C", "D").forEach { hex -> tracks.complete(hex, tracks.begin(hex)!!, points(1), 300_000L, keep = "A") }

        tracks.track("A").shouldNotBeNull()
        tracks.track("D").shouldNotBeNull()
        tracks.track("B").shouldBeNull()
        tracks.track("C").shouldBeNull()
    }
}
