package eu.darken.apl.main.core.api

import eu.darken.apl.common.datastore.DataStoreValue
import eu.darken.apl.common.http.HttpModule
import eu.darken.apl.common.serialization.SerializationModule
import eu.darken.apl.main.core.GeneralSettings
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.coroutine.TestDispatcherProvider

class AirplanesLiveEndpointTest : BaseTest() {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var endpoint: AirplanesLiveEndpoint

    private val mockResponse = """{"ac":[],"total":0,"msg":"No error","now":1234567890,"ctime":1234567890,"ptime":0}"""

    private val generalSettings = mockk<GeneralSettings>().apply {
        every { airplanesLiveApiKey } returns mockk<DataStoreValue<String?>>().apply {
            every { flow } returns flowOf(null)
        }
        every { apiKeyValid } returns MutableStateFlow(null)
    }

    @BeforeEach
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        endpoint = AirplanesLiveEndpoint(
            baseClient = HttpModule().baseHttpClient(),
            dispatcherProvider = TestDispatcherProvider(),
            jsonConverterFactory = HttpModule().jsonConverter(SerializationModule().json()),
            generalSettings = generalSettings,
        ).apply {
            baseUrl = mockWebServer.url("/").toString()
        }
    }

    @AfterEach
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `aircraft by squawks`() = runTest {
        mockWebServer.enqueue(MockResponse().setBody(mockResponse))
        mockWebServer.enqueue(MockResponse().setBody(mockResponse))
        mockWebServer.enqueue(MockResponse().setBody(mockResponse))

        endpoint.getBySquawk(setOf("3532", "1200", "0420")).apply {
            this shouldNotBe null
            this.size shouldBe 0
        }
    }

    @Test
    fun `aircraft by hexes`() = runTest {
        mockWebServer.enqueue(MockResponse().setBody(mockResponse))

        endpoint.getByHex(setOf("A213BD,A4FBAC")).apply {
            this shouldNotBe null
            this.size shouldBe 0
        }
    }

    @Test
    fun `aircraft by callsigns`() = runTest {
        mockWebServer.enqueue(MockResponse().setBody(mockResponse))

        endpoint.getByCallsign(setOf("AAL1002,AAL1328")).apply {
            this shouldNotBe null
            this.size shouldBe 0
        }
    }

    @Test
    fun `aircraft by registration`() = runTest {
        mockWebServer.enqueue(MockResponse().setBody(mockResponse))

        endpoint.getByRegistration(setOf("N656NK")).apply {
            this shouldNotBe null
            this.size shouldBe 0
        }
    }

    @Test
    fun `aircraft by airframe`() = runTest {
        mockWebServer.enqueue(MockResponse().setBody(mockResponse))

        endpoint.getByAirframe(setOf("F16")).apply {
            this shouldNotBe null
            this.size shouldBe 0
        }
    }

    @Test
    fun `aircraft by location`() = runTest {
        mockWebServer.enqueue(MockResponse().setBody(mockResponse))

        endpoint.getByLocation(51.473419, -0.491683, 100).apply {
            this shouldNotBe null
            this.size shouldBe 0
        }
    }
}
