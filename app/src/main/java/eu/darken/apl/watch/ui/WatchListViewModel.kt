package eu.darken.apl.watch.ui

import android.location.Location
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.WebpageTool
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.location.LocationManager2
import eu.darken.apl.common.planespotters.PlanespottersMeta
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.findByHex
import eu.darken.apl.search.ui.DestinationSearch
import eu.darken.apl.search.ui.actions.DestinationSearchAction
import eu.darken.apl.watch.core.WatchRepo
import eu.darken.apl.watch.core.alerts.WatchMonitor
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.FlightWatch
import eu.darken.apl.watch.core.types.LocationWatch
import eu.darken.apl.watch.core.types.SquawkWatch
import eu.darken.apl.watch.core.types.Watch
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import javax.inject.Inject

@HiltViewModel
class WatchListViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val watchRepo: WatchRepo,
    private val watchMonitor: WatchMonitor,
    private val webpageTool: WebpageTool,
    private val locationManager2: LocationManager2,
    private val aircraftRepo: AircraftRepo,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Watch", "List", "ViewModel"),
) {

    private val refreshTimer = callbackFlow {
        while (isActive) {
            refresh()
            send(Unit)
            delay(60 * 1000)
        }
        awaitClose()
    }

    val state = combine(
        refreshTimer,
        watchRepo.status,
        locationManager2.state,
        watchRepo.isRefreshing
    ) { _, alerts, locationState, isRefreshing ->
        val ourLocation = (locationState as? LocationManager2.State.Available)?.location

        val items = alerts
            .sortedByDescending { it.watch.addedAt }
            .sortedBy { alert ->
                alert.note.takeIf { it.isNotBlank() } ?: "ZZZZ"
            }
            .map { alert ->
                when (alert) {
                    is AircraftWatch.Status -> {
                        val aircraft = aircraftRepo.findByHex(alert.hex)
                        WatchItem.Single(
                            status = alert,
                            aircraft = aircraft,
                            ourLocation = ourLocation,
                        )
                    }

                    is FlightWatch.Status -> {
                        val aircraft = aircraftRepo.findByHex(alert.callsign)
                        WatchItem.Single(
                            status = alert,
                            aircraft = aircraft,
                            ourLocation = ourLocation,
                        )
                    }

                    is SquawkWatch.Status -> WatchItem.Multi(
                        status = alert,
                        ourLocation = ourLocation,
                    )

                    is LocationWatch.Status -> WatchItem.Multi(
                        status = alert,
                        ourLocation = ourLocation,
                    )
                }
            }
        State(
            items = items,
            isRefreshing = isRefreshing,
        )
    }.asStateFlow()

    fun refresh() = launch {
        log(tag) { "refresh()" }
        watchMonitor.check()
    }

    fun openWatchDetails(watchId: String) {
        navTo(DestinationWatchDetails(watchId = watchId))
    }

    fun openThumbnail(meta: PlanespottersMeta) = launch {
        webpageTool.open(meta.link)
    }

    fun showAircraftDetails(aircraft: Aircraft) {
        navTo(DestinationSearchAction(hex = aircraft.hex))
    }

    fun showSquawkInSearch(squawk: String) {
        navTo(DestinationSearch(targetSquawks = listOf(squawk)))
    }

    fun showAddWatchOptions(type: WatchType) {
        when (type) {
            WatchType.FLIGHT -> navTo(DestinationCreateFlightWatch())
            WatchType.AIRCRAFT -> navTo(DestinationCreateAircraftWatch())
            WatchType.SQUAWK -> navTo(DestinationCreateSquawkWatch())
            WatchType.LOCATION -> navTo(DestinationCreateLocationWatch())
        }
    }

    enum class WatchType { FLIGHT, AIRCRAFT, SQUAWK, LOCATION }

    sealed interface WatchItem {
        val status: Watch.Status

        data class Single(
            override val status: Watch.Status,
            val aircraft: Aircraft?,
            val ourLocation: Location?,
        ) : WatchItem

        data class Multi(
            override val status: Watch.Status,
            val ourLocation: Location?,
        ) : WatchItem
    }

    data class State(
        val items: List<WatchItem>,
        val isRefreshing: Boolean = false,
    )
}
