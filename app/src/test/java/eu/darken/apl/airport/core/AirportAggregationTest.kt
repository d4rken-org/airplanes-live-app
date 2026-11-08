package eu.darken.apl.airport.core

import eu.darken.apl.server.api.AirportSearchResultDto
import eu.darken.apl.server.api.AirportSearchResultDto.Companion.STATUS_ANSWERED
import eu.darken.apl.server.api.AirportSearchResultDto.Companion.STATUS_INVALID_QUERY
import eu.darken.apl.server.api.AirportSearchResultDto.Companion.STATUS_SKIPPED
import eu.darken.apl.server.api.AirportSummaryDto
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class AirportAggregationTest : BaseTest() {

    private fun airport(
        id: String,
        municipality: String? = null,
        region: String? = null,
        matchedCode: String? = null,
    ) = AirportSummaryDto(
        id = id,
        name = "Airport $id",
        type = AirportSummaryDto.TYPE_LARGE,
        ident = id.uppercase(),
        latitude = 1.0,
        longitude = 2.0,
        municipality = municipality,
        region = region,
        matchedCode = matchedCode,
    )

    private fun answered(index: Int, vararg airports: AirportSummaryDto, truncated: Boolean = false) =
        AirportSearchResultDto(index, STATUS_ANSWERED, airports.toList(), truncated)

    private fun skipped(index: Int) = AirportSearchResultDto(index, STATUS_SKIPPED)
    private fun invalid(index: Int) = AirportSearchResultDto(index, STATUS_INVALID_QUERY)

    private fun AirportAggregation.shown(): AirportAggregation.Shown = shouldBeInstanceOf<AirportAggregation.Shown>()

    private val AirportAggregation.Shown.ids get() = rows.map { it.airport.id }

    @Test
    fun `an airport found by several terms appears once, as the first term found it`() {
        val heathrowByIata = airport("lhr", municipality = "London", region = "GB-ENG", matchedCode = "LHR")
        val heathrowByIcao = airport("lhr", municipality = "London", region = "GB-ENG", matchedCode = "EGLL")
        val heathrowByName = airport("lhr", municipality = "London", region = "GB-ENG")
        val city = airport("lcy", municipality = "London", region = "GB-ENG")
        val ontario = airport("yxu", municipality = "London", region = "CA-ON")

        val result = aggregateAirports(
            terms = listOf("LHR", "EGLL", "London"),
            results = listOf(
                answered(0, heathrowByIata),
                answered(1, heathrowByIcao),
                answered(2, heathrowByName, city, ontario),
            ),
        ).shown()

        result.ids shouldBe listOf("lhr", "lcy", "yxu")
        result.rows.first().airport.matchedCode shouldBe "LHR"
        result.rows.map { it.municipalityAmbiguous } shouldBe listOf(true, true, true)
    }

    @Test
    fun `term order and server order are kept`() {
        val result = aggregateAirports(
            terms = listOf("a1", "b2"),
            results = listOf(
                answered(0, airport("z"), airport("a")),
                answered(1, airport("m"), airport("b")),
            ),
        ).shown()

        result.ids shouldBe listOf("z", "a", "m", "b")
    }

    @Test
    fun `results out of index order are reordered`() {
        val result = aggregateAirports(
            terms = listOf("a1", "b2", "c3"),
            results = listOf(
                answered(2, airport("c")),
                answered(0, airport("a")),
                answered(1, airport("b")),
            ),
        ).shown()

        result.ids shouldBe listOf("a", "b", "c")
    }

    @Test
    fun `a shared municipality is flagged after dedup`() {
        val result = aggregateAirports(
            terms = listOf("a1", "b2"),
            results = listOf(
                answered(0, airport("one", municipality = "Springfield"), airport("two", municipality = "Shelbyville")),
                // "one" again: removed by dedup, so it must not make Springfield ambiguous
                answered(1, airport("one", municipality = "Springfield"), airport("three", municipality = "shelbyville")),
            ),
        ).shown()

        result.ids shouldBe listOf("one", "two", "three")
        result.rows.map { it.municipalityAmbiguous } shouldBe listOf(false, true, true)
    }

    @Test
    fun `airports without a municipality are never flagged`() {
        val result = aggregateAirports(
            terms = listOf("a1"),
            results = listOf(answered(0, airport("one"), airport("two"))),
        ).shown()

        result.rows.map { it.municipalityAmbiguous } shouldBe listOf(false, false)
    }

    @Test
    fun `any truncated answered term truncates the result`() {
        aggregateAirports(
            terms = listOf("a1", "b2"),
            results = listOf(answered(0, airport("a")), answered(1, airport("b"), truncated = true)),
        ).shown().truncated shouldBe true

        aggregateAirports(
            terms = listOf("a1", "b2"),
            results = listOf(answered(0, airport("a")), answered(1, airport("b"))),
        ).shown().truncated shouldBe false
    }

    @Test
    fun `a missing index is a contract failure`() {
        aggregateAirports(
            terms = listOf("a1", "b2"),
            results = listOf(answered(0, airport("a"))),
        ) shouldBe AirportAggregation.ContractFailure

        aggregateAirports(
            terms = listOf("a1", "b2", "c3"),
            results = listOf(answered(0), answered(2), answered(3)),
        ) shouldBe AirportAggregation.ContractFailure
    }

    @Test
    fun `a duplicate index is a contract failure`() {
        aggregateAirports(
            terms = listOf("a1", "b2"),
            results = listOf(answered(0), answered(0)),
        ) shouldBe AirportAggregation.ContractFailure
    }

    @Test
    fun `an out of range index is a contract failure`() {
        aggregateAirports(
            terms = listOf("a1", "b2"),
            results = listOf(answered(0), answered(2)),
        ) shouldBe AirportAggregation.ContractFailure

        aggregateAirports(
            terms = listOf("a1", "b2"),
            results = listOf(answered(-1), answered(1)),
        ) shouldBe AirportAggregation.ContractFailure

        aggregateAirports(
            terms = listOf("a1"),
            results = listOf(answered(0), answered(1)),
        ) shouldBe AirportAggregation.ContractFailure
    }

    @Test
    fun `only skipped and unknown statuses are hidden`() {
        aggregateAirports(
            terms = listOf("3c6444", "7700", "x1"),
            results = listOf(
                skipped(0),
                skipped(1),
                AirportSearchResultDto(2, "pending", listOf(airport("a"))),
            ),
        ) shouldBe AirportAggregation.Hidden
    }

    @Test
    fun `invalid terms alone are shown without counting as no airports`() {
        val result = aggregateAirports(
            terms = listOf("a", "-"),
            results = listOf(invalid(0), invalid(1)),
        ).shown()

        result.rows shouldBe emptyList()
        result.invalidTerms shouldBe listOf("a", "-")
        result.searchedAny shouldBe false
        result.noAirports shouldBe false
    }

    @Test
    fun `invalid and skipped terms are shown without counting as no airports`() {
        val result = aggregateAirports(
            terms = listOf("7700", "a", "zz-unknown"),
            results = listOf(skipped(0), invalid(1), AirportSearchResultDto(2, "pending")),
        ).shown()

        result.invalidTerms shouldBe listOf("a")
        result.noAirports shouldBe false
    }

    @Test
    fun `an answered term without airports is no airports`() {
        val result = aggregateAirports(
            terms = listOf("zzzz"),
            results = listOf(answered(0)),
        ).shown()

        result.rows shouldBe emptyList()
        result.invalidTerms shouldBe emptyList()
        result.searchedAny shouldBe true
        result.noAirports shouldBe true
    }

    @Test
    fun `an answered term without airports next to an invalid one is no airports with the invalid term`() {
        val result = aggregateAirports(
            terms = listOf("zzzz", "a"),
            results = listOf(answered(0), invalid(1)),
        ).shown()

        result.noAirports shouldBe true
        result.invalidTerms shouldBe listOf("a")
    }

    @Test
    fun `answered, invalid and skipped terms mix`() {
        val result = aggregateAirports(
            terms = listOf("7700", "CDN2", "a", "London", "b"),
            results = listOf(
                skipped(0),
                answered(1, airport("cdn2", matchedCode = "CDN2")),
                invalid(2),
                answered(3, airport("lhr"), truncated = true),
                invalid(4),
            ),
        ).shown()

        result.ids shouldBe listOf("cdn2", "lhr")
        result.invalidTerms shouldBe listOf("a", "b")
        result.truncated shouldBe true
        result.searchedAny shouldBe true
        result.noAirports shouldBe false
    }

    @Test
    fun `rows carry the mapped airport`() {
        val dto = AirportSummaryDto(
            id = "oa:39673",
            name = "Danville Airport",
            type = "seaplane_base",
            ident = "CA-0027",
            latitude = 45.79425,
            longitude = -71.995339,
            municipality = "Val-des-Sources",
            country = "CA",
            region = "CA-QC",
            local = "CDN2",
            matchedCode = "CDN2",
        )

        val row = aggregateAirports(listOf("CDN2"), listOf(answered(0, dto))).shown().rows.single()

        row.airport shouldBe Airport(
            id = "oa:39673",
            name = "Danville Airport",
            type = "seaplane_base",
            ident = "CA-0027",
            latitude = 45.79425,
            longitude = -71.995339,
            municipality = "Val-des-Sources",
            country = "CA",
            region = "CA-QC",
            local = "CDN2",
            matchedCode = "CDN2",
        )
        row.airport.size shouldBe Airport.Size.OTHER
    }

    @Test
    fun `the size follows the known types`() {
        listOf(
            AirportSummaryDto.TYPE_LARGE to Airport.Size.LARGE,
            AirportSummaryDto.TYPE_MEDIUM to Airport.Size.MEDIUM,
            AirportSummaryDto.TYPE_SMALL to Airport.Size.SMALL,
            "heliport" to Airport.Size.OTHER,
        ).forEach { (type, size) ->
            airport("a").copy(type = type).toAirport().size shouldBe size
        }
    }
}
