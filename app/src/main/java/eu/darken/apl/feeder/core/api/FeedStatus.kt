package eu.darken.apl.feeder.core.api

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class FeedStatus(
    @SerialName("beast_clients") val beastClients: List<BeastClient>,
    @SerialName("mlat_clients") val mlatClients: List<MlatClient>,
) {

    @Serializable
    data class BeastClient(
        @Contextual @SerialName("uuid") val uuid: UUID,
        @SerialName("host") val host: String,
    )

    @Serializable
    data class MlatClient(
        @SerialName("user") val user: String,
        @Contextual @SerialName("uuid") val uuid: UUID?,
        @SerialName("lat") val latitude: Double,
        @SerialName("lon") val longitude: Double,
    )
}