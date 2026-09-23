package eu.darken.apl.search.core

import androidx.test.core.app.ApplicationProvider
import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.http.HttpModule
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.main.core.db.AircraftDatabase
import eu.darken.apl.main.core.db.PendingOperationEntity
import eu.darken.apl.main.core.query.TermOutcome
import eu.darken.apl.main.core.request.OperationFailedException
import eu.darken.apl.main.core.request.OperationRunner
import eu.darken.apl.main.core.request.OperationStore
import eu.darken.apl.main.core.request.RequestCoordinator
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.ServerModule
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.server.api.Allowance
import eu.darken.apl.server.api.RequestLimits
import eu.darken.apl.server.api.RequestRate
import eu.darken.apl.server.api.SearchBatchRequest
import eu.darken.apl.server.api.ServerCodes
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
import org.robolectric.annotation.Config
import testhelper.coroutine.TestDispatcherProvider
import java.io.IOException
import eu.darken.apl.server.api.SearchTerm as WireTerm

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
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
        repo = SearchRepo(aircraftRepo, accessRepo, store, serverClock, json)
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
    fun `every word is its own term`() {
        buildSearchQuery(SearchInput(text = "DLH453 3C65A3,7700 ,  A320")).terms shouldBe listOf(
            SearchTerm("DLH453"),
            SearchTerm("3C65A3"),
            SearchTerm("7700"),
            SearchTerm("A320"),
        )
        buildSearchQuery(SearchInput(text = " , \t ")).terms shouldBe emptyList()
    }

    @Test
    fun `a repeated word is sent once`() {
        buildSearchQuery(SearchInput(text = "dlh453 DLH453 A320")).terms shouldBe listOf(
            SearchTerm("dlh453"),
            SearchTerm("A320"),
        )
    }

    @Test
    fun `categories narrow every word`() {
        buildSearchQuery(
            SearchInput(text = "A400 C130", categories = setOf(SearchCategory.MILITARY))
        ).terms shouldBe listOf(
            SearchTerm("A400", setOf(SearchCategory.MILITARY)),
            SearchTerm("C130", setOf(SearchCategory.MILITARY)),
        )
    }

    @Test
    fun `categories without text are one term`() {
        buildSearchQuery(
            SearchInput(categories = setOf(SearchCategory.MILITARY, SearchCategory.LADD))
        ).terms shouldBe listOf(
            SearchTerm(categories = setOf(SearchCategory.MILITARY, SearchCategory.LADD))
        )
    }

    @Test
    fun `nothing to search is empty unless nearby`() {
        SearchInput().isEmpty shouldBe true
        SearchInput(text = " ").isEmpty shouldBe true
        SearchInput(nearby = true).isEmpty shouldBe false
        SearchInput(categories = setOf(SearchCategory.PIA)).isEmpty shouldBe false
    }

    @Test
    fun `a categorised term travels as its wire categories`() {
        runTest {
            server.enqueue(MockResponse().setBody(batchResponse(OUTCOME_ANSWERED_EMPTY)))

            repo.search(buildSearchQuery(SearchInput(categories = setOf(SearchCategory.PIA, SearchCategory.MILITARY))))

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
    fun `nearby without a filter keeps the whole area`() {
        runTest {
            server.enqueue(MockResponse().setBody(viewingResponse(capped = false, totalMatching = 3)))

            val result = repo.nearby(50.0, 8.0, 25.0)

            result.aircraft.map { it.hex } shouldContainExactlyInAnyOrder listOf("3C65A3", "AE1234", "4B1805")
            val outcome = result.terms.single().outcome
            outcome.shouldBeInstanceOf<TermOutcome.Answered>()
            outcome.totalMatching shouldBe 3
            result.charged shouldBe SearchRepo.Charged.VIEWING
        }
    }

    @Test
    fun `nearby keeps aircraft containing any word`() {
        runTest {
            server.enqueue(MockResponse().setBody(viewingResponse(capped = false, totalMatching = 3)))

            val result = repo.nearby(50.0, 8.0, 25.0, buildSearchQuery(SearchInput(text = "dlh hb-jc")))

            result.aircraft.map { it.hex } shouldContainExactlyInAnyOrder listOf("3C65A3", "4B1805")
        }
    }

    @Test
    fun `nearby keeps aircraft in any category that also contain a word`() {
        runTest {
            server.enqueue(MockResponse().setBody(viewingResponse(capped = false, totalMatching = 3)))
            repo.nearby(50.0, 8.0, 25.0, buildSearchQuery(SearchInput(categories = setOf(SearchCategory.MILITARY))))
                .aircraft.map { it.hex } shouldContainExactlyInAnyOrder listOf("AE1234")

            server.enqueue(MockResponse().setBody(viewingResponse(capped = false, totalMatching = 3)))
            repo.nearby(
                50.0, 8.0, 25.0,
                buildSearchQuery(SearchInput(text = "A320", categories = setOf(SearchCategory.MILITARY))),
            ).aircraft shouldBe emptyList()
        }
    }

    @Test
    fun `a filtered nearby result does not claim the area total`() {
        runTest {
            server.enqueue(MockResponse().setBody(viewingResponse(capped = true, totalMatching = 900)))

            val result = repo.nearby(50.0, 8.0, 25.0, buildSearchQuery(SearchInput(text = "A320")))

            val outcome = result.terms.single().outcome
            outcome.shouldBeInstanceOf<TermOutcome.Answered>()
            outcome.capped shouldBe true
            outcome.totalMatching.shouldBeNull()
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
    fun `a pending search with a stored result is reused instead of resent`() {
        runTest {
            val pendingId = "5a5c6b2e-3b0a-4a2e-9a0f-000000000009"
            database.pendingOperations.insert(
                PendingOperationEntity(
                    operationId = pendingId,
                    kind = OperationStore.Kind.SEARCH.name,
                    requestJson = json.encodeToString(
                        SearchBatchRequest.serializer(),
                        SearchBatchRequest(pendingId, listOf(WireTerm("DLH453"))),
                    ),
                    ownerIds = "[]",
                    createdAt = serverClock.now().toEpochMilli() - 60_000,
                    resultJson = batchResponse(OUTCOME_ANSWERED_ONE, withAircraft = true),
                )
            )
            server.enqueue(MockResponse().setBody(batchResponse(OUTCOME_ANSWERED_EMPTY)))

            val result = repo.search(SearchQuery(listOf(SearchTerm("DLH453"))))

            server.requestCount shouldBe 0
            result.aircraft.map { it.hex } shouldContainExactlyInAnyOrder listOf("3C65A3")
        }
    }

    @Test
    fun `a stored result whose outcome has expired is not replayed as answered`() {
        runTest {
            val pendingId = "5a5c6b2e-3b0a-4a2e-9a0f-000000000010"
            val expiredOutcome =
                """{"index":0,"status":"answered","aircraftIds":["3c65a3"],"totalMatching":1,""" +
                        """"complete":true,"charged":true,"expiresAt":${serverClock.now().toEpochMilli() - 60_000}}"""
            database.pendingOperations.insert(
                PendingOperationEntity(
                    operationId = pendingId,
                    kind = OperationStore.Kind.SEARCH.name,
                    requestJson = json.encodeToString(
                        SearchBatchRequest.serializer(),
                        SearchBatchRequest(pendingId, listOf(WireTerm("DLH453"))),
                    ),
                    ownerIds = "[]",
                    createdAt = serverClock.now().toEpochMilli() - 180_000,
                    resultJson = batchResponse(expiredOutcome, withAircraft = true),
                )
            )
            server.enqueue(MockResponse().setBody(batchResponse(OUTCOME_ANSWERED_EMPTY)))

            val result = repo.search(SearchQuery(listOf(SearchTerm("DLH453"))))

            server.requestCount shouldBe 0
            val outcome = result.terms.single().outcome
            outcome.shouldBeInstanceOf<TermOutcome.Rejected>()
            outcome.code shouldBe ServerCodes.RESULT_EXPIRED
        }
    }

    @Test
    fun `an operation the server cannot replay returns the cache with the error`() {
        runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(413)
                    .setHeader("Content-Type", "application/problem+json")
                    .setBody("""{"type":"about:blank","title":"x","status":413,"detail":"x","code":"result_too_large"}""")
            )

            val result = repo.search(SearchQuery(listOf(SearchTerm("DLH453"))))

            result.error.shouldBeInstanceOf<OperationFailedException>()
            result.terms shouldBe emptyList()
            server.requestCount shouldBe 1
            server.takeRequest().path shouldBe "/api/v1/aircraft/search"
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

    private fun viewingResponse(capped: Boolean, totalMatching: Int) = """
        {
          "serverTime": $SERVER_TIME,
          "metadata": {
            "sourceTime": $SERVER_TIME,
            "fetchedAt": $SERVER_TIME,
            "expiresAt": ${SERVER_TIME + 120000},
            "complete": true,
            "stale": false
          },
          "aircraft": $NEARBY_AIRCRAFT,
          "totalMatching": $totalMatching,
          "capped": $capped,
          "usage": {
            "scope": "principal",
            "bucket": "VIEWING",
            "resetsAt": 1710028800000,
            "allowance": {"limit": 25000, "used": 1, "reserved": 0, "remaining": 24999}
          }
        }
    """.trimIndent()

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

        private val NEARBY_AIRCRAFT = """
            [
              {
                "id": "3c65a3",
                "messageObservedAt": ${SERVER_TIME - 500},
                "callsign": "DLH453",
                "registration": "D-AIUE",
                "aircraftType": "A320"
              },
              {
                "id": "ae1234",
                "messageObservedAt": ${SERVER_TIME - 500},
                "callsign": "RCH123",
                "aircraftType": "C17",
                "military": true
              },
              {
                "id": "4b1805",
                "messageObservedAt": ${SERVER_TIME - 500},
                "callsign": "SWR12",
                "registration": "HB-JCA",
                "aircraftType": "BCS3"
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
