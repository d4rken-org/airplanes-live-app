package eu.darken.apl.server.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A field absent from a payload decodes to the default declared here, so each default has to match
 * the value the wire contract assigns when the field is omitted.
 */

@Serializable
data class ChallengeResponse(
    val challenge: String,
    val expiresInSeconds: Long,
)

@Serializable
data class EnrollRequest(
    val challenge: String,
    val publicKey: String,
    val signature: String,
    val variant: String,
    val appVersion: Int,
)

@Serializable
data class RefreshRequest(
    val refreshToken: String,
    val operationId: String,
    val timestamp: Long,
    val signature: String,
)

@Serializable
data class TokenResponse(
    val installationId: String,
    val refreshToken: String,
    val accessToken: String,
    val accessExpiresAt: Long,
    val refreshExpiresAt: Long,
)

@Serializable
data class TierPolicy(
    val viewingIntervalSeconds: Int,
    val viewingBurst: Int,
    val viewingPerDay: Int,
    val searchesPerDay: Int,
    val watchesPerDay: Int,
    val concurrency: Int = 3,
    val maxIdentifierBatch: Int = 100,
    val maxRadiusNm: Int,
    val maxArResults: Int = 300,
    val maxMapResults: Int? = null,
    val maxLocationWatchRadiusKm: Int? = null,
    val maxLocationWatchResults: Int = 100,
    val watchTypes: List<String>,
    val specialCategoryPreviewResults: Int? = null,
    val maxBroadResults: Int? = null,
    val historyReplay: Boolean = false,
    val maxActiveWatches: Int? = null,
    val maxStoredWatches: Int? = null,
)

@Serializable
data class Allowance(
    val limit: Int,
    val used: Int,
    val reserved: Int,
    val remaining: Int,
)

@Serializable
data class Usage(
    val resetsAt: Long,
    val viewing: Allowance,
    val search: Allowance,
    val watch: Allowance,
)

@Serializable
data class RequestRate(
    val perSecond: Double,
    val burst: Int,
)

@Serializable
data class RequestLimits(
    val viewing: RequestRate,
    val search: RequestRate,
    val watch: RequestRate,
    val concurrency: Int,
)

@Serializable
data class UsageUpdate(
    val scope: String,
    val bucket: String,
    val resetsAt: Long,
    val allowance: Allowance,
)

@Serializable
data class AccessResponse(
    val tier: String,
    val restricted: Boolean,
    val allowanceScope: String,
    val limits: TierPolicy,
    val usage: Usage,
    val installationRequests: RequestLimits,
    val entitlementRequests: RequestLimits? = null,
    val aircraftOperations: List<String> = listOf("search", "ar", "map", "watches"),
)

@Serializable
data class MapRequest(
    val south: Double,
    val north: Double,
    val west: Double,
    val east: Double,
    val selectedAircraftId: String? = null,
    val pinnedAircraftIds: List<String> = emptyList(),
    /** Newest trail point the app holds (epoch ms), 0 for the whole trail, null for none. */
    val trailSince: Long? = null,
)

@Serializable
data class ArRequest(
    val latitude: Double,
    val longitude: Double,
    val radiusNm: Double = 25.0,
)

@Serializable
data class SearchTerm(
    val text: String = "",
    val categories: List<String> = emptyList(),
)

@Serializable
data class SearchBatchRequest(
    val operationId: String,
    val terms: List<SearchTerm>,
)

@Serializable
data class WatchDefinition(
    val type: String,
    val value: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radiusKm: Double? = null,
)

@Serializable
data class WatchBatchRequest(
    val operationId: String,
    val watches: List<WatchDefinition>,
)

@Serializable
data class AircraftPosition(
    val latitude: Double,
    val longitude: Double,
    val observedAt: Long? = null,
)

@Serializable
data class AircraftObservation(
    val id: String,
    val messageObservedAt: Long? = null,
    val position: AircraftPosition? = null,
    val callsign: String? = null,
    val registration: String? = null,
    val aircraftType: String? = null,
    val description: String? = null,
    val operator: String? = null,
    val squawk: String? = null,
    val emergency: String? = null,
    val source: String? = null,
    val barometricAltitudeFeet: Double? = null,
    val geometricAltitudeFeet: Double? = null,
    val onGround: Boolean? = null,
    val groundSpeedKnots: Double? = null,
    val indicatedAirspeedKnots: Double? = null,
    val trackDegrees: Double? = null,
    val trueHeadingDegrees: Double? = null,
    val verticalRateFeetPerMinute: Double? = null,
    val outsideTemperatureCelsius: Double? = null,
    val military: Boolean = false,
    val ladd: Boolean = false,
    val pia: Boolean = false,
)

@Serializable
data class QueryMetadata(
    val sourceTime: Long,
    val fetchedAt: Long,
    val expiresAt: Long,
    val complete: Boolean,
    val stale: Boolean,
    val unpositionedAircraft: Int = 0,
)

@Serializable
data class QueryError(
    val code: String,
    val retryAfterSeconds: Long? = null,
)

@Serializable
data class QueryOutcome(
    val index: Int,
    val status: String,
    val aircraftIds: List<String> = emptyList(),
    val totalMatching: Int? = null,
    val capped: Boolean = false,
    val complete: Boolean = false,
    val charged: Boolean = false,
    val expiresAt: Long? = null,
    val evaluationBasis: String? = null,
    val error: QueryError? = null,
)

@Serializable
data class BatchResponse(
    val operationId: String,
    val serverTime: Long,
    val completedAt: Long,
    val operationExpiresAt: Long,
    val replayed: Boolean = false,
    val metadata: QueryMetadata? = null,
    val outcomes: List<QueryOutcome>,
    val aircraft: List<AircraftObservation>,
    val usage: UsageUpdate,
)

@Serializable
data class ViewingResponse(
    val serverTime: Long,
    val metadata: QueryMetadata,
    val aircraft: List<AircraftObservation>,
    val totalMatching: Int? = null,
    val capped: Boolean,
    val usage: UsageUpdate,
)

/** What the map draws for each aircraft; the selected one's full record arrives in [MapResponse.selected]. */
@Serializable
data class MapAircraft(
    val id: String,
    val position: AircraftPosition,
    val callsign: String? = null,
    val aircraftType: String? = null,
    val altitudeFeet: Double? = null,
    val onGround: Boolean? = null,
    val trackDegrees: Double? = null,
    val groundSpeedKnots: Double? = null,
    val military: Boolean = false,
    val ladd: Boolean = false,
    val pia: Boolean = false,
    /** ADS-B emitter category, e.g. A7 for rotorcraft or B1 for gliders. */
    val category: String? = null,
)

@Serializable
data class TrailPoint(
    val latitude: Double,
    val longitude: Double,
    val altitudeFeet: Double? = null,
    val observedAt: Long,
)

@Serializable
data class MapResponse(
    val serverTime: Long,
    val metadata: QueryMetadata,
    val aircraft: List<MapAircraft>,
    val selected: AircraftObservation? = null,
    val selectedTrail: List<TrailPoint>? = null,
    val trailReset: Boolean = false,
    val totalMatching: Int? = null,
    val capped: Boolean,
    val usage: UsageUpdate,
)

@Serializable
data class RegisterFeederRequest(
    val feederId: String,
)

@Serializable
data class LinkedFeeder(
    val feederId: String,
    val status: String,
    val networkVerified: Boolean,
    val linkedAt: Long,
    val checkedAt: Long,
    val lastActiveAt: Long,
    val validUntil: Long,
    val nextCheckAt: Long,
    val eligible: Boolean,
)

@Serializable
data class FeederStatusResponse(
    val tier: String,
    val restricted: Boolean,
    val limits: TierPolicy,
    val feeder: LinkedFeeder? = null,
)

@Serializable
data class ApiProblem(
    val type: String? = null,
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    val code: String,
    val retryAfterSeconds: Long? = null,
)

/** Where a watch history evaluation resumes; sent back unchanged, never interpreted by the app. */
@Serializable
data class HistoryCursor(
    val hour: Long,
    val afterAircraftId: String? = null,
    val aircraftCount: Int = 0,
    val hourFinal: Boolean = false,
    val lastSeenAt: Long? = null,
    val lastSeenAircraftId: String? = null,
)

@Serializable
data class HistoryWatchDefinition(
    val type: String,
    val value: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radiusKm: Double? = null,
    val cursor: HistoryCursor? = null,
)

/** [from] and [until] are whole UTC hours in epoch milliseconds, half-open. */
@Serializable
data class HistoryWatchRequest(
    val operationId: String,
    val from: Long,
    val until: Long,
    val watches: List<HistoryWatchDefinition>,
)

@Serializable
data class HistoryWatchHour(
    val hour: Long,
    val aircraftCount: Int,
    val coverage: String,
    @SerialName("final") val isFinal: Boolean,
)

@Serializable
data class HistoryWatchOutcome(
    val index: Int,
    val status: String,
    val evaluationBasis: String? = null,
    val hours: List<HistoryWatchHour> = emptyList(),
    val lastSeenAt: Long? = null,
    val lastSeenAircraftId: String? = null,
    val computedUntil: HistoryCursor? = null,
    val error: QueryError? = null,
)

@Serializable
data class HistoryWatchResponse(
    val operationId: String,
    val serverTime: Long,
    val from: Long,
    val until: Long,
    val outcomes: List<HistoryWatchOutcome>,
    val usage: UsageUpdate,
)

/** [from] and [until] in epoch milliseconds, half-open; the server clips them to what it keeps. */
@Serializable
data class HistoryAircraftRequest(
    val aircraftId: String,
    val from: Long,
    val until: Long,
)

@Serializable
data class HistoryAircraftResponse(
    val serverTime: Long,
    val aircraftId: String,
    val from: Long,
    val until: Long,
    /** Consecutive points further apart than this are not joined. */
    val maxGapMs: Long,
    /** Ascending, with the neighbour just outside a bound when the path crosses it. */
    val points: List<TrailPoint>,
    /** Ordered by kind, start and value; times clipped into the range. */
    val runs: List<HistoryRunSpan> = emptyList(),
    val registration: String? = null,
    val aircraftType: String? = null,
    val category: String? = null,
    /** One per UTC hour overlapping the range. */
    val hours: List<HistoryHourStatus> = emptyList(),
    val usage: UsageUpdate,
)

/** A stretch with one callsign or squawk value, [kind] is [KIND_CALLSIGN] or "squawk". */
@Serializable
data class HistoryRunSpan(
    val kind: String,
    val value: String,
    val startAt: Long,
    val lastSeenAt: Long,
) {
    companion object {
        const val KIND_CALLSIGN = "callsign"
    }
}

/** [hour] is the UTC hour's start; [coverage] is [COVERAGE_FULL], [COVERAGE_PARTIAL] or [COVERAGE_NONE]. */
@Serializable
data class HistoryHourStatus(
    val hour: Long,
    val coverage: String,
    @SerialName("final") val isFinal: Boolean = false,
) {
    companion object {
        const val COVERAGE_FULL = "full"
        const val COVERAGE_PARTIAL = "partial"
        const val COVERAGE_NONE = "none"
    }
}
