package eu.darken.apl.watch.ui.details

import android.location.Location
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.WebpageTool
import eu.darken.apl.common.chart.ChartState
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.flight.FlightRepo
import eu.darken.apl.common.flight.FlightRoute
import eu.darken.apl.common.flight.RouteProgress
import eu.darken.apl.common.flight.progressOf
import eu.darken.apl.common.flow.SingleEventFlow
import eu.darken.apl.common.flow.combine
import eu.darken.apl.common.flow.replayingShare
import eu.darken.apl.common.location.LocationManager2
import eu.darken.apl.common.planespotters.PlanespottersMeta
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.aircraft.Registration
import eu.darken.apl.main.core.aircraft.isLiveAt
import eu.darken.apl.main.core.findByCallsign
import eu.darken.apl.main.core.findByHex
import eu.darken.apl.map.core.MapOptions
import eu.darken.apl.map.ui.DestinationMap
import eu.darken.apl.search.core.SearchQuery
import eu.darken.apl.search.core.SearchRepo
import eu.darken.apl.search.core.SearchTerm
import eu.darken.apl.search.ui.DestinationSearch
import eu.darken.apl.search.ui.actions.DestinationSearchAction
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.watch.core.LocationRadiusException
import eu.darken.apl.watch.core.WatchId
import eu.darken.apl.watch.core.WatchRepo
import eu.darken.apl.watch.core.history.WatchActivityGrid
import eu.darken.apl.watch.core.history.WatchCountChartData
import eu.darken.apl.watch.core.history.WatchHistoryRepo
import eu.darken.apl.watch.core.server.WatchServerMode
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.FlightWatch
import eu.darken.apl.watch.core.types.LocationWatch
import eu.darken.apl.watch.core.types.SquawkWatch
import eu.darken.apl.watch.core.types.Watch
import eu.darken.apl.watch.ui.RadiusStops
import eu.darken.apl.watch.ui.WatchServerLabel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class WatchDetailsViewModel @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val watchRepo: WatchRepo,
    private val searchRepo: SearchRepo,
    private val aircraftRepo: AircraftRepo,
    private val locationManager2: LocationManager2,
    private val flightRepo: FlightRepo,
    private val historyRepo: WatchHistoryRepo,
    private val webpageTool: WebpageTool,
    private val serverClock: ServerClock,
    accessRepo: AccessRepo,
    serverMode: WatchServerMode,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Watch", "Action", "Dialog", "ViewModel"),
) {

    private var watchId: WatchId = ""
    val events = SingleEventFlow<WatchDetailsEvents>()

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

    /** The time and zone a grid is drawn for, renewed whenever a new local or UTC hour begins. */
    private val gridClock: Flow<Pair<Instant, ZoneId>> = flow {
        while (true) {
            val now = serverClock.now()
            val zone = ZoneId.systemDefault()
            emit(now to zone)
            delay(Duration.between(now, WatchActivityGrid.nextRefresh(now, zone)).toMillis() + 1)
        }
    }

    /** Kept apart from the count line: a watch the server's history filled may have no checks of its own yet. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val activity: Flow<ChartState<WatchActivityGrid>> = status
        .map { it.id }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            gridClock.flatMapLatest { (now, zone) ->
                historyRepo.activity(id, WatchActivityGrid.startOf(now, zone), now).map<_, ChartState<WatchActivityGrid>> { timeline ->
                    ChartState.Ready(WatchActivityGrid.build(timeline, zone, now))
                }
            }
        }
        .onStart { emit(ChartState.Loading) }

    /** Only squawk and location watches count aircraft, the others are either seen or not. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val counts: Flow<ChartState<WatchCountChartData>?> = status
        .map { it.id to it.isCounting }
        .distinctUntilChanged()
        .flatMapLatest { (id, counting) ->
            if (!counting) flowOf(null)
            else historyRepo.countChart(id, COUNT_WINDOW).map { data ->
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

    private val historyLastSeen: Flow<Instant?> = historyRepo.historyLastSeen
        .map { it[watchId] }
        .distinctUntilChanged()
        .onStart { emit(null) }

    private val maxRadiusKm: Flow<Int> = accessRepo.state
        .map { it?.maxLocationWatchRadiusKm ?: LocationWatch.DEFAULT_MAX_RADIUS_KM }
        .distinctUntilChanged()

    /** Ages and "live" labels move on between checks. */
    private val clockTicks = flow {
        while (true) {
            emit(Unit)
            delay(CLOCK_TICK.toMillis())
        }
    }

    val state = combine(
        locationManager2.state,
        status,
        aircraft,
        route,
        activity,
        counts,
        historyLastSeen,
        maxRadiusKm,
        clockTicks,
        serverMode.state,
    ) { locationState, alert, aircraft, flightRoute, activityState, countState, historyLastSeenAt, maxRadius, _, mode ->
        val ourLocation = (locationState as? LocationManager2.State.Available)?.location
        val now = serverClock.now()
        State(
            status = alert,
            now = now,
            aircraft = aircraft,
            registration = aircraft?.registration ?: (alert as? AircraftWatch.Status)?.watch?.lastRegistration,
            liveCount = alert.tracked.count { it.isLiveAt(now) },
            distanceInMeter = aircraft?.location?.let { ourLocation?.distanceTo(it) },
            route = flightRoute,
            routeProgress = flightRoute?.progressOf(aircraft, now) ?: RouteProgress.Unknown,
            // The server's history can know of a sighting none of the app's own checks caught
            lastSeenAt = alert.tracked.mapNotNull { it.messageSeenAt }.maxOrNull()
                ?: listOfNotNull(alert.lastHit?.checkAt, historyLastSeenAt).maxOrNull(),
            matches = alert.matches(ourLocation),
            activityState = activityState,
            countState = countState,
            maxRadiusKm = maxRadius,
            serverLabel = WatchServerLabel.of(alert, mode.isActive),
        )
    }.asStateFlow()

    private fun Watch.Status.matches(ourLocation: Location?): Matches? {
        val center = when (this) {
            is SquawkWatch.Status -> ourLocation
            is LocationWatch.Status -> watch.center
            else -> return null
        }
        val check = lastCheck
        return Matches(
            aircraft = tracked
                .map { ac -> Match(ac, ac.location?.let { location -> center?.distanceTo(location) }) }
                .sortedWith(compareBy(nullsLast()) { it.distanceInMeter }),
            // Without recorded hexes the matches are not the check's, counterpart: WatchRepo.trackedFrom
            checkAt = check?.takeIf { it.seenHexes.isNotEmpty() || it.aircraftCount == 0 }?.checkAt,
            total = check?.aircraftCount,
            fromWatchCenter = this is LocationWatch.Status,
            sortedByDistance = center != null,
        )
    }

    fun removeAlert(confirmed: Boolean = false) = launch {
        log(tag) { "removeAlert()" }
        if (!confirmed) {
            events.emit(WatchDetailsEvents.RemovalConfirmation(watchId))
            return@launch
        }
        watchRepo.delete(state.first().status.id)
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

    fun showInSearch() = launch {
        log(tag) { "showInSearch()" }
        val destination = when (val watchStatus = status.first()) {
            is AircraftWatch.Status -> DestinationSearch(targetHexes = listOf(watchStatus.hex))
            is FlightWatch.Status -> DestinationSearch(targetCallsigns = listOf(watchStatus.callsign))
            is SquawkWatch.Status -> DestinationSearch(targetSquawks = listOf(watchStatus.squawk))
            is LocationWatch.Status -> return@launch
        }
        navTo(destination)
    }

    fun showAircraft(aircraft: Aircraft) {
        navTo(DestinationSearchAction(hex = aircraft.hex))
    }

    fun openThumbnail(meta: PlanespottersMeta) = launch {
        webpageTool.open(meta.link)
    }

    fun updateNote(note: String) = launch {
        log(tag) { "updateNote($note)" }
        watchRepo.updateNote(watchId, note.trim())
    }

    fun enableNotifications(enabled: Boolean) = launch {
        log(tag) { "enableNotification($enabled)" }
        watchRepo.setNotification(watchId, enabled)
    }

    /** [onDone] runs on the main thread, with false when nothing was saved. */
    fun updateLocation(radiusInMeters: Float, label: String, onDone: (saved: Boolean) -> Unit) = launch {
        log(tag) { "updateLocation($radiusInMeters, $label)" }
        var saved = false
        try {
            val watch = (status.first() as? LocationWatch.Status)?.watch ?: return@launch
            val maxRadiusKm = maxRadiusKm.first()
            if (!RadiusStops.saveAllowed(radiusInMeters, watch.radiusInMeters, maxRadiusKm)) {
                throw LocationRadiusException(radiusInMeters, maxRadiusKm)
            }
            watchRepo.updateLocation(watchId, watch.latitude, watch.longitude, radiusInMeters, label.trim())
            saved = true
        } finally {
            withContext(NonCancellable + dispatcherProvider.Main) { onDone(saved) }
        }
    }

    data class State(
        val status: Watch.Status,
        /** Server time, aircraft timestamps are too. */
        val now: Instant,
        val aircraft: Aircraft?,
        /** Falls back to an aircraft watch's last known one, counterpart: AircraftWatch.lastRegistration */
        val registration: Registration?,
        val liveCount: Int,
        val distanceInMeter: Float?,
        val route: FlightRoute? = null,
        val routeProgress: RouteProgress = RouteProgress.Unknown,
        val lastSeenAt: Instant? = null,
        /** Null for watches that match one aircraft. */
        val matches: Matches? = null,
        val activityState: ChartState<WatchActivityGrid> = ChartState.Loading,
        /** Null for watches that do not count aircraft. */
        val countState: ChartState<WatchCountChartData>? = null,
        val maxRadiusKm: Int = LocationWatch.DEFAULT_MAX_RADIUS_KM,
        val serverLabel: WatchServerLabel? = null,
    )

    data class Matches(
        val aircraft: List<Match>,
        /** When the check these came from ran, null when they come from cached aircraft data instead. */
        val checkAt: Instant?,
        /** How many aircraft that check counted, the cache may know details for fewer. */
        val total: Int?,
        val fromWatchCenter: Boolean,
        /** False for a squawk watch while the device location is unknown. */
        val sortedByDistance: Boolean,
    )

    data class Match(val aircraft: Aircraft, val distanceInMeter: Float?)

    companion object {
        private val COUNT_WINDOW: Duration = Duration.ofDays(30)
        private val CLOCK_TICK: Duration = Duration.ofSeconds(30)
    }
}

/** Squawk and location watches count the aircraft they match, the others match one aircraft. */
internal val Watch.Status.isCounting: Boolean
    get() = this is SquawkWatch.Status || this is LocationWatch.Status
