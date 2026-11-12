package eu.darken.apl.search.ui

import eu.darken.apl.airport.core.Airport
import eu.darken.apl.airport.core.AirportRow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class SearchAirportsTextTest : BaseTest() {

    private val frankfurt = Airport(
        id = "oa:2212",
        name = "Frankfurt am Main Airport",
        type = "large",
        ident = "EDDF",
        latitude = 50.033333,
        longitude = 8.570556,
        municipality = "Frankfurt am Main",
        country = "DE",
        region = "DE-HE",
        icao = "EDDF",
        iata = "FRA",
    )

    private val heathrow = Airport(
        id = "oa:2434",
        name = "London Heathrow Airport",
        type = "large",
        ident = "EGLL",
        latitude = 51.4706,
        longitude = -0.461941,
        municipality = "London",
        country = "GB",
        region = "GB-ENG",
        icao = "EGLL",
        iata = "LHR",
    )

    private val danville = Airport(
        id = "oa:39673",
        name = "Danville Airport",
        type = "small",
        ident = "CA-0027",
        latitude = 45.79425,
        longitude = -71.995339,
        municipality = "Val-des-Sources",
        country = "CA",
        region = "CA-QC",
        local = "CDN2",
    )

    private val danvilleIllinois = Airport(
        id = "oa:20027",
        name = "Vermilion Regional Airport",
        type = "medium",
        ident = "KDNV",
        latitude = 40.199,
        longitude = -87.5959,
        municipality = "Danville",
        country = "US",
        region = "US-IL",
        icao = "KDNV",
        iata = "DNV",
    )

    @Test
    fun `codes are IATA and ICAO`() {
        airportCodes(frankfurt) shouldBe listOf("FRA", "EDDF")
    }

    @Test
    fun `a matched code that is already shown is not repeated`() {
        airportCodes(heathrow.copy(matchedCode = "EGLL")) shouldBe listOf("LHR", "EGLL")
        airportCodes(heathrow.copy(matchedCode = "egll")) shouldBe listOf("LHR", "EGLL")
    }

    @Test
    fun `an airport without IATA and ICAO shows its ident`() {
        airportCodes(danville) shouldBe listOf("CA-0027")
    }

    @Test
    fun `a matched code that isn't shown otherwise leads`() {
        airportCodes(danville.copy(matchedCode = "CDN2")) shouldBe listOf("CDN2", "CA-0027")
    }

    @Test
    fun `only the codes that exist are shown`() {
        airportCodes(frankfurt.copy(iata = null)) shouldBe listOf("EDDF")
        airportCodes(frankfurt.copy(icao = null)) shouldBe listOf("FRA")
    }

    @Test
    fun `place is municipality and country`() {
        airportPlaceLine(
            row = AirportRow(frankfurt, municipalityAmbiguous = false),
            country = "Germany",
            separator = ", ",
        ) shouldBe "Frankfurt am Main, Germany"
    }

    @Test
    fun `an ambiguous municipality adds the region`() {
        airportPlaceLine(
            row = AirportRow(danvilleIllinois, municipalityAmbiguous = true),
            country = "United States",
            separator = ", ",
        ) shouldBe "Danville, US-IL, United States"
    }

    @Test
    fun `the region is left out when the municipality is unique`() {
        airportPlaceLine(
            row = AirportRow(danvilleIllinois, municipalityAmbiguous = false),
            country = "United States",
            separator = ", ",
        ) shouldBe "Danville, United States"
    }

    @Test
    fun `missing parts are left out`() {
        airportPlaceLine(
            row = AirportRow(frankfurt.copy(municipality = null), municipalityAmbiguous = false),
            country = "Germany",
            separator = ", ",
        ) shouldBe "Germany"
        airportPlaceLine(
            row = AirportRow(frankfurt, municipalityAmbiguous = false),
            country = null,
            separator = ", ",
        ) shouldBe "Frankfurt am Main"
        airportPlaceLine(
            row = AirportRow(frankfurt.copy(municipality = null), municipalityAmbiguous = false),
            country = null,
            separator = ", ",
        ) shouldBe null
    }
}
