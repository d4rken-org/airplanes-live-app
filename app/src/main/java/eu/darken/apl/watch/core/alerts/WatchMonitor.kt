package eu.darken.apl.watch.core.alerts

import eu.darken.apl.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.search.core.SearchQuery
import eu.darken.apl.search.core.SearchRepo
import eu.darken.apl.watch.core.WatchRepo
import eu.darken.apl.watch.core.WatchSettings
import eu.darken.apl.watch.core.history.WatchHistoryRepo
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.FlightWatch
import eu.darken.apl.watch.core.types.LocationWatch
import eu.darken.apl.watch.core.types.SquawkWatch
import eu.darken.apl.watch.core.types.Watch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchMonitor @Inject constructor(
    private val settings: WatchSettings,
    private val watchRepo: WatchRepo,
    private val historyRepo: WatchHistoryRepo,
    private val searchRepo: SearchRepo,
    private val notifications: WatchAlertNotifications,
) {
    suspend fun check() {
        log(TAG) { "check()" }
        val currentWatches = watchRepo.watches.first()
        val alerts = mutableMapOf<Watch, Collection<Aircraft>>()

        suspend fun Watch.process(results: Collection<Aircraft>) {
            log(TAG, VERBOSE) { "Checking $this" }
            // TODO filter for position
            val matches = results.filter { matches(it) }

            if (matches.isNotEmpty()) {
                when {
                    !isNotificationEnabled -> {
                        log(TAG) { "Notifications are disabled for $this" }
                    }

                    historyRepo.getLastCheck(id)?.aircraftCount != 0 -> {
                        log(TAG, VERBOSE) { "Skipping snoozed alert" }
                    }

                    else -> {
                        log(TAG) { "Will notify about $this" }
                        alerts[this] = matches
                    }
                }
            } else {
                log(TAG, VERBOSE) { "No matching aircraft for ${id}" }
            }

            historyRepo.addCheck(id, matches.size)
        }

        currentWatches.filterIsInstance<AircraftWatch>().let { ws ->
            if (ws.isEmpty()) return@let
            val batchResults = searchRepo.search(SearchQuery.Hex(ws.map { it.hex }.toSet()))
            ws.forEach { it.process(batchResults.aircraft) }
        }

        delay(200)

        currentWatches.filterIsInstance<FlightWatch>().let { ws ->
            if (ws.isEmpty()) return@let
            val batchResults = searchRepo.search(SearchQuery.Callsign(ws.map { it.callsign }.toSet()))
            ws.forEach { it.process(batchResults.aircraft) }
        }

        delay(200)

        currentWatches.filterIsInstance<SquawkWatch>().let { ws ->
            if (ws.isEmpty()) return@let
            val batchResults = searchRepo.search(SearchQuery.Squawk(ws.map { it.code }.toSet()))
            ws.forEach { it.process(batchResults.aircraft) }
        }

        currentWatches.filterIsInstance<LocationWatch>().forEach { watch ->
            val results = searchRepo.search(SearchQuery.Position(watch.center, watch.radiusInMeters.toLong()))
            watch.process(results.aircraft)
        }

        log(TAG) { "Notifying of ${alerts.size} watch matches" }
        alerts.forEach { notifications.alert(it.key, it.value) }
    }

    companion object {
        private val TAG = logTag("Watch", "Monitor")
    }
}