package eu.darken.apl.feeder.core.link

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.datastore.createValue
import eu.darken.apl.common.datastore.value
import eu.darken.apl.common.http.HttpModule
import eu.darken.apl.common.serialization.SerializationModule
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.ServerModule
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.api.FeederStatusResponse
import eu.darken.apl.server.api.RegisterFeederRequest
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.ServerEndpoint
import eu.darken.apl.server.session.SessionManager
import eu.darken.apl.server.session.SessionState
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.coroutine.TestDispatcherProvider
import java.io.File
import java.util.concurrent.TimeUnit

class FeederLinkRepoTest : BaseTest() {

    private val json = ServerModule.serverJson()
    private val appJson = SerializationModule().json()
    private val sessionManager = mockk<SessionManager>()
    private val accessRepo = mockk<AccessRepo>(relaxed = true)

    private lateinit var server: MockWebServer
    private lateinit var endpoint: ServerEndpoint
    private lateinit var storeScope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var storeFile: File

    @BeforeEach
    fun setup() {
        server = MockWebServer()
        server.start()

        storeFile = File(IO_TEST_BASEDIR, "feeder-link-${counter++}.preferences_pb")
        storeFile.parentFile?.mkdirs()
        storeFile.delete()
        storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        dataStore = PreferenceDataStoreFactory.create(scope = storeScope, produceFile = { storeFile })

        endpoint = ServerEndpoint(
            baseClient = HttpModule().baseHttpClient(),
            json = json,
            dispatcherProvider = TestDispatcherProvider(),
            serverClock = ServerClock(object : MonotonicClock {
                override fun elapsed(): Long = 0L
            }),
        ).apply {
            baseUrl = server.url("/").toString()
        }

        // An active session refreshes the link on its own, tests that drive a call say so themselves
        every { sessionManager.state } returns MutableStateFlow<SessionState>(SessionState.NoSession)
        coEvery { sessionManager.authed<FeederStatusResponse>(any()) } coAnswers {
            firstArg<suspend (String) -> FeederStatusResponse>().invoke("token")
        }
    }

    @AfterEach
    fun teardown() {
        server.shutdown()
        storeScope.cancel()
        storeFile.delete()
    }

    private fun CoroutineScope.repo() = FeederLinkRepo(
        appScope = this,
        dataStore = dataStore,
        json = appJson,
        endpoint = endpoint,
        sessionManager = sessionManager,
        accessRepo = accessRepo,
    )

    @Test
    fun `a registered feeder becomes the linked state`() = runTest {
        server.enqueue(MockResponse().setBody(LINKED_RESPONSE))
        val repo = backgroundScope.repo()

        repo.register(FEEDER_ID)

        val sent = json.decodeFromString(
            RegisterFeederRequest.serializer(),
            server.takeRequest().body.readUtf8(),
        )
        sent.feederId shouldBe FEEDER_ID

        val state = repo.state.value
        state.shouldBeInstanceOf<FeederLinkRepo.FeederLinkState.Linked>()
        state.tier shouldBe "feeder"
        state.feeder.feederId shouldBe FEEDER_ID
        state.feeder.eligible shouldBe true

        coVerify { accessRepo.refresh("feeder-link") }
    }

    @Test
    fun `an active session refreshes the link state`() = runTest {
        every { sessionManager.state } returns MutableStateFlow<SessionState>(SessionState.Active(INSTALLATION_ID))
        server.enqueue(MockResponse().setBody(LINKED_RESPONSE))
        val repo = backgroundScope.repo()

        val state = repo.state.first { it != FeederLinkRepo.FeederLinkState.Unknown }

        server.requestCount shouldBe 1
        server.takeRequest().apply {
            method shouldBe "GET"
            path shouldBe "/api/v1/feeder"
        }
        state.shouldBeInstanceOf<FeederLinkRepo.FeederLinkState.Linked>()
        state.feeder.feederId shouldBe FEEDER_ID

        val persisted = dataStore.createValue<FeederLinkRepo.LinkRecord?>(
            key = "feeder.link.last",
            defaultValue = null,
            json = appJson,
        )
        persisted.value()!!.apply {
            installationId shouldBe INSTALLATION_ID
            tier shouldBe "feeder"
            feeder!!.feederId shouldBe FEEDER_ID
        }
    }

    @Test
    fun `an omitted feeder becomes the unlinked state`() = runTest {
        server.enqueue(MockResponse().setBody(UNLINKED_RESPONSE))
        val repo = backgroundScope.repo()

        repo.refresh()

        val state = repo.state.value
        state.shouldBeInstanceOf<FeederLinkRepo.FeederLinkState.Unlinked>()
        state.tier shouldBe "free"
    }

    @Test
    fun `an inactive feeder is reported with its code`() = runTest {
        server.enqueue(problem(409, "feeder_inactive"))
        val repo = backgroundScope.repo()

        val error = shouldThrow<ServerApiException> { repo.register(FEEDER_ID) }
        error.code shouldBe "feeder_inactive"
        repo.state.value shouldBe FeederLinkRepo.FeederLinkState.Unknown
    }

    @Test
    fun `a network mismatch is reported with its code`() = runTest {
        server.enqueue(problem(403, "feeder_network_mismatch"))
        val repo = backgroundScope.repo()

        shouldThrow<ServerApiException> { repo.register(FEEDER_ID) }.code shouldBe "feeder_network_mismatch"
    }

    @Test
    fun `unlinking is idempotent`() = runTest {
        server.enqueue(MockResponse().setBody(UNLINKED_RESPONSE))
        server.enqueue(MockResponse().setBody(UNLINKED_RESPONSE))
        val repo = backgroundScope.repo()

        repo.unlink()
        repo.unlink()

        repo.state.value.shouldBeInstanceOf<FeederLinkRepo.FeederLinkState.Unlinked>()
        coVerify(exactly = 2) { accessRepo.refresh("feeder-link") }
    }

    @Test
    fun `a slow status refresh does not overwrite a newer registration`() = runTest {
        // The status GET answers only after the registration POST is done
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.method) {
                "GET" -> MockResponse().setBody(UNLINKED_RESPONSE).setBodyDelay(1, TimeUnit.SECONDS)
                else -> MockResponse().setBody(LINKED_RESPONSE)
            }
        }
        val repo = backgroundScope.repo()

        val refreshing = launch(start = CoroutineStart.UNDISPATCHED) { repo.refresh() }
        withContext(Dispatchers.IO) {
            while (server.requestCount < 1) Thread.sleep(10)
        }

        repo.register(FEEDER_ID)
        refreshing.join()

        val state = repo.state.value
        state.shouldBeInstanceOf<FeederLinkRepo.FeederLinkState.Linked>()
        state.feeder.feederId shouldBe FEEDER_ID
    }

    @Test
    fun `a lost answer is settled by the feeder the server reports`() = runTest {
        server.enqueue(MockResponse().setBody(LINKED_RESPONSE))
        val repo = backgroundScope.repo()

        repo.reconcileRegistration(FEEDER_ID) shouldBe true

        server.takeRequest().apply {
            method shouldBe "GET"
            path shouldBe "/api/v1/feeder"
        }
        coVerify { accessRepo.refresh("feeder-link") }
    }

    @Test
    fun `a link to another feeder does not settle this registration`() = runTest {
        server.enqueue(MockResponse().setBody(LINKED_RESPONSE))
        val repo = backgroundScope.repo()

        repo.reconcileRegistration(OTHER_FEEDER_ID) shouldBe false

        repo.state.value.shouldBeInstanceOf<FeederLinkRepo.FeederLinkState.Linked>()
        coVerify(exactly = 0) { accessRepo.refresh(any()) }
    }

    @Test
    fun `no link at all does not settle this registration`() = runTest {
        server.enqueue(MockResponse().setBody(UNLINKED_RESPONSE))
        val repo = backgroundScope.repo()

        repo.reconcileRegistration(FEEDER_ID) shouldBe false
    }

    @Test
    fun `a status fetch that fails is reported to the caller`() = runTest {
        server.enqueue(problem(503, "server_error"))
        val repo = backgroundScope.repo()

        shouldThrow<ServerApiException> { repo.reconcileRegistration(FEEDER_ID) }
    }

    @Test
    fun `an entitlement refresh cannot take back a confirmed registration`() = runTest {
        // Not an IOException: AccessRepo absorbs those itself, this is what gets past it
        coEvery { accessRepo.refresh("feeder-link") } throws IllegalStateException("decoding went wrong")
        server.enqueue(MockResponse().setBody(LINKED_RESPONSE))
        server.enqueue(MockResponse().setBody(LINKED_RESPONSE))
        val repo = backgroundScope.repo()

        repo.reconcileRegistration(FEEDER_ID) shouldBe true
        repo.register(FEEDER_ID)

        repo.state.value.shouldBeInstanceOf<FeederLinkRepo.FeederLinkState.Linked>()
    }

    private fun problem(status: Int, code: String) = MockResponse()
        .setResponseCode(status)
        .setHeader("Content-Type", "application/problem+json")
        .setBody("""{"type":"about:blank","title":"error","status":$status,"detail":"nope","code":"$code"}""")

    companion object {
        private var counter = 0
        private const val INSTALLATION_ID = "5a5c6b2e-3b0a-4a2e-9a0f-000000000002"
        private const val FEEDER_ID = "11111111-1111-4111-8111-111111111111"
        private const val OTHER_FEEDER_ID = "22222222-2222-4222-8222-222222222222"

        private val LIMITS = """
            {
              "viewingIntervalSeconds": 1,
              "viewingBurst": 5,
              "viewingPerDay": 500000,
              "searchesPerDay": 2000,
              "watchesPerDay": 250000,
              "maxRadiusNm": 50,
              "maxLocationWatchRadiusKm": 250,
              "watchTypes": ["hex", "callsign", "squawk", "location"]
            }
        """.trimIndent()

        private val LINKED_RESPONSE = """
            {
              "tier": "feeder",
              "restricted": false,
              "limits": $LIMITS,
              "feeder": {
                "feederId": "$FEEDER_ID",
                "status": "active",
                "networkVerified": true,
                "linkedAt": 1710000000000,
                "checkedAt": 1710000000000,
                "lastActiveAt": 1710000000000,
                "validUntil": 1710086400000,
                "nextCheckAt": 1710021600000,
                "eligible": true
              }
            }
        """.trimIndent()

        private val UNLINKED_RESPONSE = """
            {
              "tier": "free",
              "restricted": false,
              "limits": $LIMITS
            }
        """.trimIndent()
    }
}
