package eu.darken.apl.map.core

import eu.darken.apl.server.api.AircraftPosition
import eu.darken.apl.server.api.MapAircraft
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class SelectedMotionTest : BaseTest() {

    /** Observation times below are on the elapsed clock, the server's is this far ahead. */
    private val offset = 1_700_000_000_000L

    private fun aircraft(
        lat: Double = 50.0,
        lon: Double = 8.0,
        observedAt: Long = 10_000,
        callsign: String = "DLH453",
    ) = MapAircraft(
        id = "3c65a3",
        position = AircraftPosition(lat, lon, observedAt + offset),
        callsign = callsign,
        altitudeFeet = 36_000.0,
        onGround = false,
        trackDegrees = 90.0,
        groundSpeedKnots = 450.0,
    )

    private fun placed(aircraft: MapAircraft, at: Double, hex: String = HEX, serverOffset: Long = offset) =
        MapTraffic.place(hex, aircraft, (at + serverOffset).toLong()).shouldNotBeNull()

    private fun SelectedMotion.update(now: Double, aircraft: MapAircraft, hex: String = HEX, serverOffset: Long = offset) =
        update(now, hex, aircraft, serverOffset)

    private fun MapPlane.shouldBeAt(other: MapPlane) {
        latitude shouldBe (other.latitude plusOrMinus 1e-9)
        longitude shouldBe (other.longitude plusOrMinus 1e-9)
    }

    @Test
    fun `nothing selected draws nothing`() {
        SelectedMotion().planeAt(10_000.0).shouldBeNull()
    }

    @Test
    fun `placed like the frames place it`() {
        val motion = SelectedMotion()
        motion.update(10_000.0, aircraft())

        motion.planeAt(10_500.0).shouldNotBeNull().shouldBeAt(placed(aircraft(), 10_500.0))
    }

    @Test
    fun `new details of the same motion need no crossing`() {
        val motion = SelectedMotion()
        motion.update(10_000.0, aircraft())
        motion.update(10_200.0, aircraft(callsign = "NEW"))

        motion.planeAt(10_300.0).shouldNotBeNull().apply {
            callsign shouldBe "NEW"
            shouldBeAt(placed(aircraft(), 10_300.0))
        }
    }

    @Test
    fun `a correction starts where the aircraft is drawn and ends on the new prediction`() {
        val old = aircraft()
        val new = aircraft(lon = 8.004, observedAt = 10_900)
        val motion = SelectedMotion()
        motion.update(10_000.0, old)

        motion.update(11_000.0, new)

        motion.planeAt(11_000.0).shouldNotBeNull().shouldBeAt(placed(old, 11_000.0))
        motion.planeAt(12_000.0).shouldNotBeNull().shouldBeAt(placed(new, 12_000.0))
        motion.planeAt(12_500.0).shouldNotBeNull().shouldBeAt(placed(new, 12_500.0))
    }

    @Test
    fun `speed does not jump when a correction starts`() {
        val motion = SelectedMotion()
        motion.update(10_000.0, aircraft())
        val before = motion.planeAt(10_990.0).shouldNotBeNull()
        val at = motion.planeAt(11_000.0).shouldNotBeNull()

        motion.update(11_000.0, aircraft(lon = 8.004, observedAt = 10_900))
        val after = motion.planeAt(11_010.0).shouldNotBeNull()

        (after.longitude - at.longitude) shouldBe ((at.longitude - before.longitude) plusOrMinus 1e-6)
    }

    @Test
    fun `a correction during a crossing continues from where the aircraft is drawn`() {
        val motion = SelectedMotion()
        motion.update(10_000.0, aircraft())
        motion.update(11_000.0, aircraft(lon = 8.004, observedAt = 10_900))
        val drawn = motion.planeAt(11_400.0).shouldNotBeNull()

        motion.update(11_400.0, aircraft(lon = 8.0045, observedAt = 11_300))

        motion.planeAt(11_400.0).shouldNotBeNull().shouldBeAt(drawn)
    }

    @Test
    fun `answers in quick succession keep the drawing where it is`() {
        val motion = SelectedMotion()
        motion.update(10_000.0, aircraft())

        repeat(12) { i ->
            val now = 10_100.0 + i * 100
            val drawn = motion.planeAt(now).shouldNotBeNull()
            motion.update(now, aircraft(lon = 8.0 + (i + 1) * 0.0002, observedAt = now.toLong() - 50))
            motion.planeAt(now).shouldNotBeNull().shouldBeAt(drawn)
        }
        // The answer that had to wait is taken up once crossings have finished, ending on the newest
        motion.planeAt(12_500.0)
        motion.planeAt(14_000.0).shouldNotBeNull().shouldBeAt(placed(aircraft(lon = 8.0024, observedAt = 11_150), 14_000.0))
    }

    @Test
    fun `a waiting answer is dropped with the selection`() {
        fun crowded() = SelectedMotion().apply {
            update(10_000.0, aircraft())
            repeat(12) { i -> update(10_100.0 + i * 50, aircraft(lon = 8.0 + (i + 1) * 0.0002, observedAt = 10_050L + i * 50)) }
        }
        val other = aircraft(lon = 20.0)

        crowded().apply { clear() }.planeAt(10_800.0).shouldBeNull()
        crowded().apply { update(10_800.0, other, hex = "ABCDEF") }.planeAt(10_800.0).shouldNotBeNull().apply {
            hex shouldBe "ABCDEF"
            shouldBeAt(placed(other, 10_800.0, hex = "ABCDEF"))
        }
    }

    @Test
    fun `another aircraft is not crossed to`() {
        val other = aircraft(lon = 20.0)
        val motion = SelectedMotion()
        motion.update(10_000.0, aircraft())

        motion.update(10_100.0, other, hex = "ABCDEF")

        motion.planeAt(10_100.0).shouldNotBeNull().apply {
            hex shouldBe "ABCDEF"
            shouldBeAt(placed(other, 10_100.0, hex = "ABCDEF"))
        }
    }

    @Test
    fun `a far correction jumps`() {
        val far = aircraft(lon = 9.0, observedAt = 10_900)
        val motion = SelectedMotion()
        motion.update(10_000.0, aircraft())

        motion.update(11_000.0, far)

        motion.planeAt(11_000.0).shouldNotBeNull().shouldBeAt(placed(far, 11_000.0))
    }

    @Test
    fun `an answer too old to show hides the aircraft`() {
        val motion = SelectedMotion()
        motion.update(10_000.0, aircraft())

        motion.update(10_100.0, aircraft(observedAt = 10_100 - 61_000))

        motion.planeAt(10_100.0).shouldBeNull()
    }

    @Test
    fun `the old prediction getting too old leaves the new one`() {
        // Shown until 59.5s after its observation, so hidden half a second into the crossing
        val old = aircraft(observedAt = 10_000 - 59_500)
        val drawn = placed(old, 10_100.0)
        val new = aircraft(lat = drawn.latitude, lon = drawn.longitude, observedAt = 10_050)
        val motion = SelectedMotion()
        motion.update(10_000.0, old)

        motion.update(10_100.0, new)

        motion.planeAt(10_700.0).shouldNotBeNull().shouldBeAt(placed(new, 10_700.0))
    }

    @Test
    fun `the server time anchored anew is crossed over too`() {
        val motion = SelectedMotion()
        motion.update(10_000.0, aircraft())

        motion.update(11_000.0, aircraft(), serverOffset = offset + 200)

        motion.planeAt(11_000.0).shouldNotBeNull().shouldBeAt(placed(aircraft(), 11_000.0))
        motion.planeAt(12_000.0).shouldNotBeNull().shouldBeAt(placed(aircraft(), 12_000.0, serverOffset = offset + 200))
    }

    @Test
    fun `clearing draws nothing`() {
        val motion = SelectedMotion()
        motion.update(10_000.0, aircraft())
        motion.clear()

        motion.planeAt(10_100.0).shouldBeNull()
    }

    @Test
    fun `crossing the antimeridian takes the short way`() {
        SelectedMotion.between(plane(lon = 179.9), plane(lon = -179.9), 0.75f).longitude shouldBe (-179.95 plusOrMinus 1e-9)
        SelectedMotion.between(plane(lon = -179.9), plane(lon = 179.9), 0.75f).longitude shouldBe (179.95 plusOrMinus 1e-9)
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
    fun `latitude moves along, the rest is the newer one's`() {
        val from = plane(lat = 50.0).copy(altitudeFt = 30_000, callsign = "OLD", opacity = 1f)
        val to = plane(lat = 51.0).copy(altitudeFt = 31_000, callsign = "NEW", opacity = 0.5f)

        SelectedMotion.between(from, to, 0.25f).apply {
            latitude shouldBe (50.25 plusOrMinus 1e-9)
            altitudeFt shouldBe 31_000
            callsign shouldBe "NEW"
            opacity shouldBe 0.5f
        }
    }

    private fun plane(lat: Double = 50.0, lon: Double = 8.0, track: Float? = 90f) = MapPlane(
        hex = HEX,
        latitude = lat,
        longitude = lon,
        trackDegrees = track,
        altitudeFt = 36_000,
        groundSpeedKnots = 450,
        onGround = false,
        callsign = "DLH453",
        squawk = null,
        aircraftType = "A320",
        category = "A3",
        military = false,
        opacity = 1f,
    )

    companion object {
        private const val HEX = "3C65A3"
    }
}
