package eu.darken.apl.map.core

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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
    fun `fromUrl parses camera from valid URL`() {
        val url = "https://globe.airplanes.live/?lat=52.5&lon=13.4&zoom=8.5"

        val options = MapOptions.fromUrl(url)

        options shouldNotBe null
        options!!.camera shouldNotBe null
        options.camera!!.lat shouldBe 52.5
        options.camera!!.lon shouldBe 13.4
        options.camera!!.zoom shouldBe 8.5
    }

    @Test
    fun `fromUrl returns null for non-globe URL`() {
        val url = "https://example.com/?lat=52.5&lon=13.4&zoom=8.5"

        val options = MapOptions.fromUrl(url)

        options shouldBe null
    }

    @Test
    fun `fromUrl returns options with null camera when URL lacks camera params`() {
        val url = "https://globe.airplanes.live/?scale=1.1"

        val options = MapOptions.fromUrl(url)

        options shouldNotBe null
        options!!.camera shouldBe null
    }

    @Test
    fun `fromUrl handles partial camera params - returns null camera`() {
        val url = "https://globe.airplanes.live/?lat=52.5&lon=13.4"

        val options = MapOptions.fromUrl(url)

        options shouldNotBe null
        options!!.camera shouldBe null  // zoom is missing, so camera should be null
    }

    @Test
    fun `Camera data class stores correct values`() {
        val camera = MapOptions.Camera(lat = 40.7128, lon = -74.006, zoom = 12.0)

        camera.lat shouldBe 40.7128
        camera.lon shouldBe -74.006
        camera.zoom shouldBe 12.0
    }

    @Test
    fun `createUrl roundtrip - camera survives URL creation and parsing`() {
        val original = MapOptions(
            camera = MapOptions.Camera(lat = 48.8566, lon = 2.3522, zoom = 11.0)
        )

        val url = original.createUrl()
        val parsed = MapOptions.fromUrl(url)

        parsed shouldNotBe null
        parsed!!.camera shouldNotBe null
        parsed.camera!!.lat shouldBe original.camera!!.lat
        parsed.camera!!.lon shouldBe original.camera!!.lon
        parsed.camera!!.zoom shouldBe original.camera!!.zoom
    }
}
