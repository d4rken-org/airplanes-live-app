package eu.darken.apl.server.api

import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.http.HttpModule
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.ServerModule
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
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
import testhelper.TestCompatibilityStore
import testhelper.coroutine.TestDispatcherProvider

class FeederMonitorEndpointTest : BaseTest() {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var endpoint: ServerEndpoint
    private val compatStore = TestCompatibilityStore()

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
            compatibility = compatStore.holder(),
        ).apply {
            baseUrl = mockWebServer.url("/").toString()
        }
    }

    @AfterEach
    fun teardown() {
        mockWebServer.shutdown()
        compatStore.close()
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
    fun `list decodes a full and a minimal entry`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setBody(
                """
                {
                  "serverTime": 1760000000000,
                  "revision": 7,
                  "state": "active",
                  "limits": {"maxActiveFeeders": 3, "maxStoredFeeders": 20},
                  "feeders": [
                    {"feederId": "$FEEDER_ID", "monitorId": "monitor-1", "offlineAfterMinutes": 360,
                     "status": "paused", "pauseReason": "over_limit",
                     "createdAt": 1759000000000, "updatedAt": 1759500000000,
                     "health": "unknown", "unknownReason": "paused", "healthSince": 1759600000000,
                     "problems": ["mlat_missing", "deaf", "some_future_problem"],
                     "insufficientEvidence": ["flapping"],
                     "lastSampleAt": 1759900000000, "lastSuccessAt": 1759800000000, "lastConnectedAt": 1759700000000,
                     "latest": {"beastClients": 2, "msgsPerSecond": 812.5, "positionsPerSecond": 41.25,
                                "avgKbitPerSecond": 23.5, "connTime": 86400, "positions": 123456,
                                "mlatClients": 1, "mlatMessageRate": 12.5, "mlatPeers": 14,
                                "mlatBadSyncTimeout": 0, "mlatOutlierPercent": 1.5},
                     "eventSeq": 31, "futureField": true},
                    {"feederId": "$FEEDER_ID_2", "monitorId": "monitor-2", "offlineAfterMinutes": 60,
                     "status": "active", "createdAt": 1759000000000, "updatedAt": 1759000000000,
                     "health": "online", "eventSeq": 0}
                  ],
                  "cursor": 31,
                  "sampleIntervalSeconds": 300,
                  "leasePausesAt": 1760100000000,
                  "deletesAt": 1762000000000
                }
                """.trimIndent()
            )
        )

        val response = endpoint.feederMonitorList("token-value")

        mockWebServer.takeRequest().shouldHit("GET", "/api/v1/feedermonitor")
        response.serverTime shouldBe 1_760_000_000_000L
        response.revision shouldBe 7L
        response.state shouldBe "active"
        response.limits shouldBe FeederMonitorLimits(maxActiveFeeders = 3, maxStoredFeeders = 20)
        response.cursor shouldBe 31L
        response.sampleIntervalSeconds shouldBe 300
        response.leasePausesAt shouldBe 1_760_100_000_000L
        response.deletesAt shouldBe 1_762_000_000_000L
        response.feeders[0] shouldBe FeederMonitorEntry(
            feederId = FEEDER_ID,
            monitorId = "monitor-1",
            offlineAfterMinutes = 360,
            status = "paused",
            pauseReason = "over_limit",
            createdAt = 1_759_000_000_000L,
            updatedAt = 1_759_500_000_000L,
            health = "unknown",
            unknownReason = "paused",
            healthSince = 1_759_600_000_000L,
            problems = listOf("mlat_missing", "deaf", "some_future_problem"),
            insufficientEvidence = listOf("flapping"),
            lastSampleAt = 1_759_900_000_000L,
            lastSuccessAt = 1_759_800_000_000L,
            lastConnectedAt = 1_759_700_000_000L,
            latest = FeederMonitorLatest(
                beastClients = 2,
                msgsPerSecond = 812.5,
                positionsPerSecond = 41.25,
                avgKbitPerSecond = 23.5,
                connTime = 86_400L,
                positions = 123_456L,
                mlatClients = 1,
                mlatMessageRate = 12.5,
                mlatPeers = 14,
                mlatBadSyncTimeout = 0L,
                mlatOutlierPercent = 1.5,
            ),
            eventSeq = 31L,
        )
        response.feeders[1] shouldBe FeederMonitorEntry(
            feederId = FEEDER_ID_2,
            monitorId = "monitor-2",
            offlineAfterMinutes = 60,
            status = "active",
            createdAt = 1_759_000_000_000L,
            updatedAt = 1_759_000_000_000L,
            health = "online",
            eventSeq = 0L,
        )
        response.feeders[1].apply {
            pauseReason.shouldBeNull()
            unknownReason.shouldBeNull()
            healthSince.shouldBeNull()
            problems.shouldBeEmpty()
            insufficientEvidence.shouldBeEmpty()
            lastSampleAt.shouldBeNull()
            lastSuccessAt.shouldBeNull()
            lastConnectedAt.shouldBeNull()
            latest.shouldBeNull()
        }
    }

    @Test
    fun `list decodes without its optional fields and with an empty latest`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setBody(
                """
                {
                  "serverTime": 1760000000000,
                  "revision": 1,
                  "state": "expired",
                  "limits": {"maxActiveFeeders": 20, "maxStoredFeeders": 20},
                  "feeders": [
                    {"feederId": "$FEEDER_ID", "monitorId": "monitor-1", "offlineAfterMinutes": 360,
                     "status": "active", "createdAt": 1759000000000, "updatedAt": 1759000000000,
                     "health": "unknown", "unknownReason": "pending", "latest": {}, "eventSeq": 0}
                  ],
                  "cursor": 0
                }
                """.trimIndent()
            )
        )

        val response = endpoint.feederMonitorList("token-value")

        response.state shouldBe "expired"
        response.sampleIntervalSeconds.shouldBeNull()
        response.leasePausesAt.shouldBeNull()
        response.deletesAt.shouldBeNull()
        response.feeders.single().latest shouldBe FeederMonitorLatest()
    }

    @Test
    fun `upsert answered 201 created the monitor`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(201).setBody(MUTATION_RESPONSE))

        val result = endpoint.upsertFeederMonitor("token-value", FEEDER_ID, FeederMonitorUpsertRequest())

        result.created shouldBe true
        result.mutation.revision shouldBe 8L
        result.mutation.feeder.feederId shouldBe FEEDER_ID
        result.mutation.feeder.monitorId shouldBe "monitor-1"

        val request = mockWebServer.takeRequest()
        request.shouldHit("PUT", "/api/v1/feedermonitor/feeders/$FEEDER_ID")
        request.body.readUtf8() shouldBe "{}"
    }

    @Test
    fun `upsert answered 200 updated an existing monitor`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(MUTATION_RESPONSE))

        val result = endpoint.upsertFeederMonitor(
            "token-value",
            FEEDER_ID,
            FeederMonitorUpsertRequest(offlineAfterMinutes = 360),
        )

        result.created shouldBe false
        result.mutation.revision shouldBe 8L

        val request = mockWebServer.takeRequest()
        request.shouldHit("PUT", "/api/v1/feedermonitor/feeders/$FEEDER_ID")
        request.body.readUtf8() shouldBe """{"offlineAfterMinutes":360}"""
    }

    @Test
    fun `upsert failures carry the problem`() = runTest {
        enqueueProblem(409, ServerCodes.FEEDER_MONITOR_LIMIT)
        enqueueProblem(429, ServerCodes.FEEDER_MONITOR_MUTATION_BUDGET_EXCEEDED, retryAfterSeconds = 30)
        enqueueProblem(503, ServerCodes.FEEDER_MONITORING_UNAVAILABLE, retryAfterHeader = "3600")
        enqueueProblem(413, ServerCodes.PAYLOAD_TOO_LARGE)

        suspend fun upsert() = shouldThrow<ServerApiException> {
            endpoint.upsertFeederMonitor("token-value", FEEDER_ID, FeederMonitorUpsertRequest())
        }

        upsert().shouldMatch(409, ServerCodes.FEEDER_MONITOR_LIMIT)
        upsert().shouldMatch(429, ServerCodes.FEEDER_MONITOR_MUTATION_BUDGET_EXCEEDED, retryAfterSeconds = 30L)
        upsert().shouldMatch(503, ServerCodes.FEEDER_MONITORING_UNAVAILABLE, retryAfterSeconds = 3600L)
        upsert().shouldMatch(413, ServerCodes.PAYLOAD_TOO_LARGE)
    }

    @Test
    fun `delete answered 204 succeeds`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(204))

        endpoint.deleteFeederMonitor("token-value", FEEDER_ID)

        val request = mockWebServer.takeRequest()
        request.shouldHit("DELETE", "/api/v1/feedermonitor/feeders/$FEEDER_ID")
        request.bodySize shouldBe 0L
    }

    @Test
    fun `delete failures carry the problem`() = runTest {
        enqueueProblem(429, ServerCodes.FEEDER_MONITOR_MUTATION_BUDGET_EXCEEDED, retryAfterSeconds = 12, retryAfterHeader = "99")
        enqueueProblem(503, ServerCodes.DATABASE_UNAVAILABLE, retryAfterHeader = "1")

        suspend fun delete() = shouldThrow<ServerApiException> { endpoint.deleteFeederMonitor("token-value", FEEDER_ID) }

        delete().shouldMatch(429, ServerCodes.FEEDER_MONITOR_MUTATION_BUDGET_EXCEEDED, retryAfterSeconds = 12L)
        delete().shouldMatch(503, ServerCodes.DATABASE_UNAVAILABLE, retryAfterSeconds = 1L)
    }

    @Test
    fun `events are paged by cursor with an optional limit`() = runTest {
        mockWebServer.enqueue(MockResponse().setBody(EVENTS_EMPTY))
        mockWebServer.enqueue(MockResponse().setBody(EVENTS_EMPTY))

        endpoint.feederMonitorEvents("token-value", after = 0L)
        endpoint.feederMonitorEvents("token-value", after = 42L, limit = 500)

        mockWebServer.takeRequest().shouldHit("GET", "/api/v1/feedermonitor/events?after=0")
        mockWebServer.takeRequest().shouldHit("GET", "/api/v1/feedermonitor/events?after=42&limit=500")
    }

    @Test
    fun `events decode every kind`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setBody(
                """
                {
                  "serverTime": 1760000000000,
                  "events": [
                    {"seq": 11, "kind": "offline", "observedAt": 1759990000000, "feederId": "$FEEDER_ID",
                     "monitorId": "monitor-1", "since": 1759980000000},
                    {"seq": 12, "kind": "online", "observedAt": 1759991000000, "feederId": "$FEEDER_ID",
                     "monitorId": "monitor-1", "offlineSince": 1759980000000},
                    {"seq": 13, "kind": "problem_started", "observedAt": 1759992000000, "feederId": "$FEEDER_ID",
                     "monitorId": "monitor-1", "problem": "mlat_bad_sync"},
                    {"seq": 14, "kind": "problem_cleared", "observedAt": 1759993000000, "feederId": "$FEEDER_ID",
                     "monitorId": "monitor-1", "problem": "mlat_bad_sync"},
                    {"seq": 15, "kind": "coverage_gap", "observedAt": 1759994000000, "feederId": "$FEEDER_ID",
                     "monitorId": "monitor-1", "gapFrom": 1759970000000, "gapUntil": 1759975000000},
                    {"seq": 16, "kind": "some_future_kind", "observedAt": 1759995000000, "feederId": "$FEEDER_ID",
                     "monitorId": "monitor-1", "futureField": 1}
                  ],
                  "cursor": 16,
                  "hasMore": true,
                  "revision": 9
                }
                """.trimIndent()
            )
        )

        val response = endpoint.feederMonitorEvents("token-value", after = 10L)

        response.serverTime shouldBe 1_760_000_000_000L
        response.cursor shouldBe 16L
        response.hasMore shouldBe true
        response.revision shouldBe 9L
        response.events shouldBe listOf(
            FeederMonitorEvent(
                seq = 11L,
                kind = "offline",
                observedAt = 1_759_990_000_000L,
                feederId = FEEDER_ID,
                monitorId = "monitor-1",
                since = 1_759_980_000_000L,
            ),
            FeederMonitorEvent(
                seq = 12L,
                kind = "online",
                observedAt = 1_759_991_000_000L,
                feederId = FEEDER_ID,
                monitorId = "monitor-1",
                offlineSince = 1_759_980_000_000L,
            ),
            FeederMonitorEvent(
                seq = 13L,
                kind = "problem_started",
                observedAt = 1_759_992_000_000L,
                feederId = FEEDER_ID,
                monitorId = "monitor-1",
                problem = "mlat_bad_sync",
            ),
            FeederMonitorEvent(
                seq = 14L,
                kind = "problem_cleared",
                observedAt = 1_759_993_000_000L,
                feederId = FEEDER_ID,
                monitorId = "monitor-1",
                problem = "mlat_bad_sync",
            ),
            FeederMonitorEvent(
                seq = 15L,
                kind = "coverage_gap",
                observedAt = 1_759_994_000_000L,
                feederId = FEEDER_ID,
                monitorId = "monitor-1",
                gapFrom = 1_759_970_000_000L,
                gapUntil = 1_759_975_000_000L,
            ),
            FeederMonitorEvent(
                seq = 16L,
                kind = "some_future_kind",
                observedAt = 1_759_995_000_000L,
                feederId = FEEDER_ID,
                monitorId = "monitor-1",
            ),
        )
    }

    @Test
    fun `event failures carry the problem`() = runTest {
        enqueueProblem(410, ServerCodes.CURSOR_EXPIRED)
        enqueueProblem(429, ServerCodes.INSTALLATION_RATE_EXCEEDED, retryAfterSeconds = 2)

        suspend fun events() = shouldThrow<ServerApiException> { endpoint.feederMonitorEvents("token-value", after = 5L) }

        events().shouldMatch(410, ServerCodes.CURSOR_EXPIRED)
        events().shouldMatch(429, ServerCodes.INSTALLATION_RATE_EXCEEDED, retryAfterSeconds = 2L)
    }

    @Test
    fun `history is fetched with an optional since`() = runTest {
        mockWebServer.enqueue(MockResponse().setBody(HISTORY_EMPTY))
        mockWebServer.enqueue(MockResponse().setBody(HISTORY_EMPTY))

        endpoint.feederHistory("token-value", FEEDER_ID)
        endpoint.feederHistory("token-value", FEEDER_ID, since = 1_759_993_200_000L)

        mockWebServer.takeRequest().shouldHit("GET", "/api/v1/feedermonitor/feeders/$FEEDER_ID/history")
        mockWebServer.takeRequest()
            .shouldHit("GET", "/api/v1/feedermonitor/feeders/$FEEDER_ID/history?since=1759993200000")
    }

    @Test
    fun `history decodes final and open hours with and without metrics`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setBody(
                """
                {
                  "serverTime": 1760000000000,
                  "feederId": "$FEEDER_ID",
                  "hours": [
                    {"hour": 1759993200000, "samples": 12, "failures": 1, "connectedSamples": 11,
                     "msgsPerSecondAvg": 800.5, "msgsPerSecondMin": 650.0, "msgsPerSecondMax": 950.25,
                     "positionsPerSecondAvg": 40.5, "avgKbitPerSecond": 22.0, "mlatSamples": 10,
                     "mlatPeersAvg": 13.5, "mlatOutlierPercentAvg": 1.25, "badSyncSamples": 2,
                     "reconnects": 1, "final": true},
                    {"hour": 1759996800000, "samples": 3, "failures": 3, "connectedSamples": 0,
                     "mlatSamples": 0, "badSyncSamples": 0, "reconnects": 0, "final": false}
                  ]
                }
                """.trimIndent()
            )
        )

        val response = endpoint.feederHistory("token-value", FEEDER_ID)

        response.serverTime shouldBe 1_760_000_000_000L
        response.feederId shouldBe FEEDER_ID
        response.hours[0] shouldBe FeederHistoryHour(
            hour = 1_759_993_200_000L,
            samples = 12,
            failures = 1,
            connectedSamples = 11,
            msgsPerSecondAvg = 800.5,
            msgsPerSecondMin = 650.0,
            msgsPerSecondMax = 950.25,
            positionsPerSecondAvg = 40.5,
            avgKbitPerSecond = 22.0,
            mlatSamples = 10,
            mlatPeersAvg = 13.5,
            mlatOutlierPercentAvg = 1.25,
            badSyncSamples = 2,
            reconnects = 1,
            isFinal = true,
        )
        response.hours[1].apply {
            samples shouldBe 3
            failures shouldBe 3
            connectedSamples shouldBe 0
            isFinal shouldBe false
            msgsPerSecondAvg.shouldBeNull()
            msgsPerSecondMin.shouldBeNull()
            msgsPerSecondMax.shouldBeNull()
            positionsPerSecondAvg.shouldBeNull()
            avgKbitPerSecond.shouldBeNull()
            mlatPeersAvg.shouldBeNull()
            mlatOutlierPercentAvg.shouldBeNull()
        }
    }

    @Test
    fun `history failures carry the problem`() = runTest {
        enqueueProblem(404, ServerCodes.FEEDER_NOT_MONITORED)
        enqueueProblem(400, ServerCodes.INVALID_REQUEST)

        suspend fun history() = shouldThrow<ServerApiException> { endpoint.feederHistory("token-value", FEEDER_ID) }

        history().shouldMatch(404, ServerCodes.FEEDER_NOT_MONITORED)
        history().shouldMatch(400, ServerCodes.INVALID_REQUEST)
    }

    @Test
    fun `list failures carry the problem`() = runTest {
        enqueueProblem(503, ServerCodes.DATABASE_UNAVAILABLE, retryAfterHeader = "1")

        shouldThrow<ServerApiException> { endpoint.feederMonitorList("token-value") }
            .shouldMatch(503, ServerCodes.DATABASE_UNAVAILABLE, retryAfterSeconds = 1L)
    }

    companion object {
        private const val FEEDER_ID = "0f3a6c1e-8a51-4c5e-9e2b-7d6b1a2c3d4e"
        private const val FEEDER_ID_2 = "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d"

        private val MUTATION_RESPONSE = """
            {
              "revision": 8,
              "feeder": {"feederId": "$FEEDER_ID", "monitorId": "monitor-1", "offlineAfterMinutes": 360,
                         "status": "active", "createdAt": 1759000000000, "updatedAt": 1759000000000,
                         "health": "unknown", "unknownReason": "pending", "problems": [],
                         "insufficientEvidence": [], "eventSeq": 0}
            }
        """.trimIndent()

        private const val EVENTS_EMPTY =
            """{"serverTime": 1760000000000, "events": [], "cursor": 0, "hasMore": false, "revision": 1}"""

        private val HISTORY_EMPTY =
            """{"serverTime": 1760000000000, "feederId": "$FEEDER_ID", "hours": []}"""
    }
}
