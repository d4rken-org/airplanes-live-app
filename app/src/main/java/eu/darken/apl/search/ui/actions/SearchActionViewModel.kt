package eu.darken.apl.search.ui.actions

import androidx.lifecycle.SavedStateHandle
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
import eu.darken.apl.common.navigation.navArgs
import eu.darken.apl.common.uix.ViewModel3
import eu.darken.apl.feeder.ui.actions.FeederActionEvents
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.aircraft.AircraftHex
import eu.darken.apl.main.core.getByHex
import eu.darken.apl.map.core.MapOptions
import eu.darken.apl.watch.core.WatchRepo
import eu.darken.apl.watch.core.types.Watch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapLatest
import javax.inject.Inject


@HiltViewModel
class SearchActionViewModel @Inject constructor(
    handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    aircraftRepo: AircraftRepo,
    watchRepo: WatchRepo,
    locationManager2: LocationManager2,
    private val flightRepo: FlightRepo,
) : ViewModel3(
    dispatcherProvider,
    tag = logTag("Search", "Action", "ViewModel")
) {

    private val navArgs by handle.navArgs<SearchActionDialogArgs>()
    private val aircraftHex: AircraftHex
        get() = navArgs.hex

    private val aircraft = aircraftRepo.getByHex(aircraftHex)
        .filterNotNull()
        .replayingShare(viewModelScope)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val route = aircraft
        .mapLatest { ac -> flightRepo.lookup(ac.hex, ac.callsign) }
        .distinctUntilChanged()

    val events = SingleEventFlow<FeederActionEvents>()

    init {
        log(tag) { "Loading for $aircraftHex" }
    }

    val state: Flow<State> = combine(
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
        SearchActionDialogDirections.actionSearchActionToMap(
            mapOptions = aircraft.firstOrNull()
                ?.let { MapOptions.focus(it) }
                ?: MapOptions.focus(aircraftHex)
        ).navigate()
    }

    fun showWatch() = launch {
        log(tag) { "showWatch()" }
        val watch = state.first().watch
        if (watch != null) {
            SearchActionDialogDirections.actionSearchActionToWatchlistDetailsFragment(watch.id)
        } else {
            SearchActionDialogDirections.actionSearchActionToCreateAircraftWatchFragment(aircraftHex)
        }.navigate()
    }

    data class State(
        val aircraft: Aircraft,
        val distanceInMeter: Float?,
        val watch: Watch?,
        val route: FlightRoute? = null,
    )
}
