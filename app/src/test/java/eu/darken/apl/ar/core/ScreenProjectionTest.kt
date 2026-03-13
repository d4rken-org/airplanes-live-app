package eu.darken.apl.ar.core

import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.doubles.shouldBeWithinPercentageOf
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeLessThan as intShouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import kotlin.math.abs

class ScreenProjectionTest : BaseTest() {

    /**
     * Device-to-world matrix for phone held upright in portrait, facing true North.
     *
     * SensorManager gives R that maps device→world:
     *  - Device X (right)          → World East  (1, 0, 0)
     *  - Device Y (up/top of phone) → World Up    (0, 0, 1)
     *  - Device Z (out of screen)   → World South (0,-1, 0)
     *
     * Row-major 4x4 layout.
     */
    private fun facingNorthMatrix(): FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f,     // row 0
        0f, 0f, -1f, 0f,    // row 1
        0f, 1f, 0f, 0f,     // row 2
        0f, 0f, 0f, 1f,     // row 3
    )

    @Test
    fun `aircraft directly ahead should be near center of screen`() {
        val userLat = 51.5
        val userLon = -0.1
        // Aircraft 10km due north, same altitude
        val acLat = 51.5 + 10_000.0 / 111_320.0
        val acLon = -0.1

        val result = ScreenProjection.project(
            userLat = userLat,
            userLon = userLon,
            userAltM = 100.0,
            acLat = acLat,
            acLon = acLon,
            acAltM = 100.0,
            rotationMatrix = facingNorthMatrix(),
        )

        result.shouldNotBeNull()
        result.isVisible shouldBe true
        abs(result.screenXNorm - 0.5f) shouldBeLessThan 0.1f
        abs(result.screenYNorm - 0.5f) shouldBeLessThan 0.1f
    }

    @Test
    fun `aircraft 90 degrees to the right should be outside FOV`() {
        val userLat = 51.5
        val userLon = -0.1
        // Aircraft 10km due east while facing north
        val acLat = 51.5
        val acLon = -0.1 + 10_000.0 / (111_320.0 * kotlin.math.cos(Math.toRadians(51.5)))

        val result = ScreenProjection.project(
            userLat = userLat,
            userLon = userLon,
            userAltM = 100.0,
            acLat = acLat,
            acLon = acLon,
            acAltM = 100.0,
            rotationMatrix = facingNorthMatrix(),
        )

        result.shouldNotBeNull()
        result.isVisible shouldBe false
    }

    @Test
    fun `aircraft behind should return null`() {
        val userLat = 51.5
        val userLon = -0.1
        // Aircraft 10km due south while facing north
        val acLat = 51.5 - 10_000.0 / 111_320.0
        val acLon = -0.1

        val result = ScreenProjection.project(
            userLat = userLat,
            userLon = userLon,
            userAltM = 100.0,
            acLat = acLat,
            acLon = acLon,
            acAltM = 100.0,
            rotationMatrix = facingNorthMatrix(),
        )

        result.shouldBeNull()
    }

    @Test
    fun `aircraft at higher altitude should appear above center`() {
        val userLat = 51.5
        val userLon = -0.1
        // Aircraft 5km north, 5000m higher
        val acLat = 51.5 + 5_000.0 / 111_320.0
        val acLon = -0.1

        val result = ScreenProjection.project(
            userLat = userLat,
            userLon = userLon,
            userAltM = 100.0,
            acLat = acLat,
            acLon = acLon,
            acAltM = 5100.0,
            rotationMatrix = facingNorthMatrix(),
        )

        result.shouldNotBeNull()
        result.elevationDeg shouldBeGreaterThan 0f
        // Higher altitude = lower screenYNorm (top of screen)
        result.screenYNorm shouldBeLessThan 0.5f
    }

    @Test
    fun `aircraft slightly east should appear right of center`() {
        val userLat = 51.5
        val userLon = -0.1
        // Aircraft 10km north, 1km east
        val acLat = 51.5 + 10_000.0 / 111_320.0
        val acLon = -0.1 + 1_000.0 / (111_320.0 * kotlin.math.cos(Math.toRadians(51.5)))

        val result = ScreenProjection.project(
            userLat = userLat,
            userLon = userLon,
            userAltM = 100.0,
            acLat = acLat,
            acLon = acLon,
            acAltM = 100.0,
            rotationMatrix = facingNorthMatrix(),
        )

        result.shouldNotBeNull()
        result.screenXNorm shouldBeGreaterThan 0.5f
    }

    @Test
    fun `haversineDistanceM returns correct distance for known points`() {
        // London to Paris: ~340km
        val distance = ScreenProjection.haversineDistanceM(
            lat1 = 51.5074, lon1 = -0.1278,
            lat2 = 48.8566, lon2 = 2.3522,
        )

        distance shouldBeGreaterThan 340_000.0
        distance shouldBeLessThan 345_000.0
    }

    @Test
    fun `haversineDistanceM returns zero for same point`() {
        val distance = ScreenProjection.haversineDistanceM(
            lat1 = 51.5, lon1 = -0.1,
            lat2 = 51.5, lon2 = -0.1,
        )

        distance shouldBeLessThan 0.01
    }

    @Test
    fun `bearingRad returns approximately 0 for due north`() {
        val bearing = ScreenProjection.bearingRad(
            lat1 = 51.5, lon1 = -0.1,
            lat2 = 52.5, lon2 = -0.1,
        )

        abs(bearing) shouldBeLessThan 0.01
    }

    @Test
    fun `bearingRad returns approximately PI div 2 for due east`() {
        val bearing = ScreenProjection.bearingRad(
            lat1 = 51.5, lon1 = -0.1,
            lat2 = 51.5, lon2 = 0.9,
        )

        abs(bearing - Math.PI / 2) shouldBeLessThan 0.02
    }

    @Test
    fun `distance includes altitude difference`() {
        val result = ScreenProjection.project(
            userLat = 51.5,
            userLon = -0.1,
            userAltM = 0.0,
            acLat = 51.5 + 1_000.0 / 111_320.0,
            acLon = -0.1,
            acAltM = 10_000.0,
            rotationMatrix = facingNorthMatrix(),
        )

        result.shouldNotBeNull()
        result.distanceM shouldBeGreaterThan 10_000.0
    }

    @Nested
    inner class ExtrapolatePosition {

        @Test
        fun `due north increases latitude, longitude unchanged`() {
            val (newLat, newLon) = ScreenProjection.extrapolatePosition(
                lat = 51.5, lon = -0.1, trackDeg = 0f, speedKts = 100f, ageSec = 10f,
            )
            newLat shouldBeGreaterThan 51.5
            abs(newLon - (-0.1)) shouldBeLessThan 1e-9
        }

        @Test
        fun `due east increases longitude, latitude unchanged`() {
            val (newLat, newLon) = ScreenProjection.extrapolatePosition(
                lat = 51.5, lon = -0.1, trackDeg = 90f, speedKts = 100f, ageSec = 10f,
            )
            abs(newLat - 51.5) shouldBeLessThan 1e-6
            newLon shouldBeGreaterThan -0.1
        }

        @Test
        fun `due south decreases latitude`() {
            val (newLat, _) = ScreenProjection.extrapolatePosition(
                lat = 51.5, lon = -0.1, trackDeg = 180f, speedKts = 100f, ageSec = 10f,
            )
            newLat shouldBeLessThan 51.5
        }

        @Test
        fun `due west decreases longitude`() {
            val (_, newLon) = ScreenProjection.extrapolatePosition(
                lat = 51.5, lon = -0.1, trackDeg = 270f, speedKts = 100f, ageSec = 10f,
            )
            newLon shouldBeLessThan -0.1
        }

        @Test
        fun `zero speed returns original position`() {
            val (newLat, newLon) = ScreenProjection.extrapolatePosition(
                lat = 51.5, lon = -0.1, trackDeg = 45f, speedKts = 0f, ageSec = 10f,
            )
            newLat shouldBe 51.5
            newLon shouldBe -0.1
        }

        @Test
        fun `age capped at 30 seconds`() {
            val at30s = ScreenProjection.extrapolatePosition(
                lat = 51.5, lon = -0.1, trackDeg = 0f, speedKts = 500f, ageSec = 30f,
            )
            val at120s = ScreenProjection.extrapolatePosition(
                lat = 51.5, lon = -0.1, trackDeg = 0f, speedKts = 500f, ageSec = 120f,
            )
            at120s.first shouldBe at30s.first
            at120s.second shouldBe at30s.second
        }

        @Test
        fun `500kts for 10s moves approximately 2572m`() {
            val (newLat, newLon) = ScreenProjection.extrapolatePosition(
                lat = 51.5, lon = -0.1, trackDeg = 0f, speedKts = 500f, ageSec = 10f,
            )
            // 500 kts * 0.514444 m/s * 10s = 2572m
            val dist = ScreenProjection.haversineDistanceM(51.5, -0.1, newLat, newLon)
            dist.shouldBeWithinPercentageOf(2572.0, 1.0)
        }

        @Test
        fun `near-pole returns raw position`() {
            val (newLat, newLon) = ScreenProjection.extrapolatePosition(
                lat = 90.0, lon = 10.0, trackDeg = 90f, speedKts = 500f, ageSec = 10f,
            )
            newLat shouldBe 90.0
            newLon shouldBe 10.0
        }
    }

    @Nested
    inner class ExtrapolateAltitude {

        @Test
        fun `climbing at 1000 ft per min for 10s`() {
            // 1000 / 60 * 10 = 166.67 -> rounds to 167
            ScreenProjection.extrapolateAltitudeFt(30000, 1000, 10f) shouldBe 30167
        }

        @Test
        fun `descending reduces altitude`() {
            ScreenProjection.extrapolateAltitudeFt(5000, -3000, 10f) shouldBe 4500
        }

        @Test
        fun `age capped at 30s`() {
            val at30 = ScreenProjection.extrapolateAltitudeFt(10000, 2000, 30f)
            val at120 = ScreenProjection.extrapolateAltitudeFt(10000, 2000, 120f)
            at120 shouldBe at30
        }

        @Test
        fun `zero rate returns same altitude`() {
            ScreenProjection.extrapolateAltitudeFt(35000, 0, 10f) shouldBe 35000
        }

        @Test
        fun `can go below zero for below-sea-level`() {
            val result = ScreenProjection.extrapolateAltitudeFt(100, -6000, 30f)
            result intShouldBeLessThan 0
        }
    }
}
