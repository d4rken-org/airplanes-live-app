package eu.darken.apl.watch.core

import eu.darken.apl.common.coroutine.AppScope
import eu.darken.apl.common.debug.logging.Logging.Priority.INFO
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.flow.combine
import eu.darken.apl.common.flow.replayingShare
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.main.core.aircraft.AircraftHex
import eu.darken.apl.main.core.aircraft.Callsign
import eu.darken.apl.main.core.aircraft.SquawkCode
import eu.darken.apl.watch.core.db.WatchDatabase
import eu.darken.apl.watch.core.history.WatchHistoryRepo
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.FlightWatch
import eu.darken.apl.watch.core.types.LocationWatch
import eu.darken.apl.watch.core.types.SquawkWatch
import eu.darken.apl.watch.core.types.Watch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
) {

    private val refreshTrigger = MutableStateFlow(UUID.randomUUID())
    val isRefreshing = MutableStateFlow(false)

    val watches: Flow<List<Watch>> = db.watches.replayingShare(appScope)

    val status: Flow<Collection<Watch.Status>> = combine(
        refreshTrigger,
        watchHistory.firehose,
        aircraftRepo.aircraft,
        watches
    ) { _, _, aircraft, watches ->
        log(TAG) { "Search cache size ${aircraft.size}" }

        val status = mutableSetOf<Watch.Status>()
        watches
            .map { watch ->
                when (watch) {
                    is AircraftWatch -> AircraftWatch.Status(
                        watch = watch,
                        lastCheck = watchHistory.getLastCheck(watch.id),
                        lastHit = watchHistory.getLastHit(watch.id),
                        tracked = aircraft.values
                            .filter { it.hex == watch.hex }
                            .toSet()
                            .also { if (it.isNotEmpty()) log(TAG) { "Matched $watch to $it" } }
                    )

                    is FlightWatch -> FlightWatch.Status(
                        watch = watch,
                        lastCheck = watchHistory.getLastCheck(watch.id),
                        lastHit = watchHistory.getLastHit(watch.id),
                        tracked = aircraft.values
                            .filter { it.callsign == watch.callsign }
                            .toSet()
                            .also { if (it.isNotEmpty()) log(TAG) { "Matched $watch to $it" } }
                    )

                    is SquawkWatch -> SquawkWatch.Status(
                        watch = watch,
                        lastCheck = watchHistory.getLastCheck(watch.id),
                        lastHit = watchHistory.getLastHit(watch.id),
                        tracked = aircraft.values
                            .filter { it.squawk == watch.code }
                            .toSet()
                            .also { if (it.isNotEmpty()) log(TAG) { "Matched $watch to $it" } }
                    )

                    is LocationWatch -> {
                        val recencyCutoff = Instant.now().minus(Duration.ofMinutes(10))
                        LocationWatch.Status(
                            watch = watch,
                            lastCheck = watchHistory.getLastCheck(watch.id),
                            lastHit = watchHistory.getLastHit(watch.id),
                            tracked = aircraft.values
                                .filter { watch.matches(it) && it.seenAt.isAfter(recencyCutoff) }
                                .toSet()
                                .also { if (it.isNotEmpty()) log(TAG) { "Matched $watch to $it" } }
                        )
                    }
                }

            }
            .run {
                log(TAG) { "Got ${this.size} hex alerts" }
                status.addAll(this)
            }

        status
    }
        .replayingShare(appScope)

    suspend fun refresh() {
        log(TAG) { "refresh()" }
        refreshTrigger.value = UUID.randomUUID()
    }

    suspend fun createFlight(callsign: Callsign, note: String = ""): FlightWatch {
        log(TAG) { "createFlight($callsign, $note)" }
        return db.createFlight(callsign, note).also {
            log(TAG, INFO) { "createFlight(...): Created $it" }
        }
    }

    suspend fun createAircraft(hex: AircraftHex, note: String = ""): AircraftWatch {
        log(TAG) { "createAircraft($hex, $note)" }
        return db.createAircraft(hex, note).also {
            log(TAG, INFO) { "createAircraft(...): Created $it" }
        }
    }

    suspend fun createSquawk(code: SquawkCode, note: String = ""): SquawkWatch {
        log(TAG) { "createSquawk($code, $note)" }
        return db.createSquawk(code, note).also {
            log(TAG, INFO) { "createSquawk(...): Created $it" }
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
        return db.createLocation(latitude, longitude, radiusInMeters, label, note).also {
            log(TAG, INFO) { "createLocation(...): Created $it" }
        }
    }

    suspend fun delete(id: WatchId) {
        log(TAG) { "delete($id)" }

        db.deleteWatch(id)
        log(TAG) { "delete(...): Deleted squawk $id" }
    }

    suspend fun updateNote(id: WatchId, note: String) {
        log(TAG) { "updateNote($id,$note)" }
        db.updateNote(id, note)
    }

    suspend fun setNotification(id: WatchId, boolean: Boolean) {
        log(TAG) { "setNotification($id,$boolean)" }
        db.updateNotification(id, boolean)
    }

    suspend fun updateLocation(id: WatchId, latitude: Double, longitude: Double, radiusInMeters: Float, label: String) {
        log(TAG) { "updateLocation($id, $latitude, $longitude, $radiusInMeters, $label)" }
        db.updateLocation(id, latitude, longitude, radiusInMeters, label)
    }

    companion object {
        private val TAG = logTag("Watch", "Repo")
    }
}