package eu.darken.apl.watch.ui.details

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.chart.ChartState
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
import eu.darken.apl.search.core.SearchTerm
import eu.darken.apl.search.core.SearchRepo
import eu.darken.apl.watch.core.WatchId
import eu.darken.apl.watch.core.WatchRepo
import eu.darken.apl.watch.core.history.WatchActivityTimeline
import eu.darken.apl.watch.core.history.WatchCountChartData
import eu.darken.apl.watch.core.history.WatchHistoryRepo
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.FlightWatch
import eu.darken.apl.watch.core.types.LocationWatch
import eu.darken.apl.watch.core.types.SquawkWatch
import eu.darken.apl.watch.core.types.Watch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.take
import java.time.Duration
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
    private val historyRepo: WatchHistoryRepo,
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

    /** Kept apart from the count line: a watch the server's history filled may have no checks of its own yet. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val activity: Flow<ChartState<WatchActivityTimeline>> = status
        .map { it.id }
        .distinctUntilChanged()
        .flatMapLatest { id -> historyRepo.activity(id, CHART_WINDOW) }
        .map { timeline -> if (timeline.hasData) ChartState.Ready(timeline) else ChartState.NoData }
        .onStart { emit(ChartState.Loading) }

    /** Only squawk and location watches count aircraft, the others are either seen or not. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val counts: Flow<ChartState<WatchCountChartData>?> = status
        .map { it.id to (it is SquawkWatch.Status || it is LocationWatch.Status) }
        .distinctUntilChanged()
        .flatMapLatest { (id, counting) ->
            if (!counting) flowOf(null)
            else historyRepo.countChart(id, CHART_WINDOW).map { data ->
                if (data.counts.size < 2) ChartState.NoData else ChartState.Ready(data)
            }
        }
        .onStart { emit(ChartState.Loading) }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val aircraft = status
        .mapLatest { alert ->
            when (alert) {
                is AircraftWatch.Status -> alert.tracked.firstOrNull() ?: aircraftRepo.findByHex(alert.hex)
                is FlightWatch.Status -> alert.tracked.firstOrNull() ?: aircraftRepo.findByCallsign(alert.callsign)
                is SquawkWatch.Status -> null
                is LocationWatch.Status -> null
            }
        }
        .distinctUntilChanged()
        .replayingShare(viewModelScope)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val route: Flow<FlightRoute?> = aircraft
        .onEach { ac -> ac?.let { flightRepo.prefetch(it.hex, it.callsign) } }
        .flatMapLatest { ac ->
            if (ac == null) flowOf(null)
            else flightRepo.getByHex(ac.hex)
        }
        .distinctUntilChanged()
        .onStart { emit(null) }

    val state = combine(
        trigger,
        locationManager2.state,
        status,
        aircraft,
        route,
        activity,
        counts,
    ) { _, locationState, alert, aircraft, flightRoute, activityState, countState ->
        State(
            status = alert,
            aircraft = aircraft,
            distanceInMeter = run {
                if (locationState !is LocationManager2.State.Available) return@run null
                val location = aircraft?.location ?: return@run null
                locationState.location.distanceTo(location)
            },
            route = flightRoute,
            activityState = activityState,
            countState = countState,
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
                val hexes = searchRepo.search(SearchQuery(listOf(SearchTerm(watchStatus.squawk))))
                MapOptions.focusAircraft(hexes.aircraft)
            }

            is FlightWatch.Status -> {
                val hexes = searchRepo.search(SearchQuery(listOf(SearchTerm(watchStatus.callsign))))
                MapOptions.focusAircraft(hexes.aircraft)
            }

            is LocationWatch.Status -> {
                val results = searchRepo.nearby(
                    latitude = watchStatus.watch.latitude,
                    longitude = watchStatus.watch.longitude,
                    radiusNm = watchStatus.watch.radiusInMeters / 1852.0,
                )
                if (results.aircraft.isNotEmpty()) {
                    MapOptions.focusAircraft(results.aircraft)
                } else {
                    MapOptions(
                        camera = MapOptions.Camera(
                            lat = watchStatus.watch.latitude,
                            lon = watchStatus.watch.longitude,
                            zoom = 9.0,
                        )
                    )
                }
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

    fun updateLocation(latitude: Double, longitude: Double, radiusInMeters: Float, label: String) = launch {
        log(tag) { "updateLocation($latitude, $longitude, $radiusInMeters, $label)" }
        watchRepo.updateLocation(watchId, latitude, longitude, radiusInMeters, label.trim())
    }

    data class State(
        val status: Watch.Status,
        val aircraft: Aircraft?,
        val distanceInMeter: Float?,
        val route: FlightRoute? = null,
        val activityState: ChartState<WatchActivityTimeline> = ChartState.Loading,
        /** Null for watches that do not count aircraft. */
        val countState: ChartState<WatchCountChartData>? = null,
    )

    companion object {
        private val CHART_WINDOW: Duration = Duration.ofDays(30)
    }
}
