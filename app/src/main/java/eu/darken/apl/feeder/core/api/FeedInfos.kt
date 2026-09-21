package eu.darken.apl.feeder.core.api

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class FeedInfos(
    @SerialName("beast_clients") val beast: List<Beast>,
    @SerialName("mlat_clients") val mlat: List<Mlat>,
) {

    @Serializable
    data class Beast(
        @Contextual @SerialName("uuid") val uuid: UUID,
        @SerialName("avg_kbit_s") val avgKBitsPerSecond: Double,
        @SerialName("conn_time") val connTime: Long,
        @SerialName("msgs_s") val messageRate: Double,
        @SerialName("pos_s") val positionRate: Double,
        @SerialName("rtt") val recentRtt: Long,
        @SerialName("pos") val positions: Int,
    )

    @Serializable
    data class Mlat(
        @SerialName("user") val user: String,
        @Contextual @SerialName("uuid") val uuid: UUID?,
        @SerialName("message_rate") val messageRate: Double,
        @SerialName("peer_count") val peerCount: Int,
        @SerialName("bad_sync_timeout") val badSyncTimeout: Long,
        @SerialName("outlier_percent") val outlierPercent: Float,
        @SerialName("bad_peer_list") val badPeerList: List<String>,
        @SerialName("sync_interest") val syncInterest: List<String>,
        @SerialName("mlat_interest") val mlatInterest: List<String>,
    )
}
