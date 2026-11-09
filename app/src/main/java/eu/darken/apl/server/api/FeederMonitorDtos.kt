package eu.darken.apl.server.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FeederMonitorLimits(
    val maxActiveFeeders: Int,
    val maxStoredFeeders: Int,
)

/** Every field is optional, the server leaves out what it has not measured. */
@Serializable
data class FeederMonitorLatest(
    val beastClients: Int? = null,
    val msgsPerSecond: Double? = null,
    val positionsPerSecond: Double? = null,
    val avgKbitPerSecond: Double? = null,
    val connTime: Long? = null,
    val positions: Long? = null,
    val mlatClients: Int? = null,
    val mlatMessageRate: Double? = null,
    val mlatPeers: Int? = null,
    val mlatBadSyncTimeout: Long? = null,
    val mlatOutlierPercent: Double? = null,
)

/**
 * [status] is "active" or "paused", [pauseReason] "lease", "restricted" or "over_limit".
 * [health] is "online", "offline" or "unknown", [unknownReason] "paused", "pending", "upstream" or
 * "outage_suspected". Kept as strings so a value this build does not know still decodes.
 */
@Serializable
data class FeederMonitorEntry(
    val feederId: String,
    val monitorId: String,
    val offlineAfterMinutes: Int,
    val status: String,
    val pauseReason: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val health: String,
    val unknownReason: String? = null,
    val healthSince: Long? = null,
    /** Codes such as "mlat_missing", "deaf" or "duplicate_uuid". */
    val problems: List<String> = emptyList(),
    val insufficientEvidence: List<String> = emptyList(),
    val lastSampleAt: Long? = null,
    val lastSuccessAt: Long? = null,
    val lastConnectedAt: Long? = null,
    val latest: FeederMonitorLatest? = null,
    val eventSeq: Long,
)

/** [state] is "active" or "expired"; [feeders] are in registration order. */
@Serializable
data class FeederMonitorListResponse(
    val serverTime: Long,
    val revision: Long,
    val state: String,
    val limits: FeederMonitorLimits,
    val feeders: List<FeederMonitorEntry>,
    val cursor: Long,
    val sampleIntervalSeconds: Int? = null,
    val leasePausesAt: Long? = null,
    val deletesAt: Long? = null,
)

/** An empty body keeps the server's default threshold. */
@Serializable
data class FeederMonitorUpsertRequest(
    val offlineAfterMinutes: Int? = null,
)

@Serializable
data class FeederMonitorMutationResponse(
    val revision: Long,
    val feeder: FeederMonitorEntry,
)

/** [created] is true when the server answered 201, the monitor did not exist before. */
data class FeederMonitorUpsertResult(
    val created: Boolean,
    val mutation: FeederMonitorMutationResponse,
)

/**
 * [kind] is "offline" (with [since]), "online" (with [offlineSince]), "problem_started" or
 * "problem_cleared" (with [problem]), or "coverage_gap" (with [gapFrom] and [gapUntil]).
 */
@Serializable
data class FeederMonitorEvent(
    val seq: Long,
    val kind: String,
    val observedAt: Long,
    val feederId: String,
    val monitorId: String,
    val since: Long? = null,
    val offlineSince: Long? = null,
    val problem: String? = null,
    val gapFrom: Long? = null,
    val gapUntil: Long? = null,
)

@Serializable
data class FeederMonitorEventsResponse(
    val serverTime: Long,
    val events: List<FeederMonitorEvent>,
    val cursor: Long,
    val hasMore: Boolean,
    val revision: Long,
)

@Serializable
data class FeederHistoryHour(
    val hour: Long,
    val samples: Int = 0,
    val failures: Int = 0,
    val connectedSamples: Int = 0,
    val msgsPerSecondAvg: Double? = null,
    val msgsPerSecondMin: Double? = null,
    val msgsPerSecondMax: Double? = null,
    val positionsPerSecondAvg: Double? = null,
    val avgKbitPerSecond: Double? = null,
    val mlatSamples: Int = 0,
    val mlatPeersAvg: Double? = null,
    val mlatOutlierPercentAvg: Double? = null,
    val badSyncSamples: Int = 0,
    val reconnects: Int = 0,
    @SerialName("final") val isFinal: Boolean,
)

@Serializable
data class FeederHistoryResponse(
    val serverTime: Long,
    val feederId: String,
    val hours: List<FeederHistoryHour>,
)
