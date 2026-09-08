package eu.darken.apl.server.access

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.datastore.createValue
import eu.darken.apl.common.datastore.value
import eu.darken.apl.common.http.HttpModule
import eu.darken.apl.common.network.NetworkStateProvider
import eu.darken.apl.common.serialization.SerializationModule
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.ServerModule
import eu.darken.apl.server.api.AccessResponse
import eu.darken.apl.server.api.Allowance
import eu.darken.apl.server.api.ServerEndpoint
import eu.darken.apl.server.api.UsageUpdate
import eu.darken.apl.server.session.SessionManager
import eu.darken.apl.server.session.SessionState
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.coroutine.TestDispatcherProvider
import java.io.File

class AccessRepoTest : BaseTest() {

    private val appJson: Json = SerializationModule().json()

    private lateinit var server: MockWebServer
    private lateinit var storeScope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var endpoint: ServerEndpoint
    private lateinit var storeFile: File

    private val sessionManager = mockk<SessionManager>()
    private val networkStateProvider = mockk<NetworkStateProvider>()
    private val monotonicClock = object : MonotonicClock {
        var elapsedMillis = 0L
        override fun elapsed(): Long = elapsedMillis
    }
    private val serverClock = ServerClock(monotonicClock)

    @BeforeEach
    fun setup() {
        server = MockWebServer()
        server.start()

        storeFile = File(IO_TEST_BASEDIR, "access-${storeCounter++}.preferences_pb")
        storeFile.parentFile?.mkdirs()
        storeFile.delete()
        storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        dataStore = PreferenceDataStoreFactory.create(scope = storeScope, produceFile = { storeFile })

        endpoint = ServerEndpoint(
            baseClient = HttpModule().baseHttpClient(),
            json = ServerModule.serverJson(),
            dispatcherProvider = TestDispatcherProvider(),
            serverClock = serverClock,
        ).apply {
            baseUrl = server.url("/").toString()
        }

        every { sessionManager.state } returns MutableStateFlow(SessionState.Active(INSTALLATION_ID))
        coEvery { sessionManager.authed<AccessResponse>(any()) } coAnswers {
            firstArg<suspend (String) -> AccessResponse>().invoke("token")
        }
        every { networkStateProvider.networkState } returns emptyFlow()
    }

    @AfterEach
    fun teardown() {
        server.shutdown()
        storeScope.cancel()
        storeFile.delete()
    }

    private fun TestScope.createRepo() = AccessRepo(
        // The repo keeps collectors running for its whole lifetime, they end with the test body
        appScope = backgroundScope,
        dataStore = dataStore,
        json = appJson,
        endpoint = endpoint,
        sessionManager = sessionManager,
        serverClock = serverClock,
        networkStateProvider = networkStateProvider,
    )

    @Test
    fun `access state is parsed and persisted`() = runTest {
        server.enqueue(MockResponse().setBody(ACCESS_RESPONSE))
        val repo = createRepo()

        repo.refresh("test")

        val state = repo.state.value!!
        state.installationId shouldBe INSTALLATION_ID
        state.tier shouldBe AccessState.Tier.FREE
        state.restricted shouldBe false
        state.limits.viewingIntervalSeconds shouldBe 5
        state.limits.concurrency shouldBe 3
        state.usage.search.remaining shouldBe 25
        state.allowsWatchType("hex") shouldBe true
        state.allowsWatchType("location") shouldBe false
        state.maxArRadiusNm shouldBe 25

        val persisted = dataStore.createValue<AccessState?>(
            key = "access.last",
            defaultValue = null,
            json = appJson,
        )
        persisted.value() shouldBe state
    }

    @Test
    fun `usage updates replace only the named bucket`() = runTest {
        server.enqueue(MockResponse().setBody(ACCESS_RESPONSE))
        val repo = createRepo()
        repo.refresh("test")

        val before = repo.state.value!!.usage

        repo.applyUsage(
            UsageUpdate(
                scope = "principal",
                bucket = "SEARCH",
                resetsAt = before.resetsAt,
                allowance = Allowance(limit = 25, used = 5, reserved = 0, remaining = 20),
            )
        )

        repo.state.value!!.usage.apply {
            search.used shouldBe 5
            search.remaining shouldBe 20
            viewing shouldBe before.viewing
            watch shouldBe before.watch
        }
    }

    @Test
    fun `a replayed usage update never moves the counter backwards`() = runTest {
        server.enqueue(MockResponse().setBody(ACCESS_RESPONSE))
        val repo = createRepo()
        repo.refresh("test")
        val resetsAt = repo.state.value!!.usage.resetsAt

        repo.applyUsage(
            UsageUpdate("principal", "SEARCH", resetsAt, Allowance(25, 5, 0, 20))
        )
        repo.applyUsage(
            UsageUpdate("principal", "SEARCH", resetsAt, Allowance(25, 2, 0, 23))
        )

        repo.state.value!!.usage.search.used shouldBe 5
    }

    @Test
    fun `two immediate refreshes result in one request`() = runTest {
        server.enqueue(MockResponse().setBody(ACCESS_RESPONSE))
        val repo = createRepo()

        repo.refresh("first")
        repo.refresh("second")

        server.requestCount shouldBe 1
    }

    companion object {
        private const val INSTALLATION_ID = "5a5c6b2e-3b0a-4a2e-9a0f-000000000002"
        private var storeCounter = 0

        private val ACCESS_RESPONSE = """
            {
              "tier": "free",
              "restricted": false,
              "allowanceScope": "principal",
              "limits": {
                "viewingIntervalSeconds": 5,
                "viewingBurst": 3,
                "viewingPerDay": 25000,
                "searchesPerDay": 25,
                "watchesPerDay": 1000,
                "maxRadiusNm": 25,
                "watchTypes": ["hex", "callsign"],
                "specialCategoryPreviewResults": 10,
                "maxBroadResults": 100,
                "maxMapResults": 2000
              },
              "usage": {
                "resetsAt": 1710028800000,
                "viewing": {"limit": 25000, "used": 0, "reserved": 0, "remaining": 25000},
                "search": {"limit": 25, "used": 0, "reserved": 0, "remaining": 25},
                "watch": {"limit": 1000, "used": 0, "reserved": 0, "remaining": 1000}
              },
              "installationRequests": {
                "viewing": {"perSecond": 0.2, "burst": 3},
                "search": {"perSecond": 1.0, "burst": 3},
                "watch": {"perSecond": 1.0, "burst": 3},
                "concurrency": 3
              }
            }
        """.trimIndent()
    }
}
