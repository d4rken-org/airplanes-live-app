package eu.darken.apl.watch.core

import eu.darken.apl.common.coroutine.AppScope
import eu.darken.apl.common.debug.logging.Logging.Priority.INFO
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.flow.combine
import eu.darken.apl.common.flow.replayingShare
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.aircraft.AircraftHex
import eu.darken.apl.main.core.aircraft.Callsign
import eu.darken.apl.main.core.aircraft.SquawkCode
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.watch.core.db.WatchDatabase
import eu.darken.apl.watch.core.history.WatchCheck
import eu.darken.apl.watch.core.history.WatchHistoryRepo
import eu.darken.apl.watch.core.server.WatchListSyncScheduler
import eu.darken.apl.watch.core.server.WatchServerMode
import eu.darken.apl.watch.core.server.WatchServerStatus
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.FlightWatch
import eu.darken.apl.watch.core.types.LocationWatch
import eu.darken.apl.watch.core.types.SquawkWatch
import eu.darken.apl.watch.core.types.Watch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchRepo @Inject constructor(
    @param:AppScope private val appScope: CoroutineScope,
    private val db: WatchDatabase,
    private val watchHistory: WatchHistoryRepo,
    aircraftRepo: AircraftRepo,
    private val serverMode: WatchServerMode,
    private val syncScheduler: WatchListSyncScheduler,
) {

    private val refreshTrigger = MutableStateFlow(UUID.randomUUID())
    val isRefreshing = MutableStateFlow(false)

    val watches: Flow<List<Watch>> = db.watches.replayingShare(appScope)

    val limit: Flow<WatchLimit> = combine(watches, serverMode.state) { watches, mode ->
        limitOf(watches, mode)
    }.distinctUntilChanged()

    val status: Flow<Collection<Watch.Status>> = combine(
        refreshTrigger,
        watchHistory.firehose,
        aircraftRepo.cache,
        watches,
        serverMode.state,
    ) { _, _, aircraft, watches, mode ->
        log(TAG) { "Search cache size ${aircraft.size}" }

        val status = mutableSetOf<Watch.Status>()
        watches
            .map { watch ->
                val server = mode.serverStatus(watch)
                val lastCheck: WatchCheck?
                val lastHit: WatchCheck?
                val tracked: Set<Aircraft>
                if (server?.isHeld == true) {
                    lastCheck = null
                    lastHit = server.lastMatch
                    tracked = if (server.presence == WatchServerStatus.Presence.PRESENT) {
                        server.lastMatch?.seenHexes.orEmpty().mapNotNull { aircraft[it.uppercase()] }.toSet()
                    } else {
                        emptySet()
                    }
                } else {
                    lastCheck = watchHistory.getLastCheck(watch.id)
                    lastHit = watchHistory.getLastHit(watch.id)
                    tracked = watch.trackedFrom(lastCheck, aircraft)
                }
                when (watch) {
                    is AircraftWatch -> AircraftWatch.Status(watch, lastCheck, lastHit, tracked, server)
                    is FlightWatch -> FlightWatch.Status(watch, lastCheck, lastHit, tracked, server)
                    is SquawkWatch -> SquawkWatch.Status(watch, lastCheck, lastHit, tracked, server)
                    is LocationWatch -> LocationWatch.Status(watch, lastCheck, lastHit, tracked, server)
                }
            }
            .run {
                log(TAG) { "Got ${this.size} watch states" }
                status.addAll(this)
            }

        status
    }
        .replayingShare(appScope)

    /**
     * The evidence of the last conclusive check decides what is tracked. Matching the cache locally
     * would show aircraft the server did not answer with, and would keep showing them afterwards.
     */
    private fun Watch.trackedFrom(
        lastCheck: WatchCheck?,
        cache: Map<AircraftHex, Aircraft>,
    ): Set<Aircraft> = when {
        lastCheck == null || lastCheck.seenHexes.isEmpty() && lastCheck.aircraftCount > 0 -> {
            cache.values.filter { matches(it) }.toSet()
        }

        lastCheck.aircraftCount == 0 -> emptySet()

        else -> lastCheck.seenHexes.mapNotNull { cache[it.uppercase()] }.toSet()
    }

    /** With the server's watch list its caps replace the app's own, a Feeder is not shown them. */
    private fun limitOf(watches: List<Watch>, mode: WatchServerMode.State): WatchLimit {
        val access = mode.access
        if (!mode.isActive || access == null) return WatchLimit(count = watches.size, max = access?.maxWatches)
        val active = access.limits.maxActiveWatches?.let { max ->
            WatchLimit.Cap(count = watches.count { access.countsAgainstActiveCap(it, mode.states[it.id]) }, max = max)
        }
        val stored = access.limits.maxStoredWatches?.let { max -> WatchLimit.Cap(count = watches.size, max = max) }
        val shown = active?.takeIf { access.tier != AccessState.Tier.FEEDER }
        return WatchLimit(
            count = shown?.count ?: watches.size,
            max = shown?.max,
            active = active,
            stored = stored,
        )
    }

    /** Counts what the list shows, fresh from the database: the shared [watches] may not have seen the last insert yet. */
    private suspend fun <T> withinLimit(create: suspend () -> T): T = db.withAllocation { createWithinLimit(create) }

    /** Only inside [WatchDatabase.withAllocation]. */
    private suspend fun <T> createWithinLimit(create: suspend () -> T): T {
        // A known free installation must not get an uncapped moment while its access is restored
        val mode = serverMode.current()
        limitOf(db.watches.first(), mode).reached?.let { throw WatchLimitReachedException(it) }
        return create()
    }

    suspend fun refresh() {
        log(TAG) { "refresh()" }
        refreshTrigger.value = UUID.randomUUID()
    }

    suspend fun createFlight(callsign: Callsign, note: String = ""): FlightWatch {
        log(TAG) { "createFlight($callsign, $note)" }
        return withinLimit { db.createFlight(callsign, note) }.also {
            log(TAG, INFO) { "createFlight(...): Created $it" }
            syncScheduler.requestSync("create")
        }
    }

    suspend fun createAircraft(hex: AircraftHex, note: String = ""): AircraftWatch {
        log(TAG) { "createAircraft($hex, $note)" }
        return withinLimit { db.createAircraft(hex, note) }.also {
            log(TAG, INFO) { "createAircraft(...): Created $it" }
            syncScheduler.requestSync("create")
        }
    }

    // counterpart: WatchDao.hasAircraftWatch
    suspend fun findAircraft(hex: AircraftHex): AircraftWatch? = db.watches.first()
        .filterIsInstance<AircraftWatch>()
        .firstOrNull { it.hex.trim().equals(hex.trim(), ignoreCase = true) }

    /** An aircraft that is already watched stays as it is, even at the limit. */
    suspend fun addAircraft(hex: AircraftHex): AircraftAdd = db.withAllocation {
        log(TAG) { "addAircraft($hex)" }
        findAircraft(hex)?.let { return@withAllocation AircraftAdd.Existing(it) }
        AircraftAdd.Created(createWithinLimit { db.createAircraft(hex.trim().uppercase(), "") }).also {
            log(TAG, INFO) { "addAircraft(...): Created ${it.watch}" }
            syncScheduler.requestSync("create")
        }
    }

    sealed interface AircraftAdd {
        val watch: AircraftWatch

        data class Created(override val watch: AircraftWatch) : AircraftAdd
        data class Existing(override val watch: AircraftWatch) : AircraftAdd
    }

    suspend fun createSquawk(code: SquawkCode, note: String = ""): SquawkWatch {
        log(TAG) { "createSquawk($code, $note)" }
        return withinLimit { db.createSquawk(code, note) }.also {
            log(TAG, INFO) { "createSquawk(...): Created $it" }
            syncScheduler.requestSync("create")
        }
    }

    suspend fun createLocation(
        latitude: Double,
        longitude: Double,
        radiusInMeters: Float,
        label: String,
        note: String = "",
    ): LocationWatch {
        log(TAG) { "createLocation($latitude, $longitude, $radiusInMeters, $label, $note)" }
        return withinLimit { db.createLocation(latitude, longitude, radiusInMeters, label, note) }.also {
            log(TAG, INFO) { "createLocation(...): Created $it" }
            syncScheduler.requestSync("create")
        }
    }

    suspend fun delete(id: WatchId) {
        log(TAG) { "delete($id)" }

        db.deleteWatch(id)
        log(TAG) { "delete(...): Deleted squawk $id" }
        syncScheduler.requestSync("delete")
    }

    suspend fun deleteBatch(ids: Set<WatchId>) {
        if (ids.isEmpty()) return
        log(TAG) { "deleteBatch($ids)" }
        db.deleteBatch(ids)
        log(TAG) { "deleteBatch(...): Deleted ${ids.size} watches" }
        syncScheduler.requestSync("delete-batch")
    }

    suspend fun updateNote(id: WatchId, note: String) {
        log(TAG) { "updateNote($id,$note)" }
        db.updateNote(id, note)
    }

    suspend fun setNotification(id: WatchId, boolean: Boolean) {
        log(TAG) { "setNotification($id,$boolean)" }
        db.updateNotification(id, boolean)
    }

    suspend fun updateLocation(
        id: WatchId,
        latitude: Double,
        longitude: Double,
        radiusInMeters: Float,
        label: String,
        expectedRevision: Long? = null,
    ) {
        log(TAG) { "updateLocation($id, $latitude, $longitude, $radiusInMeters, $label, $expectedRevision)" }
        db.updateLocation(id, latitude, longitude, radiusInMeters, label, expectedRevision)
        syncScheduler.requestSync("update-location")
    }

    companion object {
        private val TAG = logTag("Watch", "Repo")
    }
}