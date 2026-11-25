package eu.darken.apl.server.access

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.datastore.createValue
import eu.darken.apl.common.datastore.value
import eu.darken.apl.common.http.HttpModule
import eu.darken.apl.common.network.NetworkStateProvider
import eu.darken.apl.common.serialization.SerializationModule
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.ServerModule
import eu.darken.apl.server.api.AccessResponse
import eu.darken.apl.server.api.AccessSubscription
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
import kotlinx.coroutines.flow.Flow
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
import testhelper.TestCompatibilityStore
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
    private val compatStore = TestCompatibilityStore()

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
            compatibility = compatStore.holder(),
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
        compatStore.close()
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
    private fun TestScope.createRepo(
        fakeEndpoint: ServerEndpoint,
        store: DataStore<Preferences> = dataStore,
    ) = AccessRepo(
        appScope = backgroundScope,
        dataStore = store,
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

    private fun subscribedResponse() = farFutureResetResponse().copy(tier = "subscriber", allowanceScope = "subscription")

    private fun subscribedResponse(state: String, expiresAt: Long?) =
        subscribedResponse().copy(subscription = AccessSubscription(state = state, expiresAt = expiresAt))

    /**
     * Server time follows virtual time from [BASE_EPOCH] on. Unanchored, the clock reads the wall clock
     * and an expiry derived from virtual time would never come due.
     */
    private fun TestScope.anchorServerClock() {
        monotonicClock.elapsedSource = { testScheduler.currentTime }
        serverClock.noteServerTime(BASE_EPOCH)
    }

    private fun TestScope.advanceTimeTo(millis: Long) {
        advanceTimeBy(millis - testScheduler.currentTime)
        runCurrent()
    }

    /** Keeps persisting on the test thread, a file store lets virtual time run past a refresh that is still writing. */
    private class InMemoryDataStore : DataStore<Preferences> {
        private val current = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = current
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(current.value).also { current.value = it }
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
        // The fetch start belongs to this process and is not written to disk
        persisted.value() shouldBe state.copy(fetchStartedElapsed = null)
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
    fun `usage for the subscription scope applies while subscribed`() = runTest {
        val fakeEndpoint = mockk<ServerEndpoint>()
        coEvery { fakeEndpoint.access(any()) } returns subscribedResponse()
        val repo = createRepo(fakeEndpoint)
        repo.refresh("test")
        val before = repo.state.value!!.usage

        repo.applyUsage(UsageUpdate("principal", "SEARCH", before.resetsAt, Allowance(25, 9, 0, 16)))
        repo.state.value!!.usage shouldBe before

        repo.applyUsage(UsageUpdate("subscription", "SEARCH", before.resetsAt, Allowance(25, 5, 0, 20)))
        repo.state.value!!.usage.search.used shouldBe 5
    }

    @Test
    fun `after a refresh switches to the subscription scope only its usage applies`() = runTest {
        monotonicClock.elapsedSource = { testScheduler.currentTime }
        var response = farFutureResetResponse()
        val fakeEndpoint = mockk<ServerEndpoint>()
        coEvery { fakeEndpoint.access(any()) } coAnswers { response }
        val repo = createRepo(fakeEndpoint)
        repo.refresh("test")
        repo.state.value!!.allowanceScope shouldBe "principal"

        response = subscribedResponse()
        advanceTimeBy(5_000)
        repo.refresh("subscribed")
        val switched = repo.state.value!!
        switched.tier shouldBe AccessState.Tier.SUBSCRIBER
        switched.allowanceScope shouldBe "subscription"
        val before = switched.usage

        repo.applyUsage(UsageUpdate("principal", "SEARCH", before.resetsAt, Allowance(25, 9, 0, 16)))
        repo.state.value!!.usage shouldBe before

        repo.applyUsage(UsageUpdate("subscription", "SEARCH", before.resetsAt, Allowance(25, 5, 0, 20)))
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

    @Test
    fun `a refresh publishes when its fetch started`() = runTest {
        every { sessionManager.state } returns MutableStateFlow<SessionState>(SessionState.NoSession)
        monotonicClock.elapsedMillis = 7_000
        val fakeEndpoint = mockk<ServerEndpoint>()
        coEvery { fakeEndpoint.access(any()) } coAnswers {
            monotonicClock.elapsedMillis = 7_300
            farFutureResetResponse()
        }
        val repo = createRepo(fakeEndpoint)

        repo.refresh("test")

        repo.state.value!!.fetchStartedElapsed shouldBe 7_000L
    }

    @Test
    fun `a fetch that started before a marker lands after it with a start below the marker`() = runTest {
        every { sessionManager.state } returns MutableStateFlow<SessionState>(SessionState.NoSession)
        monotonicClock.elapsedSource = { testScheduler.currentTime }
        val responseGate = CompletableDeferred<Unit>()
        val fakeEndpoint = mockk<ServerEndpoint>()
        coEvery { fakeEndpoint.access(any()) } coAnswers {
            responseGate.await()
            farFutureResetResponse()
        }
        val repo = createRepo(fakeEndpoint)

        advanceTimeBy(1_000)
        val fetch = launch { repo.refresh("in-flight") }
        runCurrent()

        advanceTimeBy(500)
        val marker = serverClock.elapsed()

        advanceTimeBy(500)
        responseGate.complete(Unit)
        fetch.join()

        val published = repo.state.value!!
        withClue("started ${published.fetchStartedElapsed}, marker $marker") {
            published.fetchStartedElapsed shouldBe 1_000L
            (published.fetchStartedElapsed!! < marker) shouldBe true
        }
    }

    @Test
    fun `a policy restored from disk has no fetch start`() = runTest {
        val stored = AccessState.from(
            response = accessResponse(),
            installationId = INSTALLATION_ID,
            fetchedAt = Instant.ofEpochMilli(1_710_000_000_000L),
        ).copy(fetchStartedElapsed = 42L)
        dataStore.createValue<AccessState?>(
            key = "access.last",
            defaultValue = null,
            json = appJson,
        ).value(stored)

        // The startup fetch never lands, so the state is the restored one
        val fetchGate = CompletableDeferred<Unit>()
        val fakeEndpoint = mockk<ServerEndpoint>()
        coEvery { fakeEndpoint.access(any()) } coAnswers {
            fetchGate.await()
            accessResponse()
        }
        val repo = createRepo(fakeEndpoint)

        repo.awaitLoaded()
        runCurrent()

        repo.state.value!!.installationId shouldBe INSTALLATION_ID
        repo.state.value!!.fetchStartedElapsed shouldBe null
    }

    @Test
    fun `usage updates keep the fetch start`() = runTest {
        every { sessionManager.state } returns MutableStateFlow<SessionState>(SessionState.NoSession)
        monotonicClock.elapsedMillis = 3_000
        val fakeEndpoint = mockk<ServerEndpoint>()
        coEvery { fakeEndpoint.access(any()) } coAnswers { farFutureResetResponse() }
        val repo = createRepo(fakeEndpoint)
        repo.refresh("test")
        val resetsAt = repo.state.value!!.usage.resetsAt

        repo.applyUsage(UsageUpdate("principal", "SEARCH", resetsAt, Allowance(25, 5, 0, 20)))

        repo.state.value!!.usage.search.used shouldBe 5
        repo.state.value!!.fetchStartedElapsed shouldBe 3_000L
    }

    @Test
    fun `usage for another scope refreshes the policy once per throttle window`() = runTest {
        every { sessionManager.state } returns MutableStateFlow<SessionState>(SessionState.NoSession)
        monotonicClock.elapsedSource = { testScheduler.currentTime }
        var calls = 0
        val fakeEndpoint = mockk<ServerEndpoint>()
        coEvery { fakeEndpoint.access(any()) } coAnswers { calls++; farFutureResetResponse() }
        val repo = createRepo(fakeEndpoint, InMemoryDataStore())
        repo.refresh("test")
        val before = repo.state.value!!.usage
        val foreign = UsageUpdate("subscription", "SEARCH", before.resetsAt, Allowance(25, 9, 0, 16))

        advanceTimeBy(5_000)
        repo.applyUsage(foreign)
        runCurrent()
        withClue("first foreign update") { calls shouldBe 2 }
        repo.state.value!!.usage shouldBe before

        advanceTimeBy(60_000)
        repo.applyUsage(foreign)
        runCurrent()
        withClue("inside the throttle window") { calls shouldBe 2 }
        repo.state.value!!.usage shouldBe before

        advanceTimeBy(5 * MINUTE)
        repo.applyUsage(foreign)
        runCurrent()
        withClue("after the throttle window") { calls shouldBe 3 }
        repo.state.value!!.usage shouldBe before
    }

    @Test
    fun `usage for the policy's own scope does not refresh`() = runTest {
        every { sessionManager.state } returns MutableStateFlow<SessionState>(SessionState.NoSession)
        monotonicClock.elapsedSource = { testScheduler.currentTime }
        var calls = 0
        val fakeEndpoint = mockk<ServerEndpoint>()
        coEvery { fakeEndpoint.access(any()) } coAnswers { calls++; farFutureResetResponse() }
        val repo = createRepo(fakeEndpoint, InMemoryDataStore())
        repo.refresh("test")
        val resetsAt = repo.state.value!!.usage.resetsAt

        advanceTimeBy(5_000)
        repo.applyUsage(UsageUpdate("principal", "SEARCH", resetsAt, Allowance(25, 5, 0, 20)))
        runCurrent()

        repo.state.value!!.usage.search.used shouldBe 5
        calls shouldBe 1
    }

    @Test
    fun `usage outside the counted buckets does not refresh whatever its scope`() = runTest {
        every { sessionManager.state } returns MutableStateFlow<SessionState>(SessionState.NoSession)
        monotonicClock.elapsedSource = { testScheduler.currentTime }
        var calls = 0
        val fakeEndpoint = mockk<ServerEndpoint>()
        coEvery { fakeEndpoint.access(any()) } coAnswers { calls++; farFutureResetResponse() }
        val repo = createRepo(fakeEndpoint, InMemoryDataStore())
        repo.refresh("test")
        val before = repo.state.value!!.usage

        advanceTimeBy(5_000)
        repo.applyUsage(UsageUpdate("subscription", "ROUTE", before.resetsAt, Allowance(25, 9, 0, 16)))
        // The empty placeholder some responses carry
        repo.applyUsage(UsageUpdate("", "", 0L, Allowance(used = 0, remaining = 0)))
        runCurrent()

        repo.state.value!!.usage shouldBe before
        calls shouldBe 1
    }

    @Test
    fun `the policy is refreshed once the subscription expiry has passed`() = runTest {
        every { sessionManager.state } returns MutableStateFlow<SessionState>(SessionState.NoSession)
        anchorServerClock()
        var response = subscribedResponse(state = "active", expiresAt = BASE_EPOCH + 10 * MINUTE)
        var calls = 0
        val fakeEndpoint = mockk<ServerEndpoint>()
        coEvery { fakeEndpoint.access(any()) } coAnswers { calls++; response }
        val repo = createRepo(fakeEndpoint, InMemoryDataStore())
        repo.refresh("test")
        repo.state.value!!.tier shouldBe AccessState.Tier.SUBSCRIBER

        advanceTimeTo(12 * MINUTE - 1)
        withClue("before expiry and grace") { calls shouldBe 1 }

        response = farFutureResetResponse()
        advanceTimeTo(12 * MINUTE + 1)
        withClue("after expiry and grace") { calls shouldBe 2 }
        repo.state.value!!.apply {
            tier shouldBe AccessState.Tier.FREE
            allowanceScope shouldBe "principal"
            subscription shouldBe null
        }

        advanceTimeTo(90 * MINUTE)
        withClue("nothing left to wait for") { calls shouldBe 2 }
    }

    @Test
    fun `a new policy with a later expiry replaces the scheduled refresh`() = runTest {
        every { sessionManager.state } returns MutableStateFlow<SessionState>(SessionState.NoSession)
        anchorServerClock()
        var response = subscribedResponse(state = "active", expiresAt = BASE_EPOCH + 10 * MINUTE)
        var calls = 0
        val fakeEndpoint = mockk<ServerEndpoint>()
        coEvery { fakeEndpoint.access(any()) } coAnswers { calls++; response }
        val repo = createRepo(fakeEndpoint, InMemoryDataStore())
        repo.refresh("test")

        advanceTimeTo(5_000)
        response = subscribedResponse(state = "active", expiresAt = BASE_EPOCH + 30 * MINUTE)
        repo.refresh("renewed")
        calls shouldBe 2

        advanceTimeTo(12 * MINUTE + 1)
        withClue("first expiry and grace") { calls shouldBe 2 }

        advanceTimeTo(32 * MINUTE - 1)
        withClue("before the second expiry and grace") { calls shouldBe 2 }

        response = farFutureResetResponse()
        advanceTimeTo(32 * MINUTE + 1)
        withClue("after the second expiry and grace") { calls shouldBe 3 }
    }

    @Test
    fun `a new policy without a subscription cancels the scheduled refresh`() = runTest {
        every { sessionManager.state } returns MutableStateFlow<SessionState>(SessionState.NoSession)
        anchorServerClock()
        var response = subscribedResponse(state = "active", expiresAt = BASE_EPOCH + 10 * MINUTE)
        var calls = 0
        val fakeEndpoint = mockk<ServerEndpoint>()
        coEvery { fakeEndpoint.access(any()) } coAnswers { calls++; response }
        val repo = createRepo(fakeEndpoint, InMemoryDataStore())
        repo.refresh("test")

        advanceTimeTo(5_000)
        response = farFutureResetResponse()
        repo.refresh("lapsed")
        calls shouldBe 2

        advanceTimeTo(60 * MINUTE)
        calls shouldBe 2
    }

    @Test
    fun `a new policy without an expiry date cancels the scheduled refresh`() = runTest {
        every { sessionManager.state } returns MutableStateFlow<SessionState>(SessionState.NoSession)
        anchorServerClock()
        var response = subscribedResponse(state = "active", expiresAt = BASE_EPOCH + 10 * MINUTE)
        var calls = 0
        val fakeEndpoint = mockk<ServerEndpoint>()
        coEvery { fakeEndpoint.access(any()) } coAnswers { calls++; response }
        val repo = createRepo(fakeEndpoint, InMemoryDataStore())
        repo.refresh("test")

        advanceTimeTo(5_000)
        response = subscribedResponse(state = "active", expiresAt = null)
        repo.refresh("no-date")
        calls shouldBe 2

        advanceTimeTo(60 * MINUTE)
        calls shouldBe 2
    }

    @Test
    fun `a subscription still granted past its expiry is checked again until it is not`() = runTest {
        every { sessionManager.state } returns MutableStateFlow<SessionState>(SessionState.NoSession)
        anchorServerClock()
        var response = subscribedResponse(state = "active", expiresAt = BASE_EPOCH + 10 * MINUTE)
        var calls = 0
        val fakeEndpoint = mockk<ServerEndpoint>()
        coEvery { fakeEndpoint.access(any()) } coAnswers { calls++; response }
        val repo = createRepo(fakeEndpoint, InMemoryDataStore())
        repo.refresh("test")

        advanceTimeTo(12 * MINUTE + 1)
        withClue("expiry refresh, the policy still claims the subscription") { calls shouldBe 2 }
        repo.state.value!!.tier shouldBe AccessState.Tier.SUBSCRIBER

        advanceTimeTo(27 * MINUTE - 1)
        withClue("before the first recheck") { calls shouldBe 2 }
        advanceTimeTo(27 * MINUTE + 1)
        withClue("first recheck, still stale") { calls shouldBe 3 }

        response = farFutureResetResponse()
        advanceTimeTo(42 * MINUTE - 1)
        withClue("before the second recheck") { calls shouldBe 3 }
        advanceTimeTo(42 * MINUTE + 1)
        withClue("second recheck") { calls shouldBe 4 }
        repo.state.value!!.tier shouldBe AccessState.Tier.FREE

        advanceTimeTo(120 * MINUTE)
        withClue("no recheck once the subscription is gone") { calls shouldBe 4 }
    }

    @Test
    fun `a non-granting subscription past its expiry schedules no refresh`() = runTest {
        every { sessionManager.state } returns MutableStateFlow<SessionState>(SessionState.NoSession)
        anchorServerClock()
        val calls = mutableMapOf<String, Int>()
        listOf("on_hold", "paused", "pending").forEach { state ->
            val fakeEndpoint = mockk<ServerEndpoint>()
            coEvery { fakeEndpoint.access(any()) } coAnswers {
                calls[state] = (calls[state] ?: 0) + 1
                subscribedResponse(state = state, expiresAt = BASE_EPOCH - 10 * MINUTE)
            }
            createRepo(fakeEndpoint, InMemoryDataStore()).refresh("test")
        }

        advanceTimeTo(30 * MINUTE)

        calls shouldBe mapOf("on_hold" to 1, "paused" to 1, "pending" to 1)
    }

    companion object {
        private const val INSTALLATION_ID = "5a5c6b2e-3b0a-4a2e-9a0f-000000000002"
        private const val FAR_FUTURE_RESET_AT = 4_102_444_800_000L
        private const val BASE_EPOCH = 1_800_000_000_000L
        private const val MINUTE = 60_000L
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
