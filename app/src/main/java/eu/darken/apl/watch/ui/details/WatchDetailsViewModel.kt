package eu.darken.apl.watch.ui.details

import android.location.Location
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.airport.core.AirportTarget
import eu.darken.apl.airport.core.toAirportRef
import eu.darken.apl.airport.ui.DestinationAirportDetails
import eu.darken.apl.common.WebpageTool
import eu.darken.apl.common.chart.ChartState
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.flight.Airport
import eu.darken.apl.common.flight.FlightRepo
import eu.darken.apl.common.flight.FlightRoute
import eu.darken.apl.common.flight.RouteProgress
import eu.darken.apl.common.flight.progressOf
import eu.darken.apl.common.flight.routeLookupKeys
import eu.darken.apl.common.flow.SingleEventFlow
import eu.darken.apl.common.flow.combine
import eu.darken.apl.common.flow.replayingShare
import eu.darken.apl.common.location.LocationManager2
import eu.darken.apl.common.planespotters.PlanespottersMeta
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.aircraft.AircraftHex
import eu.darken.apl.main.core.aircraft.Registration
import eu.darken.apl.main.core.aircraft.currentLocation
import eu.darken.apl.main.core.aircraft.isLiveAt
import eu.darken.apl.main.core.findByCallsign
import eu.darken.apl.main.core.findByHex
import eu.darken.apl.main.core.query.WatchOutcome
import eu.darken.apl.map.core.MapOptions
import eu.darken.apl.map.ui.DestinationMap
import eu.darken.apl.search.core.SearchQuery
import eu.darken.apl.search.core.SearchRepo
import eu.darken.apl.search.core.SearchTerm
import eu.darken.apl.search.ui.DestinationSearch
import eu.darken.apl.search.ui.actions.DestinationSearchAction
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.api.ServerCodes
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.upgrade.ui.DestinationUpgrade
import eu.darken.apl.watch.core.LocationAltitudeException
import eu.darken.apl.watch.core.LocationRadiusException
import eu.darken.apl.watch.core.WatchId
import eu.darken.apl.watch.core.WatchLiveCheck
import eu.darken.apl.watch.core.WatchRepo
import eu.darken.apl.watch.core.history.HourAircraft
import eu.darken.apl.watch.core.history.HourAircraftState
import eu.darken.apl.watch.core.history.HourCoverage
import eu.darken.apl.watch.core.history.WatchActivityGrid
import eu.darken.apl.watch.core.history.WatchCountChartData
import eu.darken.apl.watch.core.history.WatchHistoryRepo
import eu.darken.apl.watch.core.history.WatchHourAircraftRepo
import eu.darken.apl.watch.core.history.WatchServerIdentity
import eu.darken.apl.watch.core.server.WatchServerMode
import eu.darken.apl.watch.core.server.WatchServerStatus
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.FlightWatch
import eu.darken.apl.watch.core.types.LocationWatch
import eu.darken.apl.watch.core.types.SquawkWatch
import eu.darken.apl.watch.core.types.Watch
import eu.darken.apl.watch.ui.AltitudeBand
import eu.darken.apl.watch.ui.LiveNow
import eu.darken.apl.watch.ui.RadiusStops
import eu.darken.apl.watch.ui.WatchServerLabel
import eu.darken.apl.watch.ui.lastSeenAt
import eu.darken.apl.watch.ui.liveNow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
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
    private val liveCheck: WatchLiveCheck,
    private val hourAircraftRepo: WatchHourAircraftRepo,
    private val accessRepo: AccessRepo,
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

        aircraft
            .routeLookupKeys()
            .onEach { (hex, callsign) -> flightRepo.prefetch(hex, callsign) }
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

    /** The one check of this opening for a definition, a failed one too, see [live]. */
    private var checked: Pair<Pair<WatchId, Long>, LiveCheck>? = null

    /**
     * The server's watch list says aircraft are there for a held watch, not which ones. One check per
     * opening and definition answers that, whatever the presence does afterwards.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val live: Flow<LiveCheck?> = status
        .map { alert -> alert.watch.takeIf { alert.isCounting && alert.server?.let { it.isHeld && it.presence == WatchServerStatus.Presence.PRESENT } == true } }
        .distinctUntilChanged { old, new -> old?.id == new?.id && old?.definitionRevision == new?.definitionRevision }
        .transformLatest { watch ->
            if (watch == null) {
                emit(null)
                return@transformLatest
            }
            val key = watch.id to watch.definitionRevision
            checked?.takeIf { it.first == key }?.let {
                emit(it.second)
                return@transformLatest
            }
            emit(LiveCheck.Running)
            val outcome = try {
                // Behind a long monitor run on the request gate, the sheet would say it is checking for minutes
                withTimeoutOrNull(LIVE_CHECK_TIMEOUT.toMillis()) { liveCheck.check(watch) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(tag, WARN) { "Live check of ${watch.id} failed: ${e.asLog()}" }
                null
            }
            val at = serverClock.now()
            val result = when (outcome) {
                is WatchOutcome.Matched -> LiveCheck.Answered(
                    watchId = watch.id,
                    revision = watch.definitionRevision,
                    at = at,
                    aircraft = outcome.aircraft,
                    // A capped answer without a total does not say how many there are
                    total = outcome.totalMatching ?: outcome.aircraft.size.takeUnless { outcome.capped },
                    expiresAt = outcome.expiresAt,
                )

                is WatchOutcome.Absent -> LiveCheck.Answered(
                    watchId = watch.id,
                    revision = watch.definitionRevision,
                    at = at,
                    aircraft = emptyList(),
                    total = 0,
                    expiresAt = outcome.expiresAt,
                )

                is WatchOutcome.Rejected ->
                    if (outcome.code == ServerCodes.DAILY_ALLOWANCE_EXHAUSTED) LiveCheck.LimitReached
                    else LiveCheck.Unavailable

                else -> LiveCheck.Unavailable
            }
            checked = key to result
            emit(result)
        }

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

    /** The UTC hours picked in the activity grid, one or two, none while nothing is picked. */
    private val selectedHours = MutableStateFlow<List<Instant>>(emptyList())
    private val hourRetries = MutableStateFlow(0)

    /** Whether the aircraft of an hour can be listed for this watch at all, a pick does not outlive it. */
    private val hourOffered: Flow<Pair<WatchId, Boolean>> = combine(
        status.map { it.id to (it is LocationWatch.Status) }.distinctUntilChanged(),
        accessRepo.state.map { it?.offersWatchHourAircraft == true }.distinctUntilChanged(),
    ) { (id, isLocation), offered -> id to (isLocation && offered) }
        .distinctUntilChanged()
        .onEach { (_, offered) -> if (!offered) selectedHours.value = emptyList() }

    /** Null while hours can't be listed, also while the server does not hold the current definition. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val hourIdentity: Flow<WatchServerIdentity?> = hourOffered
        .flatMapLatest { (id, offered) -> if (offered) historyRepo.serverIdentity(id) else flowOf(null) }
        .distinctUntilChanged()

    /** Seen hours of an aircraft watch open on the map where there is aircraft history, also as an upsell. */
    private val hourReplay: Flow<Boolean> = combine(
        status.map { it is AircraftWatch.Status }.distinctUntilChanged(),
        accessRepo.state.map { it?.hasAircraftHistory == true }.distinctUntilChanged(),
    ) { isAircraft, hasHistory -> isAircraft && hasHistory }
        .distinctUntilChanged()

    private data class HourLoad(val hours: List<Instant>, val aircraft: HourAircraftState?)

    /** Any change cancels the running load, rows of another hour or definition never stay. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val hourLoad: Flow<HourLoad?> = combine(hourIdentity, selectedHours, hourRetries) { identity, hours, _ ->
        identity to hours
    }
        .flatMapLatest { (identity, hours) ->
            if (identity == null) return@flatMapLatest flowOf<HourLoad?>(null)
            // A new definition keeps the picked hours the server still lists
            val range = HourSelection.rangeAt(serverClock.now())
            val inRange = hours.filter { it in range }
            when {
                inRange != hours -> {
                    selectedHours.value = inRange
                    flowOf(HourLoad(emptyList(), null))
                }

                hours.isEmpty() -> flowOf(HourLoad(emptyList(), null))
                else -> loadHours(identity, hours).map { HourLoad(hours, it) }
            }
        }
        .onStart { emit(null) }

    /** One hour after the other, an aircraft seen in both is listed once, with its later sighting. */
    private fun loadHours(identity: WatchServerIdentity, hours: List<Instant>): Flow<HourAircraftState> = flow {
        emit(HourAircraftState.Loading(emptyList(), provisional = false, coverage = HourCoverage.FULL))
        val done = mutableListOf<HourAircraftState.Complete>()
        for (hour in hours) {
            var end: HourAircraftState? = null
            hourAircraftRepo.load(identity, hour).collect { state ->
                when (state) {
                    is HourAircraftState.Loading -> emit(
                        HourAircraftState.Loading(
                            aircraft = union(done.map { it.aircraft } + listOf(state.aircraft)),
                            provisional = state.provisional || done.any { it.provisional },
                            coverage = minOf(state.coverage, done.minOfOrNull { it.coverage } ?: HourCoverage.FULL),
                        )
                    )

                    is HourAircraftState.Complete -> done += state
                    else -> end = state
                }
            }
            end?.let {
                log(tag) { "loadHours($hours): $hour ended with $it" }
                emit(it)
                return@flow
            }
            if (done.size < hours.size) {
                emit(
                    HourAircraftState.Loading(
                        aircraft = union(done.map { it.aircraft }),
                        provisional = done.any { it.provisional },
                        coverage = done.minOfOrNull { it.coverage } ?: HourCoverage.FULL,
                    )
                )
            }
        }
        emit(
            HourAircraftState.Complete(
                aircraft = union(done.map { it.aircraft }).sortedBy { it.lastSeenAt },
                provisional = done.any { it.provisional },
                coverage = done.minOfOrNull { it.coverage } ?: HourCoverage.FULL,
            )
        )
    }

    private fun union(lists: List<List<HourAircraft>>): List<HourAircraft> {
        val byHex = LinkedHashMap<AircraftHex, HourAircraft>()
        lists.forEach { list ->
            list.forEach { aircraft ->
                val known = byHex[aircraft.hex]
                if (known == null || aircraft.lastSeenAt.isAfter(known.lastSeenAt)) byHex[aircraft.hex] = aircraft
            }
        }
        return byHex.values.toList()
    }

    private val baseState: Flow<State> = combine(
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
        live,
    ) { locationState, alert, aircraft, flightRoute, activityState, countState, historyLastSeenAt, maxRadius, _, mode, liveState ->
        val ourLocation = (locationState as? LocationManager2.State.Available)?.location
        val now = serverClock.now()
        val liveCount = alert.tracked.count { it.isLiveAt(now) }
        // Status and answer arrive apart, an answer for the definition before an edit is not this one's
        val liveCheck = liveState?.takeUnless { it is LiveCheck.Answered && !it.answers(alert.watch) }
        State(
            status = alert,
            now = now,
            aircraft = aircraft,
            registration = aircraft?.registration ?: (alert as? AircraftWatch.Status)?.watch?.lastRegistration,
            liveCount = liveCount,
            live = when {
                // Fresher than the server's presence, also when it found nothing
                liveCheck is LiveCheck.Answered && liveCheck.isCurrentAt(now) ->
                    if (liveCheck.total == 0) null else LiveNow(liveCheck.total)

                else -> alert.liveNow(liveCount)
            },
            distanceInMeter = aircraft?.currentLocation?.let { ourLocation?.distanceTo(it) },
            route = flightRoute,
            routeProgress = flightRoute?.progressOf(aircraft, now) ?: RouteProgress.Unknown,
            lastSeenAt = alert.lastSeenAt(historyLastSeenAt),
            matches = alert.matches(ourLocation, liveCheck),
            activityState = activityState,
            countState = countState,
            maxRadiusKm = maxRadius,
            serverLabel = WatchServerLabel.of(alert, mode.isActive),
        )
    }

    val state = combine(baseState, hourLoad, hourReplay) { base, load, replay ->
        base.copy(hourSelection = load?.toSelection(base), hourReplay = replay)
    }.asStateFlow()

    private fun HourLoad.toSelection(state: State): HourSelection {
        val grid = (state.activityState as? ChartState.Ready<WatchActivityGrid>)?.data
        val first = hours.firstOrNull()
        return HourSelection(
            range = HourSelection.rangeAt(state.now),
            selected = hours,
            selectedSlot = first?.let { hour ->
                grid?.days?.firstNotNullOfOrNull { day -> day.slots.firstOrNull { hour in it.hours } }
            },
            aircraft = aircraft,
        )
    }

    private fun Watch.Status.matches(ourLocation: Location?, live: LiveCheck?): Matches? {
        val center = when (this) {
            is SquawkWatch.Status -> ourLocation
            is LocationWatch.Status -> watch.center
            else -> return null
        }
        fun sorted(aircraft: Collection<Aircraft>) = aircraft
            .map { ac -> Match(ac, ac.currentLocation?.let { location -> center?.distanceTo(location) }) }
            .sortedWith(compareBy(nullsLast()) { it.distanceInMeter })

        if (live is LiveCheck.Answered) {
            return Matches(
                aircraft = sorted(live.aircraft),
                checkAt = live.at,
                total = live.total,
                fromWatchCenter = this is LocationWatch.Status,
                sortedByDistance = center != null,
            )
        }
        // Once the aircraft left, the last match no longer describes what the watch matches
        val match = server?.lastMatch?.takeIf { server?.presence == WatchServerStatus.Presence.PRESENT }
        return Matches(
            aircraft = sorted(tracked),
            checkAt = match?.checkAt,
            total = match?.aircraftCount,
            fromWatchCenter = this is LocationWatch.Status,
            sortedByDistance = center != null,
            checking = live == LiveCheck.Running,
            checkFailed = live == LiveCheck.Unavailable,
            checkLimitReached = live == LiveCheck.LimitReached,
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

    /** Picks the hours of [slot], or lets go of them when they are the ones picked. */
    fun selectHourSlot(slot: WatchActivityGrid.Slot) = launch {
        log(tag) { "selectHourSlot(${slot.hours})" }
        val current = selectedHours.value
        if (current.isNotEmpty() && slot.hours.any { it in current }) {
            selectedHours.value = emptyList()
            return@launch
        }
        if (slot.state == WatchActivityGrid.State.NOT_YET) return@launch
        if (hourIdentity.first() == null) return@launch
        val range = HourSelection.rangeAt(serverClock.now())
        val hours = slot.hours.filter { it in range }
        if (hours.isNotEmpty()) selectedHours.value = hours
    }

    fun showPreviousHour() = stepHour(forward = false)

    fun showNextHour() = stepHour(forward = true)

    /** From a two-hour pick, back from its first hour and forward from its last. */
    private fun stepHour(forward: Boolean) {
        val current = selectedHours.value
        if (current.isEmpty()) return
        val hour = if (forward) current.last().plus(HourSelection.HOUR) else current.first().minus(HourSelection.HOUR)
        if (hour !in HourSelection.rangeAt(serverClock.now())) return
        log(tag) { "stepHour(): $hour" }
        selectedHours.value = listOf(hour)
    }

    fun clearHourSelection() {
        selectedHours.value = emptyList()
    }

    fun retryHourAircraft() {
        hourRetries.update { it + 1 }
    }

    fun showHourAircraft(aircraft: HourAircraft) {
        log(tag) { "showHourAircraft(${aircraft.hex}, ${aircraft.lastSeenAt})" }
        val mapOptions = MapOptions.focus(aircraft.hex).copy(follow = true, replayAt = aircraft.lastSeenAt.toEpochMilli())
        navTo(DestinationMap(mapOptions = mapOptions))
    }

    /** Opens the aircraft's history at the first position within [slot]'s hours, if it was seen then. */
    fun replayHourSlot(slot: WatchActivityGrid.Slot) = launch {
        log(tag) { "replayHourSlot(${slot.state}, ${slot.hours})" }
        val watchStatus = status.first() as? AircraftWatch.Status ?: return@launch
        if (slot.state != WatchActivityGrid.State.ACTIVE || slot.hours.isEmpty()) return@launch
        val access = accessRepo.state.value?.takeIf { it.hasAircraftHistory } ?: return@launch
        if (!access.offersAircraftHistory) {
            events.emit(WatchDetailsEvents.HistoryUpsell)
            return@launch
        }
        val window = MapOptions.ReplayWindow(
            from = slot.hours.min().toEpochMilli(),
            until = slot.hours.max().plus(HourSelection.HOUR).toEpochMilli(),
        )
        val mapOptions = MapOptions.focus(watchStatus.hex).copy(follow = true, replayWindow = window)
        navTo(DestinationMap(mapOptions = mapOptions))
    }

    fun goUpgrade() {
        log(tag) { "goUpgrade()" }
        navTo(DestinationUpgrade)
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

    fun showAirport(airport: Airport) {
        log(tag) { "showAirport($airport)" }
        airport.toAirportRef()?.let { navTo(DestinationAirportDetails(AirportTarget.Partial(it))) }
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
    fun updateLocation(radiusInMeters: Float, band: AltitudeBand, label: String, onDone: (saved: Boolean) -> Unit) = launch {
        log(tag) { "updateLocation($radiusInMeters, $band, $label)" }
        var saved = false
        try {
            val watch = (status.first() as? LocationWatch.Status)?.watch ?: return@launch
            val maxRadiusKm = maxRadiusKm.first()
            if (!RadiusStops.saveAllowed(radiusInMeters, watch.radiusInMeters, maxRadiusKm)) {
                throw LocationRadiusException(radiusInMeters, maxRadiusKm)
            }
            if (!LocationWatch.altitudeBandAllowed(band.minFt, band.maxFt)) {
                throw LocationAltitudeException(band.minFt, band.maxFt)
            }
            watchRepo.updateLocation(
                watchId,
                watch.latitude,
                watch.longitude,
                radiusInMeters,
                band.minFt,
                band.maxFt,
                label.trim(),
            )
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
        /** What the status says is there now, null when nothing is. */
        val live: LiveNow? = null,
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
        /** Null while the aircraft of an hour can't be listed for this watch. */
        val hourSelection: HourSelection? = null,
        /** A tap on a seen hour opens it on the map, see [replayHourSlot]. */
        val hourReplay: Boolean = false,
    )

    data class HourSelection(
        /** The oldest and the newest UTC hour the server lists. */
        val range: ClosedRange<Instant>,
        /** One or two UTC hours, none while nothing is picked. */
        val selected: List<Instant> = emptyList(),
        /** The grid slot holding the first selected hour, null when the grid does not show it. */
        val selectedSlot: WatchActivityGrid.Slot? = null,
        /** Null while nothing is picked. */
        val aircraft: HourAircraftState? = null,
    ) {
        fun isSelectable(slot: WatchActivityGrid.Slot): Boolean =
            slot.state != WatchActivityGrid.State.NOT_YET && slot.hours.any { it in range }

        val canStepBack: Boolean
            get() = selected.isNotEmpty() && selected.first().minus(HOUR) in range

        val canStepForward: Boolean
            get() = selected.isNotEmpty() && selected.last().plus(HOUR) in range

        companion object {
            val HOUR: Duration = Duration.ofHours(1)
            private val SPAN: Duration = Duration.ofDays(7)

            /** From the UTC hour 7 days before [now] up to the current one. */
            fun rangeAt(now: Instant): ClosedRange<Instant> {
                val current = now.truncatedTo(ChronoUnit.HOURS)
                return current.minus(SPAN)..current
            }
        }
    }

    data class Matches(
        val aircraft: List<Match>,
        /** When the check these came from ran, null when they come from cached aircraft data instead. */
        val checkAt: Instant?,
        /** How many aircraft that check counted, the cache may know details for fewer. */
        val total: Int?,
        val fromWatchCenter: Boolean,
        /** False for a squawk watch while the device location is unknown. */
        val sortedByDistance: Boolean,
        /** A check of what matches now is underway, see [LiveCheck]. */
        val checking: Boolean = false,
        /** That check did not answer, these are what was known before. */
        val checkFailed: Boolean = false,
        /** That check was refused because the daily watch check limit is used up, these are what was known before. */
        val checkLimitReached: Boolean = false,
    )

    sealed interface LiveCheck {
        data object Running : LiveCheck

        data class Answered(
            val watchId: WatchId,
            val revision: Long,
            val at: Instant,
            val aircraft: List<Aircraft>,
            /** Null when the server capped its answer without saying how many matched. */
            val total: Int?,
            val expiresAt: Instant?,
        ) : LiveCheck {
            fun answers(watch: Watch): Boolean = watch.id == watchId && watch.definitionRevision == revision

            /** Afterwards the rows still say what was there at [at], they no longer count what is there. */
            fun isCurrentAt(now: Instant): Boolean = expiresAt?.isAfter(now) == true
        }

        data object Unavailable : LiveCheck

        /** The daily watch check limit is used up. */
        data object LimitReached : LiveCheck
    }

    data class Match(val aircraft: Aircraft, val distanceInMeter: Float?)

    companion object {
        private val COUNT_WINDOW: Duration = Duration.ofDays(30)
        private val LIVE_CHECK_TIMEOUT: Duration = Duration.ofSeconds(20)
        private val CLOCK_TICK: Duration = Duration.ofSeconds(30)
    }
}

/** Squawk and location watches count the aircraft they match, the others match one aircraft. */
internal val Watch.Status.isCounting: Boolean
    get() = this is SquawkWatch.Status || this is LocationWatch.Status
