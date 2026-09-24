package eu.darken.apl.map.core

import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class MapViewportTest : BaseTest() {

    @Test
    fun `padding adds a quarter of the span on every side`() {
        val padded = MapViewport(south = 50.0, north = 52.0, west = 8.0, east = 12.0, zoom = 8.0).padded()

        padded.south shouldBe (49.5 plusOrMinus 1e-9)
        padded.north shouldBe (52.5 plusOrMinus 1e-9)
        padded.west shouldBe (7.0 plusOrMinus 1e-9)
        padded.east shouldBe (13.0 plusOrMinus 1e-9)
    }

    @Test
    fun `padding stops at the latitudes a map can show`() {
        val padded = MapViewport(south = -84.0, north = 84.0, west = 0.0, east = 10.0, zoom = 2.0).padded()

        padded.south shouldBe -MapViewport.MAX_LAT
        padded.north shouldBe MapViewport.MAX_LAT
    }

    @Test
    fun `a padded span of the whole earth becomes global`() {
        val padded = MapViewport(south = -60.0, north = 60.0, west = -120.0, east = 120.0, zoom = 1.0).padded()

        padded.west shouldBe -180.0
        padded.east shouldBe 180.0
        padded.isGlobal shouldBe true
    }

    @Test
    fun `padding across the antimeridian wraps the edges`() {
        val padded = MapViewport(south = 0.0, north = 10.0, west = 170.0, east = -170.0, zoom = 5.0).padded()

        padded.west shouldBe (165.0 plusOrMinus 1e-9)
        padded.east shouldBe (-165.0 plusOrMinus 1e-9)
    }

    @Test
    fun `an unwrapped visible region is normalized`() {
        MapViewport.fromVisibleRegion(0.0, 10.0, 170.0, 190.0, 5.0).apply {
            west shouldBe 170.0
            east shouldBe -170.0
        }
        MapViewport.fromVisibleRegion(0.0, 10.0, -190.0, -170.0, 5.0).apply {
            west shouldBe 170.0
            east shouldBe -170.0
        }
        MapViewport.fromVisibleRegion(-90.0, 90.0, -200.0, 200.0, 0.5).isGlobal shouldBe true
    }

    @Test
    fun `containment follows the antimeridian`() {
        val fetched = MapViewport(south = 0.0, north = 10.0, west = 160.0, east = -160.0, zoom = 5.0)

        (MapViewport(1.0, 9.0, 175.0, -175.0, 5.0) in fetched) shouldBe true
        (MapViewport(1.0, 9.0, 150.0, 170.0, 5.0) in fetched) shouldBe false
        (MapViewport(1.0, 11.0, 170.0, 175.0, 5.0) in fetched) shouldBe false
        fetched.containsPoint(5.0, 179.9) shouldBe true
        fetched.containsPoint(5.0, -170.0) shouldBe true
        fetched.containsPoint(5.0, 0.0) shouldBe false
    }

    @Test
    fun `a global area contains everything within its latitudes`() {
        val global = MapViewport(-80.0, 80.0, -180.0, 180.0, 1.0)

        (MapViewport(0.0, 10.0, 170.0, -170.0, 5.0) in global) shouldBe true
        global.containsPoint(0.0, 179.0) shouldBe true
    }

    @Test
    fun `longitudes are folded into the range the server takes`() {
        MapViewport.normalizeLongitude(190.0) shouldBe -170.0
        MapViewport.normalizeLongitude(-190.0) shouldBe 170.0
        MapViewport.normalizeLongitude(180.0) shouldBe 180.0
        MapViewport.normalizeLongitude(-180.0) shouldBe -180.0
        MapViewport.normalizeLongitude(540.0) shouldBe 180.0
    }
}
