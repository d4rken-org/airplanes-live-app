package eu.darken.apl.airport.core

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** An airport the app can show: [Full] as the airport search found it, [Partial] as a route or the map knows it. */
@Serializable
sealed interface AirportTarget : Parcelable {

    /**
     * What the map labels it with, e.g. "FRA". The same whether the search found it by code or by name, so
     * [Airport.matchedCode] is left out.
     */
    val code: String
    val name: String
    val latitude: Double?
    val longitude: Double?
    val size: Airport.Size

    @Parcelize
    @Serializable
    @SerialName("full")
    data class Full(val airport: Airport) : AirportTarget {
        override val code: String
            get() = airport.iata ?: airport.icao ?: airport.ident
        override val name: String
            get() = airport.name
        override val latitude: Double
            get() = airport.latitude
        override val longitude: Double
            get() = airport.longitude
        override val size: Airport.Size
            get() = airport.size
    }

    @Parcelize
    @Serializable
    @SerialName("partial")
    data class Partial(val ref: AirportRef) : AirportTarget {
        override val code: String
            get() = ref.iata ?: ref.icao!!
        override val name: String
            get() = ref.name ?: code
        override val latitude: Double?
            get() = ref.latitude
        override val longitude: Double?
            get() = ref.longitude
        override val size: Airport.Size
            get() = Airport.Size.OTHER
    }
}
