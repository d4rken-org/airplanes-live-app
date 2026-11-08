package eu.darken.apl.airport.core

import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.server.api.AirportSearchRequest
import eu.darken.apl.server.api.AirportSearchResponse
import eu.darken.apl.server.api.AirportSearchResultDto
import eu.darken.apl.server.api.AirportSummaryDto
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.ServerCodes
import eu.darken.apl.server.api.ServerEndpoint
import eu.darken.apl.server.compat.AppCompatibility
import eu.darken.apl.server.compat.CompatState
import eu.darken.apl.server.session.SessionManager
import eu.darken.apl.server.session.SessionRevokedException
import eu.darken.apl.server.session.SessionState
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.io.IOException

class AirportRepoTest : BaseTest() {

    private lateinit var endpoint: ServerEndpoint
    private lateinit var sessionManager: SessionManager
    private lateinit var sessionState: MutableStateFlow<SessionState>
    private lateinit var compatState: MutableStateFlow<CompatState>
    private lateinit var compatibility: AppCompatibility

    private var now = 10_000L
    private val clock = object : MonotonicClock {
        override fun elapsed(): Long = now
    }

    private val terms = listOf("EGLL")

    private val response = AirportSearchResponse(
        serverTime = 1_794_571_200_000L,
        results = listOf(
            AirportSearchResultDto(
                index = 0,
                status = AirportSearchResultDto.STATUS_ANSWERED,
                airports = listOf(
                    AirportSummaryDto(
                        id = "oa:2434",
                        name = "London Heathrow Airport",
                        type = AirportSummaryDto.TYPE_LARGE,
                        ident = "EGLL",
                        latitude = 51.4706,
                        longitude = -0.461941,
                        icao = "EGLL",
                        iata = "LHR",
                        matchedCode = "EGLL",
                    ),
                ),
            ),
        ),
    )

    @BeforeEach
    fun setup() {
        endpoint = mockk()
        sessionState = MutableStateFlow(SessionState.Active("installation"))
        sessionManager = mockk {
            every { state } returns sessionState
        }
        compatState = MutableStateFlow(CompatState.Ok)
        compatibility = mockk {
            every { state } returns compatState
            coEvery { awaitLoaded() } just Runs
        }
        coEvery { sessionManager.authed<Any>(any()) } coAnswers {
            firstArg<suspend (String) -> Any>().invoke("token")
        }
        coEvery { endpoint.searchAirports("token", AirportSearchRequest(terms)) } returns response
    }

    private fun createRepo() = AirportRepo(
        endpoint = endpoint,
        sessionManager = sessionManager,
        compatibility = compatibility,
        clock = clock,
    )

    private fun failWith(error: Throwable) {
        coEvery { endpoint.searchAirports(any(), any()) } throws error
    }

    private fun requests(count: Int) = coVerify(exactly = count) { endpoint.searchAirports(any(), any()) }

    private fun forgetSessionCalls() = clearMocks(sessionManager, answers = false)

    private fun sessionCalls(count: Int) = coVerify(exactly = count) { sessionManager.authed<Any>(any()) }

    @Test
    fun `a success carries the raw results`() = runTest {
        createRepo().search(terms) shouldBe AirportSearch.Answered(response.results)
        coVerify { endpoint.searchAirports("token", AirportSearchRequest(listOf("EGLL"))) }
    }

    @Test
    fun `a server without the endpoint is unavailable`() = runTest {
        failWith(ServerApiException(code = "http_404", status = 404))
        createRepo().search(terms) shouldBe AirportSearch.Unavailable
    }

    @Test
    fun `both throttle codes are rate limited`() = runTest {
        listOf(ServerCodes.QUOTA_EXCEEDED, ServerCodes.INSTALLATION_RATE_EXCEEDED, "http_429").forEach { code ->
            withClue(code) {
                failWith(ServerApiException(code = code, status = 429, retryAfterSeconds = 7))
                createRepo().search(terms) shouldBe AirportSearch.RateLimited
            }
        }
    }

    @Test
    fun `a restricted installation is restricted`() = runTest {
        failWith(ServerApiException(code = ServerCodes.INSTALLATION_RESTRICTED, status = 403))
        createRepo().search(terms) shouldBe AirportSearch.Restricted
    }

    @Test
    fun `an update refusal requires an update`() = runTest {
        failWith(ServerApiException(code = ServerCodes.APP_UPDATE_REQUIRED, status = 403))
        createRepo().search(terms) shouldBe AirportSearch.UpdateRequired
    }

    @Test
    fun `a stored update requirement answers without a request`() = runTest {
        compatState.value = CompatState.UpdateRequired
        createRepo().search(terms) shouldBe AirportSearch.UpdateRequired
        requests(0)
    }

    @Test
    fun `a revoked session is revoked`() = runTest {
        coEvery { sessionManager.authed<Any>(any()) } throws SessionRevokedException()
        createRepo().search(terms) shouldBe AirportSearch.Revoked
        requests(0)
    }

    @Test
    fun `a revocation refusal is revoked`() = runTest {
        failWith(ServerApiException(code = ServerCodes.INSTALLATION_REVOKED, status = 403))
        createRepo().search(terms) shouldBe AirportSearch.Revoked
    }

    @Test
    fun `network errors fail`() = runTest {
        val error = IOException("offline")
        failWith(error)
        val result = createRepo().search(terms)
        result.shouldBeInstanceOf<AirportSearch.Failed>()
        result.error shouldBeSameInstanceAs error
    }

    @Test
    fun `other server errors fail`() = runTest {
        listOf(
            ServerApiException(code = ServerCodes.INVALID_REQUEST, status = 400),
            ServerApiException(code = ServerCodes.PAYLOAD_TOO_LARGE, status = 413),
            ServerApiException(code = ServerCodes.INTERNAL, status = 500),
            ServerApiException(code = ServerCodes.REQUEST_TIMEOUT, status = 503, retryAfterSeconds = 1),
            ServerApiException(code = ServerCodes.DATABASE_UNAVAILABLE, status = 503, retryAfterSeconds = 1),
        ).forEach { error ->
            withClue(error.code) {
                failWith(error)
                val result = createRepo().search(terms)
                result.shouldBeInstanceOf<AirportSearch.Failed>()
                result.error shouldBeSameInstanceAs error
            }
        }
    }

    @Test
    fun `cancellation is rethrown`() = runTest {
        failWith(CancellationException("left"))
        shouldThrow<CancellationException> { createRepo().search(terms) }
    }

    @Test
    fun `a throttled search holds further searches until the retry delay passed`() = runTest {
        val repo = createRepo()
        failWith(ServerApiException(code = ServerCodes.QUOTA_EXCEEDED, status = 429, retryAfterSeconds = 7))
        repo.search(terms) shouldBe AirportSearch.RateLimited
        requests(1)

        coEvery { endpoint.searchAirports(any(), any()) } returns response
        now += 6_999
        repo.search(terms) shouldBe AirportSearch.RateLimited
        requests(1)

        now += 1
        repo.search(terms) shouldBe AirportSearch.Answered(response.results)
        requests(2)
    }

    @Test
    fun `a throttled search without a retry delay holds for the default delay`() = runTest {
        val repo = createRepo()
        failWith(ServerApiException(code = "http_429", status = 429))
        repo.search(terms) shouldBe AirportSearch.RateLimited

        coEvery { endpoint.searchAirports(any(), any()) } returns response
        now += 999
        repo.search(terms) shouldBe AirportSearch.RateLimited
        now += 1
        repo.search(terms) shouldBe AirportSearch.Answered(response.results)
    }

    @Test
    fun `overlapping throttled searches keep the longer hold`() = runTest {
        val repo = createRepo()
        val answers = ArrayDeque<CompletableDeferred<AirportSearchResponse>>()
        val first = CompletableDeferred<AirportSearchResponse>().also { answers.addLast(it) }
        val second = CompletableDeferred<AirportSearchResponse>().also { answers.addLast(it) }
        coEvery { endpoint.searchAirports(any(), any()) } coAnswers { answers.removeFirst().await() }

        val firstSearch = async { repo.search(terms) }
        runCurrent()
        val secondSearch = async { repo.search(terms) }
        runCurrent()
        requests(2)

        second.completeExceptionally(
            ServerApiException(code = ServerCodes.QUOTA_EXCEEDED, status = 429, retryAfterSeconds = 60)
        )
        runCurrent()
        first.completeExceptionally(
            ServerApiException(code = ServerCodes.INSTALLATION_RATE_EXCEEDED, status = 429, retryAfterSeconds = 10)
        )
        firstSearch.await() shouldBe AirportSearch.RateLimited
        secondSearch.await() shouldBe AirportSearch.RateLimited

        coEvery { endpoint.searchAirports(any(), any()) } returns response
        now += 30_000
        repo.search(terms) shouldBe AirportSearch.RateLimited
        requests(2)

        now += 30_000
        repo.search(terms) shouldBe AirportSearch.Answered(response.results)
        requests(3)
    }

    @Test
    fun `a success during the hold does not end it`() = runTest {
        val repo = createRepo()
        val answers = ArrayDeque<CompletableDeferred<AirportSearchResponse>>()
        val first = CompletableDeferred<AirportSearchResponse>().also { answers.addLast(it) }
        val second = CompletableDeferred<AirportSearchResponse>().also { answers.addLast(it) }
        coEvery { endpoint.searchAirports(any(), any()) } coAnswers { answers.removeFirst().await() }

        val firstSearch = async { repo.search(terms) }
        runCurrent()
        val secondSearch = async { repo.search(terms) }
        runCurrent()

        second.completeExceptionally(
            ServerApiException(code = ServerCodes.QUOTA_EXCEEDED, status = 429, retryAfterSeconds = 20)
        )
        secondSearch.await() shouldBe AirportSearch.RateLimited
        first.complete(response)
        firstSearch.await() shouldBe AirportSearch.Answered(response.results)

        repo.search(terms) shouldBe AirportSearch.RateLimited
        requests(2)
    }

    @Test
    fun `a held search answers without asking the session`() = runTest {
        val repo = createRepo()
        failWith(ServerApiException(code = ServerCodes.QUOTA_EXCEEDED, status = 429, retryAfterSeconds = 20))
        repo.search(terms) shouldBe AirportSearch.RateLimited
        forgetSessionCalls()

        coEvery { endpoint.searchAirports(any(), any()) } returns response
        repo.search(terms) shouldBe AirportSearch.RateLimited
        sessionCalls(0)
        requests(1)
    }

    @Test
    fun `a revoked session wins over the hold without asking the session`() = runTest {
        val repo = createRepo()
        failWith(ServerApiException(code = ServerCodes.QUOTA_EXCEEDED, status = 429, retryAfterSeconds = 20))
        repo.search(terms) shouldBe AirportSearch.RateLimited
        forgetSessionCalls()

        sessionState.value = SessionState.Revoked
        repo.search(terms) shouldBe AirportSearch.Revoked
        sessionCalls(0)
        requests(1)
    }

    @Test
    fun `a required update wins over the hold`() = runTest {
        val repo = createRepo()
        failWith(ServerApiException(code = ServerCodes.QUOTA_EXCEEDED, status = 429, retryAfterSeconds = 20))
        repo.search(terms) shouldBe AirportSearch.RateLimited
        forgetSessionCalls()

        compatState.value = CompatState.UpdateRequired
        repo.search(terms) shouldBe AirportSearch.UpdateRequired
        sessionCalls(0)
        requests(1)
    }
}
