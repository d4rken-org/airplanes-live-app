package eu.darken.apl.common.flight.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path

interface HexdbApi {

    @Serializable
    data class RouteResponse(
        @SerialName("flight") val flight: String? = null,
        @SerialName("route") val route: String? = null,
    )

    @GET("api/v1/route/icao/{callsign}")
    suspend fun getByCallsign(@Path("callsign") callsign: String): RouteResponse
}
