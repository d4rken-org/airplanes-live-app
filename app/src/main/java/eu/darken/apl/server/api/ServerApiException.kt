package eu.darken.apl.server.api

import java.time.Duration

class ServerApiException(
    val code: String,
    val status: Int,
    val retryAfterSeconds: Long? = null,
    val detail: String? = null,
) : RuntimeException("Server rejected the request: $code (HTTP $status)")

object ServerCodes {
    const val INVALID_REQUEST = "invalid_request"
    const val PAYLOAD_TOO_LARGE = "payload_too_large"
    const val DATABASE_UNAVAILABLE = "database_unavailable"
    const val INTERNAL = "internal"

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

    // Per item codes, they arrive inside a HTTP 200 batch response
    const val TIER_RESTRICTED = "tier_restricted"
    const val DATA_INCOMPLETE = "data_incomplete"
    const val STALE_OBSERVATIONS = "stale_observations"
    const val RESULT_EXPIRED = "result_expired"
    const val ACCESS_CHANGED = "access_changed"
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
