package eu.darken.apl.main.core.query

import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.server.api.UsageUpdate
import java.time.Instant

/**
 * The evidence a response was answered from. [receivedAtElapsed] is the monotonic reading when the
 * response arrived, ages are computed against it so a re-delivered snapshot cannot look fresher.
 */
data class QuerySnapshot(
    val serverTime: Instant,
    val receivedAtElapsed: Long,
    val sourceTime: Instant,
    val fetchedAt: Instant,
    val expiresAt: Instant,
    val complete: Boolean,
    val stale: Boolean,
    val unpositionedAircraft: Int,
)

sealed interface TermOutcome {
    data class Answered(
        val aircraft: List<Aircraft>,
        val complete: Boolean,
        val capped: Boolean,
        val totalMatching: Int?,
        val expiresAt: Instant?,
        val charged: Boolean,
    ) : TermOutcome

    /** invalid_request | daily_allowance_exhausted | result_expired | access_changed | tier_restricted */
    data class Rejected(val code: String) : TermOutcome
}

sealed interface WatchOutcome {
    data class Matched(
        val aircraft: List<Aircraft>,
        val totalMatching: Int?,
        val capped: Boolean,
        val expiresAt: Instant?,
    ) : WatchOutcome

    data class Absent(val expiresAt: Instant?) : WatchOutcome

    /** data_incomplete | stale_observations | null. Never a disappearance. */
    data class Inconclusive(val reason: String?) : WatchOutcome

    /** invalid_request | daily_allowance_exhausted | tier_restricted | result_expired | access_changed */
    data class Rejected(val code: String) : WatchOutcome
}

data class BatchResult<O>(
    val operationId: String,
    val replayed: Boolean,
    val snapshot: QuerySnapshot?,
    val outcomes: List<O>,
    val usage: UsageUpdate,
)

data class ViewingSnapshot(
    val aircraft: List<Aircraft>,
    val complete: Boolean,
    val capped: Boolean,
    val totalMatching: Int?,
    val snapshot: QuerySnapshot,
    val usage: UsageUpdate,
)
