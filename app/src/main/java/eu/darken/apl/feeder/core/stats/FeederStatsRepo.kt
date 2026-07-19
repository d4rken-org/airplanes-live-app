package eu.darken.apl.feeder.core.stats

import eu.darken.apl.account.core.api.AccountEndpoint
import eu.darken.apl.account.core.auth.AuthManager
import eu.darken.apl.common.coroutine.AppScope
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.completeWith
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single gateway to the feeder detail endpoint (`/api/v1/me/feeders/{id}`) for the detail screen,
 * the list sparklines and the health monitor. They share the server's 60/min throttle, so they
 * must share one client-side cache, single-flight de-dupe and pacing.
 *
 * Cache entries are keyed by (sessionEpoch, feederId): an account switch changes the epoch, so a
 * fetch that completes after logout/re-login can never populate the new session's cache, and old
 * entries become unreachable immediately (and are dropped on logout).
 *
 * The 300s TTL mirrors the server's own history cache and effectively only ever serves the
 * history/sparkline use case: the detail screen opens with [getDetail] forceRefresh and the
 * monitor's cadence (hours) always exceeds the TTL — live values are never stale-served to a
 * consumer that cares.
 */
@Singleton
class FeederStatsRepo @Inject constructor(
    private val accountEndpoint: AccountEndpoint,
    private val authManager: AuthManager,
    private val dispatcherProvider: DispatcherProvider,
    @param:AppScope private val appScope: CoroutineScope,
) {

    // Test seams, following the endpoint-wrapper convention of internal overrides.
    internal var clock: () -> Instant = Instant::now
    internal var sleeper: suspend (Duration) -> Unit = { delay(it.toMillis()) }

    private data class CacheKey(val epoch: Int, val feederId: String)
    private data class CacheEntry(val detail: FeederDetail, val at: Instant)

    private val stateLock = Mutex()
    private val cache = mutableMapOf<CacheKey, CacheEntry>()
    private val inflight = mutableMapOf<CacheKey, Deferred<FeederDetail>>()

    // Serializes actual network fetches and spaces them out; 429 pushes the next slot out by
    // the server's Retry-After.
    private val paceLock = Mutex()
    private var nextFetchAt: Instant = Instant.EPOCH

    init {
        authManager.isLoggedIn
            .onEach { loggedIn ->
                if (!loggedIn) stateLock.withLock {
                    cache.clear()
                    inflight.values.forEach { it.cancel() }
                    inflight.clear()
                }
            }
            .launchIn(appScope)
    }

    suspend fun getDetail(feederId: String, forceRefresh: Boolean = false): FeederDetail =
        withContext(dispatcherProvider.IO) {
            val key = CacheKey(authManager.sessionEpoch, feederId)
            val deferred = stateLock.withLock {
                if (!forceRefresh) {
                    cache[key]
                        ?.takeIf { Duration.between(it.at, clock()) < TTL }
                        ?.let { return@withContext it.detail }
                }
                // An in-flight fetch is fresher than the TTL either way — join it even on
                // forceRefresh instead of stacking a second request.
                inflight[key] ?: CompletableDeferred<FeederDetail>().also { flight ->
                    inflight[key] = flight
                    // Runs on appScope so one cancelled waiter can't kill a fetch that other
                    // waiters joined; completeWith keeps the failure inside the Deferred instead
                    // of crashing the scope.
                    appScope.launch { flight.completeWith(runCatching { fetch(key) }) }
                }
            }
            deferred.await()
        }

    private suspend fun fetch(key: CacheKey): FeederDetail = try {
        paceLock.withLock {
            val wait = Duration.between(clock(), nextFetchAt)
            if (!wait.isNegative && !wait.isZero) sleeper(wait)
            nextFetchAt = clock().plus(MIN_FETCH_INTERVAL)
        }
        val detail = try {
            accountEndpoint.getFeederDetail(key.feederId).toDomain(clock())
        } catch (e: HttpException) {
            if (e.code() == 429) {
                val retryAfter = e.response()?.headers()?.get("Retry-After")?.toLongOrNull()
                    ?: DEFAULT_BACKOFF.seconds
                log(TAG, WARN) { "Rate limited, backing off ${retryAfter}s" }
                paceLock.withLock { nextFetchAt = clock().plusSeconds(retryAfter) }
            }
            throw e
        }
        stateLock.withLock {
            // A session switch mid-flight makes this result foreign — never cache it.
            if (authManager.sessionEpoch == key.epoch) cache[key] = CacheEntry(detail, clock())
        }
        detail
    } finally {
        stateLock.withLock { inflight.remove(key) }
    }

    companion object {
        private val TTL = Duration.ofSeconds(300)
        private val MIN_FETCH_INTERVAL = Duration.ofMillis(1200)
        private val DEFAULT_BACKOFF = Duration.ofSeconds(60)
        private val TAG = logTag("Feeder", "Stats", "Repo")
    }
}
