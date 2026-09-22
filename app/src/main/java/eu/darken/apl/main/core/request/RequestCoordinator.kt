package eu.darken.apl.main.core.request

import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.api.RequestRate
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.ServerCodes
import eu.darken.apl.server.api.retryAfter
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

enum class Bucket {
    VIEWING,
    SEARCH,
    WATCH,
    ;
}

/**
 * Paces requests to what the server currently grants this installation, so the app runs into its
 * own limiter instead of the server's. A rejection that carries a retry hint holds the bucket until
 * it passes, the throttling codes hold every bucket because they are charged across all of them.
 */
@Singleton
class RequestCoordinator @Inject constructor(
    private val accessRepo: AccessRepo,
    private val monotonicClock: MonotonicClock,
) {

    private class TokenState(var tokens: Double, var lastRefill: Long)

    private val stateLock = Mutex()
    private val tokenStates = mutableMapOf<Bucket, TokenState>()
    /** Rate and allowance waits expire on different terms, so neither may absorb the other. */
    private val rateHoldUntil = mutableMapOf<Bucket, Long>()
    private val allowanceHolds = mutableMapOf<Bucket, AllowanceHold>()
    private var inFlight = 0

    /** [entitlement] is the one that ran out; another one is not bound by its exhaustion. */
    private class AllowanceHold(val until: Long, val entitlement: String?)

    private fun entitlementId(): String? = accessRepo.state.value?.let { "${it.tier}/${it.allowanceScope}" }

    suspend fun <T> execute(bucket: Bucket, block: suspend () -> T): T {
        admit(bucket)
        val ranUnder = entitlementId()
        try {
            return block()
        } catch (e: ServerApiException) {
            noteRejection(bucket, e, ranUnder)
            throw e
        } finally {
            // Cancellation must not skip the release, the slot would stay taken for the process lifetime
            withContext(NonCancellable) { stateLock.withLock { inFlight-- } }
        }
    }

    /**
     * Hold, rate and concurrency are decided together and re-decided after every wait, so a hold
     * that arrives while a call is queued still applies to it. The permit count follows the policy,
     * which changes when the tier does.
     */
    private suspend fun admit(bucket: Bucket) {
        while (true) {
            val rate = rateFor(bucket)
            val limit = accessRepo.state.value?.installationRequests?.concurrency ?: DEFAULT_CONCURRENCY
            val wait = stateLock.withLock {
                val now = monotonicClock.elapsed()
                val rateWait = (rateHoldUntil[bucket] ?: 0L) - now
                val allowanceHold = allowanceHolds[bucket]
                val allowanceWait = when {
                    allowanceHold == null -> 0L
                    // Linking a feeder replaces the allowance that ran out, so its wait is moot
                    allowanceHold.entitlement != null && allowanceHold.entitlement != entitlementId() -> {
                        allowanceHolds.remove(bucket)
                        0L
                    }

                    else -> allowanceHold.until - now
                }
                val holdWait = maxOf(rateWait, allowanceWait)

                val state = tokenStates.getOrPut(bucket) { TokenState(rate.burst.toDouble(), now) }
                val refilled = min(rate.burst.toDouble(), state.tokens + (now - state.lastRefill) / 1000.0 * rate.perSecond)
                state.lastRefill = now
                state.tokens = refilled
                val tokenWait = when {
                    refilled >= 1.0 -> 0L
                    else -> (((1.0 - refilled) / rate.perSecond) * 1000).toLong().coerceAtLeast(1L)
                }

                val slotWait = if (inFlight >= limit) SLOT_POLL_MS else 0L

                maxOf(holdWait, tokenWait, slotWait).also {
                    if (it <= 0L) {
                        state.tokens = refilled - 1.0
                        inFlight++
                    }
                }
            }
            if (wait <= 0L) return
            log(TAG, VERBOSE) { "$bucket waits ${wait}ms before it may run" }
            // Sliced, so an allowance hold dropped by an upgrade is noticed instead of slept through
            delay(min(wait, MAX_WAIT_SLICE_MS))
        }
    }

    private suspend fun noteRejection(bucket: Bucket, error: ServerApiException, entitlement: String?) {
        if (error.status != 429 && error.retryAfterSeconds == null) return
        val until = monotonicClock.elapsed() + error.retryAfter.toMillis()
        val affected = if (error.status == 429 && error.code in GLOBAL_HOLD_CODES) Bucket.entries else listOf(bucket)
        val isAllowance = error.code == ServerCodes.DAILY_ALLOWANCE_EXHAUSTED
        stateLock.withLock {
            affected.forEach { affectedBucket ->
                if (isAllowance) {
                    // Tagged with the entitlement the request ran under, not whatever is current by
                    // the time the rejection arrives: a link may have landed in between
                    val current = entitlementId()
                    if (entitlement != current) {
                        // An allowance that is no longer in force says nothing about the one that is
                        log(TAG, VERBOSE) { "Dropping a rejection from a replaced entitlement" }
                        return@forEach
                    }
                    val previous = allowanceHolds[affectedBucket]
                    // Deadlines only compose within one entitlement; an older one is replaced
                    val keepPrevious = previous != null &&
                            previous.entitlement == current &&
                            previous.until > until
                    if (!keepPrevious) allowanceHolds[affectedBucket] = AllowanceHold(until, current)
                } else {
                    rateHoldUntil[affectedBucket] = maxOf(rateHoldUntil[affectedBucket] ?: 0L, until)
                }
            }
        }
    }

    private fun rateFor(bucket: Bucket): RequestRate {
        val limits = accessRepo.state.value?.installationRequests ?: return defaultRate(bucket)
        return when (bucket) {
            Bucket.VIEWING -> limits.viewing
            Bucket.SEARCH -> limits.search
            Bucket.WATCH -> limits.watch
        }
    }

    private fun defaultRate(bucket: Bucket): RequestRate = when (bucket) {
        Bucket.VIEWING -> RequestRate(perSecond = 0.2, burst = 3)
        Bucket.SEARCH -> RequestRate(perSecond = 1.0, burst = 3)
        Bucket.WATCH -> RequestRate(perSecond = 1.0, burst = 3)
    }

    companion object {
        private const val DEFAULT_CONCURRENCY = 3
        private const val SLOT_POLL_MS = 25L
        private const val MAX_WAIT_SLICE_MS = 1000L
        private val GLOBAL_HOLD_CODES = setOf(
            ServerCodes.INSTALLATION_RATE_EXCEEDED,
            ServerCodes.ENTITLEMENT_RATE_EXCEEDED,
            ServerCodes.INSTALLATION_CONCURRENCY_EXCEEDED,
            ServerCodes.ENTITLEMENT_CONCURRENCY_EXCEEDED,
            ServerCodes.QUOTA_EXCEEDED,
        )
        private val TAG = logTag("Aircraft", "RequestCoordinator")
    }
}
