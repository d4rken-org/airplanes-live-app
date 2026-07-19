package eu.darken.apl.account.core.api

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Wire models for `GET api/v1/me/feeders/{feeder_id}`. Kept in their own file (the DTO family is
 * large) but in the api package next to [AccountApi], whose auth/error plumbing the call reuses.
 *
 * The `live` block is fully typed. The `history` block is deliberately generic
 * (`family -> metric -> values`): the server adds metric families over time (aircraft stats is
 * planned) and a map-shaped history means new families need zero parsing changes here.
 */
@Serializable
data class FeederDetailResponse(
    @SerialName("feeder_id") val feederId: String,
    // Identity fields duplicate the list endpoint; the domain mapper ignores them so the feeder
    // list stays the single source of truth for status/name and the two can never disagree.
    @SerialName("name") val name: String? = null,
    @SerialName("status") val status: String? = null,
    @Contextual @SerialName("last_seen") val lastSeen: Instant? = null,
    @SerialName("country") val country: String? = null,
    @SerialName("position") val position: AccountApi.Position? = null,
    @SerialName("live") val live: FeederLiveWire? = null,
    @SerialName("history") val history: Map<String, FeederHistoryWindowWire> = emptyMap(),
)

@Serializable
data class FeederLiveWire(
    @SerialName("feed") val feed: FeederFeedWire? = null,
    @SerialName("mlat") val mlat: FeederMlatWire? = null,
    // Absent when the feeder has diagnostics reporting disabled.
    @SerialName("device") val device: FeederDeviceWire? = null,
)

@Serializable
data class FeederFeedWire(
    @SerialName("connected") val connected: Boolean = false,
    @SerialName("messages_per_sec") val messagesPerSec: Double? = null,
    @SerialName("positions_per_sec") val positionsPerSec: Double? = null,
    @SerialName("rtt_ms") val rttMs: Int? = null,
    @SerialName("connected_for_seconds") val connectedForSeconds: Long? = null,
)

@Serializable
data class FeederMlatWire(
    @SerialName("connected") val connected: Boolean = false,
    @SerialName("peer_count") val peerCount: Int? = null,
    @SerialName("message_rate") val messageRate: Double? = null,
    @SerialName("outlier_percent") val outlierPercent: Double? = null,
    @SerialName("bad_sync") val badSync: Boolean? = null,
    @SerialName("interest_count") val interestCount: Int? = null,
)

@Serializable
data class FeederDeviceWire(
    @SerialName("cpu_temperature_c") val cpuTemperatureC: Double? = null,
    @SerialName("memory_used_percent") val memoryUsedPercent: Double? = null,
    @SerialName("disk_used_percent") val diskUsedPercent: Double? = null,
    @SerialName("wifi_rssi_dbm") val wifiRssiDbm: Int? = null,
    @SerialName("clock_skew_seconds") val clockSkewSeconds: Double? = null,
)

@Serializable
data class FeederHistoryWindowWire(
    @Contextual @SerialName("start") val start: Instant,
    @Contextual @SerialName("end") val end: Instant,
    @SerialName("bucket_seconds") val bucketSeconds: Int,
    @SerialName("series") val series: Map<String, Map<String, List<Double?>>> = emptyMap(),
)
