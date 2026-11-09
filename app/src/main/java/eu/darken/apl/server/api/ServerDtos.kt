package eu.darken.apl.server.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A field absent from a payload decodes to the default declared here, so each default has to match
 * the value the wire contract assigns when the field is omitted.
 */

@Serializable
data class MetaResponse(
    val recommendedAppVersion: Int,
    val minSupportedAppVersion: Int,
)

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
    val maxActiveFeederMonitors: Int? = null,
    val maxStoredFeederMonitors: Int? = null,
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
    /** Leg start (epoch ms) the app applies to the held trail, null while it applies none. */
    val trailLegStart: Long? = null,
    val militaryOnly: Boolean = false,
    /** Aircraft kinds to include, e.g. "helicopter"; empty means all kinds. */
    val kinds: List<String> = emptyList(),
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
    val minAltitudeFt: Int? = null,
    val maxAltitudeFt: Int? = null,
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
    /** Only on the ground or without [trackDegrees]; older servers omit it. */
    val trueHeadingDegrees: Double? = null,
    val groundSpeedKnots: Double? = null,
    /** Only present as a watch match, not the aircraft's squawk in general. */
    val squawk: String? = null,
    val military: Boolean = false,
    val ladd: Boolean = false,
    val pia: Boolean = false,
    /** ADS-B emitter category, e.g. A7 for rotorcraft or B1 for gliders. */
    val category: String? = null,
    /** Aircraft kind, e.g. "helicopter" or "unknown"; older servers omit it. */
    val kind: String? = null,
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
    /** Where the selected aircraft's latest leg starts (epoch ms), trail points before it are dropped. */
    val trailLegStart: Long? = null,
    val totalMatching: Int? = null,
    val capped: Boolean,
    val cutoffAltitudeFeet: Double? = null,
    val usage: UsageUpdate,
    /** Only without [selected]: where the selection was last known to be, never a live position. */
    val selectedLastSeen: SelectedLastSeen? = null,
)

@Serializable
data class SelectedLastSeen(
    val latitude: Double,
    val longitude: Double,
    val observedAt: Long,
    val trackDegrees: Double? = null,
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

@Serializable
data class RouteLookupRequest(
    val aircraftId: String,
    val callsign: String,
)

/** [status] is [STATUS_FOUND] with a [route], or [STATUS_NONE] without one. */
@Serializable
data class RouteLookupResponse(
    val serverTime: Long,
    val callsign: String,
    val status: String,
    val route: RouteDto? = null,
) {
    companion object {
        const val STATUS_FOUND = "found"
        const val STATUS_NONE = "none"
    }
}

/** [source] is [SOURCE_VRS], [SOURCE_ADSBDB] or [SOURCE_HEXDB]. */
@Serializable
data class RouteDto(
    val source: String,
    val origin: RouteAirportDto,
    val destination: RouteAirportDto,
) {
    companion object {
        const val SOURCE_VRS = "vrs"
        const val SOURCE_ADSBDB = "adsbdb"
        const val SOURCE_HEXDB = "hexdb"
    }
}

/** [country] is an ISO 3166-1 alpha-2 code. */
@Serializable
data class RouteAirportDto(
    val icao: String,
    val iata: String? = null,
    val name: String,
    val country: String? = null,
    val latitude: Double,
    val longitude: Double,
)

/** Up to 10 [queries], each answered separately in [AirportSearchResponse.results], duplicates included. */
@Serializable
data class AirportSearchRequest(
    val queries: List<String>,
)

@Serializable
data class AirportSearchResponse(
    val serverTime: Long,
    val results: List<AirportSearchResultDto>,
)

/**
 * [index] is the 0-based position of the query this answers. [status] is [STATUS_ANSWERED] with 0 to 20
 * [airports], best first, or [STATUS_SKIPPED] / [STATUS_INVALID_QUERY] when the query was not searched.
 */
@Serializable
data class AirportSearchResultDto(
    val index: Int,
    val status: String,
    val airports: List<AirportSummaryDto> = emptyList(),
    val truncated: Boolean = false,
) {
    companion object {
        const val STATUS_ANSWERED = "answered"

        /** The query was an aircraft hex or squawk. */
        const val STATUS_SKIPPED = "skipped"
        const val STATUS_INVALID_QUERY = "invalid_query"
    }
}

/**
 * [id] is opaque. [type] is [TYPE_LARGE], [TYPE_MEDIUM], [TYPE_SMALL] or a value this build does not know.
 * [country] is an ISO 3166-1 alpha-2 code, [region] an ISO 3166-2 code. [matchedCode] is only set when
 * the query equalled one of the airport's codes, and holds that code.
 */
@Serializable
data class AirportSummaryDto(
    val id: String,
    val name: String,
    val type: String,
    val ident: String,
    val latitude: Double,
    val longitude: Double,
    val municipality: String? = null,
    val country: String? = null,
    val region: String? = null,
    val icao: String? = null,
    val iata: String? = null,
    val local: String? = null,
    val matchedCode: String? = null,
) {
    companion object {
        const val TYPE_LARGE = "large"
        const val TYPE_MEDIUM = "medium"
        const val TYPE_SMALL = "small"
    }
}
