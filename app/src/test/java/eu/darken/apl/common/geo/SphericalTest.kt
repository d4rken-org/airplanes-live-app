package eu.darken.apl.common.geo

import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import kotlin.math.abs

class SphericalTest : BaseTest() {

    @Test
    fun `haversineDistanceM returns correct distance for known points`() {
        // London to Paris: ~340km
        val distance = Spherical.haversineDistanceM(
            lat1 = 51.5074, lon1 = -0.1278,
            lat2 = 48.8566, lon2 = 2.3522,
        )

        distance shouldBeGreaterThan 340_000.0
        distance shouldBeLessThan 345_000.0
    }

    @Test
    fun `haversineDistanceM returns zero for same point`() {
        val distance = Spherical.haversineDistanceM(
            lat1 = 51.5, lon1 = -0.1,
            lat2 = 51.5, lon2 = -0.1,
        )

        distance shouldBeLessThan 0.01
    }

    @Test
    fun `bearingRad returns approximately 0 for due north`() {
        val bearing = Spherical.bearingRad(
            lat1 = 51.5, lon1 = -0.1,
            lat2 = 52.5, lon2 = -0.1,
        )

        abs(bearing) shouldBeLessThan 0.01
    }

    @Test
    fun `bearingRad returns approximately PI div 2 for due east`() {
        val bearing = Spherical.bearingRad(
            lat1 = 51.5, lon1 = -0.1,
            lat2 = 51.5, lon2 = 0.9,
        )

        abs(bearing - Math.PI / 2) shouldBeLessThan 0.02
    }

    @Test
    fun `antipodal distance is half the circumference, not NaN`() {
        val distance = Spherical.haversineDistanceM(lat1 = 10.0, lon1 = 20.0, lat2 = -10.0, lon2 = -160.0)

        distance shouldBe (Math.PI * Spherical.EARTH_RADIUS_M plusOrMinus 1.0)
    }

    @Test
    fun `point on the route has no cross-track and its along-track distance`() {
        // Along the equator from 0,0 to 0,10: 0,4 is 40% of the way
        val total = Spherical.haversineDistanceM(0.0, 0.0, 0.0, 10.0)

        Spherical.crossTrackM(0.0, 0.0, 0.0, 10.0, 0.0, 4.0) shouldBe (0.0 plusOrMinus 1.0)
        Spherical.alongTrackM(0.0, 0.0, 0.0, 10.0, 0.0, 4.0) shouldBe (0.4 * total plusOrMinus 1.0)
    }

    @Test
    fun `point behind start has negative along-track`() {
        val oneDegree = Spherical.haversineDistanceM(0.0, 0.0, 0.0, 1.0)

        Spherical.alongTrackM(0.0, 0.0, 0.0, 10.0, 0.0, -1.0) shouldBe (-oneDegree plusOrMinus 1.0)
    }

    @Test
    fun `cross-track sign tells the side`() {
        // Heading east, north is to the left
        Spherical.crossTrackM(0.0, 0.0, 0.0, 10.0, 1.0, 5.0) shouldBeLessThan 0.0
        Spherical.crossTrackM(0.0, 0.0, 0.0, 10.0, -1.0, 5.0) shouldBeGreaterThan 0.0
    }

    @Test
    fun `offset point projects to its foot on the route`() {
        val oneDegreeLat = Spherical.haversineDistanceM(0.0, 5.0, 1.0, 5.0)
        val toFoot = Spherical.haversineDistanceM(0.0, 0.0, 0.0, 5.0)

        abs(Spherical.crossTrackM(0.0, 0.0, 0.0, 10.0, 1.0, 5.0)) shouldBe (oneDegreeLat plusOrMinus 100.0)
        Spherical.alongTrackM(0.0, 0.0, 0.0, 10.0, 1.0, 5.0) shouldBe (toFoot plusOrMinus 100.0)
    }

    @Test
    fun `isValid rejects out of range and non-finite coordinates`() {
        Spherical.isValid(51.5, -0.1) shouldBe true
        Spherical.isValid(-90.0, 180.0) shouldBe true
        Spherical.isValid(90.1, 0.0) shouldBe false
        Spherical.isValid(0.0, -180.1) shouldBe false
        Spherical.isValid(Double.NaN, 0.0) shouldBe false
        Spherical.isValid(0.0, Double.POSITIVE_INFINITY) shouldBe false
    }
}
