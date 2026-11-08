package eu.darken.apl.airport.core

import eu.darken.apl.server.api.AirportSearchResultDto
import java.util.Locale

sealed interface AirportAggregation {
    /** The results do not answer each term exactly once. */
    data object ContractFailure : AirportAggregation

    /** No term was searched or rejected as invalid, there is nothing to show. */
    data object Hidden : AirportAggregation

    /**
     * [searchedAny] is true when at least one term was searched. [invalidTerms] are the terms the server
     * rejected, in term order.
     */
    data class Shown(
        val rows: List<AirportRow>,
        val truncated: Boolean,
        val invalidTerms: List<String>,
        val searchedAny: Boolean,
    ) : AirportAggregation {
        /** Terms were searched and none found an airport. Invalid terms alone are not this. */
        val noAirports: Boolean
            get() = searchedAny && rows.isEmpty()
    }
}

/** [municipalityAmbiguous] is true when another row shares the municipality, the region tells them apart. */
data class AirportRow(
    val airport: Airport,
    val municipalityAmbiguous: Boolean,
)

/**
 * Example for terms `LHR, EGLL, London`: Heathrow answers all three and appears once, as found for `LHR`
 * (with its matchedCode), followed by the other London airports in the server's order.
 */
fun aggregateAirports(terms: List<String>, results: List<AirportSearchResultDto>): AirportAggregation {
    if (results.size != terms.size) return AirportAggregation.ContractFailure
    val ordered = results.sortedBy { it.index }
    ordered.forEachIndexed { position, result ->
        if (result.index != position) return AirportAggregation.ContractFailure
    }

    val answered = ordered.filter { it.status == AirportSearchResultDto.STATUS_ANSWERED }
    val invalidTerms = ordered
        .filter { it.status == AirportSearchResultDto.STATUS_INVALID_QUERY }
        .map { terms[it.index] }
    if (answered.isEmpty() && invalidTerms.isEmpty()) return AirportAggregation.Hidden

    val seen = mutableSetOf<String>()
    val airports = answered
        .flatMap { it.airports }
        .filter { seen.add(it.id) }
        .map { it.toAirport() }

    val municipalityCounts = airports
        .mapNotNull { it.municipalityKey() }
        .groupingBy { it }
        .eachCount()
    val rows = airports.map { airport ->
        val key = airport.municipalityKey()
        AirportRow(
            airport = airport,
            municipalityAmbiguous = key != null && municipalityCounts.getValue(key) > 1,
        )
    }

    return AirportAggregation.Shown(
        rows = rows,
        truncated = answered.any { it.truncated },
        invalidTerms = invalidTerms,
        searchedAny = answered.isNotEmpty(),
    )
}

private fun Airport.municipalityKey(): String? =
    municipality?.trim()?.takeIf { it.isNotEmpty() }?.lowercase(Locale.ROOT)
