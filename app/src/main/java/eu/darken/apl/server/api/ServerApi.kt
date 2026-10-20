package eu.darken.apl.server.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

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
    ): MapResponse

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

    @POST("api/v1/history/aircraft")
    suspend fun historyAircraft(
        @Header("Authorization") authorization: String,
        @Body request: HistoryAircraftRequest,
    ): HistoryAircraftResponse

    @POST("api/v1/history/watches")
    suspend fun historyWatches(
        @Header("Authorization") authorization: String,
        @Body request: HistoryWatchRequest,
    ): HistoryWatchResponse

    @GET("api/v1/watchlist")
    suspend fun watchList(@Header("Authorization") authorization: String): WatchListResponse

    @PUT("api/v1/watchlist/watches/{localId}")
    suspend fun upsertWatch(
        @Header("Authorization") authorization: String,
        @Path("localId") localId: String,
        @Body request: WatchUpsertRequest,
    ): Response<WatchMutationResponse>

    @DELETE("api/v1/watchlist/watches/{localId}")
    suspend fun deleteWatch(
        @Header("Authorization") authorization: String,
        @Path("localId") localId: String,
    ): Response<Unit>

    @GET("api/v1/watchlist/events")
    suspend fun watchEvents(
        @Header("Authorization") authorization: String,
        @Query("after") after: Long,
        @Query("limit") limit: Int?,
    ): WatchEventsResponse

    @GET("api/v1/watchlist/rollups")
    suspend fun watchRollups(
        @Header("Authorization") authorization: String,
        @Query("after") after: Long,
        @Query("limit") limit: Int?,
    ): WatchRollupsResponse
}
