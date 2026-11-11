package eu.darken.apl.airport.ui

import eu.darken.apl.airport.core.Airport
import eu.darken.apl.airport.core.AirportRef
import eu.darken.apl.airport.core.AirportTarget
import eu.darken.apl.airport.ui.AirportDetailsDisplay.Code
import eu.darken.apl.airport.ui.AirportDetailsDisplay.CodeKind
import eu.darken.apl.common.displayCountry
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class AirportDetailsDisplayTest : BaseTest() {

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

    private val routeRef = AirportRef.create(
        icao = "EDDF",
        iata = "FRA",
        name = "Frankfurt Main",
        countryName = "Deutschland",
        latitude = 50.03,
        longitude = 8.57,
    )!!

    @Test
    fun `a full airport shows the server's data`() {
        val display = AirportDetailsDisplay.from(AirportTarget.Full(frankfurt))

        display.title shouldBe "Frankfurt am Main Airport"
        display.codes shouldBe listOf(Code(CodeKind.IATA, "FRA"), Code(CodeKind.ICAO, "EDDF"))
        display.municipality shouldBe "Frankfurt am Main"
        display.region shouldBe "DE-HE"
        display.country shouldBe displayCountry("DE")
        display.type shouldBe AirportDetailsDisplay.Type(Airport.Size.LARGE, "large")
        display.latitude shouldBe 50.033333
        display.longitude shouldBe 8.570556
    }

    @Test
    fun `server values win over the ref's`() {
        val display = AirportDetailsDisplay.from(AirportTarget.Partial(routeRef), resolved = frankfurt)

        display.title shouldBe "Frankfurt am Main Airport"
        display.country shouldBe displayCountry("DE")
        display.latitude shouldBe 50.033333
        display.longitude shouldBe 8.570556
        display.codes shouldBe listOf(Code(CodeKind.IATA, "FRA"), Code(CodeKind.ICAO, "EDDF"))
    }

    @Test
    fun `a match lacking the ref's IATA code and country keeps both`() {
        val display = AirportDetailsDisplay.from(
            AirportTarget.Partial(routeRef),
            resolved = frankfurt.copy(iata = null, country = null),
        )

        display.codes shouldBe listOf(Code(CodeKind.IATA, "FRA"), Code(CodeKind.ICAO, "EDDF"))
        display.country shouldBe "Deutschland"
        display.municipality shouldBe "Frankfurt am Main"
        display.type shouldBe AirportDetailsDisplay.Type(Airport.Size.LARGE, "large")
    }

    @Test
    fun `a match keeps its ident and local codes next to the ref's codes`() {
        val display = AirportDetailsDisplay.from(
            AirportTarget.Partial(AirportRef.create(icao = "CDN2", iata = "XDV")!!),
            resolved = Airport(
                id = "oa:39673",
                name = "Danville Airport",
                type = "small",
                ident = "CA-0027",
                local = "CDN2",
                latitude = 45.79425,
                longitude = -71.995339,
            ),
        )

        display.codes shouldBe listOf(
            Code(CodeKind.IATA, "XDV"),
            Code(CodeKind.ICAO, "CDN2"),
            Code(CodeKind.IDENT, "CA-0027"),
        )
    }

    @Test
    fun `a partial airport without a match shows the ref's data and no type`() {
        val display = AirportDetailsDisplay.from(AirportTarget.Partial(routeRef))

        display.title shouldBe "Frankfurt Main"
        display.codes shouldBe listOf(Code(CodeKind.IATA, "FRA"), Code(CodeKind.ICAO, "EDDF"))
        display.country shouldBe "Deutschland"
        display.latitude shouldBe 50.03
        display.longitude shouldBe 8.57
        display.type.shouldBeNull()
        display.municipality.shouldBeNull()
        display.region.shouldBeNull()
    }

    @Test
    fun `a partial airport without a name is titled by its code`() {
        AirportDetailsDisplay.from(AirportTarget.Partial(AirportRef.create(icao = "EDDF", iata = "FRA")!!))
            .title shouldBe "FRA"
        AirportDetailsDisplay.from(AirportTarget.Partial(AirportRef.create(icao = "EDDF", iata = null)!!))
            .title shouldBe "EDDF"
    }

    @Test
    fun `a partial airport without a position has none`() {
        val display = AirportDetailsDisplay.from(AirportTarget.Partial(AirportRef.create(icao = "EDDF", iata = null)!!))

        display.latitude.shouldBeNull()
        display.longitude.shouldBeNull()
        display.country.shouldBeNull()
        display.codes shouldBe listOf(Code(CodeKind.ICAO, "EDDF"))
    }

    @Test
    fun `codes are shown once ignoring case`() {
        val display = AirportDetailsDisplay.from(
            AirportTarget.Full(frankfurt.copy(ident = "eddf", local = "EDDF"))
        )

        display.codes shouldBe listOf(Code(CodeKind.IATA, "FRA"), Code(CodeKind.ICAO, "EDDF"))
    }
}
