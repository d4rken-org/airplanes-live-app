package eu.darken.apl.airport.core

import eu.darken.apl.common.geo.Spherical

/**
 * The airport [search] found for [AirportRef.lookupTerm], or null when it is not certain which one it is.
 *
 * [Airport.matchedCode] does not say which kind of code matched (counterpart: AirportSummaryDto), it is also set for
 * `EDDF` on an airport whose local code is `EDDF`. So a candidate counts only when its code of the term's kind is
 * the term.
 */
fun resolveAirportLookup(ref: AirportRef, search: AirportSearch): Airport? {
    if (search !is AirportSearch.Answered) return null
    val term = ref.lookupTerm
    val byIcao = ref.icao != null
    val shown = aggregateAirports(listOf(term), search.results) as? AirportAggregation.Shown ?: return null

    val candidates = shown.rows
        .map { it.airport }
        .filter { candidate ->
            val sameKind = if (byIcao) candidate.icao else candidate.iata
            candidate.matchedCode.equals(term, ignoreCase = true) && sameKind.equals(term, ignoreCase = true)
        }
        .filterNot { candidate ->
            conflicts(ref.icao, candidate.icao) || conflicts(ref.iata, candidate.iata)
        }

    val lat = ref.latitude
    val lon = ref.longitude
    return when {
        candidates.size == 1 -> candidates.single()
        candidates.size > 1 && lat != null && lon != null -> candidates.minBy {
            Spherical.haversineDistanceM(lat, lon, it.latitude, it.longitude)
        }

        else -> null
    }
}

private fun conflicts(ours: String?, theirs: String?): Boolean =
    ours != null && theirs != null && !ours.equals(theirs, ignoreCase = true)
