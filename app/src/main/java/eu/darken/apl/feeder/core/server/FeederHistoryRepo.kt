package eu.darken.apl.feeder.core.server

import eu.darken.apl.common.chart.ChartPoint
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.api.FeederHistoryHour
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.ServerCodes
import eu.darken.apl.server.api.ServerEndpoint
import eu.darken.apl.server.api.retryAfter
import eu.darken.apl.server.session.SessionManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The server's hourly history of each monitored feeder, kept in memory as one 30-day window per feeder.
 * Shorter views are cut from that window, so they never cost a request of their own.
 */
@Singleton
class FeederHistoryRepo @Inject constructor(
    private val sessionManager: SessionManager,
    private val endpoint: ServerEndpoint,
    private val serverClock: ServerClock,
    private val serverMode: FeederServerMode,
) {

    private class Window(val hours: List<FeederHistoryHour>, val fetchedAtElapsed: Long)

    private val lock = Mutex()
    private val cache = mutableMapOf<String, Window>()
    private var cacheInstallation: String? = null
    private var notBeforeElapsed: Long? = null

    /** The last 30 days of [feederId], a canonical id, oldest first. Empty for a feeder the server does not monitor. */
    suspend fun get(feederId: String, maxAge: Duration = MAX_AGE): List<FeederHistoryHour> = lock.withLock {
        val mode = serverMode.current()
        if (!mode.isActive) {
            clear()
            return@withLock emptyList()
        }
        val installation = mode.server?.installationId
        if (installation != cacheInstallation) {
            log(TAG) { "get(): installation changed from $cacheInstallation to $installation" }
            clear()
            cacheInstallation = installation
        }

        val elapsed = serverClock.elapsed()
        val cutoff = serverClock.now().minus(WINDOW).toEpochMilli()
        val cached = cache[feederId]
        val cachedHours = cached?.hours.orEmpty().filter { it.hour >= cutoff }

        if (cached != null && elapsed - cached.fetchedAtElapsed < maxAge.toMillis()) return@withLock cachedHours
        notBeforeElapsed?.let { notBefore ->
            if (elapsed < notBefore) {
                log(TAG) { "get($feederId): held back for ${notBefore - elapsed} ms" }
                return@withLock cachedHours
            }
        }

        val since = when {
            cachedHours.isEmpty() -> cutoff
            else -> cachedHours.firstOrNull { !it.isFinal }?.hour ?: (cachedHours.last().hour + HOUR.toMillis())
        }

        val response = try {
            sessionManager.authed { token -> endpoint.feederHistory(token, feederId, since) }
        } catch (e: ServerApiException) {
            when {
                e.status == 429 -> {
                    log(TAG, WARN) { "get($feederId): throttled, holding history back for ${e.retryAfter}" }
                    notBeforeElapsed = serverClock.elapsed() + e.retryAfter.toMillis()
                    return@withLock cachedHours
                }

                e.status == 404 && e.code == ServerCodes.FEEDER_NOT_MONITORED -> {
                    log(TAG) { "get($feederId): not monitored" }
                    cache.remove(feederId)
                    return@withLock emptyList()
                }

                else -> throw e
            }
        }

        val merged = (cachedHours.associateBy { it.hour } + response.hours.associateBy { it.hour })
            .values
            .filter { it.hour >= cutoff }
            .sortedBy { it.hour }
        cache[feederId] = Window(merged, elapsed)
        merged
    }

    /** Hourly average message rate of the last 7 days, from the same window as [get]. */
    suspend fun messageRateSparkline(feederId: String, maxAge: Duration = MAX_AGE): List<ChartPoint> {
        val hours = get(feederId, maxAge)
        val since = serverClock.now().minus(SPARKLINE_WINDOW).toEpochMilli()
        return hours
            .filter { it.hour >= since }
            .mapNotNull { hour ->
                val rate = hour.msgsPerSecondAvg?.takeIf { it.isFinite() } ?: return@mapNotNull null
                ChartPoint(Instant.ofEpochMilli(hour.hour), rate)
            }
    }

    private fun clear() {
        cache.clear()
        notBeforeElapsed = null
        cacheInstallation = null
    }

    companion object {
        val MAX_AGE: Duration = Duration.ofMinutes(10)
        private val WINDOW: Duration = Duration.ofDays(30)
        private val SPARKLINE_WINDOW: Duration = Duration.ofDays(7)
        private val HOUR: Duration = Duration.ofHours(1)
        private val TAG = logTag("Feeder", "Server", "History")
    }
}
