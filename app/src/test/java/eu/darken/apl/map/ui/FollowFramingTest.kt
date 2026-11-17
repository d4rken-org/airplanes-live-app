package eu.darken.apl.map.ui

import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class FollowFramingTest : BaseTest() {

    private fun MapSpot.shouldBeAt(latitude: Double, longitude: Double, tolerance: Double = 1e-9) {
        this.latitude shouldBe (latitude plusOrMinus tolerance)
        this.longitude shouldBe (longitude plusOrMinus tolerance)
    }

    @Test
    fun `no step leaves the spot where it is`() {
        shiftedOnMap(MapSpot(48.1, 11.5), from = MapSpot(50.0, 8.0), to = MapSpot(50.0, 8.0)).shouldBeAt(48.1, 11.5)
    }

    @Test
    fun `a step south at the equator is shorter in degrees further north`() {
        shiftedOnMap(MapSpot(60.0, 10.0), from = MapSpot(0.0, 0.0), to = MapSpot(-1.0, 0.0))
            .shouldBeAt(59.4962, 10.0, tolerance = 1e-4)
    }

    @Test
    fun `a sideways step, as on a rotated map, moves the longitude`() {
        shiftedOnMap(MapSpot(0.0, 0.0), from = MapSpot(10.0, 20.0), to = MapSpot(10.0, 22.0)).shouldBeAt(0.0, 2.0)
    }

    @Test
    fun `a zoom level deeper the step is half as long`() {
        shiftedOnMap(MapSpot(0.0, 0.0), from = MapSpot(10.0, 20.0), to = MapSpot(10.0, 22.0), zoomIn = 1.0)
            .shouldBeAt(0.0, 1.0)
        shiftedOnMap(MapSpot(60.0, 10.0), from = MapSpot(0.0, 0.0), to = MapSpot(-2.0, 0.0), zoomIn = 1.0)
            .shouldBeAt(59.4962, 10.0, tolerance = 1e-3)
    }

    @Test
    fun `a step across the antimeridian is the short way`() {
        val from = MapSpot(0.0, 179.5)
        val to = MapSpot(0.0, -179.5)

        shiftedOnMap(MapSpot(0.0, 170.0), from, to).shouldBeAt(0.0, 171.0)
        shiftedOnMap(MapSpot(0.0, 179.8), from, to).shouldBeAt(0.0, -179.2)
    }
}
