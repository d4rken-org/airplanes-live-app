package eu.darken.apl.common.github

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import java.time.OffsetDateTime

interface GithubApi {

    @Serializable
    data class ReleaseInfo(
        @SerialName("name") val name: String,
        @SerialName("tag_name") val tagName: String,
        @SerialName("html_url") val htmlUrl: String,
        @Contextual @SerialName("published_at") val publishedAt: OffsetDateTime,
        @SerialName("body") val body: String,
        @SerialName("assets") val assets: List<Asset>,
    ) {
        @Serializable
        data class Asset(
            @SerialName("id") val id: Long,
            @SerialName("name") val name: String,
            @SerialName("label") val label: String?,
            @SerialName("size") val size: Long,
            @SerialName("content_type") val contentType: String,
            @SerialName("browser_download_url") val downloadUrl: String,
        )
    }

    @GET("/repos/{owner}/{repo}/releases/latest")
    suspend fun latestRelease(@Path("owner") owner: String, @Path("repo") repo: String): ReleaseInfo
}
