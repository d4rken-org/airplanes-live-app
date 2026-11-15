package eu.darken.apl.airport.core

/**
 * The flights pass of the current allowance owner ([scope]). [airportId] is null while that owner holds no
 * pass. [validUntil] is in epoch milliseconds.
 */
data class AirportFlightsPass(
    val scope: String,
    val revision: Long,
    val airportId: String?,
    val validUntil: Long?,
)
