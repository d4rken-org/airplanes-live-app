package eu.darken.apl.airport.core

import eu.darken.apl.server.api.AirportSummaryDto
import kotlinx.serialization.Serializable

/** An airport found by the airport search. [type] is the server's raw value, [size] what this build makes of it. */
@Serializable
data class Airport(
    val id: String,
    val name: String,
    val type: String,
    val ident: String,
    val latitude: Double,
    val longitude: Double,
    val municipality: String? = null,
    val country: String? = null,
    val region: String? = null,
    val icao: String? = null,
    val iata: String? = null,
    val local: String? = null,
    val matchedCode: String? = null,
) {
    val size: Size
        get() = when (type) {
            AirportSummaryDto.TYPE_LARGE -> Size.LARGE
            AirportSummaryDto.TYPE_MEDIUM -> Size.MEDIUM
            AirportSummaryDto.TYPE_SMALL -> Size.SMALL
            else -> Size.OTHER
        }

    enum class Size { LARGE, MEDIUM, SMALL, OTHER }
}

fun AirportSummaryDto.toAirport() = Airport(
    id = id,
    name = name,
    type = type,
    ident = ident,
    latitude = latitude,
    longitude = longitude,
    municipality = municipality,
    country = country,
    region = region,
    icao = icao,
    iata = iata,
    local = local,
    matchedCode = matchedCode,
)
