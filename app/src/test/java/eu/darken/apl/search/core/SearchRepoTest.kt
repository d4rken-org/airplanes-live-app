package eu.darken.apl.search.core

import androidx.test.core.app.ApplicationProvider
import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.http.HttpModule
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.main.core.db.AircraftDatabase
import eu.darken.apl.main.core.query.TermOutcome
import eu.darken.apl.main.core.request.OperationRunner
import eu.darken.apl.main.core.request.OperationStore
import eu.darken.apl.main.core.request.RequestCoordinator
import eu.darken.apl.search.ui.SearchViewModel
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.ServerModule
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.server.api.Allowance
import eu.darken.apl.server.api.RequestLimits
import eu.darken.apl.server.api.RequestRate
import eu.darken.apl.server.api.SearchBatchRequest
import eu.darken.apl.server.api.TierPolicy
import eu.darken.apl.server.api.Usage
import eu.darken.apl.server.api.ServerEndpoint
import eu.darken.apl.server.session.SessionManager
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import testhelper.coroutine.TestDispatcherProvider
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class SearchRepoTest {

    private lateinit var server: MockWebServer
    private lateinit var database: AircraftDatabase
    private lateinit var repo: SearchRepo

    private val json = ServerModule.serverJson()
    private val sessionManager = mockk<SessionManager>()
    private val accessRepo = mockk<AccessRepo>(relaxed = true)
    private val monotonicClock = object : MonotonicClock {
        override fun elapsed(): Long = 0L
    }
    private val serverClock = ServerClock(monotonicClock)

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.deleteDatabase("aircraft")
        database = AircraftDatabase(context, TestDispatcherProvider())

        val endpoint = ServerEndpoint(
            baseClient = HttpModule().baseHttpClient(),
            json = json,
            dispatcherProvider = TestDispatcherProvider(),
            serverClock = serverClock,
        ).apply {
            baseUrl = server.url("/").toString()
        }

        every { accessRepo.state } returns MutableStateFlow<AccessState?>(policy())
        coEvery { sessionManager.authed<Any>(any()) } coAnswers {
            firstArg<suspend (String) -> Any>().invoke("token")
        }

        val store = OperationStore(database, TestDispatcherProvider(), json)
        val aircraftRepo = AircraftRepo(
            appScope = CoroutineScope(Dispatchers.Unconfined),
            aircraftDatabase = database,
            endpoint = endpoint,
            sessionManager = sessionManager,
            accessRepo = accessRepo,
            requestCoordinator = RequestCoordinator(accessRepo, monotonicClock),
            operationRunner = OperationRunner(store, serverClock),
            operationStore = store,
            serverClock = serverClock,
            json = json,
        )
        repo = SearchRepo(aircraftRepo, accessRepo)
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    /** A rate the coordinator never has to pace, the clock in this test does not advance. */
    private fun policy() = AccessState(
        installationId = "installation",
        fetchedAt = java.time.Instant.EPOCH,
        tier = AccessState.Tier.FREE,
        restricted = false,
        allowanceScope = "principal",
        limits = TierPolicy(
            viewingIntervalSeconds = 5,
            viewingBurst = 3,
            viewingPerDay = 25000,
            searchesPerDay = 25,
            watchesPerDay = 1000,
            maxRadiusNm = 25,
            watchTypes = listOf("hex", "callsign"),
        ),
        usage = Usage(
            resetsAt = 1_710_028_800_000L,
            viewing = Allowance(25000, 0, 0, 25000),
            search = Allowance(25, 0, 0, 25),
            watch = Allowance(1000, 0, 0, 1000),
        ),
        installationRequests = RequestLimits(
            viewing = RequestRate(perSecond = 1000.0, burst = 1000),
            search = RequestRate(perSecond = 1000.0, burst = 1000),
            watch = RequestRate(perSecond = 1000.0, burst = 1000),
            concurrency = 3,
        ),
    )

    @Test
    fun `terms are built per mode`() {
        buildSearchQuery(SearchViewModel.State.Mode.ALL, "DLH453, 3C65A3").terms shouldBe listOf(
            SearchTerm("DLH453"),
            SearchTerm("3C65A3"),
        )
        buildSearchQuery(SearchViewModel.State.Mode.HEX, "3c65a3").terms shouldBe listOf(SearchTerm("3c65a3"))
        buildSearchQuery(SearchViewModel.State.Mode.SQUAWK, "7700,7600").terms shouldBe listOf(
            SearchTerm("7700"),
            SearchTerm("7600"),
        )
        buildSearchQuery(SearchViewModel.State.Mode.ALL, " , ").terms shouldBe emptyList()
        buildSearchQuery(SearchViewModel.State.Mode.POSITION, "Frankfurt").terms shouldBe emptyList()
    }

    @Test
    fun `interesting mode collapses into one categorised term`() {
        buildSearchQuery(SearchViewModel.State.Mode.INTERESTING, "military, ladd, pia").terms shouldBe listOf(
            SearchTerm(categories = setOf(SearchCategory.MILITARY, SearchCategory.LADD, SearchCategory.PIA))
        )
        buildSearchQuery(SearchViewModel.State.Mode.INTERESTING, "military, nonsense").terms shouldBe listOf(
            SearchTerm(categories = setOf(SearchCategory.MILITARY))
        )
        buildSearchQuery(SearchViewModel.State.Mode.INTERESTING, "").terms shouldBe listOf(
            SearchTerm(categories = SearchCategory.entries.toSet())
        )
    }

    @Test
    fun `a categorised term travels as its wire categories`() {
        runTest {
            server.enqueue(MockResponse().setBody(batchResponse(OUTCOME_ANSWERED_EMPTY)))

            repo.search(buildSearchQuery(SearchViewModel.State.Mode.INTERESTING, "pia, military"))

            val sent = json.decodeFromString(
                SearchBatchRequest.serializer(),
                server.takeRequest().body.readUtf8(),
            )
            sent.terms.single().apply {
                text shouldBe ""
                categories shouldBe listOf("military", "pia")
            }
        }
    }

    @Test
    fun `answered terms are mapped and written to the cache`() {
        runTest {
            server.enqueue(MockResponse().setBody(batchResponse(OUTCOME_ANSWERED_ONE, withAircraft = true)))

            val result = repo.search(SearchQuery(listOf(SearchTerm("DLH453"))))

            val outcome = result.terms.single().outcome
            outcome.shouldBeInstanceOf<TermOutcome.Answered>()
            outcome.complete shouldBe true
            outcome.charged shouldBe true
            result.aircraft.map { it.hex } shouldContainExactlyInAnyOrder listOf("3C65A3")
            result.cacheOnly shouldBe emptyList()

            database.count() shouldBe 1
        }
    }

    @Test
    fun `a capped answer carries the total number of matches`() {
        runTest {
            server.enqueue(MockResponse().setBody(batchResponse(OUTCOME_CAPPED, withAircraft = true)))

            val outcome = repo.search(SearchQuery(listOf(SearchTerm("military")))).terms.single().outcome
            outcome.shouldBeInstanceOf<TermOutcome.Answered>()
            outcome.capped shouldBe true
            outcome.totalMatching shouldBe 15
        }
    }

    @Test
    fun `per item errors become rejected outcomes`() {
        runTest {
            server.enqueue(MockResponse().setBody(batchResponse(OUTCOME_EXHAUSTED_AND_INVALID)))

            val result = repo.search(SearchQuery(listOf(SearchTerm("AAAAAA"), SearchTerm("BBBBBB"))))

            val first = result.terms[0].outcome
            first.shouldBeInstanceOf<TermOutcome.Rejected>()
            first.code shouldBe "daily_allowance_exhausted"

            val second = result.terms[1].outcome
            second.shouldBeInstanceOf<TermOutcome.Rejected>()
            second.code shouldBe "invalid_request"
        }
    }

    @Test
    fun `a network failure returns the cache with the error`() {
        runTest {
            repeat(4) { server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)) }

            val result = repo.search(SearchQuery(listOf(SearchTerm("DLH453"))))

            result.error.shouldBeInstanceOf<IOException>()
            result.aircraft shouldBe emptyList()
            result.terms shouldBe emptyList()
            result.latestUsage.shouldBeNull()
        }
    }

    private fun batchResponse(outcomes: String, withAircraft: Boolean = false) = """
        {
          "operationId": "5a5c6b2e-3b0a-4a2e-9a0f-000000000001",
          "serverTime": $SERVER_TIME,
          "completedAt": $SERVER_TIME,
          "operationExpiresAt": ${SERVER_TIME + 300000},
          "metadata": {
            "sourceTime": $SERVER_TIME,
            "fetchedAt": $SERVER_TIME,
            "expiresAt": ${SERVER_TIME + 120000},
            "complete": true,
            "stale": false
          },
          "outcomes": [$outcomes],
          "aircraft": ${if (withAircraft) AIRCRAFT else "[]"},
          "usage": {
            "scope": "principal",
            "bucket": "SEARCH",
            "resetsAt": 1710028800000,
            "allowance": {"limit": 25, "used": 1, "reserved": 0, "remaining": 24}
          }
        }
    """.trimIndent()

    companion object {
        private const val SERVER_TIME = 1_710_000_000_000L

        private val AIRCRAFT = """
            [
              {
                "id": "3c65a3",
                "messageObservedAt": ${SERVER_TIME - 500},
                "callsign": "DLH453",
                "registration": "D-AIUE",
                "aircraftType": "A320"
              }
            ]
        """.trimIndent()

        private const val OUTCOME_ANSWERED_EMPTY = """{"index":0,"status":"answered","complete":true,"charged":true}"""

        private const val OUTCOME_ANSWERED_ONE =
            """{"index":0,"status":"answered","aircraftIds":["3c65a3"],"totalMatching":1,""" +
                    """"complete":true,"charged":true}"""

        private const val OUTCOME_CAPPED =
            """{"index":0,"status":"answered","aircraftIds":["3c65a3"],"totalMatching":15,""" +
                    """"capped":true,"complete":true,"charged":true}"""

        private const val OUTCOME_EXHAUSTED_AND_INVALID =
            """{"index":0,"status":"error","error":{"code":"daily_allowance_exhausted"}},""" +
                    """{"index":1,"status":"error","error":{"code":"invalid_request"}}"""
    }
}
