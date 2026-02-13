package eu.darken.apl.common.flight.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import retrofit2.http.GET
import retrofit2.http.Path

interface AdsbdbApi {

    @Serializable
    data class CallsignResponse(
        @SerialName("response") val response: ResponseData? = null,
    )

    @Serializable
    data class ResponseData(
        @SerialName("flightroute") val flightroute: JsonElement? = null,
    )

    @Serializable
    data class FlightRouteData(
        @SerialName("callsign") val callsign: String? = null,
        @SerialName("callsign_icao") val callsignIcao: String? = null,
        @SerialName("callsign_iata") val callsignIata: String? = null,
        @SerialName("airline") val airline: AirlineData? = null,
        @SerialName("origin") val origin: AirportData? = null,
        @SerialName("destination") val destination: AirportData? = null,
    )

    @Serializable
    data class AirlineData(
        @SerialName("name") val name: String? = null,
        @SerialName("icao") val icao: String? = null,
        @SerialName("iata") val iata: String? = null,
        @SerialName("country") val country: String? = null,
        @SerialName("callsign") val callsign: String? = null,
    )

    @Serializable
    data class AirportData(
        @SerialName("country_iso_name") val countryIsoName: String? = null,
        @SerialName("country_name") val countryName: String? = null,
        @SerialName("elevation") val elevation: Int? = null,
        @SerialName("iata_code") val iataCode: String? = null,
        @SerialName("icao_code") val icaoCode: String? = null,
        @SerialName("latitude") val latitude: Double? = null,
        @SerialName("longitude") val longitude: Double? = null,
        @SerialName("municipality") val municipality: String? = null,
        @SerialName("name") val name: String? = null,
    )

    @GET("v0/callsign/{callsign}")
    suspend fun getByCallsign(@Path("callsign") callsign: String): CallsignResponse
}
