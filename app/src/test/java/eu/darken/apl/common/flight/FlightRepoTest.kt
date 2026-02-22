package eu.darken.apl.common.flight

import eu.darken.apl.common.flight.api.AdsbdbApi
import eu.darken.apl.common.flight.api.AdsbdbEndpoint
import eu.darken.apl.common.flight.api.HexdbApi
import eu.darken.apl.common.flight.api.HexdbEndpoint
import eu.darken.apl.common.flight.db.AirportEntity
import eu.darken.apl.common.flight.db.FlightDatabase
import eu.darken.apl.common.flight.db.FlightRouteEntity
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Instant

class FlightRepoTest : BaseTest() {

    private lateinit var flightDatabase: FlightDatabase
    private lateinit var adsbdbEndpoint: AdsbdbEndpoint
    private lateinit var hexdbEndpoint: HexdbEndpoint
    private lateinit var repo: FlightRepo

    @BeforeEach
    fun setup() {
        flightDatabase = mockk(relaxUnitFun = true)
        adsbdbEndpoint = mockk()
        hexdbEndpoint = mockk()

        coEvery { flightDatabase.getLatestByHex(any()) } returns null
        every { flightDatabase.airportByIcao(any()) } returns flowOf(null)

        repo = FlightRepo(
            flightDatabase = flightDatabase,
            adsbdbEndpoint = adsbdbEndpoint,
            hexdbEndpoint = hexdbEndpoint,
            appScope = mockk(relaxed = true),
        )
    }

    @Test
    fun `adsbdb success persists airports and route, hexdb not called`() = runTest {
        coEvery { adsbdbEndpoint.getByCallsign("BAW256") } returns AdsbdbApi.FlightRouteData(
            callsign = "BAW256",
            origin = AdsbdbApi.AirportData(
                icaoCode = "VIDP",
                iataCode = "DEL",
                name = "Indira Gandhi International Airport",
                countryName = "India",
            ),
            destination = AdsbdbApi.AirportData(
                icaoCode = "EGLL",
                iataCode = "LHR",
                name = "London Heathrow Airport",
                countryName = "United Kingdom",
            ),
        )

        val result = repo.lookup("A1B2C3", "BAW256")

        result.shouldNotBeNull()
        result.callsign shouldBe "BAW256"
        result.origin.shouldNotBeNull()
        result.origin!!.icaoCode shouldBe "VIDP"
        result.destination.shouldNotBeNull()
        result.destination!!.icaoCode shouldBe "EGLL"

        coVerify { flightDatabase.upsertAirport("VIDP", "DEL", "Indira Gandhi International Airport", "India") }
        coVerify { flightDatabase.upsertAirport("EGLL", "LHR", "London Heathrow Airport", "United Kingdom") }
        coVerify { flightDatabase.insertRoute(any()) }
        coVerify(exactly = 0) { hexdbEndpoint.getByCallsign(any()) }
    }

    @Test
    fun `adsbdb fails, hexdb fallback succeeds`() = runTest {
        coEvery { adsbdbEndpoint.getByCallsign("BAW256") } throws RuntimeException("timeout")
        coEvery { hexdbEndpoint.getByCallsign("BAW256") } returns HexdbApi.RouteResponse(
            flight = "BAW256",
            route = "VIDP-EGLL",
        )

        val result = repo.lookup("A1B2C3", "BAW256")

        result.shouldNotBeNull()
        result.origin.shouldNotBeNull()
        result.origin!!.icaoCode shouldBe "VIDP"
        result.destination.shouldNotBeNull()
        result.destination!!.icaoCode shouldBe "EGLL"

        coVerify { flightDatabase.upsertAirport("VIDP", null, null, null) }
        coVerify { flightDatabase.upsertAirport("EGLL", null, null, null) }
    }

    @Test
    fun `both APIs fail returns null`() = runTest {
        coEvery { adsbdbEndpoint.getByCallsign("BAW256") } throws RuntimeException("timeout")
        coEvery { hexdbEndpoint.getByCallsign("BAW256") } throws RuntimeException("timeout")

        val result = repo.lookup("A1B2C3", "BAW256")

        result.shouldBeNull()
        coVerify(exactly = 0) { flightDatabase.insertRoute(any()) }
    }

    @Test
    fun `cache hit with same callsign skips API calls`() = runTest {
        val cached = FlightRouteEntity(
            id = 1,
            aircraftHex = "A1B2C3",
            callsign = "BAW256",
            originIcao = "VIDP",
            destinationIcao = "EGLL",
            seenAt = Instant.now(),
            fetchedAt = Instant.now(),
        )
        coEvery { flightDatabase.getLatestByHex("A1B2C3") } returns cached

        val result = repo.lookup("A1B2C3", "BAW256")

        result.shouldNotBeNull()
        coVerify(exactly = 0) { adsbdbEndpoint.getByCallsign(any()) }
        coVerify(exactly = 0) { hexdbEndpoint.getByCallsign(any()) }
    }

    @Test
    fun `callsign change invalidates cache and re-fetches`() = runTest {
        val cached = FlightRouteEntity(
            id = 1,
            aircraftHex = "A1B2C3",
            callsign = "BAW256",
            originIcao = "VIDP",
            destinationIcao = "EGLL",
            seenAt = Instant.now(),
            fetchedAt = Instant.now(),
        )
        coEvery { flightDatabase.getLatestByHex("A1B2C3") } returns cached
        coEvery { adsbdbEndpoint.getByCallsign("BAW257") } returns AdsbdbApi.FlightRouteData(
            callsign = "BAW257",
            origin = AdsbdbApi.AirportData(icaoCode = "EGLL"),
            destination = AdsbdbApi.AirportData(icaoCode = "KJFK"),
        )

        val result = repo.lookup("A1B2C3", "BAW257")

        result.shouldNotBeNull()
        result.origin!!.icaoCode shouldBe "EGLL"
        result.destination!!.icaoCode shouldBe "KJFK"
        coVerify { adsbdbEndpoint.getByCallsign("BAW257") }
    }

    @Test
    fun `null callsign returns null immediately`() = runTest {
        val result = repo.lookup("A1B2C3", null)

        result.shouldBeNull()
        coVerify(exactly = 0) { adsbdbEndpoint.getByCallsign(any()) }
    }

    @Test
    fun `blank callsign returns null immediately`() = runTest {
        val result = repo.lookup("A1B2C3", "   ")

        result.shouldBeNull()
        coVerify(exactly = 0) { adsbdbEndpoint.getByCallsign(any()) }
    }

    @Test
    fun `callsign is trimmed before lookup`() = runTest {
        coEvery { adsbdbEndpoint.getByCallsign("BAW256") } returns AdsbdbApi.FlightRouteData(
            callsign = "BAW256",
            origin = AdsbdbApi.AirportData(icaoCode = "VIDP"),
            destination = AdsbdbApi.AirportData(icaoCode = "EGLL"),
        )

        val result = repo.lookup("A1B2C3", "  BAW256  ")

        result.shouldNotBeNull()
        coVerify { adsbdbEndpoint.getByCallsign("BAW256") }
    }

    @Test
    fun `hexdb data does not overwrite richer adsbdb airport data`() = runTest {
        coEvery { adsbdbEndpoint.getByCallsign("BAW256") } throws RuntimeException("timeout")
        coEvery { hexdbEndpoint.getByCallsign("BAW256") } returns HexdbApi.RouteResponse(
            flight = "BAW256",
            route = "VIDP-EGLL",
        )

        repo.lookup("A1B2C3", "BAW256")

        coVerify { flightDatabase.upsertAirport("VIDP", null, null, null) }
        coVerify { flightDatabase.upsertAirport("EGLL", null, null, null) }
    }
}
