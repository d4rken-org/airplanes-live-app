package eu.darken.apl.server.api

import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.debug.logging.Logging
import eu.darken.apl.common.http.HttpModule
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.ServerModule
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.GzipSink
import okio.buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.coroutine.TestDispatcherProvider

class ServerEndpointTest : BaseTest() {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var endpoint: ServerEndpoint
    private val logSink = CapturingLogger()

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
        ).apply {
            baseUrl = mockWebServer.url("/").toString()
        }
        Logging.install(logSink)
    }

    @AfterEach
    fun teardown() {
        Logging.remove(logSink)
        mockWebServer.shutdown()
    }

    @Test
    fun `problem json is mapped to an api exception`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Content-Type", "application/problem+json")
                .setBody(
                    """
                    {
                      "type": "about:blank",
                      "title": "Too Many Requests",
                      "status": 429,
                      "detail": "daily allowance exhausted",
                      "code": "daily_allowance_exhausted",
                      "retryAfterSeconds": 42
                    }
                    """.trimIndent()
                )
        )

        val error = shouldThrow<ServerApiException> { endpoint.access("token") }
        error.code shouldBe "daily_allowance_exhausted"
        error.status shouldBe 429
        error.retryAfterSeconds shouldBe 42L
        error.detail shouldBe "daily allowance exhausted"
    }

    @Test
    fun `retry after header is used when the problem omits it`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(503)
                .setHeader("Content-Type", "application/problem+json")
                .setHeader("Retry-After", "3")
                .setBody("""{"type":"about:blank","title":"x","status":503,"detail":"x","code":"database_unavailable"}""")
        )

        val error = shouldThrow<ServerApiException> { endpoint.access("token") }
        error.code shouldBe "database_unavailable"
        error.retryAfterSeconds shouldBe 3L
    }

    @Test
    fun `non problem errors fall back to the http status`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "text/html")
                .setBody("<html>nope</html>")
        )

        val error = shouldThrow<ServerApiException> { endpoint.access("token") }
        error.code shouldBe "http_500"
        error.status shouldBe 500
        error.retryAfterSeconds.shouldBeNull()
    }

    @Test
    fun `authorization and gzip headers are sent`() = runTest {
        mockWebServer.enqueue(MockResponse().setBody(ACCESS_RESPONSE_MINIMAL))

        endpoint.access("token-value")

        val request = mockWebServer.takeRequest()
        request.getHeader("Authorization") shouldBe "Bearer token-value"
        request.getHeader("Accept-Encoding") shouldBe "gzip"
    }

    @Test
    fun `gzipped responses are decoded`() = runTest {
        val gzipped = Buffer().also { sink ->
            GzipSink(sink).buffer().use { it.writeUtf8(ACCESS_RESPONSE_MINIMAL) }
        }
        mockWebServer.enqueue(
            MockResponse()
                .setHeader("Content-Encoding", "gzip")
                .setBody(gzipped)
        )

        endpoint.access("token").tier shouldBe "free"
    }

    @Test
    fun `access response defaults survive omitted fields`() = runTest {
        mockWebServer.enqueue(MockResponse().setBody(ACCESS_RESPONSE_MINIMAL))

        val response = endpoint.access("token")
        response.limits.concurrency shouldBe 3
        response.limits.maxArResults shouldBe 300
        response.limits.maxIdentifierBatch shouldBe 100
        response.limits.maxLocationWatchResults shouldBe 100
        response.limits.maxMapResults.shouldBeNull()
        response.limits.maxLocationWatchRadiusKm.shouldBeNull()
        response.aircraftOperations shouldBe listOf("search", "ar", "map", "watches")
        response.entitlementRequests.shouldBeNull()
    }

    @Test
    fun `batch response defaults survive omitted fields`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setBody(
                """
                {
                  "operationId": "5a5c6b2e-3b0a-4a2e-9a0f-000000000001",
                  "serverTime": 1710000000000,
                  "completedAt": 1710000000000,
                  "operationExpiresAt": 1710000300000,
                  "outcomes": [{"index": 0, "status": "answered"}],
                  "aircraft": [],
                  "usage": {
                    "scope": "principal",
                    "bucket": "SEARCH",
                    "resetsAt": 1710028800000,
                    "allowance": {"limit": 25, "used": 1, "reserved": 0, "remaining": 24}
                  }
                }
                """.trimIndent()
            )
        )

        val response = endpoint.search(
            token = "token",
            request = SearchBatchRequest("5a5c6b2e-3b0a-4a2e-9a0f-000000000001", listOf(SearchTerm("DLH453"))),
        )
        response.replayed shouldBe false
        response.metadata.shouldBeNull()
        response.outcomes.single().apply {
            aircraftIds shouldBe emptyList()
            capped shouldBe false
            complete shouldBe false
            charged shouldBe false
            totalMatching.shouldBeNull()
        }
    }

    @Test
    fun `credentials never reach the logs`() = runTest {
        mockWebServer.enqueue(MockResponse().setBody("""{"challenge":"CHALLENGE-VALUE","expiresInSeconds":120}"""))
        mockWebServer.enqueue(MockResponse().setBody(TOKEN_RESPONSE))
        mockWebServer.enqueue(MockResponse().setBody(TOKEN_RESPONSE))
        mockWebServer.enqueue(MockResponse().setBody(ACCESS_RESPONSE_MINIMAL))

        endpoint.challenge()
        endpoint.enroll(
            EnrollRequest(
                challenge = "CHALLENGE-VALUE",
                publicKey = "PUBLIC-KEY-VALUE",
                signature = "ENROLL-SIGNATURE-VALUE",
                variant = "foss",
                appVersion = 1,
            )
        )
        endpoint.refresh(
            RefreshRequest(
                refreshToken = "REFRESH-TOKEN-VALUE",
                operationId = "5a5c6b2e-3b0a-4a2e-9a0f-000000000001",
                timestamp = 1710000000L,
                signature = "REFRESH-SIGNATURE-VALUE",
            )
        )
        endpoint.access("ACCESS-TOKEN-VALUE")

        val logged = logSink.lines.joinToString("\n")
        logged shouldContain "api/v1/access"
        listOf(
            "ACCESS-TOKEN-VALUE",
            "REFRESH-TOKEN-VALUE",
            "REFRESH-SIGNATURE-VALUE",
            "ENROLL-SIGNATURE-VALUE",
            "STORED-REFRESH-TOKEN",
            "STORED-ACCESS-TOKEN",
        ).forEach { secret ->
            logSink.lines.none { it.contains(secret) } shouldBe true
        }
    }

    private class CapturingLogger : Logging.Logger {
        val lines = mutableListOf<String>()

        override fun log(priority: Logging.Priority, tag: String, message: String, metaData: Map<String, Any>?) {
            synchronized(lines) { lines.add("$tag $message") }
        }
    }

    companion object {
        private val ACCESS_RESPONSE_MINIMAL = """
            {
              "tier": "free",
              "restricted": false,
              "allowanceScope": "principal",
              "limits": {
                "viewingIntervalSeconds": 5,
                "viewingBurst": 3,
                "viewingPerDay": 25000,
                "searchesPerDay": 25,
                "watchesPerDay": 1000,
                "maxRadiusNm": 25,
                "watchTypes": ["hex", "callsign"],
                "specialCategoryPreviewResults": 10,
                "maxBroadResults": 100
              },
              "usage": {
                "resetsAt": 1710028800000,
                "viewing": {"limit": 25000, "used": 0, "reserved": 0, "remaining": 25000},
                "search": {"limit": 25, "used": 0, "reserved": 0, "remaining": 25},
                "watch": {"limit": 1000, "used": 0, "reserved": 0, "remaining": 1000}
              },
              "installationRequests": {
                "viewing": {"perSecond": 0.2, "burst": 3},
                "search": {"perSecond": 1.0, "burst": 3},
                "watch": {"perSecond": 1.0, "burst": 3},
                "concurrency": 3
              }
            }
        """.trimIndent()

        private val TOKEN_RESPONSE = """
            {
              "installationId": "5a5c6b2e-3b0a-4a2e-9a0f-000000000002",
              "refreshToken": "STORED-REFRESH-TOKEN",
              "accessToken": "STORED-ACCESS-TOKEN",
              "accessExpiresAt": 1710000600000,
              "refreshExpiresAt": 1712592000000
            }
        """.trimIndent()
    }
}
