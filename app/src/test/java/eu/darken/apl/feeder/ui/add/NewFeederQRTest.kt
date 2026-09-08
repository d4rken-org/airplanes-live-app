package eu.darken.apl.feeder.ui.add

import eu.darken.apl.common.serialization.SerializationModule
import eu.darken.apl.feeder.core.config.FeederPosition
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.encodeToString
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelper.json.toComparableJson

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class NewFeederQRTest {

    private val json = SerializationModule().json()

    @Test
    fun `toUri generates correct URI scheme and format`() {
        val feederQR = NewFeederQR(
            receiverId = "test123",
            receiverLabel = "Test Feeder"
        )

        val result = feederQR.toUri(json)

        result.toString() shouldContain "eu_darken_apl://feeder?data="
        result.toString() shouldContain "receiverId"
        result.toString() shouldContain "test123"
        result.toString() shouldContain "receiverLabel"
        result.toString() shouldContain "Test Feeder"
    }

    @Test
    fun `toUri handles null receiverLabel correctly`() {
        val feederQR = NewFeederQR(
            receiverId = "test456",
            receiverLabel = null
        )

        val result = feederQR.toUri(json)
        val uriString = result.toString()

        uriString shouldContain "eu_darken_apl://feeder?data="
        uriString shouldContain "receiverId"
        uriString shouldContain "test456"

        // With explicitNulls = false, null fields are omitted
        (uriString.contains("receiverLabel")) shouldBe false
        (uriString.contains("null")) shouldBe false
    }

    @Test
    fun `JSON serialization produces expected format`() {
        val feederQR = NewFeederQR(
            receiverId = "receiver789",
            receiverLabel = "My Custom Feeder",
            position = FeederPosition(latitude = 40.7128, longitude = -74.0060)
        )

        val jsonString = json.encodeToString(feederQR)

        jsonString.toComparableJson() shouldBe """
            {
                "receiverId": "receiver789",
                "receiverLabel": "My Custom Feeder",
                "position": {
                    "latitude": 40.7128,
                    "longitude": -74.0060
                }
            }
        """.toComparableJson()
    }

    @Test
    fun `JSON serialization handles null receiverLabel`() {
        val feederQR = NewFeederQR(
            receiverId = "receiver456",
            receiverLabel = null
        )

        val jsonString = json.encodeToString(feederQR)

        jsonString.toComparableJson() shouldBe """
            {
                "receiverId": "receiver456"
            }
        """.toComparableJson()
    }

    @Test
    fun `URI contains properly encoded JSON data`() {
        val feederQR = NewFeederQR(
            receiverId = "special-chars_123",
            receiverLabel = "Feeder with spaces"
        )

        val result = feederQR.toUri(json)
        val uriString = result.toString()

        uriString shouldContain "eu_darken_apl://feeder?data="

        // Extract the data parameter
        val dataStart = uriString.indexOf("data=") + 5
        val jsonData = uriString.substring(dataStart)

        // Verify it's valid JSON that can be parsed back
        val parsedQR = json.decodeFromString<NewFeederQR>(jsonData)
        parsedQR.receiverId shouldBe "special-chars_123"
        parsedQR.receiverLabel shouldBe "Feeder with spaces"
    }

    @Test
    fun `toUri creates exact expected URI format`() {
        val feederQR = NewFeederQR(
            receiverId = "test-receiver",
            receiverLabel = "Test Label"
        )

        val result = feederQR.toUri(json)
        val uriString = result.toString()

        // Check the URI prefix
        uriString.startsWith("eu_darken_apl://feeder?data=") shouldBe true

        // Extract and check the JSON part
        val jsonData = uriString.substringAfter("data=")
        jsonData.toComparableJson() shouldBe """
            {
                "receiverId": "test-receiver",
                "receiverLabel": "Test Label"
            }
        """.toComparableJson()
    }

    @Test
    fun `URI scheme is correct`() {
        val feederQR = NewFeederQR(
            receiverId = "test",
            receiverLabel = "label"
        )

        val result = feederQR.toUri(json)

        result.scheme shouldBe "eu_darken_apl"
        result.host shouldBe "feeder"
        result.getQueryParameter("data") shouldContain "receiverId"
    }

    @Test
    fun `URI can be parsed back to original data`() {
        val originalQR = NewFeederQR(
            receiverId = "roundtrip-test",
            receiverLabel = "Round Trip Label",
            position = FeederPosition(latitude = 51.5074, longitude = -0.1278)
        )

        val uri = originalQR.toUri(json)
        val jsonData = uri.getQueryParameter("data")!!
        val parsedQR = json.decodeFromString<NewFeederQR>(jsonData)

        parsedQR.receiverId shouldBe originalQR.receiverId
        parsedQR.receiverLabel shouldBe originalQR.receiverLabel
        parsedQR.position?.latitude shouldBe originalQR.position?.latitude
        parsedQR.position?.longitude shouldBe originalQR.position?.longitude
    }

    @Test
    fun `URI encoded JSON matches direct JSON encoding`() {
        val feederQR = NewFeederQR(
            receiverId = "complex-id-123",
            receiverLabel = "Complex Feeder Name",
            receiverIpv4Address = "192.168.1.100",
            position = FeederPosition(latitude = 37.7749, longitude = -122.4194)
        )

        // Get direct JSON encoding
        val directJson = json.encodeToString(feederQR)

        // The expected URI string that can be copied by external reviewers
        val expectedUriString =
            "eu_darken_apl://feeder?data={\"receiverId\":\"complex-id-123\",\"receiverLabel\":\"Complex Feeder Name\",\"receiverIpv4Address\":\"192.168.1.100\",\"position\":{\"latitude\":37.7749,\"longitude\":-122.4194}}"

        // Get URI and extract the JSON data
        val uri = feederQR.toUri(json)

        // Compare the generated URI with the expected literal URI
        uri.toString() shouldBe expectedUriString

        // Also verify the JSON data can be extracted and matches the direct encoding
        val uriJsonData = uri.getQueryParameter("data")!!
        uriJsonData.toComparableJson() shouldBe directJson.toComparableJson()
    }
}
