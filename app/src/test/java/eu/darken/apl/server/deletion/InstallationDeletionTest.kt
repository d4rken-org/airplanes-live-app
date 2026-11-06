package eu.darken.apl.server.deletion

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.http.HttpModule
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.ServerModule
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.ServerCodes
import eu.darken.apl.server.api.ServerEndpoint
import eu.darken.apl.server.identity.DeviceKey
import eu.darken.apl.server.identity.DeviceKeyStore
import eu.darken.apl.server.identity.JcaDeviceKey
import eu.darken.apl.server.session.SessionManager
import eu.darken.apl.server.session.SessionStore
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
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
import java.io.File
import java.io.IOException
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class InstallationDeletionTest : BaseTest() {

    private val json = ServerModule.serverJson()

    private lateinit var server: MockWebServer
    private val routes = RouteDispatcher()
    private lateinit var appScope: CoroutineScope
    private lateinit var storeScope: CoroutineScope
    private lateinit var sessionFile: File
    private lateinit var deletionFile: File
    private val compatStore = TestCompatibilityStore()

    private lateinit var sessionStore: SessionStore
    private lateinit var deletionStore: DeletionStateStore
    private lateinit var gate: ServerRequestGate
    private lateinit var endpoint: ServerEndpoint
    private lateinit var sessionManager: SessionManager

    private val events: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val keyStore = RecordingDeviceKeyStore(newDeviceKey(), events)
    private val wiper = RecordingWiper()
    private var fallbackId: String? = FALLBACK_INSTALLATION_ID

    private val monotonicClock = object : MonotonicClock {
        override fun elapsed(): Long = 0L
    }
    private val serverClock = ServerClock(monotonicClock).apply { noteServerTime(NOW_MILLIS) }

    @BeforeEach
    fun setup() {
        server = MockWebServer()
        server.dispatcher = routes
        server.start()

        val id = storeCounter++
        sessionFile = File(IO_TEST_BASEDIR, "deletion-session-$id.preferences_pb")
        deletionFile = File(IO_TEST_BASEDIR, "deletion-state-$id.preferences_pb")
        listOf(sessionFile, deletionFile).forEach {
            it.parentFile?.mkdirs()
            it.delete()
        }

        appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        start()
    }

    @AfterEach
    fun teardown() {
        appScope.cancel()
        server.shutdown()
        runBlocking { storeScope.coroutineContext.job.cancelAndJoin() }
        sessionFile.delete()
        deletionFile.delete()
        compatStore.close()
    }

    /** Builds the real server stack over the stored files, like a fresh app process. */
    private fun start() {
        storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        sessionStore = SessionStore(PreferenceDataStoreFactory.create(scope = storeScope, produceFile = { sessionFile }))
        deletionStore = DeletionStateStore(
            PreferenceDataStoreFactory.create(scope = storeScope, produceFile = { deletionFile })
        )
        gate = ServerRequestGate(deletionStore)
        endpoint = ServerEndpoint(
            baseClient = HttpModule().baseHttpClient(),
            json = json,
            dispatcherProvider = TestDispatcherProvider(),
            serverClock = serverClock,
            compatibility = compatStore.holder(),
            requestGate = gate,
        ).apply {
            baseUrl = server.url("/").toString()
        }
        sessionManager = SessionManager(
            appScope = appScope,
            store = sessionStore,
            endpoint = endpoint,
            deviceKeyStore = keyStore,
            serverClock = serverClock,
            requestGate = gate,
        )
    }

    /** A DataStore keeps its file claimed until its scope is done, like the app process. */
    private suspend fun restart() {
        storeScope.coroutineContext.job.cancelAndJoin()
        start()
    }

    private fun createDeletion() = InstallationDeletion(
        appScope = appScope,
        stateStore = deletionStore,
        requestGate = gate,
        sessionManager = sessionManager,
        endpoint = endpoint,
        wiper = wiper,
        fallbackInstallationId = { fallbackId },
    )

    private fun deletionTest(block: suspend CoroutineScope.() -> Unit) = runBlocking<Unit> {
        withTimeout(TEST_TIMEOUT_MS) { block() }
    }

    private suspend fun seedCredentials() = sessionStore.setCredentials(
        credentials = SessionStore.Credentials(
            installationId = INSTALLATION_ID,
            accessToken = "access-1",
            accessExpiresAt = Instant.ofEpochMilli(NOW_MILLIS + 600_000),
            refreshToken = "stored-refresh",
            refreshExpiresAt = Instant.ofEpochMilli(NOW_MILLIS + 30L * 24 * 3600 * 1000),
        ),
        keyThumbprint = keyStore.thumbprint,
    )

    private fun assertNothingWiped() {
        events.shouldBeEmpty()
        keyStore.exists() shouldBe true
    }

    @Test
    fun `a confirmed deletion resets the identity, then wipes the device`() = deletionTest {
        seedCredentials()
        routes.route(REFRESH_PATH) { tokenResponse("access-refreshed") }
        routes.route(DELETE_PATH) { MockResponse().setResponseCode(204) }

        createDeletion().delete() shouldBe DeletionResult.Deleted

        routes.paths() shouldBe listOf(REFRESH_PATH, DELETE_PATH)
        routes.requests.last().apply {
            method shouldBe "DELETE"
            getHeader("Authorization") shouldBe "Bearer access-refreshed"
        }
        deletionStore.current() shouldBe DeletionState.WIPE_PENDING
        events shouldBe listOf(KEY_RESET, WIPE)
        wiper.sessionAtWipe shouldBe SessionStore.Session()
    }

    @Test
    fun `a lost deletion response stays pending until a later deletion is confirmed`() = deletionTest {
        seedCredentials()
        routes.route(REFRESH_PATH) { tokenResponse("access-2") }
        routes.route(DELETE_PATH) { MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST) }

        val lost = createDeletion().delete()

        lost.shouldBeInstanceOf<DeletionResult.NotConfirmed>().cause.shouldBeInstanceOf<IOException>()
        deletionStore.current() shouldBe DeletionState.REQUEST_PENDING
        assertNothingWiped()
        sessionStore.current().credentials.shouldNotBeNull()

        restart()
        routes.route(REFRESH_PATH) { tokenResponse("access-3") }
        routes.route(DELETE_PATH) { MockResponse().setResponseCode(204) }

        createDeletion().delete() shouldBe DeletionResult.Deleted

        routes.requests.last().apply {
            path shouldBe DELETE_PATH
            getHeader("Authorization") shouldBe "Bearer access-3"
        }
        deletionStore.current() shouldBe DeletionState.WIPE_PENDING
        events shouldBe listOf(KEY_RESET, WIPE)
    }

    @Test
    fun `a refresh lost to the network leaves the deletion unconfirmed`() = deletionTest {
        seedCredentials()
        routes.route(REFRESH_PATH) { MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST) }

        val result = createDeletion().delete()

        result.shouldBeInstanceOf<DeletionResult.NotConfirmed>().cause.shouldBeInstanceOf<IOException>()
        routes.paths().toSet() shouldBe setOf(REFRESH_PATH)
        deletionStore.current() shouldBe DeletionState.REQUEST_PENDING
        assertNothingWiped()
    }

    @Test
    fun `an unavailable refresh leaves the deletion unconfirmed without enrolling`() = deletionTest {
        seedCredentials()
        routes.route(REFRESH_PATH) { problemResponse(503, ServerCodes.DATABASE_UNAVAILABLE) }

        val result = createDeletion().delete()

        result.shouldBeInstanceOf<DeletionResult.NotConfirmed>()
            .cause.shouldBeInstanceOf<ServerApiException>().status shouldBe 503
        routes.paths() shouldBe listOf(REFRESH_PATH)
        deletionStore.current() shouldBe DeletionState.REQUEST_PENDING
        assertNothingWiped()
    }

    @Test
    fun `an invalid refresh token enrolls again and deletes with the new token`() = deletionTest {
        seedCredentials()
        routes.route(REFRESH_PATH) { problemResponse(401, ServerCodes.INVALID_TOKEN) }
        routes.route(CHALLENGE_PATH) { challengeResponse("CHALLENGE-1") }
        routes.route(ENROLL_PATH) { tokenResponse("access-enrolled", installationId = OTHER_INSTALLATION_ID) }
        routes.route(DELETE_PATH) { MockResponse().setResponseCode(204) }

        createDeletion().delete() shouldBe DeletionResult.Deleted

        routes.paths() shouldBe listOf(REFRESH_PATH, CHALLENGE_PATH, ENROLL_PATH, DELETE_PATH)
        routes.requests.last().getHeader("Authorization") shouldBe "Bearer access-enrolled"
        events shouldBe listOf(KEY_RESET, WIPE)
    }

    @Test
    fun `a revocation found by the refresh is refused with the installation from before`() = deletionTest {
        seedCredentials()
        routes.route(REFRESH_PATH) { problemResponse(403, ServerCodes.INSTALLATION_REVOKED) }

        val result = createDeletion().delete()

        result shouldBe DeletionResult.Refused(DeletionResult.Refused.Reason.REVOKED, INSTALLATION_ID)
        routes.paths() shouldBe listOf(REFRESH_PATH)
        deletionStore.current() shouldBe DeletionState.NONE
        assertNothingWiped()
    }

    @Test
    fun `an already revoked session is refused with the fallback installation`() = deletionTest {
        sessionStore.setRevoked()

        val result = createDeletion().delete()

        result shouldBe DeletionResult.Refused(DeletionResult.Refused.Reason.REVOKED, FALLBACK_INSTALLATION_ID)
        routes.paths().shouldBeEmpty()
        deletionStore.current() shouldBe DeletionState.NONE
        assertNothingWiped()
    }

    @Test
    fun `a restricted installation is refused`() = deletionTest {
        seedCredentials()
        routes.route(REFRESH_PATH) { tokenResponse("access-2") }
        routes.route(DELETE_PATH) { problemResponse(403, ServerCodes.INSTALLATION_RESTRICTED) }

        val result = createDeletion().delete()

        result shouldBe DeletionResult.Refused(DeletionResult.Refused.Reason.RESTRICTED, INSTALLATION_ID)
        deletionStore.current() shouldBe DeletionState.NONE
        assertNothingWiped()
        sessionStore.current().credentials.shouldNotBeNull()
    }

    @Test
    fun `a throttled or unavailable deletion stays pending with the retry delay`() = deletionTest {
        seedCredentials()
        routes.route(REFRESH_PATH) { tokenResponse("access-2") }
        val answers = listOf(
            Triple(429, ServerCodes.QUOTA_EXCEEDED, 30L),
            Triple(503, "deletion_unavailable", 60L),
        )

        answers.forEach { (status, code, retryAfter) ->
            routes.route(DELETE_PATH) {
                problemResponse(status, code).setHeader("Retry-After", retryAfter.toString())
            }

            val result = createDeletion().delete().shouldBeInstanceOf<DeletionResult.NotConfirmed>()

            result.retryAfterSeconds shouldBe retryAfter
            result.cause.shouldBeInstanceOf<ServerApiException>().code shouldBe code
            deletionStore.current() shouldBe DeletionState.REQUEST_PENDING
        }
        assertNothingWiped()
    }

    @Test
    fun `an outdated app is told to update`() = deletionTest {
        seedCredentials()
        routes.route(REFRESH_PATH) { tokenResponse("access-2") }
        routes.route(DELETE_PATH) { problemResponse(403, ServerCodes.APP_UPDATE_REQUIRED) }

        createDeletion().delete() shouldBe DeletionResult.UpdateRequired

        deletionStore.current() shouldBe DeletionState.NONE
        assertNothingWiped()
    }

    @Test
    fun `a deletion answered without 204 is not confirmed`() = deletionTest {
        seedCredentials()
        routes.route(REFRESH_PATH) { tokenResponse("access-2") }
        routes.route(DELETE_PATH) { MockResponse().setResponseCode(200) }

        val result = createDeletion().delete()

        result.shouldBeInstanceOf<DeletionResult.NotConfirmed>()
            .cause.shouldBeInstanceOf<UnconfirmedDeletionException>().status shouldBe 200
        deletionStore.current() shouldBe DeletionState.REQUEST_PENDING
        assertNothingWiped()
    }

    @Test
    fun `a failed wipe is repeated without asking the server again`() = deletionTest {
        seedCredentials()
        routes.route(REFRESH_PATH) { tokenResponse("access-2") }
        routes.route(DELETE_PATH) { MockResponse().setResponseCode(204) }
        wiper.result = false
        val deletion = createDeletion()

        deletion.delete() shouldBe DeletionResult.WipeFailed(null)

        deletionStore.current() shouldBe DeletionState.WIPE_PENDING
        events shouldBe listOf(KEY_RESET, WIPE)
        val requestsBefore = routes.requests.size

        wiper.result = true
        deletion.delete() shouldBe DeletionResult.Deleted

        routes.requests.size shouldBe requestsBefore
        events shouldBe listOf(KEY_RESET, WIPE, KEY_RESET, WIPE)
        deletionStore.current() shouldBe DeletionState.WIPE_PENDING
    }

    @Test
    fun `cancelling drops only an unconfirmed deletion`() = deletionTest {
        val deletion = createDeletion()

        deletionStore.set(DeletionState.REQUEST_PENDING)
        deletion.cancelPending()
        deletionStore.current() shouldBe DeletionState.NONE

        deletionStore.set(DeletionState.WIPE_PENDING)
        deletion.cancelPending()
        deletionStore.current() shouldBe DeletionState.WIPE_PENDING

        routes.paths().shouldBeEmpty()
    }

    @Test
    fun `other requests wait until the deletion finished`() = deletionTest {
        seedCredentials()
        val deleteArrived = CompletableDeferred<Unit>()
        val releaseDelete = CountDownLatch(1)
        routes.route(REFRESH_PATH) { tokenResponse("access-2") }
        routes.route(DELETE_PATH) {
            deleteArrived.complete(Unit)
            releaseDelete.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            problemResponse(503, "deletion_unavailable")
        }
        routes.route(WATCH_DELETE_PATH) { MockResponse().setResponseCode(204) }

        val deletion = async(Dispatchers.IO) { createDeletion().delete() }
        deleteArrived.await()

        val other = async(Dispatchers.IO) { endpoint.deleteWatch("access-2", "local-1") }
        delay(300)
        routes.paths() shouldNotContain WATCH_DELETE_PATH

        releaseDelete.countDown()
        deletion.await().shouldBeInstanceOf<DeletionResult.NotConfirmed>()
        other.await()

        routes.paths() shouldBe listOf(REFRESH_PATH, DELETE_PATH, WATCH_DELETE_PATH)
    }

    @Test
    fun `the launch prompt reports the state the process started with once`() = deletionTest {
        listOf(DeletionState.REQUEST_PENDING, DeletionState.WIPE_PENDING).forEach { state ->
            deletionStore.set(state)
            val deletion = createDeletion()

            deletion.takeLaunchPrompt() shouldBe state
            deletion.takeLaunchPrompt().shouldBeNull()
        }
    }

    @Test
    fun `there is no launch prompt without a stored deletion`() = deletionTest {
        createDeletion().takeLaunchPrompt().shouldBeNull()
    }

    @Test
    fun `a deletion state written by this process does not prompt at launch`() = deletionTest {
        seedCredentials()
        routes.route(REFRESH_PATH) { tokenResponse("access-2") }
        routes.route(DELETE_PATH) { problemResponse(503, "deletion_unavailable") }
        val deletion = createDeletion()

        deletion.delete().shouldBeInstanceOf<DeletionResult.NotConfirmed>()
        deletionStore.current() shouldBe DeletionState.REQUEST_PENDING
        deletion.takeLaunchPrompt().shouldBeNull()

        deletionStore.set(DeletionState.WIPE_PENDING)
        deletion.takeLaunchPrompt().shouldBeNull()
    }

    private fun challengeResponse(challenge: String) = MockResponse()
        .setBody("""{"challenge":"$challenge","expiresInSeconds":120}""")

    private fun tokenResponse(
        accessToken: String,
        installationId: String = INSTALLATION_ID,
    ) = MockResponse().setBody(
        """
        {
          "installationId": "$installationId",
          "refreshToken": "rotated-refresh",
          "accessToken": "$accessToken",
          "accessExpiresAt": ${NOW_MILLIS + 600_000},
          "refreshExpiresAt": ${NOW_MILLIS + 30L * 24 * 3600 * 1000}
        }
        """.trimIndent()
    )

    private fun problemResponse(status: Int, code: String) = MockResponse()
        .setResponseCode(status)
        .setHeader("Content-Type", "application/problem+json")
        .setBody("""{"type":"about:blank","title":"error","status":$status,"detail":"nope","code":"$code"}""")

    /** Answers each path with its current route, every time it is asked, so client retries see the same answer. */
    private class RouteDispatcher : Dispatcher() {
        private val routes = ConcurrentHashMap<String, () -> MockResponse>()
        val requests: MutableList<RecordedRequest> = Collections.synchronizedList(mutableListOf())

        fun route(path: String, response: () -> MockResponse) {
            routes[path] = response
        }

        fun paths(): List<String?> = synchronized(requests) { requests.map { it.path } }

        override fun dispatch(request: RecordedRequest): MockResponse {
            requests.add(request)
            return routes[request.path]?.invoke() ?: MockResponse().setResponseCode(500)
        }
    }

    private class RecordingDeviceKeyStore(
        private var key: DeviceKey?,
        private val events: MutableList<String>,
    ) : DeviceKeyStore {
        val thumbprint: String = key!!.thumbprint

        override suspend fun getOrCreate(): DeviceKey = key ?: newDeviceKey().also { key = it }

        override fun exists(): Boolean = key != null

        override suspend fun reset() {
            events.add(KEY_RESET)
            key = null
        }
    }

    private inner class RecordingWiper : LocalDataWiper {
        var result = true
        var sessionAtWipe: SessionStore.Session? = null

        override fun wipe(): Boolean {
            events.add(WIPE)
            sessionAtWipe = runBlocking { sessionStore.current() }
            return result
        }
    }

    companion object {
        private const val NOW_MILLIS = 1_710_000_000_000L
        private const val TEST_TIMEOUT_MS = 20_000L
        private const val INSTALLATION_ID = "5a5c6b2e-3b0a-4a2e-9a0f-000000000002"
        private const val OTHER_INSTALLATION_ID = "5a5c6b2e-3b0a-4a2e-9a0f-000000000003"
        private const val FALLBACK_INSTALLATION_ID = "5a5c6b2e-3b0a-4a2e-9a0f-000000000004"
        private const val REFRESH_PATH = "/api/v1/sessions/refresh"
        private const val CHALLENGE_PATH = "/api/v1/enrollment/challenge"
        private const val ENROLL_PATH = "/api/v1/installations"
        private const val DELETE_PATH = "/api/v1/installation"
        private const val WATCH_DELETE_PATH = "/api/v1/watchlist/watches/local-1"
        private const val KEY_RESET = "key-reset"
        private const val WIPE = "wipe"
        private var storeCounter = 0

        private fun newDeviceKey(): DeviceKey = JcaDeviceKey(
            KeyPairGenerator.getInstance("EC").apply {
                initialize(ECGenParameterSpec("secp256r1"))
            }.generateKeyPair()
        )
    }
}
