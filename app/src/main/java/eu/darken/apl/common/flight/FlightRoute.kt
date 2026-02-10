package eu.darken.apl.common.flight

import eu.darken.apl.common.flight.db.AirportEntity
import eu.darken.apl.common.flight.db.FlightRouteEntity
import java.time.Instant

data class FlightRoute(
    val callsign: String,
    val origin: Airport?,
    val destination: Airport?,
    val seenAt: Instant,
)

data class Airport(
    val icaoCode: String,
    val iataCode: String?,
    val name: String?,
    val countryName: String?,
) {
    val displayLabel: String
        get() = iataCode ?: icaoCode

    val routeDisplayText: String
        get() = if (name != null) "$name ($displayLabel)" else displayLabel
}

fun FlightRouteEntity.toDomain(
    originAirport: AirportEntity?,
    destinationAirport: AirportEntity?,
) = FlightRoute(
    callsign = callsign,
    origin = originIcao?.let { icao ->
        Airport(
            icaoCode = icao,
            iataCode = originAirport?.iataCode,
            name = originAirport?.name,
            countryName = originAirport?.country,
        )
    },
    destination = destinationIcao?.let { icao ->
        Airport(
            icaoCode = icao,
            iataCode = destinationAirport?.iataCode,
            name = destinationAirport?.name,
            countryName = destinationAirport?.country,
        )
    },
    seenAt = seenAt,
)
