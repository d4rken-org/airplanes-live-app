package eu.darken.apl.server.compat

import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.http.HttpModule
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.ServerModule
import eu.darken.apl.server.api.MetaResponse
import eu.darken.apl.server.api.ServerEndpoint
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.TestCompatibilityStore
import testhelper.coroutine.TestDispatcherProvider
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Real threads throughout: requests go to a local server, and overlapping triggers are started
 * undispatched, so each one has decided to join or queue before the next line runs.
 */
class AppCompatibilityCheckTest : BaseTest() {

    private lateinit var mockWebServer: MockWebServer
    private val server = MetaServer()
    private val compatStore = TestCompatibilityStore()
    private lateinit var compatibility: AppCompatibility
    private lateinit var appScope: CoroutineScope
    private lateinit var checker: AppCompatibilityCheck

    private val serverClock = ServerClock(object : MonotonicClock {
        override fun elapsed(): Long = 0L
    }).apply { noteServerTime(NOW.toEpochMilli()) }

    @BeforeEach
    fun setup() {
        mockWebServer = MockWebServer().apply {
            dispatcher = server
            start()
        }
        appScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        compatibility = compatStore.holder(VERSION)
        val endpoint = ServerEndpoint(
            baseClient = HttpModule().baseHttpClient(),
            json = ServerModule.serverJson(),
            dispatcherProvider = TestDispatcherProvider(),
            serverClock = serverClock,
            compatibility = compatibility,
        ).apply {
            baseUrl = mockWebServer.url("/").toString()
        }
        checker = AppCompatibilityCheck(
            appScope = appScope,
            endpoint = endpoint,
            compatibility = compatibility,
            serverClock = serverClock,
        )
    }

    @AfterEach
    fun teardown() {
        server.release()
        appScope.cancel()
        mockWebServer.shutdown()
        compatStore.close()
    }

    /** Stores a successful check made at [at], without a request. */
    private suspend fun checkedAt(at: Instant, meta: MetaResponse = META_OK) {
        compatibility.applyMeta(compatibility.beginCheck(), meta, at) shouldBe true
    }

    private fun startCheck(force: Boolean): Deferred<Unit> = appScope.async(start = CoroutineStart.UNDISPATCHED) {
        checker.check(force)
    }

    private suspend fun <T> timed(block: suspend () -> T): T = withTimeout(10_000) { block() }

    @Test
    fun `a fresh check while compatible makes no request`() = runBlocking<Unit> {
        checkedAt(NOW.minus(Duration.ofHours(1)))

        timed { checker.onForeground().join() }

        server.requests.size shouldBe 0
    }

    @Test
    fun `a stale check while compatible asks again through the cache`() = runBlocking<Unit> {
        checkedAt(NOW.minus(Duration.ofHours(5)))
        server.enqueue(meta(recommended = VERSION.toInt() + 1, minSupported = 0))

        timed { checker.onForeground().join() }

        server.requests.size shouldBe 1
        server.requests.single().getHeader("Cache-Control").shouldBeNull()
        compatibility.state.value shouldBe CompatState.UpdateRecommended(VERSION.toInt() + 1)
        compatibility.lastCheckedAt() shouldBe NOW
    }

    @Test
    fun `no check yet asks through the cache`() = runBlocking<Unit> {
        compatibility.awaitLoaded()
        compatibility.lastCheckedAt().shouldBeNull()

        timed { checker.onForeground().join() }

        server.requests.size shouldBe 1
        server.requests.single().getHeader("Cache-Control").shouldBeNull()
        compatibility.state.value shouldBe CompatState.Ok
        compatibility.lastCheckedAt() shouldBe NOW
    }

    @Test
    fun `while refused every foreground asks the server, and a lowered floor clears the refusal`() = runBlocking<Unit> {
        checkedAt(NOW.minus(Duration.ofMinutes(1)), meta = META_REQUIRED)
        compatibility.state.value shouldBe CompatState.UpdateRequired
        server.enqueue(meta(recommended = 0, minSupported = 0))

        timed { checker.onForeground().join() }

        server.requests.size shouldBe 1
        server.requests.single().getHeader("Cache-Control") shouldBe "no-cache"
        compatibility.state.value shouldBe CompatState.Ok
    }

    @Test
    fun `a forced check skips the cache`() = runBlocking<Unit> {
        compatibility.awaitLoaded()

        timed { checker.check(force = true) }

        server.requests.single().getHeader("Cache-Control") shouldBe "no-cache"
    }

    @Test
    fun `a failed check changes nothing`() = runBlocking<Unit> {
        val checked = NOW.minus(Duration.ofHours(1))
        val recommended = MetaResponse(recommendedAppVersion = VERSION.toInt() + 1, minSupportedAppVersion = 0)
        checkedAt(checked, meta = recommended)
        val before = compatibility.state.value

        val failures = listOf(
            { MockResponse().setResponseCode(500).setBody("nope") },
            { MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST) },
            { MockResponse().setHeader("Content-Type", "application/json").setBody("""{"unexpected": true}""") },
        )
        failures.forEach { failure ->
            server.fallback = failure
            val requestsBefore = server.requests.size

            timed { checker.check(force = true) }

            (server.requests.size > requestsBefore) shouldBe true
            compatibility.state.value shouldBe before
            compatibility.lastCheckedAt() shouldBe checked
        }
    }

    @Test
    fun `overlapping triggers share one request`() = runBlocking<Unit> {
        compatibility.awaitLoaded()
        server.hold()

        val first = startCheck(force = false)
        val second = startCheck(force = false)
        checker.checking.value shouldBe true
        server.release()
        timed { listOf(first, second).awaitAll() }

        server.requests.size shouldBe 1
        timed { checker.checking.first { !it } }
    }

    @Test
    fun `a forced trigger does not join a request that may be answered from the cache`() = runBlocking<Unit> {
        compatibility.awaitLoaded()
        server.hold()

        val cached = startCheck(force = false)
        val forced = startCheck(force = true)
        val alsoForced = startCheck(force = true)
        server.release()
        timed { listOf(cached, forced, alsoForced).awaitAll() }

        server.requests.size shouldBe 2
        server.requests[0].getHeader("Cache-Control").shouldBeNull()
        server.requests[1].getHeader("Cache-Control") shouldBe "no-cache"
    }

    @Test
    fun `a trigger after a refusal does not join the request that began before it`() = runBlocking<Unit> {
        compatibility.awaitLoaded()
        server.hold()

        val before = startCheck(force = false)
        compatibility.noteRefusal()
        val after = startCheck(force = false)
        server.release()
        timed { listOf(before, after).awaitAll() }

        // The first answer is dropped for the refusal, only the request that followed it applies
        server.requests.size shouldBe 2
        server.requests[1].getHeader("Cache-Control") shouldBe "no-cache"
        compatibility.state.value shouldBe CompatState.Ok
    }

    /** Answers queued responses in order, then [fallback]; [hold] keeps answers back until [release]. */
    private class MetaServer : Dispatcher() {
        val requests = CopyOnWriteArrayList<RecordedRequest>()
        private val responses = ConcurrentLinkedQueue<MockResponse>()

        @Volatile var fallback: () -> MockResponse = { meta(recommended = 0, minSupported = 0) }
        @Volatile private var gate: CountDownLatch? = null

        fun enqueue(response: MockResponse) {
            responses += response
        }

        fun hold() {
            gate = CountDownLatch(1)
        }

        fun release() {
            gate?.countDown()
        }

        override fun dispatch(request: RecordedRequest): MockResponse {
            requests += request
            gate?.await(10, TimeUnit.SECONDS)
            return responses.poll() ?: fallback()
        }
    }

    companion object {
        private const val VERSION = 100L
        private val NOW: Instant = Instant.parse("2026-10-21T12:00:00Z")
        private val META_OK = MetaResponse(recommendedAppVersion = 0, minSupportedAppVersion = 0)
        private val META_REQUIRED = MetaResponse(recommendedAppVersion = 0, minSupportedAppVersion = VERSION.toInt() + 1)

        private fun meta(recommended: Int, minSupported: Int) = MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("""{"recommendedAppVersion": $recommended, "minSupportedAppVersion": $minSupported}""")
    }
}
