package eu.darken.apl.account.core.api

import eu.darken.apl.account.core.SessionExpiredException
import eu.darken.apl.account.core.auth.AuthManager
import eu.darken.apl.common.http.HttpModule
import eu.darken.apl.common.serialization.SerializationModule
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.coroutine.TestDispatcherProvider
import java.time.Instant

class AccountEndpointTest : BaseTest() {
    private lateinit var mockWebServer: MockWebServer

    private fun createEndpoint(token: String? = "test-token"): AccountEndpoint {
        val authManager = mockk<AuthManager>().apply {
            coEvery { getValidAccessToken() } returns (token ?: "test-token")
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
    fun `401 surfaces as SessionExpiredException`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))
        val endpoint = createEndpoint()

        shouldThrow<SessionExpiredException> {
            endpoint.getIdentity()
        }
    }
}
