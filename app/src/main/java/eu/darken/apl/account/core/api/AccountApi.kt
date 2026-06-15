package eu.darken.apl.account.core.api

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Header
import java.time.Instant

/**
 * airplanes.live account API under `/api/v1/me/`. Bearer token is passed per-call via the
 * Authorization header (not an interceptor) so the rotating-token fetch is explicit.
 *
 * Trailing slashes are exact server-side: `/api/v1/me/` has one, `/api/v1/me/feeders` does not.
 */
interface AccountApi {

    @GET("api/v1/me/")
    suspend fun getIdentity(@Header("Authorization") authorization: String): Identity

    @GET("api/v1/me/feeders")
    suspend fun getFeeders(@Header("Authorization") authorization: String): FeedersResponse

    @Serializable
    data class Identity(
        @SerialName("id") val id: Long,
        @SerialName("email") val email: String? = null,
        @SerialName("handle") val handle: String? = null,
        @SerialName("display_name") val displayName: String? = null,
    )

    @Serializable
    data class FeedersResponse(
        @SerialName("feeders") val feeders: List<OwnedFeeder> = emptyList(),
    )

    @Serializable
    data class OwnedFeeder(
        @SerialName("feeder_id") val feederId: String,
        @SerialName("name") val name: String? = null,
        // Raw string on purpose: `ignoreUnknownKeys` does NOT protect unknown enum *values*,
        // so we map this to the domain FeederStatus (with UNKNOWN fallback) outside serialization.
        @SerialName("status") val status: String? = null,
        @Contextual @SerialName("last_seen") val lastSeen: Instant? = null,
        @SerialName("country") val country: String? = null,
        @SerialName("position") val position: Position? = null,
    )

    @Serializable
    data class Position(
        @SerialName("lat") val lat: Double,
        @SerialName("lon") val lon: Double,
    )
}
