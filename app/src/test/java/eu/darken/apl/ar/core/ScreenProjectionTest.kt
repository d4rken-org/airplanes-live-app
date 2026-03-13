package eu.darken.apl.ar.core

import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
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
}
