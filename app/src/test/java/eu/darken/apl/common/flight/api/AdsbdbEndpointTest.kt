package eu.darken.apl.common.flight.api

import eu.darken.apl.common.http.HttpModule
import eu.darken.apl.common.serialization.SerializationModule
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.coroutine.TestDispatcherProvider

class AdsbdbEndpointTest : BaseTest() {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var endpoint: AdsbdbEndpoint

    @BeforeEach
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val json = SerializationModule().json()
        endpoint = AdsbdbEndpoint(
            baseClient = HttpModule().baseHttpClient(),
            jsonConverterFactory = HttpModule().jsonConverter(json),
            dispatcherProvider = TestDispatcherProvider(),
            json = json,
        ).apply {
            baseUrl = mockWebServer.url("/").toString()
        }
    }

    @AfterEach
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `successful route lookup`() = runTest {
        val responseBody = """
            {
              "response": {
                "flightroute": {
                  "callsign": "BAW256",
                  "callsign_icao": "BAW256",
                  "callsign_iata": "BA256",
                  "airline": {
                    "name": "British Airways",
                    "icao": "BAW",
                    "iata": "BA",
                    "country": "United Kingdom",
                    "callsign": "SPEEDBIRD"
                  },
                  "origin": {
                    "country_iso_name": "IN",
                    "country_name": "India",
                    "elevation": 777,
                    "iata_code": "DEL",
                    "icao_code": "VIDP",
                    "latitude": 28.5665,
                    "longitude": 77.1031,
                    "municipality": "Delhi",
                    "name": "Indira Gandhi International Airport"
                  },
                  "destination": {
                    "country_iso_name": "GB",
                    "country_name": "United Kingdom",
                    "elevation": 83,
                    "iata_code": "LHR",
                    "icao_code": "EGLL",
                    "latitude": 51.4706,
                    "longitude": -0.461941,
                    "municipality": "London",
                    "name": "London Heathrow Airport"
                  }
                }
              }
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setBody(responseBody))

        val result = endpoint.getByCallsign("BAW256")

        result.shouldNotBeNull()
        result.callsign shouldBe "BAW256"
        result.origin.shouldNotBeNull()
        result.origin!!.icaoCode shouldBe "VIDP"
        result.origin!!.iataCode shouldBe "DEL"
        result.origin!!.name shouldBe "Indira Gandhi International Airport"
        result.origin!!.countryName shouldBe "India"
        result.destination.shouldNotBeNull()
        result.destination!!.icaoCode shouldBe "EGLL"
        result.destination!!.iataCode shouldBe "LHR"

        val request = mockWebServer.takeRequest()
        request.path shouldBe "/v0/callsign/BAW256"
    }

    @Test
    fun `unknown callsign returns null`() = runTest {
        val responseBody = """
            {
              "response": {
                "flightroute": "unknown callsign"
              }
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setBody(responseBody))

        val result = endpoint.getByCallsign("UNKNOWN123")

        result.shouldBeNull()
    }

    @Test
    fun `unexpected payload returns null`() = runTest {
        val responseBody = """
            {
              "response": {
                "flightroute": [1, 2, 3]
              }
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setBody(responseBody))

        val result = endpoint.getByCallsign("BAW256")

        result.shouldBeNull()
    }
}
