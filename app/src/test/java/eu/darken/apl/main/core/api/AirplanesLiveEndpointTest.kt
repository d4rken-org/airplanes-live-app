package eu.darken.apl.main.core.api

import eu.darken.apl.common.datastore.DataStoreValue
import eu.darken.apl.common.http.HttpModule
import eu.darken.apl.common.serialization.SerializationModule
import eu.darken.apl.main.core.GeneralSettings
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import retrofit2.HttpException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.coroutine.TestDispatcherProvider

class AirplanesLiveEndpointTest : BaseTest() {
    private lateinit var mockWebServer: MockWebServer

    private val mockResponse = """{"ac":[],"total":0,"msg":"No error","now":1234567890,"ctime":1234567890,"ptime":0}"""

    private val apiKeyValidFlow = MutableStateFlow<Boolean?>(null)

    private fun createEndpoint(apiKey: String? = null): AirplanesLiveEndpoint {
        val generalSettings = mockk<GeneralSettings>().apply {
            every { airplanesLiveApiKey } returns mockk<DataStoreValue<String?>>().apply {
                every { flow } returns flowOf(apiKey)
            }
            every { apiKeyValid } returns apiKeyValidFlow
        }

        return AirplanesLiveEndpoint(
            baseClient = HttpModule().baseHttpClient(),
            dispatcherProvider = TestDispatcherProvider(),
            jsonConverterFactory = HttpModule().jsonConverter(SerializationModule().json()),
            generalSettings = generalSettings,
        ).apply {
            baseUrl = mockWebServer.url("/v2/").toString()
            restBaseUrl = mockWebServer.url("/rest/").toString()
        }
    }

    @BeforeEach
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
    }

    @AfterEach
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Nested
    inner class V2Api {

        @Test
        fun `hex request uses path endpoint`() = runTest {
            mockWebServer.enqueue(MockResponse().setBody(mockResponse))
            val endpoint = createEndpoint(apiKey = null)

            endpoint.getByHex(setOf("A213BD"))

            val request = mockWebServer.takeRequest()
            request.path shouldContain "/v2/hex/A213BD"
        }

        @Test
        fun `no auth header sent`() = runTest {
            mockWebServer.enqueue(MockResponse().setBody(mockResponse))
            val endpoint = createEndpoint(apiKey = null)

            endpoint.getByHex(setOf("A213BD"))

            val request = mockWebServer.takeRequest()
            request.getHeader("auth").shouldBeNull()
        }

        @Test
        fun `aircraft by squawks`() = runTest {
            mockWebServer.enqueue(MockResponse().setBody(mockResponse))
            mockWebServer.enqueue(MockResponse().setBody(mockResponse))
            val endpoint = createEndpoint(apiKey = null)

            endpoint.getBySquawk(setOf("3532", "1200")).apply {
                this shouldNotBe null
                this.size shouldBe 0
            }

            val request1 = mockWebServer.takeRequest()
            request1.path shouldContain "/v2/squawk/"
        }

        @Test
        fun `aircraft by callsigns`() = runTest {
            mockWebServer.enqueue(MockResponse().setBody(mockResponse))
            val endpoint = createEndpoint(apiKey = null)

            endpoint.getByCallsign(setOf("AAL1002", "AAL1328")).apply {
                this shouldNotBe null
                this.size shouldBe 0
            }

            val request = mockWebServer.takeRequest()
            request.path shouldContain "/v2/callsign/"
        }

        @Test
        fun `aircraft by registration`() = runTest {
            mockWebServer.enqueue(MockResponse().setBody(mockResponse))
            val endpoint = createEndpoint(apiKey = null)

            endpoint.getByRegistration(setOf("N656NK")).apply {
                this shouldNotBe null
                this.size shouldBe 0
            }

            val request = mockWebServer.takeRequest()
            request.path shouldContain "/v2/reg/"
        }

        @Test
        fun `aircraft by airframe`() = runTest {
            mockWebServer.enqueue(MockResponse().setBody(mockResponse))
            val endpoint = createEndpoint(apiKey = null)

            endpoint.getByAirframe(setOf("F16")).apply {
                this shouldNotBe null
                this.size shouldBe 0
            }

            val request = mockWebServer.takeRequest()
            request.path shouldContain "/v2/type/"
        }

        @Test
        fun `aircraft by location`() = runTest {
            mockWebServer.enqueue(MockResponse().setBody(mockResponse))
            val endpoint = createEndpoint(apiKey = null)

            endpoint.getByLocation(51.473419, -0.491683, 100_000).apply {
                this shouldNotBe null
                this.size shouldBe 0
            }

            val request = mockWebServer.takeRequest()
            request.path shouldContain "/v2/point/"
        }

        @Test
        fun `military uses path endpoint`() = runTest {
            mockWebServer.enqueue(MockResponse().setBody(mockResponse))
            val endpoint = createEndpoint(apiKey = null)

            endpoint.getMilitary()

            val request = mockWebServer.takeRequest()
            request.path shouldBe "/v2/mil"
        }

        @Test
        fun `ladd uses path endpoint`() = runTest {
            mockWebServer.enqueue(MockResponse().setBody(mockResponse))
            val endpoint = createEndpoint(apiKey = null)

            endpoint.getLADD()

            val request = mockWebServer.takeRequest()
            request.path shouldBe "/v2/ladd"
        }

        @Test
        fun `pia uses path endpoint`() = runTest {
            mockWebServer.enqueue(MockResponse().setBody(mockResponse))
            val endpoint = createEndpoint(apiKey = null)

            endpoint.getPIA()

            val request = mockWebServer.takeRequest()
            request.path shouldBe "/v2/pia"
        }
    }

    @Nested
    inner class RestApi {

        @Test
        fun `hex request uses find_hex query param`() = runTest {
            mockWebServer.enqueue(MockResponse().setBody(mockResponse))
            val endpoint = createEndpoint(apiKey = "test-key")

            endpoint.getByHex(setOf("A213BD"))

            val request = mockWebServer.takeRequest()
            val url = request.requestUrl!!
            url.queryParameter("find_hex") shouldBe "A213BD"
        }

        @Test
        fun `multiple hexes joined with comma`() = runTest {
            mockWebServer.enqueue(MockResponse().setBody(mockResponse))
            val endpoint = createEndpoint(apiKey = "test-key")

            endpoint.getByHex(setOf("A213BD", "A4FBAC", "B00123"))

            val request = mockWebServer.takeRequest()
            val findHex = request.requestUrl!!.queryParameter("find_hex")!!
            findHex shouldContain "A213BD"
            findHex shouldContain "A4FBAC"
            findHex shouldContain "B00123"
        }

        @Test
        fun `squawk uses all and filter_squawk`() = runTest {
            mockWebServer.enqueue(MockResponse().setBody(mockResponse))
            val endpoint = createEndpoint(apiKey = "test-key")

            endpoint.getBySquawk(setOf("3532"))

            val request = mockWebServer.takeRequest()
            val query = request.requestUrl!!.encodedQuery!!
            query shouldContain "all"
            query shouldContain "filter_squawk=3532"
        }

        @Test
        fun `callsign uses find_callsign`() = runTest {
            mockWebServer.enqueue(MockResponse().setBody(mockResponse))
            val endpoint = createEndpoint(apiKey = "test-key")

            endpoint.getByCallsign(setOf("AAL1002"))

            val request = mockWebServer.takeRequest()
            request.requestUrl!!.queryParameter("find_callsign") shouldBe "AAL1002"
        }

        @Test
        fun `registration uses find_reg`() = runTest {
            mockWebServer.enqueue(MockResponse().setBody(mockResponse))
            val endpoint = createEndpoint(apiKey = "test-key")

            endpoint.getByRegistration(setOf("N656NK"))

            val request = mockWebServer.takeRequest()
            request.requestUrl!!.queryParameter("find_reg") shouldBe "N656NK"
        }

        @Test
        fun `airframe uses find_type`() = runTest {
            mockWebServer.enqueue(MockResponse().setBody(mockResponse))
            val endpoint = createEndpoint(apiKey = "test-key")

            endpoint.getByAirframe(setOf("F16"))

            val request = mockWebServer.takeRequest()
            request.requestUrl!!.queryParameter("find_type") shouldBe "F16"
        }

        @Test
        fun `military uses all and filter_mil`() = runTest {
            mockWebServer.enqueue(MockResponse().setBody(mockResponse))
            val endpoint = createEndpoint(apiKey = "test-key")

            endpoint.getMilitary()

            val request = mockWebServer.takeRequest()
            val query = request.requestUrl!!.encodedQuery!!
            query shouldContain "all"
            query shouldContain "filter_mil"
        }

        @Test
        fun `flag params have no trailing equals`() = runTest {
            mockWebServer.enqueue(MockResponse().setBody(mockResponse))
            val endpoint = createEndpoint(apiKey = "test-key")

            endpoint.getMilitary()

            val request = mockWebServer.takeRequest()
            val query = request.requestUrl!!.encodedQuery!!
            query.split("&").none { it.endsWith("=") } shouldBe true
        }

        @Test
        fun `ladd uses all and filter_ladd`() = runTest {
            mockWebServer.enqueue(MockResponse().setBody(mockResponse))
            val endpoint = createEndpoint(apiKey = "test-key")

            endpoint.getLADD()

            val request = mockWebServer.takeRequest()
            val query = request.requestUrl!!.encodedQuery!!
            query shouldContain "all"
            query shouldContain "filter_ladd"
        }

        @Test
        fun `pia uses all and filter_pia`() = runTest {
            mockWebServer.enqueue(MockResponse().setBody(mockResponse))
            val endpoint = createEndpoint(apiKey = "test-key")

            endpoint.getPIA()

            val request = mockWebServer.takeRequest()
            val query = request.requestUrl!!.encodedQuery!!
            query shouldContain "all"
            query shouldContain "filter_pia"
        }

        @Test
        fun `location uses circle query param`() = runTest {
            mockWebServer.enqueue(MockResponse().setBody(mockResponse))
            val endpoint = createEndpoint(apiKey = "test-key")

            endpoint.getByLocation(51.473419, -0.491683, 100_000)

            val request = mockWebServer.takeRequest()
            val circle = request.requestUrl!!.queryParameter("circle")!!
            circle shouldContain "51.473419"
            circle shouldContain "-0.491683"
        }

        @Test
        fun `auth header is sent`() = runTest {
            mockWebServer.enqueue(MockResponse().setBody(mockResponse))
            val endpoint = createEndpoint(apiKey = "test-key")

            endpoint.getByHex(setOf("A213BD"))

            val request = mockWebServer.takeRequest()
            request.getHeader("auth") shouldBe "test-key"
        }

        @Test
        fun `jv2 param is included`() = runTest {
            mockWebServer.enqueue(MockResponse().setBody(mockResponse))
            val endpoint = createEndpoint(apiKey = "test-key")

            endpoint.getByHex(setOf("A213BD"))

            val request = mockWebServer.takeRequest()
            request.requestUrl!!.encodedQuery!! shouldContain "jv2"
        }
    }

    @Nested
    inner class Fallback {

        @Test
        fun `rest 403 falls back to v2`() = runTest {
            // REST returns 403
            mockWebServer.enqueue(MockResponse().setResponseCode(403).setBody("Forbidden"))
            // v2 fallback returns success
            mockWebServer.enqueue(MockResponse().setBody(mockResponse))

            val endpoint = createEndpoint(apiKey = "bad-key")

            endpoint.getMilitary().apply {
                this shouldNotBe null
                this.size shouldBe 0
            }

            // First request was REST
            val restRequest = mockWebServer.takeRequest()
            restRequest.requestUrl!!.encodedQuery!! shouldContain "filter_mil"
            restRequest.getHeader("auth") shouldBe "bad-key"

            // Second request falls back to v2
            val v2Request = mockWebServer.takeRequest()
            v2Request.path shouldBe "/v2/mil"
            v2Request.getHeader("auth").shouldBeNull()

            // Key marked invalid
            apiKeyValidFlow.value shouldBe false
        }

        @Test
        fun `rest 500 propagates without fallback`() = runTest {
            mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))
            val endpoint = createEndpoint(apiKey = "test-key")

            shouldThrow<HttpException> {
                endpoint.getMilitary()
            }.code() shouldBe 500

            mockWebServer.requestCount shouldBe 1
        }
    }
}
