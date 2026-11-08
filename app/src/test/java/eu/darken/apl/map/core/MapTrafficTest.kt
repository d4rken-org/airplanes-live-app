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
        heading: Double? = null,
        speed: Double? = 450.0,
        squawk: String? = null,
        kind: String? = null,
    ) = MapAircraft(
        id = id,
        position = AircraftPosition(50.0, 8.0, observedAt),
        callsign = "DLH453 ",
        altitudeFeet = 36_000.4,
        onGround = onGround,
        trackDegrees = track,
        trueHeadingDegrees = heading,
        groundSpeedKnots = speed,
        squawk = squawk,
        kind = kind,
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
    fun `a squawk is trimmed and a blank one is none`() {
        MapTraffic.place("3C65A3", aircraft(squawk = " 0425 "), NOW).shouldNotBeNull().squawk shouldBe "0425"
        MapTraffic.place("3C65A3", aircraft(squawk = " "), NOW).shouldNotBeNull().squawk.shouldBeNull()
        MapTraffic.place("3C65A3", aircraft(squawk = null), NOW).shouldNotBeNull().squawk.shouldBeNull()
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
            ),
            NOW,
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
    fun `the kind is read from the answer, an unknown or missing one is unknown`() {
        MapTraffic.place("3C65A3", aircraft(kind = "helicopter"), NOW).shouldNotBeNull().kind shouldBe AircraftKind.HELICOPTER
        MapTraffic.place("3C65A3", aircraft(kind = "zeppelin"), NOW).shouldNotBeNull().kind shouldBe AircraftKind.UNKNOWN
        MapTraffic.place("3C65A3", aircraft(kind = null), NOW).shouldNotBeNull().kind shouldBe AircraftKind.UNKNOWN
    }

    @Test
    fun `each answer replaces the membership, re-fetching an old position does not refresh it`() {
        val traffic = MapTraffic()
        traffic.replace(listOf(aircraft(id = "aaaaaa"), aircraft(id = "bbbbbb")), NOW)
        traffic.replace(listOf(aircraft(id = "bbbbbb", observedAt = NOW - 70_000)), NOW)

        traffic.aircraft.keys shouldBe setOf("BBBBBB")
        traffic.planes(NOW) shouldBe emptyList()
    }

    @Test
    fun `on the ground the icon follows the heading, in the air the track`() {
        fun bearing(onGround: Boolean, track: Double?, heading: Double?) =
            MapTraffic.place("3C65A3", aircraft(onGround = onGround, track = track, heading = heading), NOW)
                .shouldNotBeNull().bearingDegrees

        bearing(onGround = true, track = 90.0, heading = 270.0) shouldBe 270f
        bearing(onGround = true, track = 90.0, heading = null) shouldBe 90f
        bearing(onGround = false, track = 90.0, heading = 85.0) shouldBe 90f
        bearing(onGround = false, track = null, heading = 85.0) shouldBe 85f
        bearing(onGround = true, track = null, heading = Double.NaN).shouldBeNull()
    }

    @Test
    fun `the heading does not stand in for the track`() {
        MapTraffic.place("3C65A3", aircraft(onGround = true, track = null, heading = 270.0), NOW).shouldNotBeNull().apply {
            trackDegrees.shouldBeNull()
            headingDegrees shouldBe 270f
        }
    }

    @Test
    fun `an aircraft that stops reporting a direction keeps its last one`() {
        val traffic = MapTraffic()
        traffic.replace(listOf(aircraft(onGround = true, track = null, heading = 270.0)), NOW)
        traffic.replace(listOf(aircraft(onGround = true, track = null, heading = null)), NOW + 5_000)

        traffic.plane("3C65A3", NOW + 5_000).shouldNotBeNull().bearingDegrees shouldBe 270f
        traffic.rememberedBearing("3C65A3") shouldBe 270f
    }

    @Test
    fun `a remembered direction is kept while answers include the aircraft`() {
        val traffic = MapTraffic()
        traffic.replace(listOf(aircraft(track = 90.0)), NOW)
        val step = MapTraffic.REMEMBER_MS / 2
        (1..4).forEach { traffic.replace(listOf(aircraft(track = null)), NOW + it * step) }

        traffic.rememberedBearing("3C65A3") shouldBe 90f
    }

    @Test
    fun `a remembered direction is forgotten 10 minutes after the aircraft was last answered`() {
        val traffic = MapTraffic()
        traffic.replace(listOf(aircraft(track = 90.0)), NOW)
        traffic.replace(emptyList(), NOW + 60_000)
        // Back without a direction after the 10 minutes, with no answer in between
        traffic.replace(listOf(aircraft(track = null)), NOW + MapTraffic.REMEMBER_MS + 1)

        traffic.rememberedBearing("3C65A3").shouldBeNull()
    }

    @Test
    fun `a remembered direction survives an answer without the aircraft and clearing`() {
        val traffic = MapTraffic()
        traffic.replace(listOf(aircraft(track = 90.0)), NOW)
        traffic.replace(emptyList(), NOW + 1_000)
        traffic.rememberedBearing("3C65A3") shouldBe 90f

        traffic.clear()
        traffic.aircraft shouldBe emptyMap()
        traffic.rememberedBearing("3C65A3") shouldBe 90f
    }

    companion object {
        private const val NOW = 1_710_000_000_000L
    }
}
