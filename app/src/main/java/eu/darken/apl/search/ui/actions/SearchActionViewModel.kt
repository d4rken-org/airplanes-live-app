package eu.darken.apl.search.ui.actions

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.flight.FlightRepo
import eu.darken.apl.common.flight.FlightRoute
import eu.darken.apl.common.flow.combine
import eu.darken.apl.common.flow.replayingShare
import eu.darken.apl.common.location.LocationManager2
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.aircraft.AircraftHex
import eu.darken.apl.main.core.getByHex
import eu.darken.apl.map.core.MapOptions
import eu.darken.apl.map.ui.DestinationMap
import eu.darken.apl.watch.core.WatchRepo
import eu.darken.apl.watch.core.types.Watch
import eu.darken.apl.watch.ui.DestinationCreateAircraftWatch
import eu.darken.apl.watch.ui.DestinationWatchDetails
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject


@HiltViewModel
class SearchActionViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val aircraftRepo: AircraftRepo,
    private val watchRepo: WatchRepo,
    private val locationManager2: LocationManager2,
    private val flightRepo: FlightRepo,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Search", "Action", "ViewModel"),
) {

    private var aircraftHex: AircraftHex = ""
    private val hexFlow = MutableStateFlow<AircraftHex?>(null)

    fun init(hex: AircraftHex) {
        if (this.aircraftHex == hex) return
        this.aircraftHex = hex
        hexFlow.value = hex
        log(tag) { "Loading for $aircraftHex" }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val aircraft = hexFlow
        .filterNotNull()
        .flatMapLatest { aircraftRepo.getByHex(it) }
        .filterNotNull()
        .replayingShare(viewModelScope)

    init {
        aircraft
            .onEach { ac -> flightRepo.prefetch(ac.hex, ac.callsign) }
            .launchIn(viewModelScope)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val route: Flow<FlightRoute?> = aircraft
        .flatMapLatest { ac -> flightRepo.getByHex(ac.hex) }
        .distinctUntilChanged()

    val state = combine(
        watchRepo.watches,
        aircraft,
        locationManager2.state,
        route,
    ) { watches, ac, locationState, flightRoute ->
        State(
            aircraft = ac,
            distanceInMeter = run {
                if (locationState !is LocationManager2.State.Available) return@run null
                val location = ac.location ?: return@run null
                locationState.location.distanceTo(location)
            },
            watch = watches.firstOrNull { it.matches(ac) },
            route = flightRoute,
        )
    }.asStateFlow()

    fun showMap() = launch {
        log(tag) { "showMap()" }
        val ac = aircraft.firstOrNull()
        val mapOptions = ac?.let { MapOptions.focus(it) } ?: MapOptions.focus(aircraftHex)
        navTo(DestinationMap(mapOptions = mapOptions))
    }

    fun showWatch() = launch {
        log(tag) { "showWatch()" }
        val watch = state.firstOrNull()?.watch
        if (watch != null) {
            navTo(DestinationWatchDetails(watchId = watch.id))
        } else {
            navTo(DestinationCreateAircraftWatch(hex = aircraftHex))
        }
    }

    data class State(
        val aircraft: Aircraft,
        val distanceInMeter: Float?,
        val watch: Watch?,
        val route: FlightRoute? = null,
    )
}
