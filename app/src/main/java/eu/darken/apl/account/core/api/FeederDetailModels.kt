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
    // Absent on deployments predating the stats lane, null when diagnostics reporting is off —
    // both mean "unavailable" per the API contract.
    @SerialName("stats") val stats: FeederStatsWire? = null,
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
    @Contextual @SerialName("observed_at") val observedAt: Instant? = null,
    // "fresh" | "stale" | "expired"; raw values above stay present even when expired.
    @SerialName("freshness") val freshness: String? = null,
    @SerialName("connection_type") val connectionType: String? = null,
    // Server-computed per-metric severity ("ok" | "warn" | "err" | null = not classifiable),
    // keyed by the metric field names. Replaces client-side threshold mirroring.
    @SerialName("severity") val severity: Map<String, String?> = emptyMap(),
)

/**
 * The feeder-attested stats lane. Its windows carry their OWN grid metadata — the stats lane is
 * cached independently server-side and its anchor can differ from `history`'s by one bucket, so
 * the two grids must never be merged onto one time axis.
 */
@Serializable
data class FeederStatsWire(
    // "configured" | "inferred" | "none" — current provenance of the range data; both range
    // series can still carry samples when provenance changed mid-window.
    @SerialName("range_source") val rangeSource: String? = null,
    @SerialName("history") val history: Map<String, FeederStatsWindowWire> = emptyMap(),
)

@Serializable
data class FeederStatsWindowWire(
    @Contextual @SerialName("start") val start: Instant,
    @Contextual @SerialName("end") val end: Instant,
    @SerialName("bucket_seconds") val bucketSeconds: Int,
    // Flat metric -> values (no family nesting, unlike the diagnostics history).
    @SerialName("series") val series: Map<String, List<Double?>> = emptyMap(),
)

@Serializable
data class FeederHistoryWindowWire(
    @Contextual @SerialName("start") val start: Instant,
    @Contextual @SerialName("end") val end: Instant,
    @SerialName("bucket_seconds") val bucketSeconds: Int,
    @SerialName("series") val series: Map<String, Map<String, List<Double?>>> = emptyMap(),
)
