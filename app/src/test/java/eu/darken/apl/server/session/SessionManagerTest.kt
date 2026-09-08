package eu.darken.apl.server.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import eu.darken.apl.common.BuildConfigWrap
import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.http.HttpModule
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.ServerModule
import eu.darken.apl.server.api.EnrollRequest
import eu.darken.apl.server.api.RefreshRequest
import eu.darken.apl.server.api.ServerEndpoint
import eu.darken.apl.server.identity.DeviceKey
import eu.darken.apl.server.identity.DeviceKeyStore
import eu.darken.apl.server.identity.JcaDeviceKey
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.coroutine.TestDispatcherProvider
import java.io.File
import java.io.IOException
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.util.Base64

class SessionManagerTest : BaseTest() {

    private val json = ServerModule.serverJson()

    private lateinit var server: MockWebServer
    private lateinit var storeScope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: SessionStore
    private lateinit var endpoint: ServerEndpoint
    private lateinit var serverClock: ServerClock
    private lateinit var storeFile: File

    private val keyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()
    private val deviceKey = JcaDeviceKey(keyPair)
    private val keyStore = FakeDeviceKeyStore(deviceKey)

    private val monotonicClock = object : MonotonicClock {
        var elapsedMillis = 0L
        override fun elapsed(): Long = elapsedMillis
    }

    @BeforeEach
    fun setup() {
        server = MockWebServer()
        server.start()

        storeFile = File(IO_TEST_BASEDIR, "session-${storeCounter++}.preferences_pb")
        storeFile.parentFile?.mkdirs()
        storeFile.delete()
        storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        dataStore = PreferenceDataStoreFactory.create(scope = storeScope, produceFile = { storeFile })
        store = SessionStore(dataStore)

        serverClock = ServerClock(monotonicClock).apply { noteServerTime(NOW_MILLIS) }

        endpoint = ServerEndpoint(
            baseClient = HttpModule().baseHttpClient(),
            json = json,
            dispatcherProvider = TestDispatcherProvider(),
            serverClock = serverClock,
        ).apply {
            baseUrl = server.url("/").toString()
        }
    }

    @AfterEach
    fun teardown() {
        server.shutdown()
        storeScope.cancel()
        storeFile.delete()
    }

    private fun createManager() = SessionManager(
        appScope = CoroutineScope(Dispatchers.Unconfined),
        store = store,
        endpoint = endpoint,
        deviceKeyStore = keyStore,
        serverClock = serverClock,
    )

    private suspend fun seedCredentials(
        accessToken: String = "expired-access",
        refreshToken: String = "stored-refresh",
        accessExpiresAt: Instant = Instant.ofEpochMilli(NOW_MILLIS - 1000),
    ) = store.setCredentials(
        credentials = SessionStore.Credentials(
            installationId = INSTALLATION_ID,
            accessToken = accessToken,
            accessExpiresAt = accessExpiresAt,
            refreshToken = refreshToken,
            refreshExpiresAt = Instant.ofEpochMilli(NOW_MILLIS + 30 * 24 * 3600 * 1000L),
        ),
        keyThumbprint = deviceKey.thumbprint,
    )

    @Test
    fun `enrollment signs the challenge and reports the build variant`() = runBlocking {
        server.enqueue(challengeResponse("CHALLENGE-1"))
        server.enqueue(tokenResponse(accessToken = "access-1"))

        createManager().accessToken() shouldBe "access-1"

        server.takeRequest().path shouldBe "/api/v1/enrollment/challenge"
        val enrollment = server.takeRequest()
        enrollment.path shouldBe "/api/v1/installations"

        val request = json.decodeFromString<EnrollRequest>(enrollment.body.readUtf8())
        request.challenge shouldBe "CHALLENGE-1"
        request.variant shouldBe "foss"
        request.appVersion shouldBe BuildConfigWrap.VERSION_CODE.toInt()
        request.publicKey shouldBe deviceKey.publicKeySpkiBase64

        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(keyPair.public)
        verifier.update("CHALLENGE-1".toByteArray(Charsets.UTF_8))
        verifier.verify(Base64.getDecoder().decode(request.signature)) shouldBe true
    }

    @Test
    fun `the pending refresh operation is stored before the request is sent`() = runBlocking {
        seedCredentials()

        var pendingWhenReceived: SessionStore.PendingRefresh? = null
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                pendingWhenReceived = runBlocking { store.current().pendingRefresh }
                return tokenResponse(accessToken = "access-2")
            }
        }

        createManager().accessToken() shouldBe "access-2"

        pendingWhenReceived shouldNotBe null
        pendingWhenReceived!!.refreshToken shouldBe "stored-refresh"
        // The receipt is retired once the credentials it produced are stored
        store.current().pendingRefresh shouldBe null
    }

    @Test
    fun `a retry after a network failure reuses the operation id with a newer timestamp`() = runBlocking {
        seedCredentials()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(tokenResponse(accessToken = "access-2"))

        val manager = createManager()
        shouldThrow<IOException> { manager.accessToken() }

        monotonicClock.elapsedMillis += 5_000
        manager.accessToken() shouldBe "access-2"

        val first = json.decodeFromString<RefreshRequest>(server.takeRequest().body.readUtf8())
        val second = json.decodeFromString<RefreshRequest>(server.takeRequest().body.readUtf8())
        second.operationId shouldBe first.operationId
        (second.timestamp > first.timestamp) shouldBe true
    }

    @Test
    fun `a replayed refresh stores the original expiries`() = runBlocking {
        seedCredentials()
        server.enqueue(
            tokenResponse(
                accessToken = "access-2",
                accessExpiresAt = REPLAY_ACCESS_EXPIRES_AT,
                refreshExpiresAt = REPLAY_REFRESH_EXPIRES_AT,
            )
        )

        createManager().accessToken() shouldBe "access-2"

        store.current().credentials!!.apply {
            accessExpiresAt shouldBe Instant.ofEpochMilli(REPLAY_ACCESS_EXPIRES_AT)
            refreshExpiresAt shouldBe Instant.ofEpochMilli(REPLAY_REFRESH_EXPIRES_AT)
        }
    }

    @Test
    fun `concurrent token requests trigger a single refresh`() = runBlocking {
        seedCredentials()
        server.enqueue(tokenResponse(accessToken = "access-2"))

        val manager = createManager()
        val tokens = (1..6)
            .map { async(Dispatchers.IO) { manager.accessToken() } }
            .awaitAll()

        tokens.toSet() shouldBe setOf("access-2")
        server.requestCount shouldBe 1
    }

    @Test
    fun `an expired recovery window enrolls again`() = runBlocking {
        seedCredentials()
        server.enqueue(problemResponse(409, "recovery_expired"))
        server.enqueue(challengeResponse("CHALLENGE-2"))
        server.enqueue(tokenResponse(accessToken = "access-3"))

        createManager().accessToken() shouldBe "access-3"

        server.takeRequest().path shouldBe "/api/v1/sessions/refresh"
        server.takeRequest().path shouldBe "/api/v1/enrollment/challenge"
        server.takeRequest().path shouldBe "/api/v1/installations"
    }

    @Test
    fun `a used challenge is replaced once`() = runBlocking {
        server.enqueue(challengeResponse("CHALLENGE-1"))
        server.enqueue(problemResponse(409, "challenge_used"))
        server.enqueue(challengeResponse("CHALLENGE-2"))
        server.enqueue(tokenResponse(accessToken = "access-1"))

        createManager().accessToken() shouldBe "access-1"

        server.requestCount shouldBe 4
    }

    @Test
    fun `a stale proof is signed again once`() = runBlocking {
        seedCredentials()
        server.enqueue(problemResponse(401, "stale_proof"))
        server.enqueue(tokenResponse(accessToken = "access-2"))

        createManager().accessToken() shouldBe "access-2"

        val first = json.decodeFromString<RefreshRequest>(server.takeRequest().body.readUtf8())
        val second = json.decodeFromString<RefreshRequest>(server.takeRequest().body.readUtf8())
        second.operationId shouldBe first.operationId
        server.requestCount shouldBe 2
    }

    @Test
    fun `a revoked installation is terminal`() = runBlocking {
        seedCredentials()
        server.enqueue(problemResponse(403, "installation_revoked"))

        val manager = createManager()
        shouldThrow<SessionRevokedException> { manager.accessToken() }
        manager.state.value shouldBe SessionState.Revoked

        shouldThrow<SessionRevokedException> { manager.accessToken() }
        server.requestCount shouldBe 1
    }

    @Test
    fun `a revoked session performs no requests after a restart`() = runBlocking {
        store.setRevoked()

        val manager = createManager()
        shouldThrow<SessionRevokedException> { manager.ensureSession() }
        shouldThrow<SessionRevokedException> { manager.accessToken() }

        server.requestCount shouldBe 0
    }

    private fun challengeResponse(challenge: String) = MockResponse()
        .setBody("""{"challenge":"$challenge","expiresInSeconds":120}""")

    private fun tokenResponse(
        accessToken: String,
        accessExpiresAt: Long = NOW_MILLIS + 600_000,
        refreshExpiresAt: Long = NOW_MILLIS + 30L * 24 * 3600 * 1000,
    ) = MockResponse().setBody(
        """
        {
          "installationId": "$INSTALLATION_ID",
          "refreshToken": "rotated-refresh",
          "accessToken": "$accessToken",
          "accessExpiresAt": $accessExpiresAt,
          "refreshExpiresAt": $refreshExpiresAt
        }
        """.trimIndent()
    )

    private fun problemResponse(status: Int, code: String) = MockResponse()
        .setResponseCode(status)
        .setHeader("Content-Type", "application/problem+json")
        .setBody("""{"type":"about:blank","title":"error","status":$status,"detail":"nope","code":"$code"}""")

    private class FakeDeviceKeyStore(private var key: DeviceKey?) : DeviceKeyStore {
        override suspend fun getOrCreate(): DeviceKey = key!!
        override fun exists(): Boolean = key != null
        override suspend fun reset() {
            key = null
        }
    }

    companion object {
        private const val NOW_MILLIS = 1_710_000_000_000L
        private const val REPLAY_ACCESS_EXPIRES_AT = 1_710_000_123_000L
        private const val REPLAY_REFRESH_EXPIRES_AT = 1_712_592_000_000L
        private const val INSTALLATION_ID = "5a5c6b2e-3b0a-4a2e-9a0f-000000000002"
        private var storeCounter = 0
    }
}
