package eu.darken.apl.map.ui

import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class AreaGeometryTest : BaseTest() {

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * 6_371_008.8 * asin(sqrt(a))
    }

    @Test
    fun `the ring is closed and every point is at the radius`() {
        val ring = areaRing(50.77, 6.08, 25_000.0)

        ring.latitudes.size shouldBe 65
        ring.latitudes.first() shouldBe ring.latitudes.last()
        ring.longitudes.first() shouldBe ring.longitudes.last()
        ring.latitudes.zip(ring.longitudes).forEach { (lat, lon) ->
            haversineMeters(50.77, 6.08, lat, lon) shouldBe (25_000.0 plusOrMinus 1.0)
        }
    }

    @Test
    fun `a ring east of the antimeridian continues past 180`() {
        val ring = areaRing(60.0, 179.9, 50_000.0)

        ring.west shouldBeLessThan 180.0
        ring.east shouldBeGreaterThan 180.0
        (ring.east - ring.west) shouldBeLessThan 2.0
    }

    @Test
    fun `a ring west of the antimeridian is moved past 180 instead of below -180`() {
        val ring = areaRing(60.0, -179.9, 50_000.0)

        ring.west shouldBeGreaterThan 179.0
        ring.east shouldBeGreaterThan 180.0
        (ring.east - ring.west) shouldBeLessThan 2.0
    }
}
