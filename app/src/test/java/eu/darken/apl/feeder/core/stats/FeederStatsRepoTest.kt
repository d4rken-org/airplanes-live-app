package eu.darken.apl.feeder.core.stats

import eu.darken.apl.account.core.api.AccountEndpoint
import eu.darken.apl.account.core.api.FeederDetailResponse
import eu.darken.apl.account.core.SessionExpiredException
import eu.darken.apl.account.core.auth.AuthManager
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test
import retrofit2.HttpException
import retrofit2.Response
import testhelper.BaseTest
import testhelper.coroutine.TestDispatcherProvider
import java.io.IOException
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CoroutineScope

class FeederStatsRepoTest : BaseTest() {

    private var epoch = 1
    private val loggedIn = MutableStateFlow(true)
    private lateinit var endpoint: AccountEndpoint
    private var now: Instant = Instant.parse("2026-07-19T12:00:00Z")
    private val sleeps = mutableListOf<Duration>()

    private fun createRepo(scope: CoroutineScope): FeederStatsRepo {
        val authManager = mockk<AuthManager> {
            every { sessionEpoch } answers { epoch }
            every { isLoggedIn } returns loggedIn
        }
        endpoint = mockk()
        return FeederStatsRepo(
            accountEndpoint = endpoint,
            authManager = authManager,
            dispatcherProvider = TestDispatcherProvider(),
            appScope = scope,
        ).apply {
            clock = { now }
            sleeper = { duration ->
                sleeps += duration
                now += duration
            }
        }
    }

    private fun response(id: String = "abc") = FeederDetailResponse(feederId = id)

    @Test
    fun `second call within ttl is served from cache`() = runTest {
        val repo = createRepo(backgroundScope)
        coEvery { endpoint.getFeederDetail("abc") } returns response()

        repo.getDetail("abc")
        now += Duration.ofSeconds(100)
        repo.getDetail("abc")

        coVerify(exactly = 1) { endpoint.getFeederDetail("abc") }
    }

    @Test
    fun `expired ttl refetches`() = runTest {
        val repo = createRepo(backgroundScope)
        coEvery { endpoint.getFeederDetail("abc") } returns response()

        repo.getDetail("abc")
        now += Duration.ofSeconds(301)
        repo.getDetail("abc")

        coVerify(exactly = 2) { endpoint.getFeederDetail("abc") }
    }

    @Test
    fun `forceRefresh bypasses a fresh cache entry`() = runTest {
        val repo = createRepo(backgroundScope)
        coEvery { endpoint.getFeederDetail("abc") } returns response()

        repo.getDetail("abc")
        repo.getDetail("abc", forceRefresh = true)

        coVerify(exactly = 2) { endpoint.getFeederDetail("abc") }
    }

    @Test
    fun `concurrent callers share one flight`() = runTest {
        val repo = createRepo(backgroundScope)
        val gate = CompletableDeferred<Unit>()
        coEvery { endpoint.getFeederDetail("abc") } coAnswers {
            gate.await()
            response()
        }

        val first = async { repo.getDetail("abc") }
        val second = async { repo.getDetail("abc") }
        runCurrent()
        gate.complete(Unit)

        first.await().fetchedAt shouldBe now
        second.await().fetchedAt shouldBe now
        coVerify(exactly = 1) { endpoint.getFeederDetail("abc") }
    }

    @Test
    fun `failed flight is cleaned up and next call retries`() = runTest {
        val repo = createRepo(backgroundScope)
        coEvery { endpoint.getFeederDetail("abc") } throws IOException("boom") andThen response()

        shouldThrow<IOException> { repo.getDetail("abc") }
        repo.getDetail("abc").fetchedAt shouldBe now

        coVerify(exactly = 2) { endpoint.getFeederDetail("abc") }
    }

    @Test
    fun `fetches are paced by the minimum interval`() = runTest {
        val repo = createRepo(backgroundScope)
        coEvery { endpoint.getFeederDetail(any()) } returns response()

        repo.getDetail("a")
        repo.getDetail("b")

        sleeps shouldBe listOf(Duration.ofMillis(1200))
    }

    @Test
    fun `429 pushes the next fetch out by retry-after`() = runTest {
        val repo = createRepo(backgroundScope)
        val raw = okhttp3.Response.Builder()
            .code(429)
            .message("Too Many Requests")
            .protocol(Protocol.HTTP_1_1)
            .request(Request.Builder().url("http://localhost/").build())
            .header("Retry-After", "77")
            .build()
        val rateLimited = HttpException(Response.error<Any>("".toResponseBody(), raw))
        coEvery { endpoint.getFeederDetail(any()) } throws rateLimited andThen response()

        shouldThrow<HttpException> { repo.getDetail("a") }
        repo.getDetail("b")

        sleeps shouldBe listOf(Duration.ofSeconds(77))
    }

    @Test
    fun `session epoch change mid-flight rejects the result`() = runTest {
        val repo = createRepo(backgroundScope)
        val gate = CompletableDeferred<Unit>()
        coEvery { endpoint.getFeederDetail("abc") } coAnswers {
            gate.await()
            response()
        } andThen response()

        // runCatching inside the async so the child job can't fail the test scope itself.
        val flight = async { runCatching { repo.getDetail("abc") } }
        runCurrent()
        epoch = 2 // account switched while the request was in the air
        gate.complete(Unit)
        // The stale completion is rejected outright — not returned, not cached.
        flight.await().exceptionOrNull().shouldBeInstanceOf<SessionExpiredException>()

        repo.getDetail("abc")
        coVerify(exactly = 2) { endpoint.getFeederDetail("abc") }
    }

    @Test
    fun `logout clears the cache`() = runTest {
        val repo = createRepo(backgroundScope)
        coEvery { endpoint.getFeederDetail("abc") } returns response()

        repo.getDetail("abc")
        loggedIn.value = false
        runCurrent()
        loggedIn.value = true
        runCurrent()
        repo.getDetail("abc")

        coVerify(exactly = 2) { endpoint.getFeederDetail("abc") }
    }
}
