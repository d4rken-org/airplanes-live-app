package eu.darken.apl.search.ui.actions

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.WebpageTool
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.flight.FlightRepo
import eu.darken.apl.common.flight.FlightRoute
import eu.darken.apl.common.flight.RouteProgress
import eu.darken.apl.common.flight.progressOf
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
import eu.darken.apl.map.ui.NativeMapViewModel
import eu.darken.apl.search.ui.SearchViewModel
import eu.darken.apl.server.ServerClock
import eu.darken.apl.watch.core.WatchRepo
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.Watch
import eu.darken.apl.watch.ui.DestinationCreateAircraftWatch
import eu.darken.apl.watch.ui.DestinationWatchDetails
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.time.Duration
import java.time.Instant
import javax.inject.Inject


@HiltViewModel
class SearchActionViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val aircraftRepo: AircraftRepo,
    private val watchRepo: WatchRepo,
    private val locationManager2: LocationManager2,
    private val flightRepo: FlightRepo,
    private val serverClock: ServerClock,
    private val webpageTool: WebpageTool,
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

    /** Null when the app has no data for the aircraft, it only knows what searches and the map brought in. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val aircraft: Flow<Aircraft?> = hexFlow
        .filterNotNull()
        .flatMapLatest { aircraftRepo.getByHex(it) }
        .replayingShare(viewModelScope)

    init {
        aircraft
            .filterNotNull()
            .onEach { ac -> flightRepo.prefetch(ac.hex, ac.callsign) }
            .launchIn(viewModelScope)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val route: Flow<FlightRoute?> = hexFlow
        .filterNotNull()
        .flatMapLatest { hex -> flightRepo.getByHex(hex) }
        .distinctUntilChanged()

    /** A position ages out of the route line without a new one arriving. */
    private val clockTicks = flow {
        while (true) {
            emit(Unit)
            delay(CLOCK_TICK.toMillis())
        }
    }

    /** Live ends once the position is too old, without a new one arriving. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val liveEnds = aircraft.flatMapLatest { ac ->
        flow {
            emit(Unit)
            val ends = ac?.positionSeenAt?.plus(SearchViewModel.LIVE_POSITION_WITHIN) ?: return@flow
            val left = Duration.between(serverClock.now(), ends)
            if (left.isNegative) return@flow
            delay(left.toMillis() + 1)
            emit(Unit)
        }
    }

    val state = combine(
        watchRepo.watches,
        aircraft,
        locationManager2.state,
        route,
        clockTicks,
        liveEnds,
    ) { watches, ac, locationState, flightRoute, _, _ ->
        val now = serverClock.now()
        State(
            aircraft = ac,
            distanceInMeter = run {
                if (locationState !is LocationManager2.State.Available) return@run null
                val location = ac?.location ?: return@run null
                locationState.location.distanceTo(location)
            },
            watch = watches.filterIsInstance<AircraftWatch>().firstOrNull { it.hex.equals(aircraftHex, ignoreCase = true) },
            route = flightRoute,
            routeProgress = ac?.let { flightRoute?.progressOf(it, now) } ?: RouteProgress.Unknown,
            seen = ac?.let { seen(it, now) },
        )
    }.asStateFlow()

    fun serverNow(): Long = serverClock.now().toEpochMilli()

    fun openPhoto(link: String) = webpageTool.open(link)

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
        val aircraft: Aircraft?,
        val distanceInMeter: Float?,
        val watch: Watch?,
        val route: FlightRoute? = null,
        val routeProgress: RouteProgress = RouteProgress.Unknown,
        val seen: NativeMapViewModel.Seen? = null,
    )

    companion object {
        private val CLOCK_TICK: Duration = Duration.ofSeconds(30)

        internal fun seen(aircraft: Aircraft, now: Instant): NativeMapViewModel.Seen {
            val seenAt = SearchViewModel.seenAt(aircraft) ?: return NativeMapViewModel.Seen.Unknown
            return if (SearchViewModel.isLive(aircraft, now)) {
                NativeMapViewModel.Seen.Live
            } else {
                NativeMapViewModel.Seen.At(seenAt.toEpochMilli())
            }
        }
    }
}
