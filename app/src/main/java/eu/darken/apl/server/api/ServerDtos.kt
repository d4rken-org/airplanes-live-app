package eu.darken.apl.server.api

import kotlinx.serialization.Serializable

/**
 * The server serializes with `encodeDefaults = false`, so any value equal to its default never
 * travels on the wire. Defaults here must stay identical to the server's or omitted fields decode
 * to the wrong value.
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
