package eu.darken.apl.airport.core

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import eu.darken.apl.common.flight.Airport as RouteAirport

class AirportRefTest : BaseTest() {

    @Test
    fun `strings are trimmed and blanks are absent`() {
        val ref = AirportRef.create(
            icao = " EDDF ",
            iata = "  ",
            name = " Frankfurt am Main Airport\t",
            countryName = "",
        ).shouldNotBeNull()

        ref.icao shouldBe "EDDF"
        ref.iata.shouldBeNull()
        ref.name shouldBe "Frankfurt am Main Airport"
        ref.countryName.shouldBeNull()
    }

    @Test
    fun `without a usable code there is no ref`() {
        AirportRef.create(icao = null, iata = null, name = "Somewhere").shouldBeNull()
        AirportRef.create(icao = " ", iata = "\t", name = "Somewhere").shouldBeNull()
    }

    @Test
    fun `either code alone is enough`() {
        AirportRef.create(icao = "EDDF", iata = null).shouldNotBeNull().lookupTerm shouldBe "EDDF"
        AirportRef.create(icao = null, iata = "FRA").shouldNotBeNull().lookupTerm shouldBe "FRA"
    }

    @Test
    fun `the ICAO code is the lookup term when both are known`() {
        AirportRef.create(icao = "EDDF", iata = "FRA").shouldNotBeNull().lookupTerm shouldBe "EDDF"
    }

    @Test
    fun `valid coordinates are kept`() {
        val ref = AirportRef.create(icao = "EDDF", iata = "FRA", latitude = 50.0333, longitude = 8.5706).shouldNotBeNull()

        ref.latitude shouldBe 50.0333
        ref.longitude shouldBe 8.5706
    }

    @Test
    fun `half a position is dropped`() {
        AirportRef.create(icao = "EDDF", iata = null, latitude = 50.0333, longitude = null).shouldNotBeNull().apply {
            latitude.shouldBeNull()
            longitude.shouldBeNull()
        }
        AirportRef.create(icao = "EDDF", iata = null, latitude = null, longitude = 8.5706).shouldNotBeNull().apply {
            latitude.shouldBeNull()
            longitude.shouldBeNull()
        }
    }

    @Test
    fun `non-finite coordinates are dropped`() {
        listOf(
            Double.NaN to 8.5,
            50.0 to Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY to 8.5,
        ).forEach { (lat, lon) ->
            AirportRef.create(icao = "EDDF", iata = null, latitude = lat, longitude = lon).shouldNotBeNull().apply {
                latitude.shouldBeNull()
                longitude.shouldBeNull()
            }
        }
    }

    @Test
    fun `out of range coordinates are dropped`() {
        listOf(
            90.1 to 8.5,
            -90.1 to 8.5,
            50.0 to 180.1,
            50.0 to -180.1,
        ).forEach { (lat, lon) ->
            AirportRef.create(icao = "EDDF", iata = null, latitude = lat, longitude = lon).shouldNotBeNull().apply {
                latitude.shouldBeNull()
                longitude.shouldBeNull()
            }
        }
    }

    @Test
    fun `the edges of the range are valid`() {
        AirportRef.create(icao = "NZSP", iata = null, latitude = -90.0, longitude = 180.0).shouldNotBeNull().apply {
            latitude shouldBe -90.0
            longitude shouldBe 180.0
        }
    }

    @Test
    fun `a route airport becomes a ref`() {
        val route = RouteAirport(
            icaoCode = "EDDF",
            iataCode = "FRA",
            name = "Frankfurt am Main Airport",
            countryName = "Germany",
            latitude = 50.0333,
            longitude = 8.5706,
        )

        route.toAirportRef() shouldBe AirportRef.create(
            icao = "EDDF",
            iata = "FRA",
            name = "Frankfurt am Main Airport",
            countryName = "Germany",
            latitude = 50.0333,
            longitude = 8.5706,
        )
    }

    @Test
    fun `a route airport with only a blank code becomes no ref`() {
        RouteAirport(icaoCode = " ", iataCode = null, name = "Unknown", countryName = null).toAirportRef().shouldBeNull()
    }

    @Test
    fun `a route airport without a position becomes a ref without one`() {
        val ref = RouteAirport(icaoCode = "EDDF", iataCode = null, name = null, countryName = null)
            .toAirportRef()
            .shouldNotBeNull()

        ref.icao shouldBe "EDDF"
        ref.latitude.shouldBeNull()
        ref.longitude.shouldBeNull()
    }
}
