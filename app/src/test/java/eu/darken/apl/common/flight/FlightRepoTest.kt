package eu.darken.apl.common.flight

import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.flight.db.AirportEntity
import eu.darken.apl.common.flight.db.FlightDatabase
import eu.darken.apl.common.flight.db.FlightRouteEntity
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.server.api.RouteAirportDto
import eu.darken.apl.server.api.RouteDto
import eu.darken.apl.server.api.RouteLookupRequest
import eu.darken.apl.server.api.RouteLookupResponse
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.ServerCodes
import eu.darken.apl.server.api.ServerEndpoint
import eu.darken.apl.server.session.SessionManager
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.util.Locale

class FlightRepoTest : BaseTest() {

    private lateinit var flightDatabase: FlightDatabase
    private lateinit var endpoint: ServerEndpoint
    private lateinit var sessionManager: SessionManager
    private lateinit var accessRepo: AccessRepo
    private lateinit var accessState: MutableStateFlow<AccessState?>

    @BeforeEach
    fun setup() {
        flightDatabase = mockk(relaxUnitFun = true)
        endpoint = mockk()
        sessionManager = mockk()
        accessRepo = mockk()
        accessState = MutableStateFlow(null)

        coEvery { flightDatabase.getLatestByHex(any()) } returns null
        every { flightDatabase.airportByIcao(any()) } returns flowOf(null)
        coEvery { sessionManager.authed<Any>(any()) } coAnswers {
            firstArg<suspend (String) -> Any>().invoke("token")
        }
        every { accessRepo.state } returns accessState
        coEvery { accessRepo.awaitLoaded() } just Runs
    }

    private fun TestScope.createRepo(): FlightRepo {
        val clock = object : MonotonicClock {
            override fun elapsed(): Long = testScheduler.currentTime
        }
        return FlightRepo(
            flightDatabase = flightDatabase,
            endpoint = endpoint,
            sessionManager = sessionManager,
            accessRepo = accessRepo,
            gate = RouteLookupGate(clock),
            appScope = backgroundScope,
        )
    }

    private fun request(callsign: String, hex: String = "a1b2c3") = RouteLookupRequest(hex, callsign)

    private fun found(callsign: String = "BAW256") = RouteLookupResponse(
        serverTime = 0L,
        callsign = callsign,
        status = RouteLookupResponse.STATUS_FOUND,
        route = RouteDto(
            source = RouteDto.SOURCE_VRS,
            origin = RouteAirportDto(
                icao = "VIDP",
                iata = "DEL",
                name = "Indira Gandhi International Airport",
                country = "IN",
                latitude = 28.5665,
                longitude = 77.1031,
            ),
            destination = RouteAirportDto(
                icao = "EGLL",
                iata = "LHR",
                name = "London Heathrow Airport",
                country = "GB",
                latitude = 51.4706,
                longitude = -0.461941,
            ),
        ),
    )

    private fun none(callsign: String = "BAW256") = RouteLookupResponse(
        serverTime = 0L,
        callsign = callsign,
        status = RouteLookupResponse.STATUS_NONE,
    )

    private fun cachedEntity(callsign: String = "BAW256", age: Duration = Duration.ofMinutes(10)) = FlightRouteEntity(
        id = 1,
        aircraftHex = "A1B2C3",
        callsign = callsign,
        originIcao = "VIDP",
        destinationIcao = "EGLL",
        seenAt = Instant.now().minus(age),
        fetchedAt = Instant.now().minus(age),
    )

    private fun noRouteRequests() = coVerify(exactly = 0) { endpoint.route(any(), any()) }

    private fun nothingPersisted() {
        coVerify(exactly = 0) { flightDatabase.upsertAirport(any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { flightDatabase.insertRoute(any()) }
    }

    @Test
    fun `found persists airports with country code and the route`() = runTest {
        coEvery { endpoint.route("token", request("BAW256")) } returns found()
        val repo = createRepo()

        val result = repo.lookupResult("A1B2C3", "BAW256")

        result.shouldBeInstanceOf<RouteLookupResult.Found>()
        result.route.callsign shouldBe "BAW256"
        result.route.origin!!.icaoCode shouldBe "VIDP"
        result.route.destination!!.icaoCode shouldBe "EGLL"
        coVerify {
            flightDatabase.upsertAirport("VIDP", "DEL", "Indira Gandhi International Airport", "IN", 28.5665, 77.1031)
        }
        coVerify {
            flightDatabase.upsertAirport("EGLL", "LHR", "London Heathrow Airport", "GB", 51.4706, -0.461941)
        }
        val entity = slot<FlightRouteEntity>()
        coVerify { flightDatabase.insertRoute(capture(entity)) }
        entity.captured.aircraftHex shouldBe "A1B2C3"
        entity.captured.callsign shouldBe "BAW256"
        entity.captured.originIcao shouldBe "VIDP"
        entity.captured.destinationIcao shouldBe "EGLL"
    }

    @Test
    fun `found route reaches the caller with stored airport details`() = runTest {
        every { flightDatabase.airportByIcao("VIDP") } returns flowOf(
            AirportEntity(
                icaoCode = "VIDP", iataCode = "DEL", name = null, country = "IN",
                latitude = 28.5665, longitude = 77.1031,
            )
        )
        coEvery { endpoint.route("token", request("BAW256")) } returns found()
        val defaultLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        try {
            val route = createRepo().lookup("A1B2C3", "BAW256")

            route.shouldNotBeNull()
            route.origin!!.latitude shouldBe 28.5665
            route.origin!!.countryName shouldBe "India"
            route.destination!!.latitude shouldBe null
        } finally {
            Locale.setDefault(defaultLocale)
        }
    }

    @Test
    fun `invalid airport coordinates are stored as none`() = runTest {
        val route = found().route!!
        val response = found().copy(route = route.copy(destination = route.destination.copy(longitude = 200.0)))
        coEvery { endpoint.route("token", request("BAW256")) } returns response

        createRepo().lookup("A1B2C3", "BAW256")

        coVerify { flightDatabase.upsertAirport("EGLL", "LHR", "London Heathrow Airport", "GB", null, null) }
    }

    @Test
    fun `none persists nothing`() = runTest {
        coEvery { endpoint.route("token", request("BAW256")) } returns none()
        val repo = createRepo()

        repo.lookupResult("A1B2C3", "BAW256") shouldBe RouteLookupResult.None
        repo.lookup("A1B2C3", "BAW256").shouldBeNull()

        nothingPersisted()
    }

    @Test
    fun `found without a route is treated as none`() = runTest {
        coEvery { endpoint.route("token", request("BAW256")) } returns found().copy(route = null)

        createRepo().lookupResult("A1B2C3", "BAW256") shouldBe RouteLookupResult.None

        nothingPersisted()
    }

    @Test
    fun `none is remembered for 10 minutes`() = runTest {
        coEvery { endpoint.route("token", request("BAW256")) } returns none()
        val repo = createRepo()

        repo.lookupResult("A1B2C3", "BAW256") shouldBe RouteLookupResult.None
        advanceTimeBy(RouteLookupGate.NONE_TTL_MS - 1)
        repo.lookupResult("A1B2C3", " baw256 ") shouldBe RouteLookupResult.None
        coVerify(exactly = 1) { endpoint.route("token", request("BAW256")) }

        advanceTimeBy(1)
        coEvery { endpoint.route("token", request("BAW256")) } returns found()
        repo.lookupResult("A1B2C3", "BAW256").shouldBeInstanceOf<RouteLookupResult.Found>()
        coVerify(exactly = 2) { endpoint.route("token", request("BAW256")) }
    }

    @Test
    fun `remembered none is per hex and callsign only`() = runTest {
        coEvery { endpoint.route("token", request("BAW256")) } returns none()
        coEvery { endpoint.route("token", request("BAW257")) } returns found("BAW257")
        coEvery { endpoint.route("token", request("BAW256", hex = "d4e5f6")) } returns found()
        val repo = createRepo()

        repo.lookupResult("A1B2C3", "BAW256") shouldBe RouteLookupResult.None

        repo.lookupResult("A1B2C3", "BAW257").shouldBeInstanceOf<RouteLookupResult.Found>()
        repo.lookupResult("D4E5F6", "BAW256").shouldBeInstanceOf<RouteLookupResult.Found>()
    }

    @Test
    fun `server error persists nothing and sets no hold`() = runTest {
        coEvery { endpoint.route("token", request("BAW256")) } throws ServerApiException(ServerCodes.INTERNAL, 500)
        val repo = createRepo()

        repo.lookupResult("A1B2C3", "BAW256") shouldBe RouteLookupResult.Unavailable(5_000L)
        repo.lookup("A1B2C3", "BAW256").shouldBeNull()

        nothingPersisted()
        coVerify(exactly = 2) { endpoint.route("token", request("BAW256")) }
    }

    @Test
    fun `IOException from authed gives null and sets no hold`() = runTest {
        coEvery { sessionManager.authed<Any>(any()) } throws IOException("offline")
        val repo = createRepo()

        repo.lookup("A1B2C3", "BAW256").shouldBeNull()
        repo.lookupResult("A1B2C3", "BAW257") shouldBe RouteLookupResult.Unavailable(5_000L)

        coEvery { sessionManager.authed<Any>(any()) } coAnswers {
            firstArg<suspend (String) -> Any>().invoke("token")
        }
        coEvery { endpoint.route("token", request("BAW256")) } returns found()
        repo.lookup("A1B2C3", "BAW256").shouldNotBeNull()
        testScheduler.currentTime shouldBe 0L
    }

    @Test
    fun `hold code thrown by authed itself sets no hold`() = runTest {
        coEvery { sessionManager.authed<Any>(any()) } throws ServerApiException(
            ServerCodes.QUOTA_EXCEEDED, 429, retryAfterSeconds = 600,
        )
        val repo = createRepo()

        repo.lookupResult("A1B2C3", "BAW256") shouldBe RouteLookupResult.Unavailable(5_000L)

        coEvery { sessionManager.authed<Any>(any()) } coAnswers {
            firstArg<suspend (String) -> Any>().invoke("token")
        }
        coEvery { endpoint.route("token", request("BAW256")) } returns found()
        repo.lookupResult("A1B2C3", "BAW256").shouldBeInstanceOf<RouteLookupResult.Found>()
        testScheduler.currentTime shouldBe 0L
    }

    @Test
    fun `cache read failure gives null`() = runTest {
        coEvery { flightDatabase.getLatestByHex(any()) } throws IllegalStateException("db closed")

        createRepo().lookup("A1B2C3", "BAW256").shouldBeNull()

        noRouteRequests()
    }

    @Test
    fun `cancellation of the request propagates`() = runTest {
        coEvery { endpoint.route("token", request("BAW256")) } throws CancellationException("gone")

        shouldThrow<CancellationException> { createRepo().lookupResult("A1B2C3", "BAW256") }
    }

    @Test
    fun `cancelled caller does not continue with a result`() = runTest {
        coEvery { endpoint.route("token", request("BAW256")) } coAnswers { awaitCancellation() }
        val repo = createRepo()
        var continued = false

        val job = launch {
            repo.lookup("A1B2C3", "BAW256")
            continued = true
        }
        runCurrent()
        job.cancel()
        job.join()

        continued shouldBe false
    }

    @Test
    fun `app-wide hold expires`() = runTest {
        coEvery { endpoint.route("token", request("BAW256")) } throws ServerApiException(
            ServerCodes.INSTALLATION_RATE_EXCEEDED, 429, retryAfterSeconds = 60,
        )
        coEvery { endpoint.route("token", request("BAW257")) } returns found("BAW257")
        val repo = createRepo()

        repo.lookupResult("A1B2C3", "BAW256") shouldBe RouteLookupResult.Unavailable(60_000L)
        repo.lookupResult("A1B2C3", "BAW257") shouldBe RouteLookupResult.Unavailable(60_000L)
        coVerify(exactly = 0) { endpoint.route(any(), request("BAW257")) }

        advanceTimeBy(60_000L)
        repo.lookupResult("A1B2C3", "BAW257").shouldBeInstanceOf<RouteLookupResult.Found>()
    }

    @Test
    fun `app-wide hold falls back to 5 seconds without a usable hint`() = runTest {
        coEvery { endpoint.route("token", request("BAW256")) } throws ServerApiException(
            ServerCodes.UPSTREAM_UNAVAILABLE, 503, retryAfterSeconds = 0,
        )

        createRepo().lookupResult("A1B2C3", "BAW256") shouldBe RouteLookupResult.Unavailable(5_000L)
    }

    @Test
    fun `a later shorter hint does not shorten the hold`() = runTest {
        val longAnswer = CompletableDeferred<Unit>()
        val shortAnswer = CompletableDeferred<Unit>()
        coEvery { endpoint.route("token", request("BAW256")) } coAnswers {
            longAnswer.await()
            throw ServerApiException(ServerCodes.QUOTA_EXCEEDED, 429, retryAfterSeconds = 60)
        }
        coEvery { endpoint.route("token", request("BAW257")) } coAnswers {
            shortAnswer.await()
            throw ServerApiException(ServerCodes.QUOTA_EXCEEDED, 429, retryAfterSeconds = 2)
        }
        val repo = createRepo()

        val long = async { repo.lookupResult("A1B2C3", "BAW256") }
        val short = async { repo.lookupResult("A1B2C3", "BAW257") }
        runCurrent()
        longAnswer.complete(Unit)
        long.await() shouldBe RouteLookupResult.Unavailable(60_000L)
        shortAnswer.complete(Unit)
        short.await() shouldBe RouteLookupResult.Unavailable(60_000L)

        advanceTimeBy(2_001L)
        repo.lookupResult("A1B2C3", "BAW258") shouldBe RouteLookupResult.Unavailable(60_000L)
        coVerify(exactly = 0) { endpoint.route(any(), request("BAW258")) }
    }

    @Test
    fun `an earlier short hint is extended by a later long one`() = runTest {
        val shortAnswer = CompletableDeferred<Unit>()
        val longAnswer = CompletableDeferred<Unit>()
        coEvery { endpoint.route("token", request("BAW256")) } coAnswers {
            shortAnswer.await()
            throw ServerApiException(ServerCodes.UPSTREAM_ERROR, 502, retryAfterSeconds = 2)
        }
        coEvery { endpoint.route("token", request("BAW257")) } coAnswers {
            longAnswer.await()
            throw ServerApiException(ServerCodes.INSTALLATION_RATE_EXCEEDED, 429, retryAfterSeconds = 60)
        }
        val repo = createRepo()

        val short = async { repo.lookupResult("A1B2C3", "BAW256") }
        val long = async { repo.lookupResult("A1B2C3", "BAW257") }
        runCurrent()
        shortAnswer.complete(Unit)
        short.await() shouldBe RouteLookupResult.Unavailable(2_000L)
        longAnswer.complete(Unit)
        long.await() shouldBe RouteLookupResult.Unavailable(60_000L)

        advanceTimeBy(2_001L)
        repo.lookupResult("A1B2C3", "BAW258") shouldBe RouteLookupResult.Unavailable(60_000L)
        coVerify(exactly = 0) { endpoint.route(any(), request("BAW258")) }
    }

    @Test
    fun `a lookup waits out a short hold, then sends`() = runTest {
        coEvery { endpoint.route("token", request("BAW256")) } throws ServerApiException(
            ServerCodes.DATABASE_UNAVAILABLE, 503, retryAfterSeconds = 10,
        )
        coEvery { endpoint.route("token", request("BAW257")) } returns found("BAW257")
        val repo = createRepo()

        repo.lookupResult("A1B2C3", "BAW256") shouldBe RouteLookupResult.Unavailable(10_000L)
        repo.lookupResult("A1B2C3", "BAW257").shouldBeInstanceOf<RouteLookupResult.Found>()

        testScheduler.currentTime shouldBe 10_000L
        coVerify(exactly = 1) { endpoint.route("token", request("BAW257")) }
    }

    @Test
    fun `a hold over 30 seconds returns unavailable without a request`() = runTest {
        coEvery { endpoint.route("token", request("BAW256")) } throws ServerApiException(
            ServerCodes.REQUEST_TIMEOUT, 503, retryAfterSeconds = 31,
        )
        val repo = createRepo()

        repo.lookupResult("A1B2C3", "BAW256") shouldBe RouteLookupResult.Unavailable(31_000L)
        repo.lookupResult("A1B2C3", "BAW257") shouldBe RouteLookupResult.Unavailable(31_000L)

        testScheduler.currentTime shouldBe 0L
        coVerify(exactly = 0) { endpoint.route(any(), request("BAW257")) }
    }

    @Test
    fun `a lookup paused inside authed does not send after another lookup set a hold`() = runTest {
        val sessionReady = CompletableDeferred<Unit>()
        var authedCalls = 0
        coEvery { sessionManager.authed<Any>(any()) } coAnswers {
            if (authedCalls++ == 0) sessionReady.await()
            firstArg<suspend (String) -> Any>().invoke("token")
        }
        coEvery { endpoint.route("token", request("BAW257")) } throws ServerApiException(
            ServerCodes.INSTALLATION_RATE_EXCEEDED, 429, retryAfterSeconds = 60,
        )
        val repo = createRepo()

        val paused = async { repo.lookupResult("A1B2C3", "BAW256") }
        runCurrent()
        repo.lookupResult("A1B2C3", "BAW257") shouldBe RouteLookupResult.Unavailable(60_000L)
        sessionReady.complete(Unit)

        paused.await() shouldBe RouteLookupResult.Unavailable(60_000L)
        coVerify(exactly = 0) { endpoint.route(any(), request("BAW256")) }
    }

    @Test
    fun `route_lookup_unavailable holds only that callsign`() = runTest {
        coEvery { endpoint.route("token", request("BAW256")) } throws ServerApiException(
            ServerCodes.ROUTE_LOOKUP_UNAVAILABLE, 503, retryAfterSeconds = 30,
        )
        coEvery { endpoint.route("token", request("BAW257")) } returns found("BAW257")
        val repo = createRepo()

        repo.lookupResult("A1B2C3", "BAW256") shouldBe RouteLookupResult.Unavailable(30_000L)
        repo.lookupResult("A1B2C3", "BAW257").shouldBeInstanceOf<RouteLookupResult.Found>()
        testScheduler.currentTime shouldBe 0L

        advanceTimeBy(29_000L)
        repo.lookupResult("A1B2C3", "BAW256") shouldBe RouteLookupResult.Unavailable(30_000L)
        coVerify(exactly = 1) { endpoint.route("token", request("BAW256")) }

        advanceTimeBy(1_000L)
        coEvery { endpoint.route("token", request("BAW256")) } returns found()
        repo.lookupResult("A1B2C3", "BAW256").shouldBeInstanceOf<RouteLookupResult.Found>()
        coVerify(exactly = 2) { endpoint.route("token", request("BAW256")) }
    }

    @Test
    fun `route_lookup_unavailable without a hint holds for 5 seconds`() = runTest {
        coEvery { endpoint.route("token", request("BAW256")) } throws ServerApiException(
            ServerCodes.ROUTE_LOOKUP_UNAVAILABLE, 503,
        )

        createRepo().lookupResult("A1B2C3", "BAW256") shouldBe RouteLookupResult.Unavailable(5_000L)
    }

    @Test
    fun `invalid_request is remembered per hex and callsign only`() = runTest {
        coEvery { endpoint.route("token", request("BAW256")) } throws ServerApiException(
            ServerCodes.INVALID_REQUEST, 400,
        )
        coEvery { endpoint.route("token", request("BAW257")) } returns found("BAW257")
        coEvery { endpoint.route("token", request("BAW256", hex = "d4e5f6")) } returns found()
        val repo = createRepo()

        repo.lookupResult("A1B2C3", "BAW256") shouldBe RouteLookupResult.None
        repo.lookupResult("A1B2C3", "BAW256") shouldBe RouteLookupResult.None
        coVerify(exactly = 1) { endpoint.route("token", request("BAW256")) }

        repo.lookupResult("A1B2C3", "BAW257").shouldBeInstanceOf<RouteLookupResult.Found>()
        repo.lookupResult("D4E5F6", "BAW256").shouldBeInstanceOf<RouteLookupResult.Found>()
    }

    @Test
    fun `other definite client errors give no route`() = runTest {
        coEvery { endpoint.route("token", request("BAW256")) } throws ServerApiException(
            ServerCodes.INSTALLATION_RESTRICTED, 403,
        )

        createRepo().lookupResult("A1B2C3", "BAW256") shouldBe RouteLookupResult.None
    }

    @Test
    fun `identical concurrent lookups share one request`() = runTest {
        val answer = CompletableDeferred<RouteLookupResponse>()
        coEvery { endpoint.route("token", request("BAW256")) } coAnswers { answer.await() }
        val repo = createRepo()

        val first = async { repo.lookupResult("A1B2C3", "BAW256") }
        val second = async { repo.lookupResult("A1B2C3", "baw256 ") }
        runCurrent()
        answer.complete(found())

        val firstResult = first.await()
        firstResult.shouldBeInstanceOf<RouteLookupResult.Found>()
        second.await() shouldBe firstResult
        coVerify(exactly = 1) { endpoint.route(any(), any()) }
    }

    @Test
    fun `loaded policy without route lookup makes no request`() = runTest {
        accessState.value = mockk<AccessState> { every { offersRouteLookup } returns false }

        createRepo().lookupResult("A1B2C3", "BAW256") shouldBe RouteLookupResult.None

        coVerify { accessRepo.awaitLoaded() }
        noRouteRequests()
    }

    @Test
    fun `missing policy still makes the request`() = runTest {
        coEvery { endpoint.route("token", request("BAW256")) } returns found()

        createRepo().lookupResult("A1B2C3", "BAW256").shouldBeInstanceOf<RouteLookupResult.Found>()

        coVerify { accessRepo.awaitLoaded() }
    }

    @Test
    fun `policy offering route lookup makes the request`() = runTest {
        accessState.value = mockk<AccessState> { every { offersRouteLookup } returns true }
        coEvery { endpoint.route("token", request("BAW256")) } returns found()

        createRepo().lookupResult("A1B2C3", "BAW256").shouldBeInstanceOf<RouteLookupResult.Found>()
    }

    @Test
    fun `locally invalid hex or callsign makes no request`() = runTest {
        val repo = createRepo()

        repo.lookupResult("XYZ123", "BAW256") shouldBe RouteLookupResult.None
        repo.lookupResult("A1B2C", "BAW256") shouldBe RouteLookupResult.None
        repo.lookupResult("A1B2C3", "BAW-256") shouldBe RouteLookupResult.None
        repo.lookupResult("A1B2C3", "BAW256789") shouldBe RouteLookupResult.None

        noRouteRequests()
    }

    @Test
    fun `non-icao hex marker is accepted`() = runTest {
        coEvery { endpoint.route("token", request("BAW256", hex = "~a1b2c3")) } returns found()

        createRepo().lookupResult("~A1B2C3", "BAW256").shouldBeInstanceOf<RouteLookupResult.Found>()
    }

    @Test
    fun `null or blank callsign returns null without a request`() = runTest {
        val repo = createRepo()

        repo.lookup("A1B2C3", null).shouldBeNull()
        repo.lookup("A1B2C3", "   ").shouldBeNull()

        noRouteRequests()
    }

    @Test
    fun `callsign is normalized for the request and the stored route`() = runTest {
        coEvery { endpoint.route("token", request("BAW256")) } returns found()

        createRepo().lookup("A1B2C3", "  baw256 ").shouldNotBeNull()

        val entity = slot<FlightRouteEntity>()
        coVerify { flightDatabase.insertRoute(capture(entity)) }
        entity.captured.callsign shouldBe "BAW256"
    }

    @Test
    fun `padded lowercase callsign hits a cache entry stored uppercase`() = runTest {
        coEvery { flightDatabase.getLatestByHex("A1B2C3") } returns cachedEntity()

        createRepo().lookupResult("A1B2C3", " baw256 ").shouldBeInstanceOf<RouteLookupResult.Found>()

        noRouteRequests()
    }

    @Test
    fun `cache hit under an hour makes no request`() = runTest {
        coEvery { flightDatabase.getLatestByHex("A1B2C3") } returns cachedEntity(age = Duration.ofMinutes(59))

        val route = createRepo().lookup("A1B2C3", "BAW256")

        route.shouldNotBeNull()
        route.origin!!.icaoCode shouldBe "VIDP"
        noRouteRequests()
    }

    @Test
    fun `cache entry over an hour old is fetched again`() = runTest {
        coEvery { flightDatabase.getLatestByHex("A1B2C3") } returns cachedEntity(age = Duration.ofMinutes(61))
        coEvery { endpoint.route("token", request("BAW256")) } returns found()

        createRepo().lookup("A1B2C3", "BAW256").shouldNotBeNull()

        coVerify(exactly = 1) { endpoint.route("token", request("BAW256")) }
    }

    @Test
    fun `cache entry for another callsign is fetched again`() = runTest {
        coEvery { flightDatabase.getLatestByHex("A1B2C3") } returns cachedEntity(callsign = "BAW255")
        coEvery { endpoint.route("token", request("BAW256")) } returns found()

        createRepo().lookup("A1B2C3", "BAW256").shouldNotBeNull()

        coVerify(exactly = 1) { endpoint.route("token", request("BAW256")) }
    }
}
