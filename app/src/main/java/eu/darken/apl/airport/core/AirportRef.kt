package eu.darken.apl.airport.core

import android.os.Parcelable
import eu.darken.apl.common.geo.Spherical
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import eu.darken.apl.common.flight.Airport as RouteAirport

/**
 * What a route or a map tile knows about an airport, enough to find it with the airport search.
 * [countryName] is already a display name.
 */
@ConsistentCopyVisibility
@Parcelize
@Serializable
data class AirportRef private constructor(
    val icao: String?,
    val iata: String?,
    val name: String?,
    val countryName: String?,
    val latitude: Double?,
    val longitude: Double?,
) : Parcelable {

    val lookupTerm: String
        get() = icao ?: iata!!

    companion object {
        /** Null without an ICAO or IATA code. Coordinates are kept only as a valid pair. */
        fun create(
            icao: String?,
            iata: String?,
            name: String? = null,
            countryName: String? = null,
            latitude: Double? = null,
            longitude: Double? = null,
        ): AirportRef? {
            val icaoCode = icao.cleaned()
            val iataCode = iata.cleaned()
            if (icaoCode == null && iataCode == null) return null
            val validPosition = latitude != null && longitude != null && Spherical.isValid(latitude, longitude)
            return AirportRef(
                icao = icaoCode,
                iata = iataCode,
                name = name.cleaned(),
                countryName = countryName.cleaned(),
                latitude = latitude.takeIf { validPosition },
                longitude = longitude.takeIf { validPosition },
            )
        }

        private fun String?.cleaned(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
    }
}

fun RouteAirport.toAirportRef(): AirportRef? = AirportRef.create(
    icao = icaoCode,
    iata = iataCode,
    name = name,
    countryName = countryName,
    latitude = latitude,
    longitude = longitude,
)
