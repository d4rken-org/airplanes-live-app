package eu.darken.apl.map.core.overlay

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import eu.darken.apl.common.BuildConfigWrap
import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.http.HttpModule
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.ServerModule
import eu.darken.apl.server.api.ServerEndpoint
import eu.darken.apl.server.api.ServerVariant
import eu.darken.apl.server.compat.AppCompatibility
import eu.darken.apl.server.compat.CompatState
import eu.darken.apl.server.identity.DeviceKey
import eu.darken.apl.server.identity.DeviceKeyStore
import eu.darken.apl.server.identity.JcaDeviceKey
import eu.darken.apl.server.session.SessionManager
import eu.darken.apl.server.session.SessionState
import eu.darken.apl.server.session.SessionStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.TestCompatibilityStore
import testhelper.coroutine.TestDispatcherProvider
import java.io.File
import java.io.IOException
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class OverlayTileClientTest : BaseTest() {

    private val json = ServerModule.serverJson()

    private lateinit var server: MockWebServer
    private lateinit var other: MockWebServer
    private lateinit var scope: CoroutineScope
    private lateinit var storeFile: File
    private lateinit var store: SessionStore
    private lateinit var compatibility: AppCompatibility
    private lateinit var sessionManager: SessionManager
    private lateinit var tileClient: OverlayTileClient
    private val compatStore = TestCompatibilityStore()
    private val refreshSignal = mockk<OverlayRefreshSignal>(relaxUnitFun = true)

    private val deviceKey = JcaDeviceKey(
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    )

    private val monotonicClock = object : MonotonicClock {
        override fun elapsed(): Long = 0L
    }

    @BeforeEach
    fun setup() = runBlocking {
        server = MockWebServer().apply { start() }
        other = MockWebServer().apply { start() }

        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        storeFile = File(IO_TEST_BASEDIR, "overlay-tile-session-${storeCounter++}.preferences_pb")
        storeFile.parentFile?.mkdirs()
        storeFile.delete()
        store = SessionStore(PreferenceDataStoreFactory.create(scope = scope, produceFile = { storeFile }))
        store.setCredentials(
            credentials = SessionStore.Credentials(
                installationId = INSTALLATION_ID,
                accessToken = "access-1",
                accessExpiresAt = Instant.ofEpochMilli(NOW_MILLIS + 600_000),
                refreshToken = "refresh-1",
                refreshExpiresAt = Instant.ofEpochMilli(NOW_MILLIS + 30L * 24 * 3600 * 1000),
            ),
            keyThumbprint = deviceKey.thumbprint,
        )

        val serverClock = ServerClock(monotonicClock).apply { noteServerTime(NOW_MILLIS) }
        compatibility = compatStore.holder()
        val endpoint = ServerEndpoint(
            baseClient = HttpModule().baseHttpClient(),
            json = json,
            dispatcherProvider = TestDispatcherProvider(),
            serverClock = serverClock,
            compatibility = compatibility,
        ).apply {
            baseUrl = server.url("/").toString()
        }
        sessionManager = SessionManager(
            appScope = scope,
            store = store,
            endpoint = endpoint,
            deviceKeyStore = FakeDeviceKeyStore(deviceKey),
            serverClock = serverClock,
        )
        tileClient = OverlayTileClient(
            baseClient = HttpModule().baseHttpClient(),
            json = json,
            endpoint = endpoint,
            sessionManager = sessionManager,
            compatibility = compatibility,
            appScope = scope,
            refreshSignal = refreshSignal,
        )

        withTimeout(5_000) {
            compatibility.awaitLoaded()
            sessionManager.currentAccessToken.first { it == "access-1" }
        }
        Unit
    }

    @AfterEach
    fun teardown() {
        server.shutdown()
        other.shutdown()
        runBlocking { scope.coroutineContext.job.cancelAndJoin() }
        storeFile.delete()
        compatStore.close()
    }

    private fun get(url: String) = tileClient.client.newCall(Request.Builder().url(url).build()).execute()

    private fun tileUrl(path: String = TILE_PATH) = server.url(path).toString()

    @Test
    fun `a tile request to the server carries the token and the app headers`() {
        server.enqueue(MockResponse().setBody("tile"))

        get(tileUrl()).use { it.code shouldBe 200 }

        val request = server.takeRequest()
        request.path shouldBe TILE_PATH
        request.getHeader("Authorization") shouldBe "Bearer access-1"
        request.getHeader("X-App-Version") shouldBe BuildConfigWrap.VERSION_CODE.toString()
        request.getHeader("X-App-Variant") shouldBe ServerVariant.CURRENT
    }

    @Test
    fun `a request to another host carries none of them`() {
        other.enqueue(MockResponse().setBody("base map"))

        get(other.url("/tiles/3/4/2.pbf").toString()).use { it.code shouldBe 200 }

        other.takeRequest().assertNoServerHeaders()
    }

    @Test
    fun `a redirect from the server to another host carries none of them`() {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", other.url("/elsewhere.png")))
        other.enqueue(MockResponse().setBody("tile"))

        get(tileUrl()).use { it.code shouldBe 200 }

        server.takeRequest().getHeader("Authorization") shouldBe "Bearer access-1"
        other.takeRequest().assertNoServerHeaders()
    }

    @Test
    fun `concurrent expired tokens are refreshed once and the retries carry the new token`() = runBlocking<Unit> {
        val parallel = 6
        val refreshes = AtomicInteger()
        val refused = CountDownLatch(parallel)
        val retriedTokens = mutableListOf<String?>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/api/v1/sessions/refresh" -> {
                    refreshes.incrementAndGet()
                    tokenResponse("access-2")
                }

                request.getHeader("Authorization") == "Bearer access-1" -> {
                    // Every call holds the old token before any of them is answered
                    refused.countDown()
                    refused.await(5, TimeUnit.SECONDS)
                    problemResponse(401, "invalid_token")
                }

                else -> {
                    synchronized(retriedTokens) { retriedTokens.add(request.getHeader("Authorization")) }
                    MockResponse().setBody("tile")
                }
            }
        }

        val codes = (1..parallel)
            .map { async(Dispatchers.IO) { get(tileUrl()).use { it.code } } }
            .awaitAll()

        codes shouldBe List(parallel) { 200 }
        refreshes.get() shouldBe 1
        retriedTokens shouldBe List(parallel) { "Bearer access-2" }
    }

    @Test
    fun `a refusal for an outdated app is noted and later tiles stay off the network`() = runBlocking<Unit> {
        server.enqueue(problemResponse(403, "app_update_required"))

        get(tileUrl()).use { it.code shouldBe 403 }
        withTimeout(5_000) { compatibility.state.first { it is CompatState.UpdateRequired } }

        shouldThrow<IOException> { get(tileUrl()).close() }
        server.requestCount shouldBe 1
    }

    @Test
    fun `a revoked installation reaches the session`() = runBlocking<Unit> {
        server.enqueue(problemResponse(403, "installation_revoked"))

        get(tileUrl()).use { it.code shouldBe 403 }

        withTimeout(5_000) { sessionManager.state.first { it is SessionState.Revoked } }
        shouldThrow<IOException> { get(tileUrl()).close() }
        server.requestCount shouldBe 1
    }

    @Test
    fun `refusals the catalog answers ask for a catalog read`() {
        server.enqueue(problemResponse(403, "overlay_upgrade_required"))
        server.enqueue(problemResponse(404, "overlay_frame_unknown"))
        server.enqueue(problemResponse(404, "overlay_not_found"))

        repeat(3) { get(tileUrl()).close() }

        verify(exactly = 1) { refreshSignal.request("overlay_upgrade_required") }
        verify(exactly = 1) { refreshSignal.request("overlay_frame_unknown") }
        verify(exactly = 1) { refreshSignal.request("overlay_not_found") }
    }

    @Test
    fun `a throttled tile asks for nothing`() {
        server.enqueue(problemResponse(429, "overlay_rate_exceeded").setHeader("Retry-After", "1"))
        server.enqueue(problemResponse(404, "overlay_tile_outside"))

        get(tileUrl()).use { it.code shouldBe 429 }
        get(tileUrl()).use { it.code shouldBe 404 }

        verify(exactly = 0) { refreshSignal.request(any()) }
    }

    private fun RecordedRequest.assertNoServerHeaders() {
        getHeader("Authorization").shouldBeNull()
        getHeader("X-App-Version").shouldBeNull()
        getHeader("X-App-Variant").shouldBeNull()
    }

    private fun tokenResponse(accessToken: String) = MockResponse().setBody(
        """
        {
          "installationId": "$INSTALLATION_ID",
          "refreshToken": "refresh-2",
          "accessToken": "$accessToken",
          "accessExpiresAt": ${NOW_MILLIS + 600_000},
          "refreshExpiresAt": ${NOW_MILLIS + 30L * 24 * 3600 * 1000}
        }
        """.trimIndent()
    )

    private fun problemResponse(status: Int, code: String) = MockResponse()
        .setResponseCode(status)
        .setHeader("Content-Type", "application/problem+json")
        .setBody("""{"type":"about:blank","title":"error","status":$status,"code":"$code"}""")

    private class FakeDeviceKeyStore(private val key: DeviceKey) : DeviceKeyStore {
        override suspend fun getOrCreate(): DeviceKey = key
        override fun exists(): Boolean = true
        override suspend fun reset() = Unit
    }

    companion object {
        private const val NOW_MILLIS = 1_710_000_000_000L
        private const val INSTALLATION_ID = "5a5c6b2e-3b0a-4a2e-9a0f-000000000002"
        private const val TILE_PATH = "/api/v1/overlays/radar/3f9c2a1b7d40/3/4/2.png"
        private var storeCounter = 0
    }
}
