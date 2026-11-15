package eu.darken.apl.common.flight

import eu.darken.apl.common.displayCountry
import eu.darken.apl.common.flight.db.AirportEntity
import eu.darken.apl.common.flight.db.FlightRouteEntity
import java.time.Instant

data class FlightRoute(
    val callsign: String,
    val origin: Airport?,
    val destination: Airport?,
    val seenAt: Instant,
)

/** [catalogueId] is the airport search's id for it, when the search knows it. */
data class Airport(
    val icaoCode: String,
    val iataCode: String?,
    val name: String?,
    val countryName: String?,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val catalogueId: String? = null,
    val elevationFt: Int? = null,
    val timeZone: String? = null,
) {
    val displayLabel: String
        get() = iataCode ?: icaoCode
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
            countryName = displayCountry(originAirport?.country),
            latitude = originAirport?.latitude,
            longitude = originAirport?.longitude,
            catalogueId = originAirport?.catalogueId,
            elevationFt = originAirport?.elevationFt,
            timeZone = originAirport?.timeZone,
        )
    },
    destination = destinationIcao?.let { icao ->
        Airport(
            icaoCode = icao,
            iataCode = destinationAirport?.iataCode,
            name = destinationAirport?.name,
            countryName = displayCountry(destinationAirport?.country),
            latitude = destinationAirport?.latitude,
            longitude = destinationAirport?.longitude,
            catalogueId = destinationAirport?.catalogueId,
            elevationFt = destinationAirport?.elevationFt,
            timeZone = destinationAirport?.timeZone,
        )
    },
    seenAt = seenAt,
)
