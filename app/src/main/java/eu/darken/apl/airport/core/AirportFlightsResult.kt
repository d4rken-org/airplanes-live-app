package eu.darken.apl.airport.core

import eu.darken.apl.server.api.AirportFlightsResponse

/** Times ending in `Elapsed` are monotonic, see [eu.darken.apl.common.MonotonicClock]. */
sealed interface AirportFlightsResult {
    /** The aircraft it carries are already in the cache. */
    data class Answered(val response: AirportFlightsResponse) : AirportFlightsResult

    data object AllowanceExhausted : AirportFlightsResult

    /** The list or its evaluation is gone, or access changed: only a new load can answer. */
    data class ReloadNeeded(val outcome: String) : AirportFlightsResult

    /** An outcome this build does not know; it carries no list. */
    data class UnknownOutcome(val outcome: String) : AirportFlightsResult

    /** This airport was asked for too recently. */
    data class CoolingDown(val untilElapsed: Long) : AirportFlightsResult

    /** The server asked for a wait that ends after the operation could still be replayed. */
    data class FlightsUnavailable(val untilElapsed: Long) : AirportFlightsResult

    /** The replay window passed without an answer, a recovery needs a new operation. */
    data object Expired : AirportFlightsResult

    /** The server has no airport flights. */
    data object NotOffered : AirportFlightsResult

    /** The airport id is unknown to the server or retired. */
    data object UnknownAirport : AirportFlightsResult

    data object UpdateRequired : AirportFlightsResult

    data object Restricted : AirportFlightsResult

    data object Revoked : AirportFlightsResult

    data class Failed(val error: Throwable) : AirportFlightsResult
}
