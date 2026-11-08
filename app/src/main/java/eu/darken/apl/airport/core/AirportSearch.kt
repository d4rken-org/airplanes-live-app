package eu.darken.apl.airport.core

import eu.darken.apl.server.api.AirportSearchResultDto

sealed interface AirportSearch {
    /** As the server sent them, check them with [aggregateAirports] before use. */
    data class Answered(val results: List<AirportSearchResultDto>) : AirportSearch

    data object RateLimited : AirportSearch

    /** The server has no airport search. */
    data object Unavailable : AirportSearch

    data object UpdateRequired : AirportSearch

    data object Restricted : AirportSearch

    data object Revoked : AirportSearch

    data class Failed(val error: Throwable) : AirportSearch
}
