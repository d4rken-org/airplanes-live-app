package eu.darken.apl.server.api

import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.http.HttpModule
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.ServerModule
import eu.darken.apl.server.api.AirportFlightEntryDto.Companion.DIRECTION_ARRIVAL
import eu.darken.apl.server.api.AirportFlightEntryDto.Companion.DIRECTION_DEPARTURE
import eu.darken.apl.server.api.AirportFlightEntryDto.Companion.DIRECTION_UNKNOWN
import eu.darken.apl.server.api.AirportFlightTimeDto.Companion.BASIS_GATE
import eu.darken.apl.server.api.AirportFlightTimeDto.Companion.BASIS_RUNWAY
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.TestCompatibilityStore
import testhelper.coroutine.TestDispatcherProvider
import testhelper.json.readTestResource

class AirportFlightsEndpointTest : BaseTest() {

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

    private val loadRequest = AirportFlightsRequest(operationId = "op-1", airportId = "oa:2212")

    private suspend fun answer(fixture: String): AirportFlightsResponse {
        mockWebServer.enqueue(MockResponse().setBody(readTestResource("airport-flights/$fixture")))
        return endpoint.airportFlights("token", loadRequest)
    }

    private fun problem(status: Int, code: String, retryAfter: Long? = null) = MockResponse()
        .setResponseCode(status)
        .setHeader("Content-Type", "application/problem+json")
        .apply { retryAfter?.let { setHeader("Retry-After", it.toString()) } }
        .setBody("""{"type": "about:blank", "title": "Problem", "status": $status, "code": "$code"}""")

    @Test
    fun `a load posts the airport with the token and without page or window`() = runTest {
        mockWebServer.enqueue(MockResponse().setBody(readTestResource("airport-flights/answered-load.json")))

        endpoint.airportFlights("token-value", AirportFlightsRequest(operationId = "op-1", airportId = "oa:2212"))

        val request = mockWebServer.takeRequest()
        request.method shouldBe "POST"
        request.path shouldBe "/api/v1/airports/flights"
        request.getHeader("Authorization") shouldBe "Bearer token-value"
        request.body.readUtf8() shouldBe """{"operationId":"op-1","airportId":"oa:2212"}"""
    }

    @Test
    fun `a load with a window sends it`() = runTest {
        mockWebServer.enqueue(MockResponse().setBody(readTestResource("airport-flights/answered-load.json")))

        endpoint.airportFlights(
            "token",
            AirportFlightsRequest(
                operationId = "op-1",
                airportId = "oa:2212",
                window = AirportFlightsWindowDto(from = 1_794_567_600_000L, to = 1_794_582_000_000L),
            ),
        )

        mockWebServer.takeRequest().body.readUtf8() shouldBe
                """{"operationId":"op-1","airportId":"oa:2212","window":{"from":1794567600000,"to":1794582000000}}"""
    }

    @Test
    fun `a page request posts only the page, without airport or window`() = runTest {
        mockWebServer.enqueue(MockResponse().setBody(readTestResource("airport-flights/answered-page.json")))

        endpoint.airportFlights(
            "token-value",
            AirportFlightsRequest(
                operationId = "op-2",
                page = AirportFlightsPageDto(evaluationId = "ev-9", direction = DIRECTION_DEPARTURE, cursor = "c-dep-2"),
            ),
        )

        val request = mockWebServer.takeRequest()
        request.path shouldBe "/api/v1/airports/flights"
        request.getHeader("Authorization") shouldBe "Bearer token-value"
        request.body.readUtf8() shouldBe
                """{"operationId":"op-2","page":{"evaluationId":"ev-9","direction":"departure","cursor":"c-dep-2"}}"""
    }

    @Test
    fun `an answered load decodes every field, keeping unknown values`() = runTest {
        val response = answer("answered-load.json")

        response.operationId shouldBe "op-load-1"
        response.outcome shouldBe AirportFlightsResponse.OUTCOME_ANSWERED
        response.replayed shouldBe false
        response.charged shouldBe true
        response.pass shouldBe AirportFlightsPassDto(
            scope = "s-3",
            revision = 7,
            airportId = "oa:2212",
            validUntil = 1_794_573_000_000L,
        )
        response.evaluationId shouldBe "ev-9"
        response.evaluatedAt shouldBe 1_794_571_200_000L
        response.expiresAt shouldBe 1_794_571_260_000L
        response.evaluationExpiresAt shouldBe 1_794_573_000_000L
        response.operationExpiresAt shouldBe 1_794_571_500_000L
        response.refreshAfter shouldBe 1_794_571_260_000L
        response.airport shouldBe AirportFlightsAirportDto(id = "oa:2212", timeZone = "Europe/Berlin", elevationFt = 364)
        response.window shouldBe AirportFlightsWindowDto(from = 1_794_567_600_000L, to = 1_794_582_000_000L)
        response.usage shouldBe UsageUpdate(
            scope = "principal",
            bucket = "SEARCH",
            resetsAt = 1_794_614_400_000L,
            allowance = Allowance(limit = 40, used = 4, reserved = 0, remaining = 36),
        )

        response.sources shouldBe listOf(
            AirportFlightsSourceDto(AirportFlightsSourceDto.KIND_KNOWN_ROUTES, AirportFlightsSourceDto.STATE_OK),
            AirportFlightsSourceDto(AirportFlightsSourceDto.KIND_CACHED_ROUTES, AirportFlightsSourceDto.STATE_PARTIAL),
            AirportFlightsSourceDto(AirportFlightsSourceDto.KIND_NEARBY_TRAFFIC, AirportFlightsSourceDto.STATE_STALE),
            AirportFlightsSourceDto(AirportFlightsSourceDto.KIND_HISTORY, AirportFlightsSourceDto.STATE_UNAVAILABLE),
            AirportFlightsSourceDto(AirportFlightsSourceDto.KIND_SCHEDULES, "warming_up"),
            AirportFlightsSourceDto("crowd_reports", AirportFlightsSourceDto.STATE_OK),
        )
        response.groups shouldBe listOf(
            AirportFlightsGroupDto(direction = DIRECTION_ARRIVAL, total = 31),
            AirportFlightsGroupDto(direction = DIRECTION_DEPARTURE, total = 64, next = "c-dep-2"),
            AirportFlightsGroupDto(direction = DIRECTION_UNKNOWN, total = 3, next = "c-unk-2"),
        )

        response.entries shouldHaveSize 12
        response.entries.map { it.status } shouldBe listOf(
            AirportFlightEntryDto.STATUS_APPROACHING,
            AirportFlightEntryDto.STATUS_ON_GROUND,
            AirportFlightEntryDto.STATUS_EN_ROUTE,
            AirportFlightEntryDto.STATUS_LANDED,
            AirportFlightEntryDto.STATUS_DIVERTED,
            "holding_pattern",
            AirportFlightEntryDto.STATUS_DEPARTED,
            AirportFlightEntryDto.STATUS_SCHEDULED,
            AirportFlightEntryDto.STATUS_DELAYED,
            AirportFlightEntryDto.STATUS_BOARDING,
            AirportFlightEntryDto.STATUS_CANCELLED,
            null,
        )
        response.entries.map { it.direction }.toSet() shouldBe setOf(
            DIRECTION_ARRIVAL,
            DIRECTION_DEPARTURE,
            DIRECTION_UNKNOWN,
        )
        response.entries.flatMap { it.evidence }.toSet() shouldBe setOf(
            AirportFlightEntryDto.EVIDENCE_KNOWN_ROUTE,
            AirportFlightEntryDto.EVIDENCE_CACHED_ROUTE,
            AirportFlightEntryDto.EVIDENCE_FITTED_LEG,
            AirportFlightEntryDto.EVIDENCE_NEARBY_LOW,
            AirportFlightEntryDto.EVIDENCE_ON_GROUND,
            AirportFlightEntryDto.EVIDENCE_DESCENDING,
            AirportFlightEntryDto.EVIDENCE_CLIMBING,
            AirportFlightEntryDto.EVIDENCE_HISTORY,
            AirportFlightEntryDto.EVIDENCE_SCHEDULE,
            "radar_contact",
        )
        response.entries
            .flatMap { listOfNotNull(it.times?.scheduled, it.times?.estimated, it.times?.actual) }
            .map { it.basis }
            .toSet() shouldBe setOf(BASIS_GATE, BASIS_RUNWAY, "block", null)
        response.entries.map { it.estimated }.toSet() shouldBe setOf(true, false, null)

        val byKey = response.entries.associateBy { it.key }
        byKey.getValue("k-a1") shouldBe AirportFlightEntryDto(
            key = "k-a1",
            direction = DIRECTION_ARRIVAL,
            status = AirportFlightEntryDto.STATUS_APPROACHING,
            estimated = true,
            flight = AirportFlightDto(callsign = "XLA401"),
            otherAirport = AirportFlightOtherAirportDto(
                id = "oa:3622",
                ident = "KXYZ",
                icao = "KXYZ",
                iata = "XYZ",
                name = "Example County International Airport",
            ),
            times = AirportFlightTimesDto(estimated = AirportFlightTimeDto(at = 1_794_571_800_000L, basis = BASIS_RUNWAY)),
            aircraftId = "3c6444",
            evidence = listOf(
                AirportFlightEntryDto.EVIDENCE_KNOWN_ROUTE,
                AirportFlightEntryDto.EVIDENCE_FITTED_LEG,
                AirportFlightEntryDto.EVIDENCE_DESCENDING,
            ),
        )
        byKey.getValue("k-a2").run {
            estimated shouldBe false
            flight shouldBe AirportFlightDto(callsign = "XLB908", number = "XB908")
            otherAirport shouldBe AirportFlightOtherAirportDto(ident = "EXMP", iata = "XMP")
            times shouldBe AirportFlightTimesDto(
                scheduled = AirportFlightTimeDto(at = 1_794_570_600_000L, basis = BASIS_GATE),
                estimated = AirportFlightTimeDto(at = 1_794_570_900_000L, basis = BASIS_GATE),
                actual = AirportFlightTimeDto(at = 1_794_570_720_000L, basis = BASIS_RUNWAY),
            )
            aircraftId shouldBe "406a91"
        }
        byKey.getValue("k-a3").run {
            estimated.shouldBeNull()
            otherAirport.shouldBeNull()
            times.shouldBeNull()
            aircraftId shouldBe "39c4f2"
            response.aircraft.map { it.id } shouldNotContain "39c4f2"
        }
        byKey.getValue("k-a4").run {
            flight shouldBe AirportFlightDto(number = "XA1105")
            otherAirport shouldBe AirportFlightOtherAirportDto(name = "Sample Regional Airport")
            times?.actual shouldBe AirportFlightTimeDto(at = 1_794_569_100_000L, basis = "block")
            aircraftId.shouldBeNull()
        }
        byKey.getValue("k-a5").times shouldBe AirportFlightTimesDto(
            scheduled = AirportFlightTimeDto(at = 1_794_574_800_000L, basis = BASIS_GATE),
        )
        byKey.getValue("k-a6") shouldBe AirportFlightEntryDto(
            key = "k-a6",
            direction = DIRECTION_ARRIVAL,
            status = "holding_pattern",
        )
        byKey.getValue("k-d4").times shouldBe AirportFlightTimesDto(
            estimated = AirportFlightTimeDto(at = 1_794_572_400_000L),
        )
        byKey.getValue("k-u1").run {
            direction shouldBe DIRECTION_UNKNOWN
            status.shouldBeNull()
        }

        response.aircraft shouldHaveSize 2
        response.aircraft[0].run {
            id shouldBe "3c6444"
            barometricAltitudeFeet shouldBe 4200.0
            geometricAltitudeFeet shouldBe 4350.0
            position shouldBe AircraftPosition(latitude = 50.112, longitude = 8.402, observedAt = 1_794_571_199_000L)
            registration shouldBe "X-EXMP"
            verticalRateFeetPerMinute shouldBe -896.0
        }
        response.aircraft[1].run {
            id shouldBe "406a91"
            barometricAltitudeFeet.shouldBeNull()
            geometricAltitudeFeet shouldBe 375.0
            onGround shouldBe true
        }
    }

    @Test
    fun `the other airport's country decodes as sent and is absent without one`() = runTest {
        val response = answer("answered-compact.json")

        val byKey = response.entries.associateBy { it.key }
        byKey.getValue("k-c1").otherAirport shouldBe AirportFlightOtherAirportDto(
            id = "oa:3622",
            ident = "KXYZ",
            icao = "KXYZ",
            iata = "XYZ",
            name = "Example County International Airport",
            country = "DE",
        )
        byKey.getValue("k-c2").otherAirport?.country shouldBe "US"
        byKey.getValue("k-c3").otherAirport shouldBe AirportFlightOtherAirportDto(ident = "EXMP", iata = "XMP")
        byKey.getValue("k-c4").otherAirport shouldBe AirportFlightOtherAirportDto(country = "FR")
        byKey.getValue("k-c5").otherAirport?.country shouldBe "D"
        byKey.getValue("k-c6").otherAirport?.country shouldBe "DEU"
        byKey.getValue("k-c7").otherAirport?.country shouldBe "1A"
    }

    @Test
    fun `an answered page holds only the paged direction and no refresh time`() = runTest {
        val response = answer("answered-page.json")

        response.outcome shouldBe AirportFlightsResponse.OUTCOME_ANSWERED
        response.charged shouldBe false
        response.refreshAfter.shouldBeNull()
        response.evaluationId shouldBe "ev-9"
        response.groups shouldBe listOf(
            AirportFlightsGroupDto(direction = DIRECTION_DEPARTURE, total = 64, next = "c-dep-3"),
        )
        response.entries shouldHaveSize 3
        response.entries.map { it.direction }.toSet() shouldBe setOf(DIRECTION_DEPARTURE)
        response.aircraft.map { it.id } shouldBe listOf("4b18f2")
        response.sources shouldHaveSize 2
    }

    @Test
    fun `directions this build does not know decode with their own groups`() = runTest {
        val response = answer("answered-other-directions.json")

        response.groups shouldBe listOf(
            AirportFlightsGroupDto(direction = DIRECTION_UNKNOWN, total = 4, next = "c-unk-2"),
            AirportFlightsGroupDto(direction = "overflight", total = 12, next = "c-ovf-2"),
            AirportFlightsGroupDto(direction = "diverted_in", total = 2, next = "c-div-2"),
        )
        response.entries.map { it.direction } shouldBe listOf(DIRECTION_UNKNOWN, "overflight", "diverted_in")
        response.airport shouldBe AirportFlightsAirportDto(id = "oa:2212")
        response.aircraft.shouldBeEmpty()
    }

    @Test
    fun `a replayed answer keeps its original evaluation times`() = runTest {
        val response = answer("answered-replayed.json")

        response.replayed shouldBe true
        response.charged shouldBe true
        val evaluatedAt = response.evaluatedAt.shouldNotBeNull()
        response.expiresAt.shouldNotBeNull() shouldBeLessThan evaluatedAt + 3_600_000L
        response.entries shouldHaveSize 2
        response.aircraft shouldHaveSize 1
    }

    @Test
    fun `a history only answer has actual times and no aircraft`() = runTest {
        val response = answer("history-only.json")

        response.sources shouldBe listOf(
            AirportFlightsSourceDto(AirportFlightsSourceDto.KIND_HISTORY, AirportFlightsSourceDto.STATE_OK),
        )
        response.aircraft.shouldBeEmpty()
        response.entries shouldHaveSize 3
        response.entries.map { it.status }.toSet() shouldBe setOf(
            AirportFlightEntryDto.STATUS_LANDED,
            AirportFlightEntryDto.STATUS_DEPARTED,
        )
        response.entries.forEach { entry ->
            withClue(entry.key) {
                entry.evidence shouldBe listOf(AirportFlightEntryDto.EVIDENCE_HISTORY)
                entry.times?.actual.shouldNotBeNull()
                entry.times?.scheduled.shouldBeNull()
                entry.times?.estimated.shouldBeNull()
                entry.aircraftId.shouldBeNull()
            }
        }
    }

    @Test
    fun `a schedule only answer has flight numbers and gate times`() = runTest {
        val response = answer("schedule-only.json")

        response.sources shouldBe listOf(
            AirportFlightsSourceDto(AirportFlightsSourceDto.KIND_SCHEDULES, AirportFlightsSourceDto.STATE_OK),
        )
        response.aircraft.shouldBeEmpty()
        response.entries.map { it.status } shouldContainExactlyInAnyOrder listOf(
            AirportFlightEntryDto.STATUS_SCHEDULED,
            AirportFlightEntryDto.STATUS_DIVERTED,
            AirportFlightEntryDto.STATUS_DELAYED,
            AirportFlightEntryDto.STATUS_BOARDING,
            AirportFlightEntryDto.STATUS_CANCELLED,
        )
        response.entries.forEach { entry ->
            withClue(entry.key) {
                entry.flight?.callsign.shouldBeNull()
                entry.flight?.number.shouldNotBeNull()
                entry.times?.scheduled?.basis shouldBe BASIS_GATE
                entry.times?.estimated?.basis shouldBe BASIS_GATE
                entry.times?.actual.shouldBeNull()
                entry.evidence shouldBe listOf(AirportFlightEntryDto.EVIDENCE_SCHEDULE)
            }
        }
    }

    @Test
    fun `outcomes other than answered decode without evaluation fields`() = runTest {
        data class Expected(
            val fixture: String,
            val outcome: String,
            val pass: AirportFlightsPassDto,
            val replayed: Boolean = false,
            val charged: Boolean = false,
        )

        val withPass = AirportFlightsPassDto(
            scope = "s-3",
            revision = 0,
            airportId = "oa:2212",
            validUntil = 1_794_573_000_000L,
        )
        listOf(
            Expected(
                fixture = "outcome-allowance-exhausted.json",
                outcome = AirportFlightsResponse.OUTCOME_ALLOWANCE_EXHAUSTED,
                pass = AirportFlightsPassDto(scope = "s-3", revision = 11),
            ),
            Expected(
                fixture = "outcome-result-expired.json",
                outcome = AirportFlightsResponse.OUTCOME_RESULT_EXPIRED,
                pass = withPass.copy(revision = 12),
                replayed = true,
                charged = true,
            ),
            Expected(
                fixture = "outcome-evaluation-expired.json",
                outcome = AirportFlightsResponse.OUTCOME_EVALUATION_EXPIRED,
                pass = withPass.copy(revision = 13),
            ),
            Expected(
                fixture = "outcome-pass-required.json",
                outcome = AirportFlightsResponse.OUTCOME_PASS_REQUIRED,
                pass = AirportFlightsPassDto(scope = "s-3", revision = 14),
            ),
            Expected(
                fixture = "outcome-access-changed.json",
                outcome = AirportFlightsResponse.OUTCOME_ACCESS_CHANGED,
                pass = AirportFlightsPassDto(scope = "s-4", revision = 1),
            ),
            Expected(
                fixture = "outcome-unknown.json",
                outcome = "airport_closed",
                pass = withPass.copy(revision = 15),
            ),
        ).forEach { expected ->
            val response = answer(expected.fixture)
            withClue(expected.fixture) {
                response.outcome shouldBe expected.outcome
                response.pass shouldBe expected.pass
                response.replayed shouldBe expected.replayed
                response.charged shouldBe expected.charged
                response.usage.bucket shouldBe "SEARCH"
                response.evaluationId.shouldBeNull()
                response.evaluatedAt.shouldBeNull()
                response.expiresAt.shouldBeNull()
                response.evaluationExpiresAt.shouldBeNull()
                response.operationExpiresAt.shouldBeNull()
                response.refreshAfter.shouldBeNull()
                response.airport.shouldBeNull()
                response.window.shouldBeNull()
                response.sources.shouldBeEmpty()
                response.groups.shouldBeEmpty()
                response.entries.shouldBeEmpty()
                response.aircraft.shouldBeEmpty()
            }
        }
    }

    @Test
    fun `an unknown airport surfaces its code`() = runTest {
        mockWebServer.enqueue(problem(400, ServerCodes.UNKNOWN_AIRPORT))

        val error = shouldThrow<ServerApiException> { endpoint.airportFlights("token", loadRequest) }

        error.status shouldBe 400
        error.code shouldBe ServerCodes.UNKNOWN_AIRPORT
        error.retryAfterSeconds.shouldBeNull()
    }

    @Test
    fun `a server without the endpoint surfaces as 404`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(404))

        val error = shouldThrow<ServerApiException> { endpoint.airportFlights("token", loadRequest) }

        error.status shouldBe 404
        error.code shouldBe "http_404"
        error.retryAfterSeconds.shouldBeNull()
    }

    @Test
    fun `a reused operation id surfaces as a mismatch`() = runTest {
        mockWebServer.enqueue(problem(409, ServerCodes.OPERATION_MISMATCH))

        val error = shouldThrow<ServerApiException> { endpoint.airportFlights("token", loadRequest) }

        error.status shouldBe 409
        error.code shouldBe ServerCodes.OPERATION_MISMATCH
        error.retryAfterSeconds.shouldBeNull()
    }

    @Test
    fun `a refresh too soon carries the retry delay from the header`() = runTest {
        mockWebServer.enqueue(problem(429, ServerCodes.REFRESH_TOO_SOON, retryAfter = 45))

        val error = shouldThrow<ServerApiException> { endpoint.airportFlights("token", loadRequest) }

        error.status shouldBe 429
        error.code shouldBe ServerCodes.REFRESH_TOO_SOON
        error.retryAfterSeconds shouldBe 45L
    }

    @Test
    fun `unavailable flights carry the retry delay from the header`() = runTest {
        mockWebServer.enqueue(problem(503, ServerCodes.FLIGHTS_UNAVAILABLE, retryAfter = 30))

        val error = shouldThrow<ServerApiException> { endpoint.airportFlights("token", loadRequest) }

        error.status shouldBe 503
        error.code shouldBe ServerCodes.FLIGHTS_UNAVAILABLE
        error.retryAfterSeconds shouldBe 30L
    }
}
