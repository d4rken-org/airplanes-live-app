package eu.darken.apl.airport.core

import eu.darken.apl.map.core.MapOptions
import io.kotest.matchers.nulls.shouldBeNull
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

    private fun Airport.mapOptions(): MapOptions = AirportTarget.Full(this).toMapOptions()!!

    private fun partial(
        icao: String? = "EDDF",
        iata: String? = "FRA",
        name: String? = "Frankfurt am Main Airport",
        latitude: Double? = 50.0333,
        longitude: Double? = 8.5706,
    ) = AirportTarget.Partial(
        AirportRef.create(icao = icao, iata = iata, name = name, latitude = latitude, longitude = longitude)!!
    )

    @Test
    fun `the map labels an airport with its IATA code`() {
        frankfurt.mapOptions().airport!!.code shouldBe "FRA"
    }

    @Test
    fun `without an IATA code the map uses the ICAO code`() {
        frankfurt.copy(iata = null).mapOptions().airport!!.code shouldBe "EDDF"
    }

    @Test
    fun `without IATA and ICAO codes the map uses the ident`() {
        danville.mapOptions().airport!!.code shouldBe "CA-0027"
    }

    @Test
    fun `the code does not follow the code that matched the search`() {
        frankfurt.copy(matchedCode = "EDDF").mapOptions().airport!!.code shouldBe "FRA"
    }

    @Test
    fun `zoom follows the airport size`() {
        frankfurt.copy(type = "large").mapOptions().camera!!.zoom shouldBe AirportMapZoom.LARGE
        frankfurt.copy(type = "medium").mapOptions().camera!!.zoom shouldBe AirportMapZoom.MEDIUM
        frankfurt.copy(type = "small").mapOptions().camera!!.zoom shouldBe AirportMapZoom.SMALL
        frankfurt.copy(type = "seaplane_base").mapOptions().camera!!.zoom shouldBe AirportMapZoom.OTHER
    }

    @Test
    fun `the camera centres on the airport`() {
        danville.mapOptions().camera shouldBe MapOptions.Camera(
            lat = 45.79425,
            lon = -71.995339,
            zoom = AirportMapZoom.SMALL,
        )
    }

    @Test
    fun `the map highlights the full airport it was given`() {
        val target = AirportTarget.Full(danville)

        target.toMapOptions()!!.airport shouldBe target
        target.name shouldBe "Danville Airport"
        target.latitude shouldBe 45.79425
        target.longitude shouldBe -71.995339
    }

    @Test
    fun `an airport request asks for nothing but the airport`() {
        val options = frankfurt.mapOptions()
        options.filter shouldBe MapOptions.Filter()
        options.feeds shouldBe emptySet()
    }

    @Test
    fun `a partial airport is labelled with its IATA code, else its ICAO code`() {
        partial().code shouldBe "FRA"
        partial(iata = null).code shouldBe "EDDF"
    }

    @Test
    fun `a partial airport is named by its name, else its code`() {
        partial().name shouldBe "Frankfurt am Main Airport"
        partial(name = null).name shouldBe "FRA"
        partial(iata = null, name = null).name shouldBe "EDDF"
    }

    @Test
    fun `a partial airport keeps its provenance on the map`() {
        val target = partial()

        target.toMapOptions()!!.airport shouldBe target
    }

    @Test
    fun `a partial airport opens at the zoom of an airport of unknown size`() {
        partial().size shouldBe Airport.Size.OTHER
        partial().toMapOptions()!!.camera shouldBe MapOptions.Camera(
            lat = 50.0333,
            lon = 8.5706,
            zoom = AirportMapZoom.OTHER,
        )
    }

    @Test
    fun `a partial airport without a position has no map options`() {
        partial(latitude = null, longitude = null).toMapOptions().shouldBeNull()
        partial(latitude = 50.0333, longitude = null).toMapOptions().shouldBeNull()
    }
}
