package eu.darken.apl.map.core

import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class SelectedMotionTest : BaseTest() {

    private fun plane(
        lat: Double = 50.0,
        lon: Double = 8.0,
        track: Float? = 90f,
        hex: String = "3C65A3",
        opacity: Float = 1f,
    ) = MapPlane(
        hex = hex,
        latitude = lat,
        longitude = lon,
        trackDegrees = track,
        altitudeFt = 36_000,
        groundSpeedKnots = 450,
        onGround = false,
        callsign = "DLH453",
        aircraftType = "A320",
        category = "A3",
        military = false,
        opacity = opacity,
    )

    @Test
    fun `nothing selected draws nothing`() {
        SelectedMotion(delayMs = 100).planeAt(1_000.0).shouldBeNull()
    }

    @Test
    fun `drawn between the two frames around now minus the delay`() {
        val motion = SelectedMotion(delayMs = 100)
        motion.add(1_000, plane(lon = 8.0))
        motion.add(1_100, plane(lon = 8.1))

        motion.planeAt(1_125.0).shouldNotBeNull().longitude shouldBe (8.025 plusOrMinus 1e-9)
        motion.planeAt(1_200.0).shouldNotBeNull().longitude shouldBe (8.1 plusOrMinus 1e-9)
    }

    @Test
    fun `holds the first frame until there is a second to move to`() {
        val motion = SelectedMotion(delayMs = 100)
        motion.add(1_000, plane(lon = 8.0))

        motion.planeAt(1_050.0).shouldNotBeNull().longitude shouldBe 8.0
    }

    @Test
    fun `holds the newest frame when frames stop`() {
        val motion = SelectedMotion(delayMs = 100)
        motion.add(1_000, plane(lon = 8.0))
        motion.add(1_100, plane(lon = 8.1))

        motion.planeAt(5_000.0).shouldNotBeNull().longitude shouldBe 8.1
    }

    @Test
    fun `a late frame is caught up with`() {
        val motion = SelectedMotion(delayMs = 100)
        motion.add(1_000, plane(lon = 8.0))
        motion.add(1_100, plane(lon = 8.1))
        motion.planeAt(1_500.0).shouldNotBeNull().longitude shouldBe 8.1

        motion.add(1_500, plane(lon = 8.5))

        motion.planeAt(1_500.0).shouldNotBeNull().longitude shouldBe (8.4 plusOrMinus 1e-9)
    }

    @Test
    fun `another aircraft starts over instead of sliding across`() {
        val motion = SelectedMotion(delayMs = 100)
        motion.add(1_000, plane(lon = 8.0))
        motion.add(1_100, plane(lon = 8.1))

        motion.add(1_200, plane(hex = "ABCDEF", lon = 20.0))

        motion.planeAt(1_250.0).shouldNotBeNull().apply {
            hex shouldBe "ABCDEF"
            longitude shouldBe 20.0
        }
    }

    @Test
    fun `another aircraft in the same millisecond starts over`() {
        val motion = SelectedMotion(delayMs = 100)
        motion.add(1_000, plane(lon = 8.0))

        motion.add(1_000, plane(hex = "ABCDEF", lon = 20.0))

        motion.planeAt(1_100.0).shouldNotBeNull().hex shouldBe "ABCDEF"
    }

    @Test
    fun `a frame of the same moment replaces the earlier one`() {
        val motion = SelectedMotion(delayMs = 100)
        motion.add(1_000, plane(lon = 8.0))
        motion.add(1_100, plane(lon = 8.1))
        motion.add(1_100, plane(lon = 8.2))

        motion.planeAt(1_200.0).shouldNotBeNull().longitude shouldBe (8.2 plusOrMinus 1e-9)
    }

    @Test
    fun `clearing draws nothing`() {
        val motion = SelectedMotion(delayMs = 100)
        motion.add(1_000, plane())
        motion.clear()

        motion.planeAt(1_100.0).shouldBeNull()
    }

    @Test
    fun `crossing the antimeridian takes the short way`() {
        SelectedMotion.between(plane(lon = 179.9), plane(lon = -179.9), 0.75f).longitude shouldBe (-179.95 plusOrMinus 1e-9)
        SelectedMotion.between(plane(lon = -179.9), plane(lon = 179.9), 0.75f).longitude shouldBe (179.95 plusOrMinus 1e-9)
    }

    @Test
    fun `latitude moves along, the rest is the newer frame's`() {
        val from = plane(lat = 50.0).copy(altitudeFt = 30_000, callsign = "OLD")
        val to = plane(lat = 51.0).copy(altitudeFt = 31_000, callsign = "NEW")

        SelectedMotion.between(from, to, 0.25f).apply {
            latitude shouldBe (50.25 plusOrMinus 1e-9)
            altitudeFt shouldBe 31_000
            callsign shouldBe "NEW"
        }
    }

    @Test
    fun `many frames keep moving between the newest`() {
        val motion = SelectedMotion(delayMs = 100)
        repeat(20) { i -> motion.add(1_000L + i * 100, plane(lon = 8.0 + i * 0.1)) }

        motion.planeAt(2_850.0).shouldNotBeNull().longitude shouldBe (9.75 plusOrMinus 1e-9)
    }

    @Test
    fun `turning through north takes the short way`() {
        SelectedMotion.between(plane(track = 350f), plane(track = 10f), 0.5f).trackDegrees shouldBe (0f plusOrMinus 1e-3f)
        SelectedMotion.between(plane(track = 10f), plane(track = 350f), 0.25f).trackDegrees shouldBe (5f plusOrMinus 1e-3f)
    }

    @Test
    fun `a missing track takes the newer one`() {
        SelectedMotion.between(plane(track = null), plane(track = 90f), 0.5f).trackDegrees shouldBe 90f
        SelectedMotion.between(plane(track = 90f), plane(track = null), 0.5f).trackDegrees.shouldBeNull()
    }

    @Test
    fun `fading keeps the newer frame's steps`() {
        SelectedMotion.between(plane(opacity = 1f), plane(opacity = 0.5f), 0.5f).opacity shouldBe 0.5f
    }
}
