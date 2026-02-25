package eu.darken.apl.main.core.api

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface for the Airplanes.Live Pro REST API at rest.api.airplanes.live.
 * Uses query parameter-based endpoints. Requires auth header (added via interceptor).
 * Reuses response data classes from [AirplanesLiveApi].
 */
interface AirplanesLiveRestApi {

    @GET(".")
    suspend fun findByHex(
        @Query("find_hex") hexes: String,
        @Query("jv2") jv2: String = "",
    ): AirplanesLiveApi.HexesResponse

    @GET(".")
    suspend fun findBySquawk(
        @Query("all") all: String = "",
        @Query("filter_squawk") squawk: String,
        @Query("jv2") jv2: String = "",
    ): AirplanesLiveApi.SquawksResponse

    @GET(".")
    suspend fun findByCallsign(
        @Query("find_callsign") callsigns: String,
        @Query("jv2") jv2: String = "",
    ): AirplanesLiveApi.CallsignsResponse

    @GET(".")
    suspend fun findByRegistration(
        @Query("find_reg") registrations: String,
        @Query("jv2") jv2: String = "",
    ): AirplanesLiveApi.RegistrationsResponse

    @GET(".")
    suspend fun findByAirframe(
        @Query("find_type") types: String,
        @Query("jv2") jv2: String = "",
    ): AirplanesLiveApi.AirframesResponse

    @GET(".")
    suspend fun getMilitary(
        @Query("all") all: String = "",
        @Query("filter_mil") mil: String = "",
        @Query("jv2") jv2: String = "",
    ): AirplanesLiveApi.MilitaryResponse

    @GET(".")
    suspend fun getLADD(
        @Query("all") all: String = "",
        @Query("filter_ladd") ladd: String = "",
        @Query("jv2") jv2: String = "",
    ): AirplanesLiveApi.LADDResponse

    @GET(".")
    suspend fun getPIA(
        @Query("all") all: String = "",
        @Query("filter_pia") pia: String = "",
        @Query("jv2") jv2: String = "",
    ): AirplanesLiveApi.PIAResponse

    @GET(".")
    suspend fun findByCircle(
        @Query("circle", encoded = true) circle: String,
        @Query("jv2") jv2: String = "",
    ): AirplanesLiveApi.AirframesResponse
}
