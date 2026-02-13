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

class HexdbEndpointTest : BaseTest() {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var endpoint: HexdbEndpoint

    @BeforeEach
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        endpoint = HexdbEndpoint(
            baseClient = HttpModule().baseHttpClient(),
            jsonConverterFactory = HttpModule().jsonConverter(SerializationModule().json()),
            dispatcherProvider = TestDispatcherProvider(),
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
              "flight": "BAW256",
              "route": "VIDP-EGLL"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setBody(responseBody))

        val result = endpoint.getByCallsign("BAW256")

        result.shouldNotBeNull()
        result.flight shouldBe "BAW256"
        result.route shouldBe "VIDP-EGLL"

        val request = mockWebServer.takeRequest()
        request.path shouldBe "/api/v1/route/icao/BAW256"
    }

    @Test
    fun `empty route returns null`() = runTest {
        val responseBody = """
            {
              "flight": "UNKNOWN",
              "route": ""
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setBody(responseBody))

        val result = endpoint.getByCallsign("UNKNOWN")

        result.shouldBeNull()
    }

    @Test
    fun `null route returns null`() = runTest {
        val responseBody = """
            {
              "flight": "UNKNOWN"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setBody(responseBody))

        val result = endpoint.getByCallsign("UNKNOWN")

        result.shouldBeNull()
    }
}
