package eu.darken.apl.airport.core

import eu.darken.apl.server.api.AirportSearchResultDto
import eu.darken.apl.server.api.AirportSearchResultDto.Companion.STATUS_ANSWERED
import eu.darken.apl.server.api.AirportSearchResultDto.Companion.STATUS_INVALID_QUERY
import eu.darken.apl.server.api.AirportSearchResultDto.Companion.STATUS_SKIPPED
import eu.darken.apl.server.api.AirportSummaryDto
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class AirportLookupTest : BaseTest() {

    private val frankfurt = AirportSummaryDto(
        id = "oa:2212",
        name = "Frankfurt am Main Airport",
        type = AirportSummaryDto.TYPE_LARGE,
        ident = "EDDF",
        latitude = 50.033333,
        longitude = 8.570556,
        country = "DE",
        icao = "EDDF",
        iata = "FRA",
    )

    private val hahn = AirportSummaryDto(
        id = "oa:2297",
        name = "Frankfurt-Hahn Airport",
        type = AirportSummaryDto.TYPE_MEDIUM,
        ident = "EDFH",
        latitude = 49.9487,
        longitude = 7.26389,
        country = "DE",
        icao = "EDFH",
        iata = "HHN",
    )

    private fun ref(
        icao: String? = "EDDF",
        iata: String? = "FRA",
        latitude: Double? = null,
        longitude: Double? = null,
    ) = AirportRef.create(icao = icao, iata = iata, latitude = latitude, longitude = longitude)!!

    private fun answered(vararg airports: AirportSummaryDto) = AirportSearch.Answered(
        listOf(AirportSearchResultDto(0, STATUS_ANSWERED, airports.toList()))
    )

    @Test
    fun `an exact match on the term's kind is the airport`() {
        val result = resolveAirportLookup(ref(), answered(frankfurt.copy(matchedCode = "EDDF"))).shouldNotBeNull()

        result.id shouldBe "oa:2212"
        result.name shouldBe "Frankfurt am Main Airport"
    }

    @Test
    fun `an IATA-only ref matches on the IATA code`() {
        val result = resolveAirportLookup(
            ref(icao = null, iata = "FRA"),
            answered(frankfurt.copy(matchedCode = "FRA")),
        )

        result.shouldNotBeNull().id shouldBe "oa:2212"
    }

    @Test
    fun `a match through another kind of code is not the airport`() {
        val localMatch = AirportSummaryDto(
            id = "oa:99999",
            name = "Some Field",
            type = AirportSummaryDto.TYPE_SMALL,
            ident = "XX-0001",
            latitude = 10.0,
            longitude = 10.0,
            local = "EDDF",
            matchedCode = "EDDF",
        )

        resolveAirportLookup(ref(), answered(localMatch)).shouldBeNull()
        resolveAirportLookup(ref(), answered(localMatch.copy(icao = "XXXX"))).shouldBeNull()
    }

    @Test
    fun `a candidate without a code of the term's kind is not the airport`() {
        resolveAirportLookup(ref(), answered(frankfurt.copy(icao = null, matchedCode = "EDDF"))).shouldBeNull()
        resolveAirportLookup(
            ref(icao = null, iata = "FRA"),
            answered(frankfurt.copy(iata = null, matchedCode = "FRA")),
        ).shouldBeNull()
    }

    @Test
    fun `a candidate found by name only is not the airport`() {
        resolveAirportLookup(ref(), answered(frankfurt.copy(matchedCode = null))).shouldBeNull()
    }

    @Test
    fun `a candidate whose other code differs is rejected`() {
        resolveAirportLookup(ref(iata = "FRA"), answered(frankfurt.copy(iata = "XFR", matchedCode = "EDDF"))).shouldBeNull()
    }

    @Test
    fun `a candidate without the ref's other code is not a conflict`() {
        resolveAirportLookup(ref(iata = "FRA"), answered(frankfurt.copy(iata = null, matchedCode = "EDDF")))
            .shouldNotBeNull().id shouldBe "oa:2212"
    }

    @Test
    fun `a conflicting candidate leaves the other one`() {
        val clash = hahn.copy(id = "oa:clash", icao = "EDDF", iata = "XFR", matchedCode = "EDDF")

        resolveAirportLookup(ref(), answered(clash, frankfurt.copy(matchedCode = "EDDF")))
            .shouldNotBeNull().id shouldBe "oa:2212"
    }

    @Test
    fun `of two valid candidates the one nearest the ref's position wins`() {
        val near = frankfurt.copy(id = "oa:near", matchedCode = "EDDF")
        val far = frankfurt.copy(id = "oa:far", latitude = -33.9, longitude = 151.2, matchedCode = "EDDF")

        resolveAirportLookup(ref(latitude = 50.03, longitude = 8.57), answered(far, near))
            .shouldNotBeNull().id shouldBe "oa:near"
        resolveAirportLookup(ref(latitude = -33.95, longitude = 151.18), answered(near, far))
            .shouldNotBeNull().id shouldBe "oa:far"
    }

    @Test
    fun `two valid candidates without a ref position are not resolved`() {
        val near = frankfurt.copy(id = "oa:near", matchedCode = "EDDF")
        val far = frankfurt.copy(id = "oa:far", latitude = -33.9, longitude = 151.2, matchedCode = "EDDF")

        resolveAirportLookup(ref(), answered(near, far)).shouldBeNull()
    }

    @Test
    fun `codes are compared ignoring case`() {
        resolveAirportLookup(
            ref(icao = "eddf", iata = "fra"),
            answered(frankfurt.copy(matchedCode = "EDDF")),
        ).shouldNotBeNull().id shouldBe "oa:2212"
        resolveAirportLookup(
            ref(icao = "EDDF", iata = "FRA"),
            answered(frankfurt.copy(icao = "eddf", iata = "fra", matchedCode = "eddf")),
        ).shouldNotBeNull().id shouldBe "oa:2212"
    }

    @Test
    fun `no candidates resolve to nothing`() {
        resolveAirportLookup(ref(), answered()).shouldBeNull()
    }

    @Test
    fun `a search that was not answered resolves to nothing`() {
        listOf(
            AirportSearch.RateLimited,
            AirportSearch.Unavailable,
            AirportSearch.UpdateRequired,
            AirportSearch.Restricted,
            AirportSearch.Revoked,
            AirportSearch.Failed(RuntimeException("boom")),
        ).forEach { search ->
            resolveAirportLookup(ref(), search).shouldBeNull()
        }
    }

    @Test
    fun `an answer that breaks the contract resolves to nothing`() {
        val twoResults = AirportSearch.Answered(
            listOf(
                AirportSearchResultDto(0, STATUS_ANSWERED, listOf(frankfurt.copy(matchedCode = "EDDF"))),
                AirportSearchResultDto(1, STATUS_ANSWERED, listOf(frankfurt.copy(matchedCode = "EDDF"))),
            )
        )
        val wrongIndex = AirportSearch.Answered(
            listOf(AirportSearchResultDto(3, STATUS_ANSWERED, listOf(frankfurt.copy(matchedCode = "EDDF"))))
        )

        resolveAirportLookup(ref(), twoResults).shouldBeNull()
        resolveAirportLookup(ref(), wrongIndex).shouldBeNull()
    }

    @Test
    fun `a term the server did not search resolves to nothing`() {
        resolveAirportLookup(
            ref(),
            AirportSearch.Answered(listOf(AirportSearchResultDto(0, STATUS_SKIPPED))),
        ).shouldBeNull()
        resolveAirportLookup(
            ref(),
            AirportSearch.Answered(listOf(AirportSearchResultDto(0, STATUS_INVALID_QUERY))),
        ).shouldBeNull()
    }
}
