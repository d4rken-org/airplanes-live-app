package eu.darken.apl.server.api

import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.http.HttpModule
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.ServerModule
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.TestCompatibilityStore
import testhelper.coroutine.TestDispatcherProvider

class AirportSearchEndpointTest : BaseTest() {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var endpoint: ServerEndpoint
    private val compatStore = TestCompatibilityStore()

    private val fakeMonotonicClock = object : MonotonicClock {
        override fun elapsed(): Long = 1_000L
    }

    @BeforeEach
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        endpoint = ServerEndpoint(
            baseClient = HttpModule().baseHttpClient(),
            json = ServerModule.serverJson(),
            dispatcherProvider = TestDispatcherProvider(),
            serverClock = ServerClock(fakeMonotonicClock),
            compatibility = compatStore.holder(),
        ).apply {
            baseUrl = mockWebServer.url("/").toString()
        }
    }

    @AfterEach
    fun teardown() {
        mockWebServer.shutdown()
        compatStore.close()
    }

    @Test
    fun `a search posts the queries with the token`() = runTest {
        mockWebServer.enqueue(MockResponse().setBody("""{"serverTime": 1794571200000, "results": []}"""))

        endpoint.searchAirports("token-value", AirportSearchRequest(listOf("CDN2", "7700", "a")))

        val request = mockWebServer.takeRequest()
        request.method shouldBe "POST"
        request.path shouldBe "/api/v1/airports/search"
        request.getHeader("Authorization") shouldBe "Bearer token-value"
        request.body.readUtf8() shouldBe """{"queries":["CDN2","7700","a"]}"""
    }

    @Test
    fun `per query results decode with omitted fields, unknown values and unknown keys`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setBody(
                """
                {
                  "serverTime": 1794571200000,
                  "futureField": {"nested": true},
                  "results": [
                    {"index": 0, "status": "answered", "truncated": false, "airports": [
                      {"id": "oa:39673", "name": "Danville Airport", "type": "small", "municipality": "Val-des-Sources", "country": "CA", "region": "CA-QC", "ident": "CA-0027", "local": "CDN2", "matchedCode": "CDN2", "latitude": 45.79425, "longitude": -71.995339, "elevationFeet": 1},
                      {"id": "oa:1", "name": "Somewhere Heliport", "type": "heliport", "ident": "XX-0001", "latitude": 1.5, "longitude": -2.5}
                    ]},
                    {"index": 1, "status": "skipped", "airports": [], "truncated": false},
                    {"index": 2, "status": "invalid_query", "airports": [], "truncated": false},
                    {"index": 3, "status": "pending", "airports": [], "truncated": true}
                  ]
                }
                """.trimIndent()
            )
        )

        val response = endpoint.searchAirports("token", AirportSearchRequest(listOf("CDN2", "7700", "a", "x1")))

        response shouldBe AirportSearchResponse(
            serverTime = 1_794_571_200_000L,
            results = listOf(
                AirportSearchResultDto(
                    index = 0,
                    status = AirportSearchResultDto.STATUS_ANSWERED,
                    airports = listOf(
                        AirportSummaryDto(
                            id = "oa:39673",
                            name = "Danville Airport",
                            type = AirportSummaryDto.TYPE_SMALL,
                            ident = "CA-0027",
                            latitude = 45.79425,
                            longitude = -71.995339,
                            municipality = "Val-des-Sources",
                            country = "CA",
                            region = "CA-QC",
                            local = "CDN2",
                            matchedCode = "CDN2",
                        ),
                        AirportSummaryDto(
                            id = "oa:1",
                            name = "Somewhere Heliport",
                            type = "heliport",
                            ident = "XX-0001",
                            latitude = 1.5,
                            longitude = -2.5,
                        ),
                    ),
                    truncated = false,
                ),
                AirportSearchResultDto(index = 1, status = AirportSearchResultDto.STATUS_SKIPPED),
                AirportSearchResultDto(index = 2, status = AirportSearchResultDto.STATUS_INVALID_QUERY),
                AirportSearchResultDto(index = 3, status = "pending", truncated = true),
            ),
        )
        response.results[0].airports[1].run {
            municipality.shouldBeNull()
            icao.shouldBeNull()
            iata.shouldBeNull()
            local.shouldBeNull()
            matchedCode.shouldBeNull()
        }
    }

    @Test
    fun `a server without the endpoint surfaces as 404`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(404))

        val error = shouldThrow<ServerApiException> {
            endpoint.searchAirports("token", AirportSearchRequest(listOf("EGLL")))
        }

        error.status shouldBe 404
        error.code shouldBe "http_404"
        error.retryAfterSeconds.shouldBeNull()
    }

    @Test
    fun `a throttled search surfaces its code and retry delay`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Content-Type", "application/problem+json")
                .setHeader("Retry-After", "37")
                .setBody("""{"type": "about:blank", "title": "Too Many Requests", "status": 429, "code": "installation_rate_exceeded", "retryAfterSeconds": 37}""")
        )

        val error = shouldThrow<ServerApiException> {
            endpoint.searchAirports("token", AirportSearchRequest(listOf("EGLL")))
        }

        error.status shouldBe 429
        error.code shouldBe ServerCodes.INSTALLATION_RATE_EXCEEDED
        error.retryAfterSeconds shouldBe 37L
    }

    @Test
    fun `a restricted installation surfaces its code`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setHeader("Content-Type", "application/problem+json")
                .setBody("""{"type": "about:blank", "title": "Forbidden", "status": 403, "code": "installation_restricted"}""")
        )

        val error = shouldThrow<ServerApiException> {
            endpoint.searchAirports("token", AirportSearchRequest(listOf("EGLL")))
        }

        error.status shouldBe 403
        error.code shouldBe ServerCodes.INSTALLATION_RESTRICTED
        error.retryAfterSeconds.shouldBeNull()
    }
}
