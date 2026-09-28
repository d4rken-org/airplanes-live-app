package eu.darken.apl.map.core

import eu.darken.apl.ar.core.ScreenProjection
import eu.darken.apl.server.api.AircraftPosition
import eu.darken.apl.server.api.MapAircraft
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class MapTrafficTest : BaseTest() {

    private fun aircraft(
        id: String = "3c65a3",
        observedAt: Long? = NOW - 5_000,
        onGround: Boolean? = false,
        track: Double? = 90.0,
        speed: Double? = 450.0,
    ) = MapAircraft(
        id = id,
        position = AircraftPosition(50.0, 8.0, observedAt),
        callsign = "DLH453 ",
        altitudeFeet = 36_000.4,
        onGround = onGround,
        trackDegrees = track,
        groundSpeedKnots = speed,
    )

    @Test
    fun `a recent position moves along its track`() {
        val plane = MapTraffic.place("3C65A3", aircraft(observedAt = NOW - 10_000), NOW).shouldNotBeNull()

        val (lat, lon) = ScreenProjection.extrapolatePosition(50.0, 8.0, 90f, 450f, 10f)
        plane.latitude shouldBe lat
        plane.longitude shouldBe lon
        plane.opacity shouldBe 1f
        plane.altitudeFt shouldBe 36_000
        plane.callsign shouldBe "DLH453"
    }

    @Test
    fun `extrapolation stops at 15 seconds and the plane fades`() {
        val at20 = MapTraffic.place("3C65A3", aircraft(observedAt = NOW - 20_000), NOW).shouldNotBeNull()
        val at40 = MapTraffic.place("3C65A3", aircraft(observedAt = NOW - 40_000), NOW).shouldNotBeNull()

        val (lat, lon) = ScreenProjection.extrapolatePosition(50.0, 8.0, 90f, 450f, 15f)
        at20.latitude shouldBe lat
        at20.longitude shouldBe lon
        at40.longitude shouldBe lon
        (at20.opacity < 1f) shouldBe true
        (at40.opacity < at20.opacity) shouldBe true
    }

    @Test
    fun `fades cover only aircraft that faded, in visible steps, and 0 once past hiding`() {
        val traffic = MapTraffic()
        traffic.replace(
            listOf(
                aircraft(id = "aaaaaa", observedAt = NOW - 5_000),
                aircraft(id = "bbbbbb", observedAt = NOW - 30_000),
                aircraft(id = "cccccc", observedAt = NOW - 61_000),
            )
        )

        val fades = traffic.fades(listOf("AAAAAA", "BBBBBB", "CCCCCC", "DDDDDD"), NOW)

        fades.keys shouldBe setOf("BBBBBB", "CCCCCC")
        // A third into the fade from 1 to 0.3 is 0.77, drawn as 0.75
        fades["BBBBBB"] shouldBe 0.75f
        fades["CCCCCC"] shouldBe 0f
        MapTraffic.place("BBBBBB", aircraft(observedAt = NOW - 30_000), NOW)?.opacity shouldBe 0.75f
    }

    @Test
    fun `a position of 60 seconds or more is hidden`() {
        MapTraffic.place("3C65A3", aircraft(observedAt = NOW - 60_000), NOW).shouldBeNull()
    }

    @Test
    fun `a position without a time is hidden`() {
        MapTraffic.place("3C65A3", aircraft(observedAt = null), NOW).shouldBeNull()
    }

    @Test
    fun `a position slightly ahead of the clock counts as current`() {
        val plane = MapTraffic.place("3C65A3", aircraft(observedAt = NOW + 2_000), NOW).shouldNotBeNull()

        plane.latitude shouldBe 50.0
        plane.longitude shouldBe 8.0
        plane.opacity shouldBe 1f
    }

    @Test
    fun `aircraft on the ground or without speed stay where they were seen`() {
        MapTraffic.place("3C65A3", aircraft(onGround = true), NOW).shouldNotBeNull().longitude shouldBe 8.0
        MapTraffic.place("3C65A3", aircraft(speed = null), NOW).shouldNotBeNull().longitude shouldBe 8.0
        MapTraffic.place("3C65A3", aircraft(track = Double.NaN), NOW).shouldNotBeNull().apply {
            longitude shouldBe 8.0
            trackDegrees.shouldBeNull()
        }
    }

    @Test
    fun `each answer replaces the membership, re-fetching an old position does not refresh it`() {
        val traffic = MapTraffic()
        traffic.replace(listOf(aircraft(id = "aaaaaa"), aircraft(id = "bbbbbb")))
        traffic.replace(listOf(aircraft(id = "bbbbbb", observedAt = NOW - 70_000)))

        traffic.aircraft.keys shouldBe setOf("BBBBBB")
        traffic.planes(NOW) shouldBe emptyList()
    }

    companion object {
        private const val NOW = 1_710_000_000_000L
    }
}
