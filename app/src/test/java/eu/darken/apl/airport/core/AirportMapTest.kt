package eu.darken.apl.airport.core

import eu.darken.apl.map.core.MapAirport
import eu.darken.apl.map.core.MapOptions
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class AirportMapTest : BaseTest() {

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

    @Test
    fun `the map labels an airport with its IATA code`() {
        frankfurt.toMapOptions().airport!!.code shouldBe "FRA"
    }

    @Test
    fun `without an IATA code the map uses the ICAO code`() {
        frankfurt.copy(iata = null).toMapOptions().airport!!.code shouldBe "EDDF"
    }

    @Test
    fun `without IATA and ICAO codes the map uses the ident`() {
        danville.toMapOptions().airport!!.code shouldBe "CA-0027"
    }

    @Test
    fun `the code does not follow the code that matched the search`() {
        frankfurt.copy(matchedCode = "EDDF").toMapOptions().airport!!.code shouldBe "FRA"
    }

    @Test
    fun `zoom follows the airport size`() {
        frankfurt.copy(type = "large").toMapOptions().camera!!.zoom shouldBe AirportMapZoom.LARGE
        frankfurt.copy(type = "medium").toMapOptions().camera!!.zoom shouldBe AirportMapZoom.MEDIUM
        frankfurt.copy(type = "small").toMapOptions().camera!!.zoom shouldBe AirportMapZoom.SMALL
        frankfurt.copy(type = "seaplane_base").toMapOptions().camera!!.zoom shouldBe AirportMapZoom.OTHER
    }

    @Test
    fun `the camera centres on the airport`() {
        danville.toMapOptions().camera shouldBe MapOptions.Camera(
            lat = 45.79425,
            lon = -71.995339,
            zoom = AirportMapZoom.SMALL,
        )
    }

    @Test
    fun `the map airport carries the airport's fields`() {
        danville.toMapOptions().airport shouldBe MapAirport(
            id = "oa:39673",
            name = "Danville Airport",
            code = "CA-0027",
            lat = 45.79425,
            lon = -71.995339,
            type = "small",
        )
    }

    @Test
    fun `an airport request asks for nothing but the airport`() {
        val options = frankfurt.toMapOptions()
        options.filter shouldBe MapOptions.Filter()
        options.feeds shouldBe emptySet()
    }
}
