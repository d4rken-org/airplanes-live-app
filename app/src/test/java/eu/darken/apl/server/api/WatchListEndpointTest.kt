package eu.darken.apl.server.api

import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.http.HttpModule
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.ServerModule
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.coroutine.TestDispatcherProvider

class WatchListEndpointTest : BaseTest() {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var endpoint: ServerEndpoint

    private val fakeMonotonicClock = object : MonotonicClock {
        override fun elapsed(): Long = 1_000L
    }

    @BeforeEach
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        endpoint = ServerEndpoint(
            baseClient = HttpModule().baseHttpClient(),
            json = ServerModule.serverJson(),
            dispatcherProvider = TestDispatcherProvider(),
            serverClock = ServerClock(fakeMonotonicClock),
        ).apply {
            baseUrl = mockWebServer.url("/").toString()
        }
    }

    @AfterEach
    fun teardown() {
        mockWebServer.shutdown()
    }

    private fun RecordedRequest.shouldHit(method: String, path: String) {
        this.method shouldBe method
        this.path shouldBe path
        getHeader("Authorization") shouldBe "Bearer token-value"
    }

    private fun enqueueProblem(status: Int, code: String, retryAfterSeconds: Long? = null, retryAfterHeader: String? = null) {
        val retryField = retryAfterSeconds?.let { ""","retryAfterSeconds":$it""" } ?: ""
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(status)
                .setHeader("Content-Type", "application/problem+json")
                .apply { retryAfterHeader?.let { setHeader("Retry-After", it) } }
                .setBody("""{"type":"about:blank","title":"x","status":$status,"detail":"detail-$code","code":"$code"$retryField}""")
        )
    }

    private fun ServerApiException.shouldMatch(status: Int, code: String, retryAfterSeconds: Long? = null) {
        withClue(code) {
            this.code shouldBe code
            this.status shouldBe status
            this.retryAfterSeconds shouldBe retryAfterSeconds
            detail shouldBe "detail-$code"
        }
    }

    @Test
    fun `watch list is fetched with all optional fields omitted`() = runTest {
        mockWebServer.enqueue(MockResponse().setBody(WATCH_LIST_MINIMAL))

        val response = endpoint.watchList("token-value")

        mockWebServer.takeRequest().shouldHit("GET", "/api/v1/watchlist")
        response.serverTime shouldBe 1_760_000_000_000L
        response.revision shouldBe 4L
        response.state shouldBe "active"
        response.limits shouldBe WatchListLimits(maxActiveWatches = 10, maxStoredWatches = 50)
        response.cursor shouldBe 0L
        response.leasePausesAt.shouldBeNull()
        response.deletesAt.shouldBeNull()
        response.watches.single() shouldBe WatchListWatch(
            localId = "local-1",
            watchId = "watch-1",
            type = "hex",
            value = "3C65A3",
            revision = 1,
            status = "active",
            createdAt = 1_759_000_000_000L,
            updatedAt = 1_759_000_000_000L,
            presence = "unknown",
            eventSeq = 0L,
            backfill = "pending",
        )
    }

    @Test
    fun `watch list decodes every optional field when present`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setBody(
                """
                {
                  "serverTime": 1760000000000,
                  "revision": 9,
                  "state": "expired",
                  "limits": {"maxActiveWatches": 10, "maxStoredWatches": 50},
                  "watches": [
                    {"localId": "local-2", "watchId": "watch-2", "type": "location",
                     "latitude": 50.0, "longitude": 8.5, "radiusKm": 5.0,
                     "revision": 3, "priority": 1, "enabled": true, "status": "paused", "pauseReason": "lease",
                     "createdAt": 1759000000000, "updatedAt": 1759500000000,
                     "presence": "present", "lastSeenAt": 1759900000000, "lastSeenAircraftId": "3c65a3",
                     "eventSeq": 17, "backfill": "done", "futureField": true}
                  ],
                  "cursor": 17,
                  "leasePausesAt": 1760100000000,
                  "deletesAt": 1762000000000
                }
                """.trimIndent()
            )
        )

        val response = endpoint.watchList("token-value")

        response.state shouldBe "expired"
        response.leasePausesAt shouldBe 1_760_100_000_000L
        response.deletesAt shouldBe 1_762_000_000_000L
        response.cursor shouldBe 17L
        response.watches.single().apply {
            value.shouldBeNull()
            latitude shouldBe 50.0
            longitude shouldBe 8.5
            radiusKm shouldBe 5.0
            status shouldBe "paused"
            pauseReason shouldBe "lease"
            presence shouldBe "present"
            lastSeenAt shouldBe 1_759_900_000_000L
            lastSeenAircraftId shouldBe "3c65a3"
            eventSeq shouldBe 17L
            backfill shouldBe "done"
        }
    }

    @Test
    fun `upsert answered 201 created the watch`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(201).setBody(MUTATION_RESPONSE))

        val result = endpoint.upsertWatch("token-value", "local-1", WatchUpsertRequest(type = "hex", value = "3C65A3"))

        result.created shouldBe true
        result.mutation.listRevision shouldBe 5L
        result.mutation.watch.localId shouldBe "local-1"

        val request = mockWebServer.takeRequest()
        request.shouldHit("PUT", "/api/v1/watchlist/watches/local-1")
        request.body.readUtf8() shouldBe """{"type":"hex","value":"3C65A3"}"""
    }

    @Test
    fun `upsert answered 200 updated an existing watch`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(MUTATION_RESPONSE))

        val result = endpoint.upsertWatch(
            "token-value",
            "local-1",
            WatchUpsertRequest(type = "hex", value = "3C65A3", expectedWatchRevision = 3, baseline = "absent"),
        )

        result.created shouldBe false
        result.mutation.listRevision shouldBe 5L

        val request = mockWebServer.takeRequest()
        request.shouldHit("PUT", "/api/v1/watchlist/watches/local-1")
        request.body.readUtf8() shouldBe
            """{"type":"hex","value":"3C65A3","expectedWatchRevision":3,"baseline":"absent"}"""
    }

    @Test
    fun `upsert of a location watch sends coordinates and no value`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(201).setBody(MUTATION_RESPONSE))

        endpoint.upsertWatch(
            "token-value",
            "local-3",
            WatchUpsertRequest(type = "location", latitude = 50.0, longitude = 8.5, radiusKm = 5.0, baseline = "present"),
        )

        val request = mockWebServer.takeRequest()
        request.shouldHit("PUT", "/api/v1/watchlist/watches/local-3")
        request.body.readUtf8() shouldBe
            """{"type":"location","latitude":50.0,"longitude":8.5,"radiusKm":5.0,"baseline":"present"}"""
    }

    @Test
    fun `upsert failures carry the problem`() = runTest {
        enqueueProblem(409, ServerCodes.REVISION_CONFLICT)
        enqueueProblem(409, ServerCodes.STORED_WATCH_LIMIT)
        enqueueProblem(404, ServerCodes.WATCH_NOT_FOUND)
        enqueueProblem(400, ServerCodes.INVALID_REQUEST)
        enqueueProblem(410, ServerCodes.WATCH_LIST_DELETED)
        enqueueProblem(429, ServerCodes.WATCH_MUTATION_BUDGET_EXCEEDED, retryAfterSeconds = 30)
        enqueueProblem(503, ServerCodes.WATCHLIST_UNAVAILABLE, retryAfterHeader = "7")

        val request = WatchUpsertRequest(type = "hex", value = "3C65A3")
        suspend fun upsert() = shouldThrow<ServerApiException> { endpoint.upsertWatch("token-value", "local-1", request) }

        upsert().shouldMatch(409, ServerCodes.REVISION_CONFLICT)
        upsert().shouldMatch(409, ServerCodes.STORED_WATCH_LIMIT)
        upsert().shouldMatch(404, ServerCodes.WATCH_NOT_FOUND)
        upsert().shouldMatch(400, ServerCodes.INVALID_REQUEST)
        upsert().shouldMatch(410, ServerCodes.WATCH_LIST_DELETED)
        upsert().shouldMatch(429, ServerCodes.WATCH_MUTATION_BUDGET_EXCEEDED, retryAfterSeconds = 30L)
        upsert().shouldMatch(503, ServerCodes.WATCHLIST_UNAVAILABLE, retryAfterSeconds = 7L)
    }

    @Test
    fun `delete answered 204 succeeds`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(204))

        endpoint.deleteWatch("token-value", "local-1")

        val request = mockWebServer.takeRequest()
        request.shouldHit("DELETE", "/api/v1/watchlist/watches/local-1")
        request.bodySize shouldBe 0L
    }

    @Test
    fun `delete failures carry the problem`() = runTest {
        enqueueProblem(404, ServerCodes.WATCH_NOT_FOUND)
        enqueueProblem(400, ServerCodes.INVALID_REQUEST)
        enqueueProblem(503, ServerCodes.WATCHLIST_UNAVAILABLE, retryAfterHeader = "5")
        enqueueProblem(429, ServerCodes.WATCH_MUTATION_BUDGET_EXCEEDED, retryAfterSeconds = 12, retryAfterHeader = "99")

        suspend fun delete() = shouldThrow<ServerApiException> { endpoint.deleteWatch("token-value", "local-1") }

        delete().shouldMatch(404, ServerCodes.WATCH_NOT_FOUND)
        delete().shouldMatch(400, ServerCodes.INVALID_REQUEST)
        delete().shouldMatch(503, ServerCodes.WATCHLIST_UNAVAILABLE, retryAfterSeconds = 5L)
        delete().shouldMatch(429, ServerCodes.WATCH_MUTATION_BUDGET_EXCEEDED, retryAfterSeconds = 12L)
    }

    @Test
    fun `delete failures without a problem fall back to the http status`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(502)
                .setHeader("Content-Type", "text/html")
                .setBody("<html>nope</html>")
        )

        val error = shouldThrow<ServerApiException> { endpoint.deleteWatch("token-value", "local-1") }

        error.code shouldBe "http_502"
        error.status shouldBe 502
        error.retryAfterSeconds.shouldBeNull()
        error.detail.shouldBeNull()
    }

    @Test
    fun `events are paged by cursor with an optional limit`() = runTest {
        mockWebServer.enqueue(MockResponse().setBody(EVENTS_EMPTY))
        mockWebServer.enqueue(MockResponse().setBody(EVENTS_EMPTY))

        endpoint.watchEvents("token-value", after = 0L)
        endpoint.watchEvents("token-value", after = 42L, limit = 500)

        mockWebServer.takeRequest().shouldHit("GET", "/api/v1/watchlist/events?after=0")
        mockWebServer.takeRequest().shouldHit("GET", "/api/v1/watchlist/events?after=42&limit=500")
    }

    @Test
    fun `events decode matched, cleared and coverage gaps`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setBody(
                """
                {
                  "serverTime": 1760000000000,
                  "events": [
                    {"seq": 11, "kind": "matched", "observedAt": 1759990000000, "watchId": "watch-1", "localId": "local-1",
                     "revision": 2, "aircraftIds": ["3c65a3", "4b1805"], "totalMatching": 3},
                    {"seq": 12, "kind": "cleared", "observedAt": 1759991000000, "watchId": "watch-1", "localId": "local-1", "revision": 2},
                    {"seq": 13, "kind": "coverage_gap", "observedAt": 1759992000000, "gapFrom": 1759980000000, "gapUntil": 1759985000000}
                  ],
                  "cursor": 13,
                  "hasMore": true,
                  "listRevision": 6
                }
                """.trimIndent()
            )
        )

        val response = endpoint.watchEvents("token-value", after = 10L)

        response.cursor shouldBe 13L
        response.hasMore shouldBe true
        response.listRevision shouldBe 6L
        response.events[0] shouldBe WatchEvent(
            seq = 11L,
            kind = "matched",
            observedAt = 1_759_990_000_000L,
            watchId = "watch-1",
            localId = "local-1",
            revision = 2,
            aircraftIds = listOf("3c65a3", "4b1805"),
            totalMatching = 3,
        )
        response.events[1].apply {
            kind shouldBe "cleared"
            aircraftIds.shouldBeNull()
            totalMatching.shouldBeNull()
        }
        response.events[2] shouldBe WatchEvent(
            seq = 13L,
            kind = "coverage_gap",
            observedAt = 1_759_992_000_000L,
            gapFrom = 1_759_980_000_000L,
            gapUntil = 1_759_985_000_000L,
        )
    }

    @Test
    fun `event failures carry the problem`() = runTest {
        enqueueProblem(410, ServerCodes.CURSOR_EXPIRED)
        enqueueProblem(400, ServerCodes.INVALID_REQUEST)
        enqueueProblem(503, ServerCodes.WATCHLIST_UNAVAILABLE, retryAfterHeader = "4")

        suspend fun events() = shouldThrow<ServerApiException> { endpoint.watchEvents("token-value", after = 5L) }

        events().shouldMatch(410, ServerCodes.CURSOR_EXPIRED)
        events().shouldMatch(400, ServerCodes.INVALID_REQUEST)
        events().shouldMatch(503, ServerCodes.WATCHLIST_UNAVAILABLE, retryAfterSeconds = 4L)
    }

    @Test
    fun `rollups are paged by cursor with an optional limit`() = runTest {
        mockWebServer.enqueue(MockResponse().setBody(ROLLUPS_EMPTY))
        mockWebServer.enqueue(MockResponse().setBody(ROLLUPS_EMPTY))

        endpoint.watchRollups("token-value", after = 0L)
        endpoint.watchRollups("token-value", after = 99L, limit = 1000)

        mockWebServer.takeRequest().shouldHit("GET", "/api/v1/watchlist/rollups?after=0")
        mockWebServer.takeRequest().shouldHit("GET", "/api/v1/watchlist/rollups?after=99&limit=1000")
    }

    @Test
    fun `rollups decode with and without coverage minutes and last seen`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setBody(
                """
                {
                  "serverTime": 1760000000000,
                  "rollups": [
                    {"seq": 21, "watchId": "watch-1", "localId": "local-1", "revision": 2, "hour": 1759996800000,
                     "source": "live", "evaluationBasis": "recent_messages", "aircraftCount": 2, "coverage": "partial",
                     "coveredMinutes": 41, "final": false, "lastSeenAt": 1759998000000, "lastSeenAircraftId": "3c65a3"},
                    {"seq": 22, "watchId": "watch-1", "localId": "local-1", "revision": 2, "hour": 1759993200000,
                     "source": "history", "evaluationBasis": "recent_messages", "aircraftCount": 0, "coverage": "full",
                     "final": true}
                  ],
                  "cursor": 22,
                  "hasMore": false
                }
                """.trimIndent()
            )
        )

        val response = endpoint.watchRollups("token-value", after = 20L)

        response.cursor shouldBe 22L
        response.hasMore shouldBe false
        response.rollups[0] shouldBe WatchRollup(
            seq = 21L,
            watchId = "watch-1",
            localId = "local-1",
            revision = 2,
            hour = 1_759_996_800_000L,
            source = "live",
            evaluationBasis = "recent_messages",
            aircraftCount = 2,
            coverage = "partial",
            coveredMinutes = 41,
            isFinal = false,
            lastSeenAt = 1_759_998_000_000L,
            lastSeenAircraftId = "3c65a3",
        )
        response.rollups[1].apply {
            source shouldBe "history"
            coverage shouldBe "full"
            isFinal shouldBe true
            coveredMinutes.shouldBeNull()
            lastSeenAt.shouldBeNull()
            lastSeenAircraftId.shouldBeNull()
        }
    }

    @Test
    fun `rollup failures carry the problem`() = runTest {
        enqueueProblem(410, ServerCodes.CURSOR_EXPIRED)
        enqueueProblem(429, ServerCodes.INSTALLATION_RATE_EXCEEDED, retryAfterSeconds = 2)

        suspend fun rollups() = shouldThrow<ServerApiException> { endpoint.watchRollups("token-value", after = 5L) }

        rollups().shouldMatch(410, ServerCodes.CURSOR_EXPIRED)
        rollups().shouldMatch(429, ServerCodes.INSTALLATION_RATE_EXCEEDED, retryAfterSeconds = 2L)
    }

    @Test
    fun `watch list failures carry the problem`() = runTest {
        enqueueProblem(503, ServerCodes.WATCHLIST_UNAVAILABLE, retryAfterHeader = "9")

        shouldThrow<ServerApiException> { endpoint.watchList("token-value") }
            .shouldMatch(503, ServerCodes.WATCHLIST_UNAVAILABLE, retryAfterSeconds = 9L)
    }

    companion object {
        private val WATCH_LIST_MINIMAL = """
            {
              "serverTime": 1760000000000,
              "revision": 4,
              "state": "active",
              "limits": {"maxActiveWatches": 10, "maxStoredWatches": 50},
              "watches": [
                {"localId": "local-1", "watchId": "watch-1", "type": "hex", "value": "3C65A3",
                 "revision": 1, "priority": 0, "enabled": true, "status": "active",
                 "createdAt": 1759000000000, "updatedAt": 1759000000000,
                 "presence": "unknown", "eventSeq": 0, "backfill": "pending"}
              ],
              "cursor": 0
            }
        """.trimIndent()

        private val MUTATION_RESPONSE = """
            {
              "listRevision": 5,
              "watch": {"localId": "local-1", "watchId": "watch-1", "type": "hex", "value": "3C65A3",
                        "revision": 1, "priority": 0, "enabled": true, "status": "active",
                        "createdAt": 1759000000000, "updatedAt": 1759000000000,
                        "presence": "unknown", "eventSeq": 0, "backfill": "pending"}
            }
        """.trimIndent()

        private const val EVENTS_EMPTY =
            """{"serverTime": 1760000000000, "events": [], "cursor": 0, "hasMore": false, "listRevision": 1}"""

        private const val ROLLUPS_EMPTY =
            """{"serverTime": 1760000000000, "rollups": [], "cursor": 0, "hasMore": false}"""
    }
}
