package eu.darken.apl.feeder.core.link

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import eu.darken.apl.common.MonotonicClock
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.coroutine.TestDispatcherProvider
import java.io.File

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

        every { sessionManager.state } returns MutableStateFlow(SessionState.Active(INSTALLATION_ID))
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

    private fun problem(status: Int, code: String) = MockResponse()
        .setResponseCode(status)
        .setHeader("Content-Type", "application/problem+json")
        .setBody("""{"type":"about:blank","title":"error","status":$status,"detail":"nope","code":"$code"}""")

    companion object {
        private var counter = 0
        private const val INSTALLATION_ID = "5a5c6b2e-3b0a-4a2e-9a0f-000000000002"
        private const val FEEDER_ID = "11111111-1111-4111-8111-111111111111"

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
