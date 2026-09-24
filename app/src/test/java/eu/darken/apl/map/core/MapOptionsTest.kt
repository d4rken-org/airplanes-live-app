package eu.darken.apl.map.core

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class MapOptionsTest : BaseTest() {

    @Test
    fun `createUrl includes camera parameters when camera is set`() {
        val options = MapOptions(
            camera = MapOptions.Camera(lat = 51.5, lon = -0.12, zoom = 10.0)
        )

        val url = options.createUrl()

        url shouldContain "lat=51.5"
        url shouldContain "lon=-0.12"
        url shouldContain "zoom=10.0"
    }

    @Test
    fun `createUrl does not include camera parameters when camera is null`() {
        val options = MapOptions(camera = null)

        val url = options.createUrl()

        url.contains("lat=") shouldBe false
        url.contains("lon=") shouldBe false
        url.contains("zoom=") shouldBe false
    }

    @Test
    fun `plain options open the plain website`() {
        MapOptions().createUrl() shouldBe AirplanesLive.URL_GLOBE
    }

    @Test
    fun `feeds open the website's view of those feeders`() {
        MapOptions(feeds = setOf("a1b2", "c3d4")).createUrl() shouldBe "${AirplanesLive.URL_GLOBE}?uuid=a1b2,c3d4"
    }

    @Test
    fun `a selection and a camera travel to the website`() {
        val url = MapOptions(
            filter = MapOptions.Filter(selected = setOf("3C65A3")),
            camera = MapOptions.Camera(lat = 50.0, lon = 8.5, zoom = 9.0),
        ).createUrl()

        url shouldBe "${AirplanesLive.URL_GLOBE}?lat=50.0&lon=8.5&zoom=9.0&icao=3C65A3"
    }

    @Test
    fun `Camera data class stores correct values`() {
        val camera = MapOptions.Camera(lat = 40.7128, lon = -74.006, zoom = 12.0)

        camera.lat shouldBe 40.7128
        camera.lon shouldBe -74.006
        camera.zoom shouldBe 12.0
    }
}
