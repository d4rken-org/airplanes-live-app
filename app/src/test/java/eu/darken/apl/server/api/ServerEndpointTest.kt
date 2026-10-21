package eu.darken.apl.server.api

import eu.darken.apl.common.BuildConfigWrap
import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.debug.logging.Logging
import eu.darken.apl.common.http.HttpModule
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.ServerModule
import eu.darken.apl.server.compat.AppCompatibility
import eu.darken.apl.server.compat.CompatState
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.GzipSink
import okio.buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.TestCompatibilityStore
import testhelper.coroutine.TestDispatcherProvider
import java.io.File
import java.net.InetAddress
import java.time.Instant

class ServerEndpointTest : BaseTest() {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var endpoint: ServerEndpoint
    private val logSink = CapturingLogger()
    private val compatStore = TestCompatibilityStore()
    private lateinit var compatibility: AppCompatibility

    private val fakeMonotonicClock = object : MonotonicClock {
        override fun elapsed(): Long = 1_000L
    }

    @BeforeEach
    fun setup() {
        mockWebServer = MockWebServer()
        // registerFeeder resolves IPv4 only, the server must not end up listening on ::1
        mockWebServer.start(InetAddress.getByName("127.0.0.1"), 0)

        compatibility = compatStore.holder()
        endpoint = createEndpoint(HttpModule().baseHttpClient())
        Logging.install(logSink)
    }

    @AfterEach
    fun teardown() {
        Logging.remove(logSink)
        mockWebServer.shutdown()
        compatStore.close()
    }

    private fun createEndpoint(
        baseClient: OkHttpClient,
        compatibility: AppCompatibility = this.compatibility,
    ) = ServerEndpoint(
        baseClient = baseClient,
        json = ServerModule.serverJson(),
        dispatcherProvider = TestDispatcherProvider(),
        serverClock = ServerClock(fakeMonotonicClock),
        compatibility = compatibility,
    ).apply {
        baseUrl = mockWebServer.url("/").toString()
    }

    @Test
    fun `server calls carry the app version and variant`() = runTest {
        mockWebServer.enqueue(MockResponse().setBody(ACCESS_RESPONSE_MINIMAL))

        endpoint.access("token")

        val request = mockWebServer.takeRequest()
        request.getHeader("X-App-Version") shouldBe BuildConfigWrap.VERSION_CODE.toString()
        request.getHeader("X-App-Variant") shouldBe ServerVariant.CURRENT
    }

    @Test
    fun `feeder registration carries the app version and variant`() = runTest {
        mockWebServer.enqueue(MockResponse().setBody(FEEDER_STATUS_RESPONSE))

        endpoint.registerFeeder("token", "feeder-id")

        val request = mockWebServer.takeRequest()
        request.path shouldBe "/api/v1/feeder"
        request.getHeader("X-App-Version") shouldBe BuildConfigWrap.VERSION_CODE.toString()
        request.getHeader("X-App-Variant") shouldBe ServerVariant.CURRENT
    }

    @Test
    fun `meta is fetched without credentials and ignores unknown fields`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"recommendedAppVersion": 803000, "minSupportedAppVersion": 0, "futureField": true}""")
        )

        endpoint.meta(forceNetwork = false) shouldBe MetaResponse(
            recommendedAppVersion = 803_000,
            minSupportedAppVersion = 0,
        )

        val request = mockWebServer.takeRequest()
        request.method shouldBe "GET"
        request.path shouldBe "/api/v1/meta"
        request.getHeader("Authorization").shouldBeNull()
        request.getHeader("Cache-Control").shouldBeNull()
    }

    @Test
    fun `meta is answered from the http cache unless the network is forced`() = runTest {
        val cacheDir = File(IO_TEST_BASEDIR, "server-endpoint-cache").apply { deleteRecursively() }
        val cache = Cache(cacheDir, 1024L * 1024L)
        try {
            val cachingEndpoint = createEndpoint(HttpModule().baseHttpClient(cache = cache))
            repeat(2) {
                mockWebServer.enqueue(
                    MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setHeader("Cache-Control", "max-age=3600")
                        .setBody("""{"recommendedAppVersion": 803000, "minSupportedAppVersion": 0}""")
                )
            }

            cachingEndpoint.meta(forceNetwork = false)
            mockWebServer.requestCount shouldBe 1

            cachingEndpoint.meta(forceNetwork = false).recommendedAppVersion shouldBe 803_000
            mockWebServer.requestCount shouldBe 1

            cachingEndpoint.meta(forceNetwork = true)
            mockWebServer.requestCount shouldBe 2

            mockWebServer.takeRequest()
            mockWebServer.takeRequest().getHeader("Cache-Control") shouldBe "no-cache"
        } finally {
            cache.close()
            cacheDir.deleteRecursively()
        }
    }

    @Test
    fun `a refusal for an outdated app maps to the update code`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setHeader("Content-Type", "application/problem+json")
                .setBody("""{"type": "about:blank", "title": "Forbidden", "status": 403, "code": "app_update_required"}""")
        )

        val error = shouldThrow<ServerApiException> { endpoint.access("token") }

        error.code shouldBe ServerCodes.APP_UPDATE_REQUIRED
        error.status shouldBe 403
    }

    @Test
    fun `a stored refusal stops calls before they reach the server`() = runTest {
        compatibility.noteRefusal()
        // A new holder over the same store is the app after a restart
        val restarted = createEndpoint(HttpModule().baseHttpClient(), compatStore.holder())

        val error = shouldThrow<ServerApiException> { restarted.access("token") }

        error.code shouldBe ServerCodes.APP_UPDATE_REQUIRED
        error.status shouldBe 403
        mockWebServer.requestCount shouldBe 0
    }

    @Test
    fun `meta still reaches the server after a refusal`() = runTest {
        compatibility.noteRefusal()
        mockWebServer.enqueue(MockResponse().setBody(META_RESPONSE))

        endpoint.meta(forceNetwork = true).minSupportedAppVersion shouldBe 0

        mockWebServer.requestCount shouldBe 1
    }

    @Test
    fun `a refusal stops the calls that follow it`() = runTest {
        mockWebServer.enqueue(problemResponse(403, ServerCodes.APP_UPDATE_REQUIRED))

        shouldThrow<ServerApiException> { endpoint.access("token") }.code shouldBe ServerCodes.APP_UPDATE_REQUIRED
        compatibility.state.value shouldBe CompatState.UpdateRequired

        shouldThrow<ServerApiException> { endpoint.access("token") }.code shouldBe ServerCodes.APP_UPDATE_REQUIRED
        mockWebServer.requestCount shouldBe 1
    }

    @Test
    fun `a refused watch upsert stops the calls that follow it`() = runTest {
        mockWebServer.enqueue(problemResponse(403, ServerCodes.APP_UPDATE_REQUIRED))

        shouldThrow<ServerApiException> {
            endpoint.upsertWatch("token", "local-1", WatchUpsertRequest(type = "hex", value = "3C65A3"))
        }.code shouldBe ServerCodes.APP_UPDATE_REQUIRED
        compatibility.state.value shouldBe CompatState.UpdateRequired

        shouldThrow<ServerApiException> { endpoint.deleteWatch("token", "local-1") }.code shouldBe ServerCodes.APP_UPDATE_REQUIRED
        mockWebServer.requestCount shouldBe 1
    }

    @Test
    fun `calls stopped locally do not count as new refusals`() = runTest {
        compatibility.noteRefusal()
        val generation = compatibility.beginCheck()

        repeat(3) { shouldThrow<ServerApiException> { endpoint.access("token") } }

        compatibility.applyMeta(generation, MetaResponse(recommendedAppVersion = 0, minSupportedAppVersion = 0), Instant.EPOCH) shouldBe true
        compatibility.state.value shouldBe CompatState.Ok
        mockWebServer.requestCount shouldBe 0
    }

    @Test
    fun `fetching meta alone does not change the compatibility`() = runTest {
        compatibility.awaitLoaded()
        mockWebServer.enqueue(
            MockResponse().setBody("""{"recommendedAppVersion": ${Int.MAX_VALUE}, "minSupportedAppVersion": ${Int.MAX_VALUE}}""")
        )
        endpoint.meta(forceNetwork = true)
        compatibility.state.value shouldBe CompatState.Ok

        compatibility.noteRefusal()
        mockWebServer.enqueue(MockResponse().setBody(META_RESPONSE))
        endpoint.meta(forceNetwork = true)
        compatibility.state.value shouldBe CompatState.UpdateRequired
    }

    @Test
    fun `problem json is mapped to an api exception`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Content-Type", "application/problem+json")
                .setBody(
                    """
                    {
                      "type": "about:blank",
                      "title": "Too Many Requests",
                      "status": 429,
                      "detail": "daily allowance exhausted",
                      "code": "daily_allowance_exhausted",
                      "retryAfterSeconds": 42
                    }
                    """.trimIndent()
                )
        )

        val error = shouldThrow<ServerApiException> { endpoint.access("token") }
        error.code shouldBe "daily_allowance_exhausted"
        error.status shouldBe 429
        error.retryAfterSeconds shouldBe 42L
        error.detail shouldBe "daily allowance exhausted"
    }

    @Test
    fun `retry after header is used when the problem omits it`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(503)
                .setHeader("Content-Type", "application/problem+json")
                .setHeader("Retry-After", "3")
                .setBody("""{"type":"about:blank","title":"x","status":503,"detail":"x","code":"database_unavailable"}""")
        )

        val error = shouldThrow<ServerApiException> { endpoint.access("token") }
        error.code shouldBe "database_unavailable"
        error.retryAfterSeconds shouldBe 3L
    }

    @Test
    fun `non problem errors fall back to the http status`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "text/html")
                .setBody("<html>nope</html>")
        )

        val error = shouldThrow<ServerApiException> { endpoint.access("token") }
        error.code shouldBe "http_500"
        error.status shouldBe 500
        error.retryAfterSeconds.shouldBeNull()
    }

    @Test
    fun `authorization and gzip headers are sent`() = runTest {
        mockWebServer.enqueue(MockResponse().setBody(ACCESS_RESPONSE_MINIMAL))

        endpoint.access("token-value")

        val request = mockWebServer.takeRequest()
        request.getHeader("Authorization") shouldBe "Bearer token-value"
        request.getHeader("Accept-Encoding") shouldBe "gzip"
    }

    @Test
    fun `gzipped responses are decoded`() = runTest {
        val gzipped = Buffer().also { sink ->
            GzipSink(sink).buffer().use { it.writeUtf8(ACCESS_RESPONSE_MINIMAL) }
        }
        mockWebServer.enqueue(
            MockResponse()
                .setHeader("Content-Encoding", "gzip")
                .setBody(gzipped)
        )

        endpoint.access("token").tier shouldBe "free"
    }

    @Test
    fun `access response defaults survive omitted fields`() = runTest {
        mockWebServer.enqueue(MockResponse().setBody(ACCESS_RESPONSE_MINIMAL))

        val response = endpoint.access("token")
        response.limits.concurrency shouldBe 3
        response.limits.maxArResults shouldBe 300
        response.limits.maxIdentifierBatch shouldBe 100
        response.limits.maxLocationWatchResults shouldBe 100
        response.limits.maxMapResults.shouldBeNull()
        response.limits.maxLocationWatchRadiusKm.shouldBeNull()
        response.aircraftOperations shouldBe listOf("search", "ar", "map", "watches")
        response.entitlementRequests.shouldBeNull()
    }

    @Test
    fun `batch response defaults survive omitted fields`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setBody(
                """
                {
                  "operationId": "5a5c6b2e-3b0a-4a2e-9a0f-000000000001",
                  "serverTime": 1710000000000,
                  "completedAt": 1710000000000,
                  "operationExpiresAt": 1710000300000,
                  "outcomes": [{"index": 0, "status": "answered"}],
                  "aircraft": [],
                  "usage": {
                    "scope": "principal",
                    "bucket": "SEARCH",
                    "resetsAt": 1710028800000,
                    "allowance": {"limit": 25, "used": 1, "reserved": 0, "remaining": 24}
                  }
                }
                """.trimIndent()
            )
        )

        val response = endpoint.search(
            token = "token",
            request = SearchBatchRequest("5a5c6b2e-3b0a-4a2e-9a0f-000000000001", listOf(SearchTerm("DLH453"))),
        )
        response.replayed shouldBe false
        response.metadata.shouldBeNull()
        response.outcomes.single().apply {
            aircraftIds shouldBe emptyList()
            capped shouldBe false
            complete shouldBe false
            charged shouldBe false
            totalMatching.shouldBeNull()
        }
    }

    @Test
    fun `map response carries slim aircraft, the selected record and its trail`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setBody(
                """
                {
                  "serverTime": 1710000000000,
                  "metadata": {"sourceTime": 1710000000000, "fetchedAt": 1710000000000, "expiresAt": 1710000120000, "complete": true, "stale": false},
                  "aircraft": [
                    {"id": "3c65a3", "position": {"latitude": 50.03, "longitude": 8.57, "observedAt": 1709999999500},
                     "callsign": "DLH453", "aircraftType": "A320", "altitudeFeet": 36000.0, "trackDegrees": 271.5, "groundSpeedKnots": 450.0,
                     "squawk": "0425", "military": false, "category": "A3", "futureField": 1}
                  ],
                  "selected": {"id": "3c65a3", "registration": "D-AIZZ", "callsign": "DLH453"},
                  "totalMatching": 13200,
                  "capped": true,
                  "usage": {"scope": "principal", "bucket": "VIEWING", "resetsAt": 1710028800000, "allowance": {"limit": 25000, "used": 1, "reserved": 0, "remaining": 24999}},
                  "selectedTrail": [
                    {"latitude": 50.0, "longitude": 8.5, "altitudeFeet": 35000.0, "observedAt": 1709999990000},
                    {"latitude": 50.01, "longitude": 8.52, "observedAt": 1709999995000}
                  ],
                  "trailReset": true
                }
                """.trimIndent()
            )
        )

        val response = endpoint.map(
            "token",
            MapRequest(
                49.0, 51.0, 7.0, 9.0,
                selectedAircraftId = "3c65a3",
                pinnedAircraftIds = listOf("4b1805"),
                trailSince = 0,
                militaryOnly = true,
            ),
        )

        response.aircraft.single().apply {
            id shouldBe "3c65a3"
            position.observedAt shouldBe 1_709_999_999_500L
            altitudeFeet shouldBe 36_000.0
            trackDegrees shouldBe 271.5
            squawk shouldBe "0425"
            category shouldBe "A3"
        }
        response.selected?.registration shouldBe "D-AIZZ"
        response.selectedTrail?.map { it.observedAt } shouldBe listOf(1_709_999_990_000L, 1_709_999_995_000L)
        response.selectedTrail?.last()?.altitudeFeet.shouldBeNull()
        response.trailReset shouldBe true
        response.capped shouldBe true
        response.totalMatching shouldBe 13_200

        val body = mockWebServer.takeRequest().body.readUtf8()
        body shouldContain "\"pinnedAircraftIds\":[\"4b1805\"]"
        body shouldContain "\"trailSince\":0"
        body shouldContain "\"militaryOnly\":true"
    }

    @Test
    fun `a capped map response carries the altitude it was cut off at`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setBody(
                """
                {
                  "serverTime": 1710000000000,
                  "metadata": {"sourceTime": 1710000000000, "fetchedAt": 1710000000000, "expiresAt": 1710000120000, "complete": true, "stale": false},
                  "aircraft": [{"id": "3c65a3", "position": {"latitude": 50.03, "longitude": 8.57}, "altitudeFeet": 36000.0}],
                  "totalMatching": 13200,
                  "capped": true,
                  "cutoffAltitudeFeet": 24125.0,
                  "usage": {"scope": "principal", "bucket": "VIEWING", "resetsAt": 1710028800000, "allowance": {"limit": 25000, "used": 1, "reserved": 0, "remaining": 24999}}
                }
                """.trimIndent()
            )
        )

        val response = endpoint.map("token", MapRequest(49.0, 51.0, 7.0, 9.0))

        response.capped shouldBe true
        response.cutoffAltitudeFeet shouldBe 24_125.0
    }

    @Test
    fun `watch history pages carry hours, errors and a cursor to send back`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setBody(
                """
                {
                  "operationId": "5a5c6b2e-3b0a-4a2e-9a0f-000000000003",
                  "serverTime": 1760356800000,
                  "from": 1759752000000,
                  "until": 1760360400000,
                  "outcomes": [
                    {"index": 0, "status": "answered", "evaluationBasis": "recent_messages",
                     "hours": [
                       {"hour": 1759752000000, "aircraftCount": 1, "coverage": "full", "final": true},
                       {"hour": 1759755600000, "aircraftCount": 0, "coverage": "partial", "final": false}
                     ],
                     "lastSeenAt": 1759752600000, "lastSeenAircraftId": "3c65a3",
                     "computedUntil": {"hour": 1759759200000, "afterAircraftId": "3c65a3", "aircraftCount": 2,
                                       "hourFinal": true, "lastSeenAt": 1759752600000, "lastSeenAircraftId": "3c65a3"}},
                    {"index": 1, "status": "error", "error": {"code": "budget_exhausted"}},
                    {"index": 2, "status": "error", "error": {"code": "tier_restricted"}}
                  ],
                  "usage": {"scope": "principal", "bucket": "WATCH", "resetsAt": 1760400000000, "allowance": {"limit": 1000, "used": 1, "reserved": 0, "remaining": 999}}
                }
                """.trimIndent()
            )
        )

        val cursor = HistoryCursor(hour = 1_759_755_600_000L, afterAircraftId = "3c65a3", aircraftCount = 1)
        val response = endpoint.historyWatches(
            "token",
            HistoryWatchRequest(
                operationId = "5a5c6b2e-3b0a-4a2e-9a0f-000000000003",
                from = 1_759_752_000_000L,
                until = 1_760_360_400_000L,
                watches = listOf(
                    HistoryWatchDefinition(type = "hex", value = "3C65A3", cursor = cursor),
                    HistoryWatchDefinition(type = "callsign", value = "DLH453"),
                    HistoryWatchDefinition(type = "location", latitude = 50.0, longitude = 8.5, radiusKm = 5.0),
                ),
            ),
        )

        response.outcomes[0].apply {
            status shouldBe "answered"
            evaluationBasis shouldBe "recent_messages"
            hours.map { it.isFinal } shouldBe listOf(true, false)
            hours.last().coverage shouldBe "partial"
            lastSeenAircraftId shouldBe "3c65a3"
            computedUntil shouldBe HistoryCursor(
                hour = 1_759_759_200_000L,
                afterAircraftId = "3c65a3",
                aircraftCount = 2,
                hourFinal = true,
                lastSeenAt = 1_759_752_600_000L,
                lastSeenAircraftId = "3c65a3",
            )
        }
        response.outcomes[1].error?.code shouldBe ServerCodes.BUDGET_EXHAUSTED
        response.outcomes[2].computedUntil.shouldBeNull()
        response.usage.allowance.remaining shouldBe 999

        val request = mockWebServer.takeRequest()
        request.path shouldBe "/api/v1/history/watches"
        val body = request.body.readUtf8()
        body shouldContain "\"from\":1759752000000"
        body shouldContain "\"cursor\":{\"hour\":1759755600000,\"afterAircraftId\":\"3c65a3\",\"aircraftCount\":1"
    }

    @Test
    fun `aircraft history carries the points and the gap to break the line at`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setBody(
                """
                {
                  "serverTime": 1760356800000,
                  "aircraftId": "3c6444",
                  "from": 1759752000000,
                  "until": 1760356800000,
                  "maxGapMs": 300000,
                  "points": [
                    {"latitude": 50.0, "longitude": 8.5, "altitudeFeet": 35000.0, "observedAt": 1759752010000},
                    {"latitude": 50.1, "longitude": 8.6, "observedAt": 1759752070000}
                  ],
                  "runs": [{"kind": "callsign", "value": "DLH400", "startAt": 1759752000000, "lastSeenAt": 1759752070000}],
                  "registration": "D-ABYA",
                  "aircraftType": "B748",
                  "category": "A5",
                  "hours": [{"hour": 1759752000000, "coverage": "full", "final": true}],
                  "usage": {"scope": "principal", "bucket": "VIEWING", "resetsAt": 1760400000000, "allowance": {"limit": 500000, "used": 1, "reserved": 0, "remaining": 499999}}
                }
                """.trimIndent()
            )
        )

        val response = endpoint.historyAircraft("token", HistoryAircraftRequest("3c6444", 1_759_752_000_000L, 1_760_356_800_000L))

        response.maxGapMs shouldBe 300_000L
        response.points.map { it.observedAt } shouldBe listOf(1_759_752_010_000L, 1_759_752_070_000L)
        response.points.last().altitudeFeet.shouldBeNull()
        response.runs shouldBe listOf(HistoryRunSpan("callsign", "DLH400", 1_759_752_000_000L, 1_759_752_070_000L))
        response.registration shouldBe "D-ABYA"
        response.aircraftType shouldBe "B748"
        response.category shouldBe "A5"
        response.hours shouldBe listOf(HistoryHourStatus(1_759_752_000_000L, HistoryHourStatus.COVERAGE_FULL, isFinal = true))
        response.usage.bucket shouldBe "VIEWING"

        val request = mockWebServer.takeRequest()
        request.path shouldBe "/api/v1/history/aircraft"
        request.body.readUtf8() shouldBe """{"aircraftId":"3c6444","from":1759752000000,"until":1760356800000}"""
    }

    @Test
    fun `a free installation is refused aircraft history`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setHeader("Content-Type", "application/problem+json")
                .setBody("""{"type": "about:blank", "title": "Forbidden", "status": 403, "code": "tier_restricted"}""")
        )

        val error = shouldThrow<ServerApiException> {
            endpoint.historyAircraft("token", HistoryAircraftRequest("3c6444", 0L, 1L))
        }

        error.code shouldBe ServerCodes.TIER_RESTRICTED
        error.status shouldBe 403
    }

    @Test
    fun `access lists the history operations and the replay flag`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setBody(
                ACCESS_RESPONSE_MINIMAL
                    .replace("\"watchTypes\": [\"hex\", \"callsign\"],", "\"watchTypes\": [\"hex\", \"callsign\"], \"historyReplay\": true,")
                    .replace("\"installationRequests\": {", "\"aircraftOperations\": [\"search\", \"history_watches\"], \"installationRequests\": {")
            )
        )

        val access = endpoint.access("token")

        access.limits.historyReplay shouldBe true
        access.aircraftOperations shouldBe listOf("search", "history_watches")
    }

    @Test
    fun `map response defaults survive omitted fields`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setBody(
                """
                {
                  "serverTime": 1710000000000,
                  "metadata": {"sourceTime": 1710000000000, "fetchedAt": 1710000000000, "expiresAt": 1710000120000, "complete": true, "stale": false},
                  "aircraft": [{"id": "3c65a3", "position": {"latitude": 50.03, "longitude": 8.57}}],
                  "capped": false,
                  "usage": {"scope": "principal", "bucket": "VIEWING", "resetsAt": 1710028800000, "allowance": {"limit": 25000, "used": 1, "reserved": 0, "remaining": 24999}}
                }
                """.trimIndent()
            )
        )

        val response = endpoint.map("token", MapRequest(49.0, 51.0, 7.0, 9.0))

        response.selected.shouldBeNull()
        response.selectedTrail.shouldBeNull()
        response.trailReset shouldBe false
        response.totalMatching.shouldBeNull()
        response.cutoffAltitudeFeet.shouldBeNull()
        response.aircraft.single().apply {
            military shouldBe false
            altitudeFeet.shouldBeNull()
            position.observedAt.shouldBeNull()
            squawk.shouldBeNull()
            category.shouldBeNull()
        }
    }

    @Test
    fun `credentials never reach the logs`() = runTest {
        mockWebServer.enqueue(MockResponse().setBody("""{"challenge":"CHALLENGE-VALUE","expiresInSeconds":120}"""))
        mockWebServer.enqueue(MockResponse().setBody(TOKEN_RESPONSE))
        mockWebServer.enqueue(MockResponse().setBody(TOKEN_RESPONSE))
        mockWebServer.enqueue(MockResponse().setBody(ACCESS_RESPONSE_MINIMAL))

        endpoint.challenge()
        endpoint.enroll(
            EnrollRequest(
                challenge = "CHALLENGE-VALUE",
                publicKey = "PUBLIC-KEY-VALUE",
                signature = "ENROLL-SIGNATURE-VALUE",
                variant = "foss",
                appVersion = 1,
            )
        )
        endpoint.refresh(
            RefreshRequest(
                refreshToken = "REFRESH-TOKEN-VALUE",
                operationId = "5a5c6b2e-3b0a-4a2e-9a0f-000000000001",
                timestamp = 1710000000L,
                signature = "REFRESH-SIGNATURE-VALUE",
            )
        )
        endpoint.access("ACCESS-TOKEN-VALUE")

        val logged = logSink.lines.joinToString("\n")
        logged shouldContain "api/v1/access"
        listOf(
            "ACCESS-TOKEN-VALUE",
            "REFRESH-TOKEN-VALUE",
            "REFRESH-SIGNATURE-VALUE",
            "ENROLL-SIGNATURE-VALUE",
            "STORED-REFRESH-TOKEN",
            "STORED-ACCESS-TOKEN",
        ).forEach { secret ->
            logSink.lines.none { it.contains(secret) } shouldBe true
        }
    }

    private fun problemResponse(status: Int, code: String) = MockResponse()
        .setResponseCode(status)
        .setHeader("Content-Type", "application/problem+json")
        .setBody("""{"type": "about:blank", "title": "error", "status": $status, "code": "$code"}""")

    private class CapturingLogger : Logging.Logger {
        val lines = mutableListOf<String>()

        override fun log(priority: Logging.Priority, tag: String, message: String, metaData: Map<String, Any>?) {
            synchronized(lines) { lines.add("$tag $message") }
        }
    }

    companion object {
        private const val META_RESPONSE = """{"recommendedAppVersion": 0, "minSupportedAppVersion": 0}"""

        private val ACCESS_RESPONSE_MINIMAL = """
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
                "maxBroadResults": 100
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

        private val FEEDER_STATUS_RESPONSE = """
            {
              "tier": "feeder",
              "restricted": false,
              "limits": {
                "viewingIntervalSeconds": 5,
                "viewingBurst": 3,
                "viewingPerDay": 25000,
                "searchesPerDay": 25,
                "watchesPerDay": 1000,
                "maxRadiusNm": 25,
                "watchTypes": ["hex", "callsign"]
              }
            }
        """.trimIndent()

        private val TOKEN_RESPONSE = """
            {
              "installationId": "5a5c6b2e-3b0a-4a2e-9a0f-000000000002",
              "refreshToken": "STORED-REFRESH-TOKEN",
              "accessToken": "STORED-ACCESS-TOKEN",
              "accessExpiresAt": 1710000600000,
              "refreshExpiresAt": 1712592000000
            }
        """.trimIndent()
    }
}
