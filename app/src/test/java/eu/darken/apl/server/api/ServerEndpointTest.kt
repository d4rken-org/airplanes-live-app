package eu.darken.apl.server.api

import eu.darken.apl.common.BuildConfigWrap
import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.datastore.DataStoreValue
import eu.darken.apl.common.debug.autoreport.DebugSettings
import eu.darken.apl.common.debug.logging.Logging
import eu.darken.apl.common.http.HttpModule
import eu.darken.apl.server.PrivacyAcceptance
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.ServerModule
import eu.darken.apl.server.compat.AppCompatibility
import eu.darken.apl.server.compat.CompatState
import eu.darken.apl.server.deletion.UnconfirmedDeletionException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
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
        debugSettings: DebugSettings? = null,
    ) = ServerEndpoint(
        baseClient = baseClient,
        json = ServerModule.serverJson(),
        dispatcherProvider = TestDispatcherProvider(),
        serverClock = ServerClock(fakeMonotonicClock),
        compatibility = compatibility,
        debugSettings = debugSettings,
    ).apply {
        baseUrl = mockWebServer.url("/").toString()
    }

    @Test
    fun `no request leaves before the privacy screen was accepted`() = runTest {
        // Both requests leave at once, a queue would hand out the bodies in arrival order
        mockWebServer.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setBody(
                if (request.path!!.contains("meta")) META_RESPONSE else ACCESS_RESPONSE_MINIMAL
            )
        }
        val accepted = MutableStateFlow(false)
        val gated = ServerEndpoint(
            baseClient = HttpModule().baseHttpClient(),
            json = ServerModule.serverJson(),
            dispatcherProvider = TestDispatcherProvider(),
            serverClock = ServerClock(fakeMonotonicClock),
            compatibility = compatibility,
            privacyAcceptance = PrivacyAcceptance(accepted),
        ).apply { baseUrl = mockWebServer.url("/").toString() }

        val access = async { gated.access("token") }
        val meta = async { gated.meta(forceNetwork = true) }
        runCurrent()
        mockWebServer.requestCount shouldBe 0

        accepted.value = true
        access.await()
        meta.await()
        mockWebServer.requestCount shouldBe 2
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
    fun `a maintenance problem carries its code, retry hint and expected end`() = runTest {
        mockWebServer.enqueue(
            outageResponse(""""code": "server_maintenance", "retryAfterSeconds": 60, "until": "2026-11-26T15:30:00Z"""")
        )

        val error = shouldThrow<ServerApiException> { endpoint.access("token") }
        error.code shouldBe ServerCodes.SERVER_MAINTENANCE
        error.status shouldBe 503
        error.retryAfterSeconds shouldBe 60L
        error.until shouldBe Instant.parse("2026-11-26T15:30:00Z")
    }

    @Test
    fun `an outage problem without an expected end has none`() = runTest {
        mockWebServer.enqueue(outageResponse(""""code": "server_incident", "retryAfterSeconds": 30"""))

        val error = shouldThrow<ServerApiException> { endpoint.access("token") }
        error.code shouldBe ServerCodes.SERVER_INCIDENT
        error.retryAfterSeconds shouldBe 30L
        error.until.shouldBeNull()
    }

    @Test
    fun `an unparseable expected end is dropped`() = runTest {
        mockWebServer.enqueue(
            outageResponse(""""code": "server_maintenance", "retryAfterSeconds": 60, "until": "tomorrow-ish"""")
        )

        val error = shouldThrow<ServerApiException> { endpoint.access("token") }
        error.code shouldBe ServerCodes.SERVER_MAINTENANCE
        error.retryAfterSeconds shouldBe 60L
        error.until.shouldBeNull()
    }

    @Test
    fun `an expected end that is not a string is dropped without losing the code`() = runTest {
        listOf(
            """{"at": "2026-11-26T15:30:00Z"}""",
            """["2026-11-26T15:30:00Z"]""",
            """1764171000000""",
        ).forEach { until ->
            mockWebServer.enqueue(
                outageResponse(""""code": "server_maintenance", "retryAfterSeconds": 60, "until": $until""")
            )

            val error = shouldThrow<ServerApiException> { endpoint.access("token") }
            error.code shouldBe ServerCodes.SERVER_MAINTENANCE
            error.retryAfterSeconds shouldBe 60L
            error.until.shouldBeNull()
        }
    }

    @Test
    fun `a malformed problem body falls back to the http status`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(503)
                .setHeader("Content-Type", "application/problem+json")
                .setBody("""{"type": "about:blank", "status": 503""")
        )

        val error = shouldThrow<ServerApiException> { endpoint.access("token") }
        error.code shouldBe "http_503"
        error.status shouldBe 503
        error.until.shouldBeNull()
    }

    @Test
    fun `a bare bad gateway falls back to the http status`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(502)
                .setHeader("Content-Type", "text/plain")
                .setBody("Bad Gateway")
        )

        val error = shouldThrow<ServerApiException> { endpoint.access("token") }
        error.code shouldBe "http_502"
        error.status shouldBe 502
        error.until.shouldBeNull()
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
    fun `access decodes without the unused limits and allowance fields`() = runTest {
        mockWebServer.enqueue(MockResponse().setBody(ACCESS_RESPONSE_WITHOUT_UNUSED))

        val response = endpoint.access("token")
        response.limits.apply {
            viewingIntervalSeconds shouldBe 5
            maxRadiusNm shouldBe 25
            viewingBurst.shouldBeNull()
            viewingPerDay.shouldBeNull()
            searchesPerDay.shouldBeNull()
            watchesPerDay.shouldBeNull()
        }
        response.usage.viewing shouldBe Allowance(limit = null, used = 3, reserved = null, remaining = 24997)
        response.usage.search shouldBe Allowance(limit = null, used = 2, reserved = null, remaining = 23)
        response.usage.watch shouldBe Allowance(limit = null, used = 1, reserved = null, remaining = 999)
    }

    @Test
    fun `feeder status decodes a linked feeder without the unused fields`() = runTest {
        mockWebServer.enqueue(MockResponse().setBody(FEEDER_STATUS_LINKED_WITHOUT_UNUSED))

        val response = endpoint.feederStatus("token")
        response.limits.apply {
            viewingIntervalSeconds shouldBe 5
            maxRadiusNm shouldBe 25
            viewingBurst.shouldBeNull()
            viewingPerDay.shouldBeNull()
            searchesPerDay.shouldBeNull()
            watchesPerDay.shouldBeNull()
        }
        val feeder = response.feeder.shouldNotBeNull()
        feeder.feederId shouldBe "feeder-id"
        feeder.status shouldBe "active"
        feeder.linkedAt shouldBe 1710000000000L
        feeder.lastActiveAt shouldBe 1710000060000L
        feeder.networkVerified.shouldBeNull()
        feeder.checkedAt.shouldBeNull()
        feeder.nextCheckAt.shouldBeNull()
        feeder.validUntil.shouldBeNull()
        feeder.eligible.shouldBeNull()
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
                  "trailReset": true,
                  "trailLegStart": 1709999988000
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
        response.trailLegStart shouldBe 1_709_999_988_000L
        response.capped shouldBe true
        response.totalMatching shouldBe 13_200

        val body = mockWebServer.takeRequest().body.readUtf8()
        body shouldContain "\"pinnedAircraftIds\":[\"4b1805\"]"
        body shouldContain "\"trailSince\":0"
        body shouldContain "\"militaryOnly\":true"
        body shouldNotContain "trailLegStart"
    }

    @Test
    fun `map aircraft carry a true heading, older servers omit it`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setBody(
                """
                {
                  "serverTime": 1710000000000,
                  "metadata": {"sourceTime": 1710000000000, "fetchedAt": 1710000000000, "expiresAt": 1710000120000, "complete": true, "stale": false},
                  "aircraft": [
                    {"id": "3c65a3", "position": {"latitude": 50.03, "longitude": 8.57}, "onGround": true, "trueHeadingDegrees": 271.5},
                    {"id": "4b1805", "position": {"latitude": 50.04, "longitude": 8.58}, "trackDegrees": 90.0}
                  ],
                  "capped": false,
                  "usage": {"scope": "principal", "bucket": "VIEWING", "resetsAt": 1710028800000, "allowance": {"limit": 25000, "used": 1, "reserved": 0, "remaining": 24999}}
                }
                """.trimIndent()
            )
        )

        val (ground, air) = endpoint.map("token", MapRequest(49.0, 51.0, 7.0, 9.0)).aircraft
        ground.trueHeadingDegrees shouldBe 271.5
        air.trueHeadingDegrees.shouldBeNull()
    }

    @Test
    fun `a map request carries the applied leg start`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setBody(
                """
                {
                  "serverTime": 1710000000000,
                  "metadata": {"sourceTime": 1710000000000, "fetchedAt": 1710000000000, "expiresAt": 1710000120000, "complete": true, "stale": false},
                  "aircraft": [],
                  "capped": false,
                  "usage": {"scope": "principal", "bucket": "VIEWING", "resetsAt": 1710028800000, "allowance": {"limit": 25000, "used": 1, "reserved": 0, "remaining": 24999}}
                }
                """.trimIndent()
            )
        )

        endpoint.map(
            "token",
            MapRequest(
                49.0, 51.0, 7.0, 9.0,
                selectedAircraftId = "3c65a3",
                trailSince = 1_709_999_995_000,
                trailLegStart = 1_709_999_988_000,
            ),
        )

        val body = mockWebServer.takeRequest().body.readUtf8()
        body shouldContain "\"trailSince\":1709999995000"
        body shouldContain "\"trailLegStart\":1709999988000"
    }

    @Test
    fun `map aircraft carry their kind and the request carries the chosen kinds`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setBody(
                """
                {
                  "serverTime": 1710000000000,
                  "metadata": {"sourceTime": 1710000000000, "fetchedAt": 1710000000000, "expiresAt": 1710000120000, "complete": true, "stale": false},
                  "aircraft": [{"id": "3c65a3", "position": {"latitude": 50.03, "longitude": 8.57}, "kind": "helicopter"}],
                  "capped": false,
                  "usage": {"scope": "principal", "bucket": "VIEWING", "resetsAt": 1710028800000, "allowance": {"limit": 25000, "used": 1, "reserved": 0, "remaining": 24999}}
                }
                """.trimIndent()
            )
        )

        val response = endpoint.map("token", MapRequest(49.0, 51.0, 7.0, 9.0, kinds = listOf("helicopter", "glider")))

        response.aircraft.single().kind shouldBe "helicopter"
        mockWebServer.takeRequest().body.readUtf8() shouldContain "\"kinds\":[\"helicopter\",\"glider\"]"
    }

    @Test
    fun `a map request without kinds sends no kinds`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setBody(
                """
                {
                  "serverTime": 1710000000000,
                  "metadata": {"sourceTime": 1710000000000, "fetchedAt": 1710000000000, "expiresAt": 1710000120000, "complete": true, "stale": false},
                  "aircraft": [],
                  "capped": false,
                  "usage": {"scope": "principal", "bucket": "VIEWING", "resetsAt": 1710028800000, "allowance": {"limit": 25000, "used": 1, "reserved": 0, "remaining": 24999}}
                }
                """.trimIndent()
            )
        )

        endpoint.map("token", MapRequest(49.0, 51.0, 7.0, 9.0))

        mockWebServer.takeRequest().body.readUtf8() shouldNotContain "kinds"
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

    private fun mapResponseWithLastSeen(lastSeen: String) = MockResponse().setBody(
        """
        {
          "serverTime": 1710000000000,
          "metadata": {"sourceTime": 1710000000000, "fetchedAt": 1710000000000, "expiresAt": 1710000120000, "complete": true, "stale": false},
          "aircraft": [],
          "capped": false,
          "usage": {"scope": "principal", "bucket": "VIEWING", "resetsAt": 1710028800000, "allowance": {"limit": 25000, "used": 1, "reserved": 0, "remaining": 24999}},
          "selectedLastSeen": $lastSeen
        }
        """.trimIndent()
    )

    @Test
    fun `a map response without the selection carries where it was last seen, with its track`() = runTest {
        mockWebServer.enqueue(
            mapResponseWithLastSeen("""{"latitude": 50.03, "longitude": 8.57, "observedAt": 1709999400000, "trackDegrees": 271.5}""")
        )

        val response = endpoint.map("token", MapRequest(49.0, 51.0, 7.0, 9.0, selectedAircraftId = "3c65a3", trailSince = 0))

        response.selected.shouldBeNull()
        response.selectedLastSeen shouldBe SelectedLastSeen(
            latitude = 50.03,
            longitude = 8.57,
            observedAt = 1_709_999_400_000L,
            trackDegrees = 271.5,
        )
    }

    @Test
    fun `a last seen position may come without a track`() = runTest {
        mockWebServer.enqueue(
            mapResponseWithLastSeen("""{"latitude": 50.03, "longitude": 8.57, "observedAt": 1709999400000}""")
        )

        val response = endpoint.map("token", MapRequest(49.0, 51.0, 7.0, 9.0, selectedAircraftId = "3c65a3", trailSince = 0))

        response.selectedLastSeen shouldBe SelectedLastSeen(latitude = 50.03, longitude = 8.57, observedAt = 1_709_999_400_000L)
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
    fun `a found route carries both airports and ignores unknown fields`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setBody(
                """
                {
                  "serverTime": 1793880000000,
                  "callsign": "DLH920",
                  "status": "found",
                  "futureField": {"nested": true},
                  "route": {
                    "source": "vrs",
                    "origin": {"icao": "EDDF", "iata": "FRA", "name": "Frankfurt Main Airport", "country": "DE", "latitude": 50.026706, "longitude": 8.55835, "elevationFeet": 364},
                    "destination": {"icao": "EGLL", "name": "London Heathrow Airport", "latitude": 51.470748, "longitude": -0.459909}
                  }
                }
                """.trimIndent()
            )
        )

        val response = endpoint.route("token-value", RouteLookupRequest(aircraftId = "3c6444", callsign = "DLH920"))

        response shouldBe RouteLookupResponse(
            serverTime = 1_793_880_000_000L,
            callsign = "DLH920",
            status = RouteLookupResponse.STATUS_FOUND,
            route = RouteDto(
                source = RouteDto.SOURCE_VRS,
                origin = RouteAirportDto(
                    icao = "EDDF",
                    iata = "FRA",
                    name = "Frankfurt Main Airport",
                    country = "DE",
                    latitude = 50.026706,
                    longitude = 8.55835,
                ),
                destination = RouteAirportDto(
                    icao = "EGLL",
                    iata = null,
                    name = "London Heathrow Airport",
                    country = null,
                    latitude = 51.470748,
                    longitude = -0.459909,
                ),
            ),
        )

        val request = mockWebServer.takeRequest()
        request.method shouldBe "POST"
        request.path shouldBe "/api/v1/aircraft/route"
        request.getHeader("Authorization") shouldBe "Bearer token-value"
        request.body.readUtf8() shouldBe """{"aircraftId":"3c6444","callsign":"DLH920"}"""
    }

    @Test
    fun `route airports carry catalogue id, elevation and time zone when the server knows them`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setBody(
                """
                {
                  "serverTime": 1793880000000,
                  "callsign": "DLH920",
                  "status": "found",
                  "route": {
                    "source": "vrs",
                    "origin": {"icao": "EDDF", "iata": "FRA", "name": "Frankfurt Main Airport", "country": "DE", "latitude": 50.026706, "longitude": 8.55835, "id": "oa:2212", "elevationFt": 364, "timeZone": "Europe/Berlin"},
                    "destination": {"icao": "XXXX", "name": "Somewhere", "latitude": 1.5, "longitude": -2.5}
                  }
                }
                """.trimIndent()
            )
        )

        val route = endpoint.route("token", RouteLookupRequest(aircraftId = "3c6444", callsign = "DLH920")).route!!

        route.origin shouldBe RouteAirportDto(
            icao = "EDDF",
            iata = "FRA",
            name = "Frankfurt Main Airport",
            country = "DE",
            latitude = 50.026706,
            longitude = 8.55835,
            id = "oa:2212",
            elevationFt = 364,
            timeZone = "Europe/Berlin",
        )
        route.destination.run {
            id.shouldBeNull()
            elevationFt.shouldBeNull()
            timeZone.shouldBeNull()
        }
    }

    @Test
    fun `a route lookup without a match has no route`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setBody("""{"serverTime": 1793880000000, "callsign": "BAW1", "status": "none"}""")
        )

        val response = endpoint.route("token", RouteLookupRequest(aircraftId = "400f01", callsign = "BAW1"))

        response.status shouldBe RouteLookupResponse.STATUS_NONE
        response.callsign shouldBe "BAW1"
        response.route.shouldBeNull()
    }

    @Test
    fun `an unknown route status still decodes`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setBody("""{"serverTime": 1793880000000, "callsign": "BAW1", "status": "pending"}""")
        )

        val response = endpoint.route("token", RouteLookupRequest(aircraftId = "400f01", callsign = "BAW1"))

        response.status shouldBe "pending"
        response.route.shouldBeNull()
    }

    @Test
    fun `an unavailable route lookup surfaces its code and retry delay`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(503)
                .setHeader("Content-Type", "application/problem+json")
                .setHeader("Retry-After", "30")
                .setBody("""{"type": "about:blank", "title": "Service Unavailable", "status": 503, "code": "route_lookup_unavailable"}""")
        )

        val error = shouldThrow<ServerApiException> {
            endpoint.route("token", RouteLookupRequest(aircraftId = "3c6444", callsign = "DLH920"))
        }

        error.code shouldBe ServerCodes.ROUTE_LOOKUP_UNAVAILABLE
        error.status shouldBe 503
        error.retryAfterSeconds shouldBe 30L
    }

    @Test
    fun `an invalid route request surfaces its code`() = runTest {
        mockWebServer.enqueue(problemResponse(400, ServerCodes.INVALID_REQUEST))

        val error = shouldThrow<ServerApiException> {
            endpoint.route("token", RouteLookupRequest(aircraftId = "nothex", callsign = ""))
        }

        error.code shouldBe ServerCodes.INVALID_REQUEST
        error.status shouldBe 400
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
        response.trailLegStart.shouldBeNull()
        response.totalMatching.shouldBeNull()
        response.cutoffAltitudeFeet.shouldBeNull()
        response.selectedLastSeen.shouldBeNull()
        response.aircraft.single().apply {
            military shouldBe false
            altitudeFeet.shouldBeNull()
            position.observedAt.shouldBeNull()
            squawk.shouldBeNull()
            category.shouldBeNull()
            kind.shouldBeNull()
        }
    }

    @Test
    fun `deleting the installation sends an authorized delete`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(204))

        endpoint.deleteInstallation("token-value")

        val request = mockWebServer.takeRequest()
        request.method shouldBe "DELETE"
        request.path shouldBe "/api/v1/installation"
        request.getHeader("Authorization") shouldBe "Bearer token-value"
    }

    @Test
    fun `a deletion answered with another success status is not confirmed`() = runTest {
        listOf(200, 202).forEach { status ->
            mockWebServer.enqueue(MockResponse().setResponseCode(status))

            shouldThrow<UnconfirmedDeletionException> { endpoint.deleteInstallation("token") }.status shouldBe status
        }
        mockWebServer.requestCount shouldBe 2
    }

    @Test
    fun `a refused deletion maps the problem to an api exception`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setHeader("Content-Type", "application/problem+json")
                .setBody("""{"type": "about:blank", "title": "Forbidden", "status": 403, "code": "installation_restricted"}""")
        )

        val error = shouldThrow<ServerApiException> { endpoint.deleteInstallation("token") }

        error.code shouldBe ServerCodes.INSTALLATION_RESTRICTED
        error.status shouldBe 403
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

    @Test
    fun `a subscription is submitted as an authorized post`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setBody("""{"state": "active", "granted": true, "expiresAt": 1795400000000, "attached": true}""")
        )

        val response = endpoint.submitSubscription(
            "token-value",
            SubscriptionRequest(productId = "upgrade.pro", purchaseToken = "purchase-token-value"),
        )

        response shouldBe SubscriptionResponse(
            state = "active",
            granted = true,
            expiresAt = 1_795_400_000_000L,
            attached = true,
        )
        val request = mockWebServer.takeRequest()
        request.method shouldBe "POST"
        request.path shouldBe "/api/v1/billing/subscription"
        request.getHeader("Authorization") shouldBe "Bearer token-value"
        request.body.readUtf8() shouldBe """{"productId":"upgrade.pro","purchaseToken":"purchase-token-value"}"""
    }

    @Test
    fun `a subscription without an expiry decodes`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setBody("""{"state": "pending", "granted": false, "expiresAt": null, "attached": true}""")
        )

        endpoint.submitSubscription("token", SubscriptionRequest("upgrade.pro", "purchase-token")) shouldBe
            SubscriptionResponse(state = "pending", granted = false, expiresAt = null, attached = true)
    }

    @Test
    fun `an unknown subscription state and unknown fields still decode`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setBody(
                """{"state": "future_state", "granted": false, "attached": false, "futureField": {"x": 1}}"""
            )
        )

        endpoint.submitSubscription("token", SubscriptionRequest("upgrade.pro", "purchase-token")) shouldBe
            SubscriptionResponse(state = "future_state", granted = false, expiresAt = null, attached = false)
    }

    @Test
    fun `subscription refusals map to api exceptions`() = runTest {
        data class Case(
            val code: String,
            val status: Int,
            val retryAfterHeader: Long? = null,
            val problemRetryAfter: Long? = null,
            val expectedRetryAfter: Long? = null,
        )
        listOf(
            Case(ServerCodes.INVALID_REQUEST, 400),
            Case(ServerCodes.PURCHASE_INVALID, 422),
            Case(ServerCodes.INSTALLATION_RATE_EXCEEDED, 429, retryAfterHeader = 7, expectedRetryAfter = 7),
            Case(
                ServerCodes.INSTALLATION_RATE_EXCEEDED,
                429,
                retryAfterHeader = 7,
                problemRetryAfter = 42,
                expectedRetryAfter = 42
            ),
            Case(ServerCodes.BILLING_UNAVAILABLE, 503, retryAfterHeader = 5, expectedRetryAfter = 5),
            Case(
                ServerCodes.BILLING_UNAVAILABLE,
                503,
                retryAfterHeader = 5,
                problemRetryAfter = 30,
                expectedRetryAfter = 30
            ),
            Case(ServerCodes.DATABASE_UNAVAILABLE, 503, retryAfterHeader = 3, expectedRetryAfter = 3),
            Case(
                ServerCodes.DATABASE_UNAVAILABLE,
                503,
                retryAfterHeader = 3,
                problemRetryAfter = 12,
                expectedRetryAfter = 12
            ),
        ).forEach { case ->
            val retryField = case.problemRetryAfter?.let { """, "retryAfterSeconds": $it""" } ?: ""
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(case.status)
                    .setHeader("Content-Type", "application/problem+json")
                    .apply { case.retryAfterHeader?.let { setHeader("Retry-After", it.toString()) } }
                    .setBody("""{"type": "about:blank", "title": "x", "status": ${case.status}, "code": "${case.code}"$retryField}""")
            )

            val error = shouldThrow<ServerApiException> {
                endpoint.submitSubscription("token", SubscriptionRequest("upgrade.pro", "purchase-token"))
            }

            error.code shouldBe case.code
            error.status shouldBe case.status
            error.retryAfterSeconds shouldBe case.expectedRetryAfter
        }
    }

    @Test
    fun `a subscription request does not reveal its purchase token`() {
        val request = SubscriptionRequest(productId = "upgrade.pro", purchaseToken = "PURCHASE-TOKEN-SENTINEL")

        request.toString() shouldNotContain "PURCHASE-TOKEN-SENTINEL"
        request.toString() shouldContain "upgrade.pro"
    }

    @Test
    fun `purchase tokens never reach the logs, even with body logging`() = runTest {
        val debugMode = mockk<DataStoreValue<Boolean>>().apply { every { flow } returns flowOf(true) }
        val debugSettings = mockk<DebugSettings>().apply { every { isDebugMode } returns debugMode }
        val debugEndpoint = createEndpoint(HttpModule().baseHttpClient(), debugSettings = debugSettings)
        mockWebServer.enqueue(
            MockResponse().setBody("""{"serverTime": 1793880000000, "callsign": "CONTROL-BODY-MARKER", "status": "none"}""")
        )
        mockWebServer.enqueue(
            MockResponse().setBody(
                """{"state": "active", "granted": true, "attached": true, "echo": "RESPONSE-BODY-SENTINEL"}"""
            )
        )

        debugEndpoint.route("token", RouteLookupRequest(aircraftId = "3c6444", callsign = "CONTROL-BODY-MARKER"))
        debugEndpoint.submitSubscription(
            "token",
            SubscriptionRequest(productId = "upgrade.pro", purchaseToken = "PURCHASE-TOKEN-SENTINEL"),
        )

        val logged = logSink.lines.joinToString("\n")
        // Without this, the absence checks below would also pass if bodies were never logged at all
        logged shouldContain "CONTROL-BODY-MARKER"
        logged shouldContain "api/v1/billing/subscription"
        logged shouldNotContain "PURCHASE-TOKEN-SENTINEL"
        logged shouldNotContain "RESPONSE-BODY-SENTINEL"
    }

    private fun problemResponse(status: Int, code: String) = MockResponse()
        .setResponseCode(status)
        .setHeader("Content-Type", "application/problem+json")
        .setBody("""{"type": "about:blank", "title": "error", "status": $status, "code": "$code"}""")

    /** [fields] are the problem's code, retry hint and expected end, as JSON members. */
    private fun outageResponse(fields: String) = MockResponse()
        .setResponseCode(503)
        .setHeader("Content-Type", "application/problem+json")
        .setBody("""{"type": "about:blank", "title": "Service Unavailable", "status": 503, $fields}""")

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

        private val ACCESS_RESPONSE_WITHOUT_UNUSED = """
            {
              "tier": "free",
              "restricted": false,
              "allowanceScope": "principal",
              "limits": {
                "viewingIntervalSeconds": 5,
                "maxRadiusNm": 25,
                "watchTypes": ["hex", "callsign"]
              },
              "usage": {
                "resetsAt": 1710028800000,
                "viewing": {"used": 3, "remaining": 24997},
                "search": {"used": 2, "remaining": 23},
                "watch": {"used": 1, "remaining": 999}
              },
              "installationRequests": {
                "viewing": {"perSecond": 0.2, "burst": 3},
                "search": {"perSecond": 1.0, "burst": 3},
                "watch": {"perSecond": 1.0, "burst": 3},
                "concurrency": 3
              }
            }
        """.trimIndent()

        private val FEEDER_STATUS_LINKED_WITHOUT_UNUSED = """
            {
              "tier": "feeder",
              "restricted": false,
              "limits": {
                "viewingIntervalSeconds": 5,
                "maxRadiusNm": 25,
                "watchTypes": ["hex", "callsign"]
              },
              "feeder": {
                "feederId": "feeder-id",
                "status": "active",
                "linkedAt": 1710000000000,
                "lastActiveAt": 1710000060000
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
