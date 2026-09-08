package eu.darken.apl.server.api

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface ServerApi {

    @POST("api/v1/enrollment/challenge")
    suspend fun challenge(): ChallengeResponse

    @POST("api/v1/installations")
    suspend fun enroll(@Body request: EnrollRequest): TokenResponse

    @POST("api/v1/sessions/refresh")
    suspend fun refresh(@Body request: RefreshRequest): TokenResponse

    @GET("api/v1/access")
    suspend fun access(@Header("Authorization") authorization: String): AccessResponse

    @POST("api/v1/feeder")
    suspend fun registerFeeder(
        @Header("Authorization") authorization: String,
        @Body request: RegisterFeederRequest,
    ): FeederStatusResponse

    @GET("api/v1/feeder")
    suspend fun feederStatus(@Header("Authorization") authorization: String): FeederStatusResponse

    @DELETE("api/v1/feeder")
    suspend fun unlinkFeeder(@Header("Authorization") authorization: String): FeederStatusResponse

    @POST("api/v1/aircraft/map")
    suspend fun map(
        @Header("Authorization") authorization: String,
        @Body request: MapRequest,
    ): ViewingResponse

    @POST("api/v1/aircraft/ar")
    suspend fun ar(
        @Header("Authorization") authorization: String,
        @Body request: ArRequest,
    ): ViewingResponse

    @POST("api/v1/aircraft/search")
    suspend fun search(
        @Header("Authorization") authorization: String,
        @Body request: SearchBatchRequest,
    ): BatchResponse

    @POST("api/v1/watches/check")
    suspend fun checkWatches(
        @Header("Authorization") authorization: String,
        @Body request: WatchBatchRequest,
    ): BatchResponse
}
