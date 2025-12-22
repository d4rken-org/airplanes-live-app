package eu.darken.apl.map.core

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.json.toComparableJson

class SavedCameraTest : BaseTest() {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `serialization roundtrip preserves values`() {
        val original = SavedCamera(lat = 51.5074, lon = -0.1278, zoom = 10.5)

        val jsonString = json.encodeToString(SavedCamera.serializer(), original)
        val restored = json.decodeFromString(SavedCamera.serializer(), jsonString)

        restored shouldBe original
        restored.lat shouldBe 51.5074
        restored.lon shouldBe -0.1278
        restored.zoom shouldBe 10.5
    }

    @Test
    fun `serializes to expected JSON format`() {
        val camera = SavedCamera(lat = 52.52, lon = 13.405, zoom = 8.0)

        val jsonString = json.encodeToString(SavedCamera.serializer(), camera)

        jsonString.toComparableJson() shouldBe """
            {
                "lat": 52.52,
                "lon": 13.405,
                "zoom": 8.0
            }
        """.toComparableJson()
    }

    @Test
    fun `deserializes from JSON string`() {
        val jsonString = """{"lat":48.8566,"lon":2.3522,"zoom":12.0}"""

        val camera = json.decodeFromString(SavedCamera.serializer(), jsonString)

        camera.lat shouldBe 48.8566
        camera.lon shouldBe 2.3522
        camera.zoom shouldBe 12.0
    }

    @Test
    fun `toCamera converts to MapOptions Camera`() {
        val savedCamera = SavedCamera(lat = 40.7128, lon = -74.006, zoom = 11.0)

        val camera = savedCamera.toCamera()

        camera.lat shouldBe 40.7128
        camera.lon shouldBe -74.006
        camera.zoom shouldBe 11.0
    }

    @Test
    fun `from creates SavedCamera from MapOptions Camera`() {
        val camera = MapOptions.Camera(lat = 35.6762, lon = 139.6503, zoom = 9.0)

        val savedCamera = SavedCamera.from(camera)

        savedCamera.lat shouldBe 35.6762
        savedCamera.lon shouldBe 139.6503
        savedCamera.zoom shouldBe 9.0
    }

    @Test
    fun `roundtrip through Camera conversion preserves values`() {
        val original = SavedCamera(lat = -33.8688, lon = 151.2093, zoom = 14.0)

        val camera = original.toCamera()
        val restored = SavedCamera.from(camera)

        restored shouldBe original
    }

    @Test
    fun `handles negative coordinates`() {
        val camera = SavedCamera(lat = -23.5505, lon = -46.6333, zoom = 7.5)

        val jsonString = json.encodeToString(SavedCamera.serializer(), camera)
        val restored = json.decodeFromString(SavedCamera.serializer(), jsonString)

        restored.lat shouldBe -23.5505
        restored.lon shouldBe -46.6333
        restored.zoom shouldBe 7.5
    }

    @Test
    fun `handles high precision decimals`() {
        val camera = SavedCamera(lat = 51.50735087738341, lon = -0.12775829999999998, zoom = 15.123456789)

        val jsonString = json.encodeToString(SavedCamera.serializer(), camera)
        val restored = json.decodeFromString(SavedCamera.serializer(), jsonString)

        restored.lat shouldBe 51.50735087738341
        restored.lon shouldBe -0.12775829999999998
        restored.zoom shouldBe 15.123456789
    }
}
