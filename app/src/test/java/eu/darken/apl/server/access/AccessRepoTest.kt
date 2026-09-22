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
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.ServerEndpoint
import eu.darken.apl.server.api.UsageUpdate
import eu.darken.apl.server.session.SessionManager
import eu.darken.apl.server.session.SessionState
import io.kotest.assertions.withClue
import io.kotest.matchers.longs.shouldBeInRange
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
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
import java.time.Instant

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

        /** Lets a test tie the monotonic clock to virtual time instead of stepping it by hand. */
        var elapsedSource: (() -> Long)? = null
        override fun elapsed(): Long = elapsedSource?.invoke() ?: elapsedMillis
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

    /** Attempts run on the test scheduler, a socket round trip would complete outside virtual time. */
    private fun TestScope.createRepo(fakeEndpoint: ServerEndpoint) = AccessRepo(
        appScope = backgroundScope,
        dataStore = dataStore,
        json = appJson,
        endpoint = fakeEndpoint,
        sessionManager = sessionManager,
        serverClock = serverClock,
        networkStateProvider = networkStateProvider,
    )

    private fun accessResponse() = ServerModule.serverJson().decodeFromString(AccessResponse.serializer(), ACCESS_RESPONSE)

    /** No allowance reset inside the virtual time a test advances, the reset trigger would blur the count. */
    private fun farFutureResetResponse() = accessResponse().let { response ->
        response.copy(usage = response.usage.copy(resetsAt = FAR_FUTURE_RESET_AT))
    }

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
    fun `a new period does not block the buckets it did not carry`() = runTest {
        // Seeded with search already spent, so a count that dropped has something to be rejected by
        val spent = ACCESS_RESPONSE.replace(
            """"search": {"limit": 25, "used": 0, "reserved": 0, "remaining": 25}""",
            """"search": {"limit": 25, "used": 20, "reserved": 0, "remaining": 5}""",
        )
        server.enqueue(MockResponse().setBody(spent))
        // The rollover asks for authoritative counters for the buckets this update cannot speak for
        server.enqueue(MockResponse().setBody(spent))
        val repo = createRepo()
        repo.refresh("test")
        val resetsAt = repo.state.value!!.usage.resetsAt
        val spentYesterday = repo.state.value!!.usage.search.used
        spentYesterday shouldBe 20

        // No SEARCH update first: its period is only known from the fetched policy, which is the
        // case where an inherited period would silently reject today's first count
        val nextPeriod = resetsAt + 86_400_000
        repo.applyUsage(UsageUpdate("principal", "VIEWING", nextPeriod, Allowance(25000, 1, 0, 24999)))

        repo.state.value!!.usage.apply {
            this.resetsAt shouldBe nextPeriod
            viewing.used shouldBe 1
            // Last known rather than an invented zero: this client has no count for today yet
            search.used shouldBe spentYesterday
        }

        repo.applyUsage(UsageUpdate("principal", "SEARCH", nextPeriod, Allowance(25, 1, 0, 24)))
        repo.state.value!!.usage.search.used shouldBe 1
    }

    @Test
    fun `a late update for the previous period does not rewind the shared reset`() = runTest {
        server.enqueue(MockResponse().setBody(ACCESS_RESPONSE))
        server.enqueue(MockResponse().setBody(ACCESS_RESPONSE))
        val repo = createRepo()
        repo.refresh("test")
        val resetsAt = repo.state.value!!.usage.resetsAt
        val nextPeriod = resetsAt + 86_400_000

        repo.applyUsage(UsageUpdate("principal", "VIEWING", nextPeriod, Allowance(25000, 1, 0, 24999)))
        // A search answer from before the rollover finally lands
        repo.applyUsage(UsageUpdate("principal", "SEARCH", resetsAt, Allowance(25, 24, 0, 1)))

        repo.state.value!!.usage.apply {
            search.used shouldBe 24
            this.resetsAt shouldBe nextPeriod
        }
    }

    @Test
    fun `usage for another allowance scope is ignored`() = runTest {
        server.enqueue(MockResponse().setBody(ACCESS_RESPONSE))
        val repo = createRepo()
        repo.refresh("test")
        val before = repo.state.value!!.usage
        val scope = repo.state.value!!.allowanceScope

        repo.applyUsage(
            UsageUpdate("$scope-other", "SEARCH", before.resetsAt, Allowance(25, 9, 0, 16))
        )

        repo.state.value!!.usage shouldBe before
    }

    @Test
    fun `two immediate refreshes result in one request`() = runTest {
        server.enqueue(MockResponse().setBody(ACCESS_RESPONSE))
        val repo = createRepo()

        repo.refresh("first")
        repo.refresh("second")

        server.requestCount shouldBe 1
    }

    @Test
    fun `a trigger inside the spacing window is deferred instead of dropped`() = runTest {
        var calls = 0
        val fakeEndpoint = mockk<ServerEndpoint>()
        coEvery { fakeEndpoint.access(any()) } coAnswers { calls++; accessResponse() }
        val repo = createRepo(fakeEndpoint)

        repo.refresh("first")
        monotonicClock.elapsedMillis = 200
        advanceTimeBy(200)
        repo.refresh("feeder-link")

        monotonicClock.elapsedMillis = 5_200
        advanceTimeBy(5_000)
        runCurrent()

        calls shouldBe 2
    }

    @Test
    fun `retries without a retry hint back off exponentially`() = runTest {
        monotonicClock.elapsedSource = { testScheduler.currentTime }
        val attemptsAt = mutableListOf<Long>()
        val fakeEndpoint = mockk<ServerEndpoint>()
        coEvery { fakeEndpoint.access(any()) } coAnswers {
            attemptsAt.add(testScheduler.currentTime)
            if (attemptsAt.size <= 3) throw ServerApiException(code = "database_unavailable", status = 503)
            accessResponse()
        }
        val repo = createRepo(fakeEndpoint)

        repo.refresh("test")
        advanceTimeBy(60_000)
        runCurrent()

        val gaps = attemptsAt.zipWithNext { a, b -> b - a }
        withClue("attempts at $attemptsAt, gaps $gaps") {
            gaps.size shouldBe 3
            gaps[0] shouldBeInRange 4_000L..6_000L
            gaps[1] shouldBeInRange 9_000L..11_000L
            gaps[2] shouldBeInRange 19_000L..21_000L
        }
    }

    @Test
    fun `a trigger raised during a running retry is not discarded because an older trigger was covered`() = runTest {
        every { sessionManager.state } returns MutableStateFlow<SessionState>(SessionState.NoSession)
        monotonicClock.elapsedSource = { testScheduler.currentTime }
        var calls = 0
        val retryGate = CompletableDeferred<Unit>()
        val fakeEndpoint = mockk<ServerEndpoint>()
        coEvery { fakeEndpoint.access(any()) } coAnswers {
            calls++
            when (calls) {
                1 -> throw ServerApiException(code = "database_unavailable", status = 503)
                2 -> {
                    retryGate.await()
                    farFutureResetResponse()
                }

                else -> farFutureResetResponse()
            }
        }
        val repo = createRepo(fakeEndpoint)

        // Attempt 1 fails at t=0 and schedules a retry at t=5000
        repo.refresh("initial")
        withClue("initial attempt") { calls shouldBe 1 }

        // Trigger A inside the spacing window: deferred, its follow-up yields to the pending retry
        advanceTimeBy(200)
        repo.refresh("trigger-a")

        // The retry fires at t=5000 and its attempt is now suspended on the gate
        advanceTimeBy(4_800)
        runCurrent()
        withClue("retry attempt running") { calls shouldBe 2 }

        // Trigger B is requested after the retry started, so that attempt cannot answer it
        advanceTimeBy(100)
        val triggerB = launch { repo.refresh("feeder-link") }
        runCurrent()

        retryGate.complete(Unit)
        triggerB.join()

        advanceTimeBy(10_000)
        runCurrent()

        withClue("initial, retry, and a follow-up for trigger B") { calls shouldBe 3 }
    }

    @Test
    fun `a trigger raised during a running retry gets its follow-up once the retry finishes`() = runTest {
        every { sessionManager.state } returns MutableStateFlow<SessionState>(SessionState.NoSession)
        monotonicClock.elapsedSource = { testScheduler.currentTime }
        var calls = 0
        val retryGate = CompletableDeferred<Unit>()
        val fakeEndpoint = mockk<ServerEndpoint>()
        coEvery { fakeEndpoint.access(any()) } coAnswers {
            calls++
            when (calls) {
                1 -> throw ServerApiException(code = "database_unavailable", status = 503)
                2 -> {
                    retryGate.await()
                    farFutureResetResponse()
                }

                else -> farFutureResetResponse()
            }
        }
        val repo = createRepo(fakeEndpoint)

        // Attempt 1 fails at t=0 and schedules a retry at t=5000, nothing is pending
        repo.refresh("initial")
        withClue("initial attempt") { calls shouldBe 1 }

        // The retry fires at t=5000 and its attempt is now suspended on the gate
        advanceTimeBy(5_000)
        runCurrent()
        withClue("retry attempt running") { calls shouldBe 2 }

        // The only pending trigger is requested while the retry attempt runs
        advanceTimeBy(100)
        val triggerB = launch { repo.refresh("feeder-link") }
        runCurrent()

        retryGate.complete(Unit)
        triggerB.join()

        advanceTimeBy(10_000)
        runCurrent()

        withClue("initial, retry, and a follow-up for feeder-link") { calls shouldBe 3 }
    }

    @Test
    fun `a persisted policy for the active installation survives startup`() = runTest {
        val stored = AccessState.from(
            response = accessResponse(),
            installationId = INSTALLATION_ID,
            fetchedAt = Instant.ofEpochMilli(1_710_000_000_000L),
        )
        val persisted = dataStore.createValue<AccessState?>(
            key = "access.last",
            defaultValue = null,
            json = appJson,
        )
        persisted.value(stored)

        // The startup fetch never lands, so what the test sees is what the cold start alone produced
        val fetchGate = CompletableDeferred<Unit>()
        val fakeEndpoint = mockk<ServerEndpoint>()
        coEvery { fakeEndpoint.access(any()) } coAnswers {
            fetchGate.await()
            accessResponse()
        }
        val repo = createRepo(fakeEndpoint)

        repo.awaitLoaded()
        runCurrent()

        repo.state.value shouldBe stored
        persisted.value() shouldBe stored
    }

    companion object {
        private const val INSTALLATION_ID = "5a5c6b2e-3b0a-4a2e-9a0f-000000000002"
        private const val FAR_FUTURE_RESET_AT = 4_102_444_800_000L
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
