package eu.darken.apl.account.core.api

import eu.darken.apl.account.core.SessionExpiredException
import eu.darken.apl.account.core.auth.AuthManager
import eu.darken.apl.common.http.HttpModule
import eu.darken.apl.common.serialization.SerializationModule
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.HttpException
import testhelper.BaseTest
import testhelper.coroutine.TestDispatcherProvider
import java.time.Instant

class AccountEndpointTest : BaseTest() {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var authManager: AuthManager

    private fun createEndpoint(token: String = "test-token"): AccountEndpoint {
        authManager = mockk<AuthManager>(relaxed = true).apply {
            coEvery { getValidAccessToken() } returns token
        }
        return AccountEndpoint(
            baseClient = HttpModule().baseHttpClient(),
            jsonConverterFactory = HttpModule().jsonConverter(SerializationModule().json()),
            dispatcherProvider = TestDispatcherProvider(),
            authManager = authManager,
        ).apply {
            baseUrl = mockWebServer.url("/").toString()
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

    @Test
    fun `identity uses trailing-slash path and bearer header`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setBody("""{"id":42,"email":"u@example.com","handle":null,"display_name":"Alice"}""")
        )
        val endpoint = createEndpoint()

        val identity = endpoint.getIdentity()

        identity.id shouldBe 42L
        identity.email shouldBe "u@example.com"
        identity.handle.shouldBeNull()
        identity.displayName shouldBe "Alice"

        val request = mockWebServer.takeRequest()
        request.path shouldBe "/api/v1/me/"
        request.getHeader("Authorization") shouldBe "Bearer test-token"
    }

    @Test
    fun `feeders use no-trailing-slash path`() = runTest {
        mockWebServer.enqueue(MockResponse().setBody("""{"feeders":[]}"""))
        val endpoint = createEndpoint()

        endpoint.getOwnedFeeders() shouldBe emptyList()

        val request = mockWebServer.takeRequest()
        request.path shouldBe "/api/v1/me/feeders"
        request.getHeader("Authorization") shouldBe "Bearer test-token"
    }

    @Test
    fun `feeders parse with nulls, unknown status and null position`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setBody(
                """
                {"feeders":[
                    {"feeder_id":"uuid-1","name":"Roof","status":"active",
                     "last_seen":"2026-06-01T07:55:01.123456Z","country":"DE",
                     "position":{"lat":51.1,"lon":6.7}},
                    {"feeder_id":"uuid-2","name":null,"status":"weird-future-state",
                     "last_seen":null,"country":null,"position":null}
                ]}
                """.trimIndent()
            )
        )
        val endpoint = createEndpoint()

        val feeders = endpoint.getOwnedFeeders()
        feeders.size shouldBe 2

        feeders[0].feederId shouldBe "uuid-1"
        feeders[0].name shouldBe "Roof"
        feeders[0].status shouldBe "active"
        feeders[0].lastSeen shouldBe Instant.parse("2026-06-01T07:55:01.123456Z")
        feeders[0].country shouldBe "DE"
        feeders[0].position?.lat shouldBe 51.1
        feeders[0].position?.lon shouldBe 6.7

        feeders[1].feederId shouldBe "uuid-2"
        feeders[1].name.shouldBeNull()
        feeders[1].status shouldBe "weird-future-state"
        feeders[1].lastSeen.shouldBeNull()
        feeders[1].country.shouldBeNull()
        feeders[1].position.shouldBeNull()
    }

    @Test
    fun `401 for the current token clears the session and surfaces SessionExpiredException`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))
        val endpoint = createEndpoint()
        coEvery { authManager.invalidateSessionIfCurrent("test-token") } returns true

        shouldThrow<SessionExpiredException> {
            endpoint.getIdentity()
        }
        coVerify { authManager.invalidateSessionIfCurrent("test-token") }
    }

    @Test
    fun `401 for a non-current token keeps the session and rethrows the http error`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))
        val endpoint = createEndpoint()
        coEvery { authManager.invalidateSessionIfCurrent(any()) } returns false

        shouldThrow<HttpException> {
            endpoint.getIdentity()
        }
        coVerify { authManager.invalidateSessionIfCurrent("test-token") }
    }

    @Test
    fun `non-401 errors do not touch the session`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("Server Error"))
        val endpoint = createEndpoint()

        shouldThrow<HttpException> {
            endpoint.getIdentity()
        }
        coVerify(exactly = 0) { authManager.invalidateSessionIfCurrent(any()) }
    }

    @Test
    fun `getFeederDetail hits the per-feeder path with bearer header`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"feeder_id":"0f2184c3","live":{"feed":{"connected":true,"messages_per_sec":9.5},"mlat":{"connected":false}}}"""
            )
        )
        val endpoint = createEndpoint()

        val detail = endpoint.getFeederDetail("0f2184c3")

        detail.feederId shouldBe "0f2184c3"
        detail.live!!.feed!!.messagesPerSec shouldBe 9.5
        val request = mockWebServer.takeRequest()
        request.path shouldBe "/api/v1/me/feeders/0f2184c3"
        request.getHeader("Authorization") shouldBe "Bearer test-token"
    }

    @Test
    fun `getFeederDetail 401 for the current token clears the session`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))
        val endpoint = createEndpoint()
        coEvery { authManager.invalidateSessionIfCurrent("test-token") } returns true

        shouldThrow<SessionExpiredException> {
            endpoint.getFeederDetail("abc")
        }
        coVerify { authManager.invalidateSessionIfCurrent("test-token") }
    }

    @Test
    fun `getFeederDetail 429 surfaces as raw http exception`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(429).setHeader("Retry-After", "60").setBody("""{"error":"rate_limited"}""")
        )
        val endpoint = createEndpoint()

        val e = shouldThrow<HttpException> {
            endpoint.getFeederDetail("abc")
        }
        e.code() shouldBe 429
        coVerify(exactly = 0) { authManager.invalidateSessionIfCurrent(any()) }
    }
}
