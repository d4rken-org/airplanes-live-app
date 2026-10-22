package eu.darken.apl.server.api

import android.content.Context
import eu.darken.apl.R
import eu.darken.apl.common.ca.toCaString
import eu.darken.apl.common.error.HasLocalizedError
import eu.darken.apl.common.error.LocalizedError
import java.time.Duration

class ServerApiException(
    val code: String,
    val status: Int,
    val retryAfterSeconds: Long? = null,
    val detail: String? = null,
) : RuntimeException("Server rejected the request: $code (HTTP $status)"), HasLocalizedError {

    override fun getLocalizedError(context: Context): LocalizedError {
        if (code == ServerCodes.APP_UPDATE_REQUIRED) return LocalizedError(
            throwable = this,
            label = R.string.update_prompt_required_title.toCaString(),
            description = R.string.update_required_inline_msg.toCaString(),
        )
        val description = when (code) {
            ServerCodes.INSTALLATION_REVOKED -> R.string.server_error_revoked_msg.toCaString()

            ServerCodes.INSTALLATION_RESTRICTED -> R.string.upgrade_restricted_msg.toCaString()
            ServerCodes.TIER_RESTRICTED -> R.string.server_error_tier_restricted_msg.toCaString()

            ServerCodes.DAILY_ALLOWANCE_EXHAUSTED,
            ServerCodes.BUDGET_EXHAUSTED -> R.string.server_error_allowance_exhausted_msg.toCaString()

            ServerCodes.QUOTA_EXCEEDED,
            ServerCodes.INSTALLATION_RATE_EXCEEDED,
            ServerCodes.ENTITLEMENT_RATE_EXCEEDED,
            ServerCodes.INSTALLATION_CONCURRENCY_EXCEEDED,
            ServerCodes.ENTITLEMENT_CONCURRENCY_EXCEEDED,
            ServerCodes.WATCH_MUTATION_BUDGET_EXCEEDED -> R.string.server_error_rate_limited_msg.toCaString()

            ServerCodes.STORED_WATCH_LIMIT -> R.string.watch_limit_stored_reached_unnumbered_msg.toCaString()

            ServerCodes.FEEDER_INACTIVE -> R.string.feeder_link_error_inactive.toCaString()
            ServerCodes.FEEDER_NETWORK_MISMATCH -> R.string.feeder_link_error_network_mismatch.toCaString()
            ServerCodes.FEEDER_VERIFICATION_UNAVAILABLE -> R.string.feeder_link_error_unavailable.toCaString()
            ServerCodes.FEEDER_VERIFICATION_ERROR -> R.string.feeder_link_error_verification.toCaString()

            ServerCodes.DATABASE_UNAVAILABLE,
            ServerCodes.INTERNAL,
            ServerCodes.UPSTREAM_UNAVAILABLE,
            ServerCodes.UPSTREAM_ERROR,
            ServerCodes.WATCHLIST_UNAVAILABLE,
            ServerCodes.REQUEST_TIMEOUT,
            ServerCodes.RESERVATION_EXPIRED,
            ServerCodes.OPERATION_IN_PROGRESS,
            ServerCodes.OPERATION_INTERRUPTED,
            ServerCodes.OPERATION_EXPIRED,
            ServerCodes.OPERATION_RESULT_UNAVAILABLE -> R.string.server_error_unavailable_msg.toCaString()

            else -> if (status >= 500) {
                R.string.server_error_unavailable_msg.toCaString()
            } else {
                R.string.server_error_generic_msg_x.toCaString(code)
            }
        }
        return LocalizedError(
            throwable = this,
            label = R.string.server_error_title.toCaString(),
            description = description,
        )
    }
}

object ServerCodes {
    const val INVALID_REQUEST = "invalid_request"
    const val PAYLOAD_TOO_LARGE = "payload_too_large"
    const val DATABASE_UNAVAILABLE = "database_unavailable"
    const val INTERNAL = "internal"
    const val APP_UPDATE_REQUIRED = "app_update_required"

    const val INVALID_KEY = "invalid_key"
    const val INVALID_VARIANT = "invalid_variant"
    const val BAD_SIGNATURE = "bad_signature"
    const val INVALID_CHALLENGE = "invalid_challenge"
    const val EXPIRED_CHALLENGE = "expired_challenge"
    const val CHALLENGE_USED = "challenge_used"
    const val INSTALLATION_REVOKED = "installation_revoked"

    const val QUOTA_EXCEEDED = "quota_exceeded"
    const val STALE_PROOF = "stale_proof"
    const val BAD_PROOF = "bad_proof"
    const val INVALID_TOKEN = "invalid_token"
    const val REVOKED = "revoked"
    const val REUSE_DETECTED = "reuse_detected"
    const val EXPIRED = "expired"
    const val RECOVERY_EXPIRED = "recovery_expired"
    const val RECOVERY_SUPERSEDED = "recovery_superseded"
    const val RECOVERY_UNAVAILABLE = "recovery_unavailable"

    const val INSTALLATION_RATE_EXCEEDED = "installation_rate_exceeded"
    const val ENTITLEMENT_RATE_EXCEEDED = "entitlement_rate_exceeded"
    const val INSTALLATION_CONCURRENCY_EXCEEDED = "installation_concurrency_exceeded"
    const val ENTITLEMENT_CONCURRENCY_EXCEEDED = "entitlement_concurrency_exceeded"
    const val DAILY_ALLOWANCE_EXHAUSTED = "daily_allowance_exhausted"
    const val INSTALLATION_RESTRICTED = "installation_restricted"

    const val REQUEST_TIMEOUT = "request_timeout"
    const val RESERVATION_EXPIRED = "reservation_expired"
    const val OPERATION_MISMATCH = "operation_mismatch"
    const val OPERATION_IN_PROGRESS = "operation_in_progress"
    const val OPERATION_INTERRUPTED = "operation_interrupted"
    const val OPERATION_EXPIRED = "operation_expired"
    const val OPERATION_RESULT_UNAVAILABLE = "operation_result_unavailable"
    const val RESULT_TOO_LARGE = "result_too_large"

    const val UPSTREAM_UNAVAILABLE = "upstream_unavailable"
    const val UPSTREAM_ERROR = "upstream_error"

    const val FEEDER_NOT_FOUND = "feeder_not_found"
    const val FEEDER_INACTIVE = "feeder_inactive"
    const val FEEDER_NETWORK_MISMATCH = "feeder_network_mismatch"
    const val FEEDER_VERIFICATION_UNAVAILABLE = "feeder_verification_unavailable"
    const val FEEDER_VERIFICATION_ERROR = "feeder_verification_error"

    const val WATCH_NOT_FOUND = "watch_not_found"
    const val REVISION_CONFLICT = "revision_conflict"
    const val STORED_WATCH_LIMIT = "stored_watch_limit"
    const val WATCH_LIST_DELETED = "watch_list_deleted"
    const val CURSOR_EXPIRED = "cursor_expired"
    const val WATCH_MUTATION_BUDGET_EXCEEDED = "watch_mutation_budget_exceeded"
    const val WATCHLIST_UNAVAILABLE = "watchlist_unavailable"

    // Per item codes, they arrive inside a HTTP 200 batch response
    const val TIER_RESTRICTED = "tier_restricted"
    const val DATA_INCOMPLETE = "data_incomplete"
    const val STALE_OBSERVATIONS = "stale_observations"
    const val RESULT_EXPIRED = "result_expired"
    const val ACCESS_CHANGED = "access_changed"
    const val BUDGET_EXHAUSTED = "budget_exhausted"
}

private val RETRYABLE_CODES = setOf(
    ServerCodes.DATABASE_UNAVAILABLE,
    ServerCodes.REQUEST_TIMEOUT,
    ServerCodes.RESERVATION_EXPIRED,
    ServerCodes.UPSTREAM_UNAVAILABLE,
    ServerCodes.OPERATION_IN_PROGRESS,
    ServerCodes.OPERATION_INTERRUPTED,
)

private val RETRYABLE_THROTTLE_CODES = setOf(
    ServerCodes.INSTALLATION_RATE_EXCEEDED,
    ServerCodes.ENTITLEMENT_RATE_EXCEEDED,
    ServerCodes.INSTALLATION_CONCURRENCY_EXCEEDED,
    ServerCodes.ENTITLEMENT_CONCURRENCY_EXCEEDED,
    ServerCodes.QUOTA_EXCEEDED,
)

/** True when resending the identical request (same operationId) can still succeed. */
val ServerApiException.isRetryableSameRequest: Boolean
    get() = code in RETRYABLE_CODES || (status == 429 && code in RETRYABLE_THROTTLE_CODES)

val ServerApiException.retryAfter: Duration
    get() = retryAfterSeconds
        ?.let { Duration.ofSeconds(it) }
        ?: if (status == 429) Duration.ofSeconds(1) else Duration.ofSeconds(2)
