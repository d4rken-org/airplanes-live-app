package eu.darken.apl.feeder.core.api

import eu.darken.apl.common.http.HttpModule
import eu.darken.apl.common.serialization.SerializationModule
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.HttpException
import retrofit2.Response
import testhelper.BaseTest
import testhelper.coroutine.TestDispatcherProvider
import java.io.IOException
import java.util.UUID

class FeederEndpointTest : BaseTest() {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var endpoint: FeederEndpoint

    @BeforeEach
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        endpoint = FeederEndpoint(
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

    private fun enqueueFeed(user: String) {
        mockWebServer.enqueue(MockResponse().setBody(feedJson(user)))
    }

    private fun takeQueriedId(): String? = mockWebServer.takeRequest().requestUrl!!.queryParameter("id")

    @Test
    fun `an ID the API refuses leaves the others their results`() = runTest {
        val refused = HttpException(Response.error<Any>(400, "".toResponseBody(null)))

        val results = FeederEndpoint.fetchEach(listOf("a", "b", "c")) { id ->
            if (id == "b") throw refused else "infos-$id"
        }

        results["a"] shouldBe Result.success("infos-a")
        results["b"] shouldBe Result.failure(refused)
        results["c"] shouldBe Result.success("infos-c")
    }

    @Test
    fun `a network failure fails the remaining IDs without asking for them`() = runTest {
        val timeout = IOException("timeout")
        val asked = mutableListOf<String>()

        val results = FeederEndpoint.fetchEach(listOf("a", "b", "c")) { id ->
            asked += id
            if (id == "b") throw timeout else "infos-$id"
        }

        asked shouldBe listOf("a", "b")
        results["a"] shouldBe Result.success("infos-a")
        results["b"] shouldBe Result.failure(timeout)
        results["c"] shouldBe Result.failure(timeout)
    }

    @Test
    fun `cancellation is not swallowed as a failed ID`() = runTest {
        shouldThrow<CancellationException> {
            FeederEndpoint.fetchEach(listOf("a")) { throw CancellationException("gone") }
        }
    }

    @Test
    fun `an uppercase ID is looked up in lowercase and keyed as given`() = runTest {
        enqueueFeed("feeder-a")

        val results = endpoint.getFeedInfos(setOf(FEEDER_A_UPPER))

        takeQueriedId() shouldBe FEEDER_A
        mockWebServer.requestCount shouldBe 1
        results shouldBe mapOf(FEEDER_A_UPPER to Result.success(feedInfos("feeder-a")))
    }

    @Test
    fun `case variants of one ID share one lookup and its result`() = runTest {
        enqueueFeed("feeder-a")

        val results = endpoint.getFeedInfos(setOf(FEEDER_A_UPPER, FEEDER_A))

        takeQueriedId() shouldBe FEEDER_A
        mockWebServer.requestCount shouldBe 1
        results shouldBe mapOf(
            FEEDER_A_UPPER to Result.success(feedInfos("feeder-a")),
            FEEDER_A to Result.success(feedInfos("feeder-a")),
        )
    }

    @Test
    fun `case variants the API refuses both fail and leave other IDs their results`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(400))
        enqueueFeed("feeder-b")

        val results = endpoint.getFeedInfos(setOf(FEEDER_A_UPPER, FEEDER_A, FEEDER_B))

        takeQueriedId() shouldBe FEEDER_A
        takeQueriedId() shouldBe FEEDER_B
        mockWebServer.requestCount shouldBe 2
        results.keys shouldBe setOf(FEEDER_A_UPPER, FEEDER_A, FEEDER_B)
        val refused = results.getValue(FEEDER_A).exceptionOrNull().shouldBeInstanceOf<HttpException>()
        refused.code() shouldBe 400
        results.getValue(FEEDER_A_UPPER).exceptionOrNull() shouldBeSameInstanceAs refused
        results.getValue(FEEDER_B) shouldBe Result.success(feedInfos("feeder-b"))
    }

    @Test
    fun `a network failure fails every ID, case variants and skipped ones included, without asking further`() =
        runTest {
            mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
            enqueueFeed("never-asked")

            val ids = setOf(FEEDER_A_UPPER, FEEDER_A, FEEDER_B, FEEDER_C)
            val results = endpoint.getFeedInfos(ids)

            mockWebServer.requestCount shouldBe 1
            results.keys shouldBe ids
            val failure = results.getValue(FEEDER_A_UPPER).exceptionOrNull().shouldBeInstanceOf<IOException>()
            results.getValue(FEEDER_A).exceptionOrNull() shouldBeSameInstanceAs failure
            results.getValue(FEEDER_B).exceptionOrNull() shouldBeSameInstanceAs failure
            results.getValue(FEEDER_C).exceptionOrNull() shouldBeSameInstanceAs failure
        }

    @Test
    fun `IDs without a canonical form are looked up and keyed as given`() = runTest {
        enqueueFeed("short-groups")
        enqueueFeed("all-zero")

        val results = endpoint.getFeedInfos(setOf(SHORT_GROUPS_ID, ALL_ZERO_ID))

        takeQueriedId() shouldBe SHORT_GROUPS_ID
        takeQueriedId() shouldBe ALL_ZERO_ID
        mockWebServer.requestCount shouldBe 2
        results shouldBe mapOf(
            SHORT_GROUPS_ID to Result.success(feedInfos("short-groups")),
            ALL_ZERO_ID to Result.success(feedInfos("all-zero")),
        )
    }

    companion object {
        private const val FEEDER_A = "0f3a6c1e-8a51-4c5e-9e2b-7d6b1a2c3d4e"
        private const val FEEDER_A_UPPER = "0F3A6C1E-8A51-4C5E-9E2B-7D6B1A2C3D4E"
        private const val FEEDER_B = "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d"
        private const val FEEDER_C = "5e6f7a8b-9c0d-4e1f-a2b3-c4d5e6f7a8b9"
        private const val SHORT_GROUPS_ID = "1-2-3-4-5"
        private const val ALL_ZERO_ID = "00000000-0000-0000-0000-000000000000"
        private const val MLAT_UUID = "7c1d2e3f-4a5b-4c6d-8e7f-9a0b1c2d3e4f"

        private fun feedJson(user: String) = """
            {
              "beast_clients": [],
              "mlat_clients": [
                {"user": "$user", "uuid": "$MLAT_UUID", "message_rate": 12.5, "peer_count": 14,
                 "bad_sync_timeout": 0, "outlier_percent": 1.5, "sync_interest": [], "mlat_interest": []}
              ]
            }
        """.trimIndent()

        private fun feedInfos(user: String) = FeedInfos(
            beast = emptyList(),
            mlat = listOf(
                FeedInfos.Mlat(
                    user = user,
                    uuid = UUID.fromString(MLAT_UUID),
                    messageRate = 12.5,
                    peerCount = 14,
                    badSyncTimeout = 0L,
                    outlierPercent = 1.5f,
                    syncInterest = emptyList(),
                    mlatInterest = emptyList(),
                )
            ),
        )
    }
}
