package eu.darken.apl.server.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** [pauseReason] is set exactly when [status] is "paused". */
@Serializable
data class WatchListWatch(
    val localId: String,
    val watchId: String,
    val type: String,
    val value: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radiusKm: Double? = null,
    val revision: Int,
    val status: String,
    val pauseReason: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val presence: String,
    val lastSeenAt: Long? = null,
    val lastSeenAircraftId: String? = null,
    val eventSeq: Long,
    val backfill: String,
)

@Serializable
data class WatchListLimits(
    val maxActiveWatches: Int,
    val maxStoredWatches: Int,
)

@Serializable
data class WatchListResponse(
    val serverTime: Long,
    val revision: Long,
    val state: String,
    val limits: WatchListLimits,
    val watches: List<WatchListWatch>,
    val cursor: Long,
    val leasePausesAt: Long? = null,
    val deletesAt: Long? = null,
)

/** [baseline] is "present" or "absent". */
@Serializable
data class WatchUpsertRequest(
    val type: String,
    val value: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radiusKm: Double? = null,
    val expectedWatchRevision: Int? = null,
    val baseline: String? = null,
)

@Serializable
data class WatchMutationResponse(
    val listRevision: Long,
    val watch: WatchListWatch,
)

/** [created] is true when the server answered 201, the watch did not exist before. */
data class WatchUpsertResult(
    val created: Boolean,
    val mutation: WatchMutationResponse,
)

/** [kind] is "matched", "cleared" or "coverage_gap"; a gap carries [gapFrom] and [gapUntil] instead of a watch. */
@Serializable
data class WatchEvent(
    val seq: Long,
    val kind: String,
    val observedAt: Long,
    val watchId: String? = null,
    val localId: String? = null,
    val revision: Int? = null,
    val aircraftIds: List<String>? = null,
    val totalMatching: Int? = null,
    val gapFrom: Long? = null,
    val gapUntil: Long? = null,
)

@Serializable
data class WatchEventsResponse(
    val serverTime: Long,
    val events: List<WatchEvent>,
    val cursor: Long,
    val hasMore: Boolean,
    val listRevision: Long,
)

@Serializable
data class WatchRollup(
    val seq: Long,
    val watchId: String,
    val localId: String,
    val revision: Int,
    val hour: Long,
    val source: String,
    val evaluationBasis: String,
    val aircraftCount: Int,
    val coverage: String,
    val coveredMinutes: Int? = null,
    @SerialName("final") val isFinal: Boolean,
    val lastSeenAt: Long? = null,
    val lastSeenAircraftId: String? = null,
)

@Serializable
data class WatchRollupsResponse(
    val serverTime: Long,
    val rollups: List<WatchRollup>,
    val cursor: Long,
    val hasMore: Boolean,
)
