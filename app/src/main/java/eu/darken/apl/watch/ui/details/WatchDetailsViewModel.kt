package eu.darken.apl.watch.ui.details

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.flight.FlightRepo
import eu.darken.apl.common.flight.FlightRoute
import eu.darken.apl.common.flow.SingleEventFlow
import eu.darken.apl.common.flow.combine
import eu.darken.apl.common.flow.replayingShare
import eu.darken.apl.common.location.LocationManager2
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.findByCallsign
import eu.darken.apl.main.core.findByHex
import eu.darken.apl.map.core.MapOptions
import eu.darken.apl.map.ui.DestinationMap
import eu.darken.apl.search.core.SearchQuery
import eu.darken.apl.search.core.SearchRepo
import eu.darken.apl.watch.core.WatchId
import eu.darken.apl.watch.core.WatchRepo
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.FlightWatch
import eu.darken.apl.watch.core.types.SquawkWatch
import eu.darken.apl.watch.core.types.Watch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class WatchDetailsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val watchRepo: WatchRepo,
    private val searchRepo: SearchRepo,
    private val aircraftRepo: AircraftRepo,
    private val locationManager2: LocationManager2,
    private val flightRepo: FlightRepo,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Watch", "Action", "Dialog", "ViewModel"),
) {

    private var watchId: WatchId = ""
    val events = SingleEventFlow<WatchDetailsEvents>()
    private val trigger = MutableStateFlow(UUID.randomUUID())

    fun init(watchId: WatchId) {
        if (this.watchId == watchId) return
        this.watchId = watchId

        watchRepo.status
            .map { alerts -> alerts.singleOrNull { it.id == watchId } }
            .filter { it == null }
            .take(1)
            .onEach {
                log(tag) { "Alert data for $watchId is no longer available" }
                navUp()
            }
            .launchInViewModel()
    }

    private val status = watchRepo.status
        .mapNotNull { data -> data.singleOrNull { it.id == watchId } }
        .replayingShare(viewModelScope)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val aircraft = status
        .mapLatest { alert ->
            when (alert) {
                is AircraftWatch.Status -> alert.tracked.firstOrNull() ?: aircraftRepo.findByHex(alert.hex)
                is FlightWatch.Status -> alert.tracked.firstOrNull() ?: aircraftRepo.findByCallsign(alert.callsign)
                is SquawkWatch.Status -> null
            }
        }
        .distinctUntilChanged()
        .replayingShare(viewModelScope)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val route = aircraft
        .mapLatest { ac -> ac?.let { flightRepo.lookup(it.hex, it.callsign) } }
        .distinctUntilChanged()

    val state = combine(
        trigger,
        locationManager2.state,
        status,
        aircraft,
        route,
    ) { _, locationState, alert, aircraft, flightRoute ->
        State(
            status = alert,
            aircraft = aircraft,
            distanceInMeter = run {
                if (locationState !is LocationManager2.State.Available) return@run null
                val location = aircraft?.location ?: return@run null
                locationState.location.distanceTo(location)
            },
            route = flightRoute,
        )
    }.asStateFlow()

    fun removeAlert(confirmed: Boolean = false) = launch {
        log(tag) { "removeAlert()" }
        if (!confirmed) {
            events.emit(WatchDetailsEvents.RemovalConfirmation(watchId))
            return@launch
        }
        watchRepo.delete(state.first()?.status?.id ?: return@launch)
    }

    fun showOnMap() = launch {
        log(tag) { "showOnMap()" }
        val mapOptions = when (val watchStatus = status.first()) {
            is AircraftWatch.Status -> MapOptions.focus(watchStatus.hex)
            is SquawkWatch.Status -> {
                val hexes = searchRepo.search(SearchQuery.Squawk(watchStatus.squawk))
                MapOptions.focusAircraft(hexes.aircraft)
            }

            is FlightWatch.Status -> {
                val hexes = searchRepo.search(SearchQuery.Callsign(watchStatus.callsign))
                MapOptions.focusAircraft(hexes.aircraft)
            }
        }
        navTo(DestinationMap(mapOptions = mapOptions))
    }

    fun updateNote(note: String) = launch {
        log(tag) { "updateNote($note)" }
        watchRepo.updateNote(watchId, note.trim())
    }

    fun enableNotifications(enabled: Boolean) = launch {
        log(tag) { "enableNotification($enabled)" }
        watchRepo.setNotification(watchId, enabled)
    }

    data class State(
        val status: Watch.Status,
        val aircraft: Aircraft?,
        val distanceInMeter: Float?,
        val route: FlightRoute? = null,
    )
}
