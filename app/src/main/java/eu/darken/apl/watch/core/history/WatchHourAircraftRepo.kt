package eu.darken.apl.watch.core.history

import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.ServerCodes
import eu.darken.apl.server.api.ServerEndpoint
import eu.darken.apl.server.api.WatchHourAircraft
import eu.darken.apl.server.api.WatchHourAircraftResponse
import eu.darken.apl.server.api.retryAfter
import eu.darken.apl.server.session.SessionManager
import eu.darken.apl.server.session.SessionRevokedException
import eu.darken.apl.watch.core.history.HourAircraftState.NotAvailable.Reason
import eu.darken.apl.watch.core.server.WatchListSync
import eu.darken.apl.watch.core.server.WatchListSyncScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** The aircraft that passed a watch hour by hour. The caller checks that the server offers this. */
@Singleton
class WatchHourAircraftRepo @Inject constructor(
    private val endpoint: ServerEndpoint,
    private val sessionManager: SessionManager,
    private val watchListSync: WatchListSync,
    private val syncScheduler: WatchListSyncScheduler,
) {

    private data class CacheKey(
        val installationId: String,
        val serverWatchId: String,
        val revision: Long,
        val hour: Instant,
    )

    /** Only answers the server marked final on every page, a provisional hour can still grow. */
    private val cache = ConcurrentHashMap<CacheKey, HourAircraftState.Complete>()

    private class InstallationChangedException(expected: String, actual: String) :
        Exception("Identity of $expected, the session is now $actual")

    /**
     * Reads every page of the [hour] that starts at that UTC hour. Emits [HourAircraftState.Loading] after
     * each page another one follows, and ends with one of the other states.
     */
    fun load(identity: WatchServerIdentity, hour: Instant): Flow<HourAircraftState> = flow {
        val key = CacheKey(identity.installationId, identity.serverWatchId, identity.revision, hour)
        cache[key]?.let {
            log(TAG) { "load(${identity.localId}, $hour): cached" }
            emit(it)
            return@flow
        }

        val aircraft = mutableListOf<HourAircraft>()
        var provisional = false
        var coverage = HourCoverage.FULL
        var after: String? = null
        while (true) {
            val page = try {
                fetchPage(identity, hour, after)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(TAG, WARN) { "load(${identity.localId}, $hour): failed after ${aircraft.size}: ${e.asLog()}" }
                emit(e.toTerminal())
                return@flow
            }
            aircraft += page.aircraft.map { it.toHourAircraft() }
            if (!page.isFinal) provisional = true
            coverage = minOf(coverage, HourCoverage.of(page.coverage))
            after = page.next ?: break
            emit(HourAircraftState.Loading(aircraft.toList(), provisional, coverage))
        }

        val complete = HourAircraftState.Complete(aircraft.toList(), provisional, coverage)
        log(TAG) { "load(${identity.localId}, $hour): ${aircraft.size} aircraft, provisional=$provisional, $coverage" }
        if (!provisional) cache[key] = complete
        emit(complete)
    }

    /** Throttling, an unavailable server and network errors are retried for the same page. */
    private suspend fun fetchPage(identity: WatchServerIdentity, hour: Instant, after: String?): WatchHourAircraftResponse {
        var tries = 0
        while (true) {
            tries++
            val wait = try {
                return sessionManager.authedAs { installationId, token ->
                    if (installationId != identity.installationId) {
                        throw InstallationChangedException(identity.installationId, installationId)
                    }
                    endpoint.watchHourAircraft(
                        token = token,
                        localId = identity.localId,
                        watchId = identity.serverWatchId,
                        revision = identity.revision,
                        hour = hour.toEpochMilli(),
                        after = after,
                    )
                }
            } catch (e: ServerApiException) {
                if (e.status != 429 && e.status != 503) throw e
                if (tries >= MAX_TRIES || e.retryAfter > MAX_WAIT) throw e
                e.retryAfter
            } catch (e: IOException) {
                if (tries >= MAX_TRIES) throw e
                NETWORK_RETRY_WAIT
            }
            log(TAG) { "fetchPage(${identity.localId}, $hour, $after): try $tries failed, retrying in $wait" }
            delay(wait.toMillis())
        }
    }

    private fun Exception.toTerminal(): HourAircraftState = when (this) {
        is InstallationChangedException -> HourAircraftState.Changed
        is SessionRevokedException -> HourAircraftState.NotAvailable(Reason.INSTALLATION_REVOKED)
        is ServerApiException -> when (code) {
            ServerCodes.WATCH_NOT_FOUND, ServerCodes.REVISION_CONFLICT -> {
                watchListSync.requestReread()
                syncScheduler.requestSync(SYNC_REASON)
                HourAircraftState.Changed
            }

            ServerCodes.INVALID_REQUEST -> HourAircraftState.NotAvailable(Reason.OUT_OF_RANGE)
            ServerCodes.TIER_RESTRICTED -> HourAircraftState.NotAvailable(Reason.TIER_RESTRICTED)
            ServerCodes.INSTALLATION_RESTRICTED -> HourAircraftState.NotAvailable(Reason.INSTALLATION_RESTRICTED)
            ServerCodes.INSTALLATION_REVOKED -> HourAircraftState.NotAvailable(Reason.INSTALLATION_REVOKED)
            ServerCodes.WATCH_LIST_DELETED -> HourAircraftState.NotAvailable(Reason.LIST_DELETED)
            else -> HourAircraftState.Failed(this)
        }

        else -> HourAircraftState.Failed(this)
    }

    private fun WatchHourAircraft.toHourAircraft() = HourAircraft(
        hex = aircraftId.uppercase(),
        lastSeenAt = Instant.ofEpochMilli(lastSeenAt),
        callsign = callsign,
        registration = registration,
        aircraftType = aircraftType,
        airlineName = airline?.name,
    )

    companion object {
        private const val MAX_TRIES = 3
        private val MAX_WAIT: Duration = Duration.ofSeconds(30)
        private val NETWORK_RETRY_WAIT: Duration = Duration.ofSeconds(2)
        private const val SYNC_REASON = "hour-aircraft-changed"
        private val TAG = logTag("Watch", "Hour", "Aircraft")
    }
}
