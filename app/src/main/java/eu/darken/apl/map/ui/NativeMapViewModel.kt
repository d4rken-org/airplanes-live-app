package eu.darken.apl.map.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.SystemClock
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.apl.R
import eu.darken.apl.ar.ui.DestinationAr
import eu.darken.apl.common.WebpageTool
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.datastore.value
import eu.darken.apl.common.datastore.valueBlocking
import eu.darken.apl.common.debug.logging.Logging.Priority.INFO
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.flight.FlightRepo
import eu.darken.apl.common.flight.FlightRoute
import eu.darken.apl.common.flight.RouteProgress
import eu.darken.apl.common.flight.progressOf
import eu.darken.apl.common.flow.SingleEventFlow
import eu.darken.apl.common.flow.replayingShare
import eu.darken.apl.common.location.LocationManager2
import eu.darken.apl.common.permissions.Permission
import eu.darken.apl.common.uix.ScreenVisits
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.aircraft.AircraftHex
import eu.darken.apl.main.core.aircraft.IcaoCountries
import eu.darken.apl.main.core.findByHex
import eu.darken.apl.map.core.AircraftShapes
import eu.darken.apl.map.core.AircraftShapesRepo
import eu.darken.apl.map.core.AirplanesLive
import eu.darken.apl.map.core.HistoryTimeline
import eu.darken.apl.map.core.MapAircraftDetails
import eu.darken.apl.map.core.MapAircraftProvider
import eu.darken.apl.map.core.MapFrames
import eu.darken.apl.map.core.MapOptions
import eu.darken.apl.map.core.MapSettings
import eu.darken.apl.map.core.MapSidebarData
import eu.darken.apl.map.core.MapViewport
import eu.darken.apl.map.core.MapWatches
import eu.darken.apl.map.core.NativeMapStyle
import eu.darken.apl.map.core.SavedCamera
import eu.darken.apl.map.core.Sighting
import eu.darken.apl.map.core.selectedSighting
import eu.darken.apl.map.core.sighting
import eu.darken.apl.search.ui.DestinationSearch
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.ServerCodes
import eu.darken.apl.server.compat.AppCompatibility
import eu.darken.apl.server.compat.CompatState
import eu.darken.apl.upgrade.UpgradeRepo
import eu.darken.apl.upgrade.ui.DestinationUpgrade
import eu.darken.apl.watch.core.LocationWatchCreator
import eu.darken.apl.watch.core.LocationWatchNotAllowedException
import eu.darken.apl.watch.core.WatchId
import eu.darken.apl.watch.core.WatchLimitReachedException
import eu.darken.apl.watch.core.WatchRepo
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.LocationWatch
import eu.darken.apl.watch.ui.DestinationCreateAircraftWatch
import eu.darken.apl.watch.ui.DestinationWatchDetails
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningReduce
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class NativeMapViewModel @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    @param:ApplicationContext private val context: Context,
    sensorManager: SensorManager,
    private val mapSettings: MapSettings,
    private val webpageTool: WebpageTool,
    private val watchRepo: WatchRepo,
    private val aircraftRepo: AircraftRepo,
    private val flightRepo: FlightRepo,
    private val locationManager2: LocationManager2,
    private val provider: MapAircraftProvider,
    private val accessRepo: AccessRepo,
    private val serverClock: ServerClock,
    private val mapRequests: MapRequests,
    private val locationWatchCreator: LocationWatchCreator,
    shapesRepo: AircraftShapesRepo,
    upgradeRepo: UpgradeRepo,
    compatibility: AppCompatibility,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Map", "Native", "ViewModel"),
) {

    val hasRotationSensor: Boolean =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null ||
                sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) != null

    val events = SingleEventFlow<NativeMapEvents>()

    private val mapFrames = MapFrames(provider, serverClock, dispatcherProvider)

    /**
     * Collected by the map view itself, frames arrive too often to go through composition.
     *
     * Everything built on frames stops as soon as nothing shows the map, so returning to it starts
     * a new viewing loop, see the one-screen rule in [AircraftRepo.viewing]. While the history is
     * open they come from it instead, and no viewing loop runs.
     */
    val frames: SharedFlow<MapAircraftProvider.Frame> = mapFrames.frames
        .shareIn(vmScope, SharingStarted.WhileSubscribed(), replay = 1)

    // Without them every aircraft keeps the generic icon
    val shapes: StateFlow<AircraftShapes?> = flow<AircraftShapes?> { emit(shapesRepo.shapes()) }
        .catch { log(tag, WARN) { "Aircraft shapes unavailable: ${it.asLog()}" } }
        .stateIn(vmScope, SharingStarted.Lazily, null)

    private val follow = MutableStateFlow(false)
    private val multiSelect = MutableStateFlow(false)
    private val isolate = MutableStateFlow(false)
    private val pinned = MutableStateFlow<List<AircraftHex>>(emptyList())
    private val selectedHex = MutableStateFlow<AircraftHex?>(null)
    private val myLocation = MutableStateFlow<Pair<Double, Double>?>(null)

    private val _initialCamera = MutableStateFlow<MapOptions.Camera?>(null)

    /** Where the map opens, known once the options and the saved view were read. */
    val initialCamera: StateFlow<MapOptions.Camera?> = _initialCamera

    private val toggles = combine(
        mapSettings.isLabelsEnabled.flow,
        mapSettings.isAllTracksEnabled.flow,
        combine(
            mapSettings.isMilitaryOnlyEnabled.flow,
            mapSettings.isMilitaryHighlightEnabled.flow,
            mapSettings.isWatchHighlightEnabled.flow,
        ) { o, h, w -> Triple(o, h, w) },
        follow,
        combine(multiSelect, isolate) { m, i -> m to i },
    ) { labels, allTracks, (militaryOnly, militaryHighlight, watchHighlight), follow, (multiSelect, isolate) ->
        Toggles(labels, allTracks, militaryOnly, militaryHighlight, watchHighlight, follow, multiSelect, isolate)
    }

    /** Isolating with nothing selected or pinned left shows everything again. */
    private val isolating = combine(isolate, selectedHex, pinned) { isolate, hex, pins ->
        isolate && (hex != null || pins.isNotEmpty())
    }

    private val historyLoading = MutableStateFlow<AircraftHex?>(null)
    private var historyJob: Job? = null

    /** Null while there is nothing to offer, e.g. the history is already open. */
    private val historyAction = combine(
        accessRepo.state,
        selectedHex,
        historyLoading,
        mapFrames.replay,
    ) { access, hex, loading, replay ->
        when {
            hex == null || replay != null || access?.hasAircraftHistory != true -> null
            !access.offersAircraftHistory -> HistoryAction.LOCKED
            loading == hex -> HistoryAction.LOADING
            else -> HistoryAction.READY
        }
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val historyView: StateFlow<HistoryView?> = mapFrames.replay
        .flatMapLatest { replay ->
            if (replay == null) {
                flowOf(null)
            } else {
                replay.state.map { state ->
                    val days = replay.timeline.days
                    val index = days.indexOf(state.day)
                    HistoryView(
                        hex = replay.hex,
                        timeline = replay.timeline,
                        day = replay.timeline.day(state.day),
                        cursor = state.cursor,
                        playing = state.playing,
                        speed = state.speed,
                        callsign = replay.timeline.callsignAt(state.cursor),
                        canGoBack = index > 0,
                        canGoForward = index in 0 until days.lastIndex,
                    )
                }
            }
        }
        .stateIn(vmScope, SharingStarted.WhileSubscribed(), null)

    // Playback moves the cursor every frame, the sheet only needs to keep up roughly
    private val historyDetails: Flow<HistoryDetails?> = historyView
        .map { view -> view?.let { historyDetails(it) } }
        .distinctUntilChanged()
        .conflate()
        .transform { details ->
            emit(details)
            if (details != null) delay(HISTORY_DETAILS_PERIOD)
        }

    // Everything the sheet and banners show changes rarely compared to the frames
    private val frameSummary = frames
        .map { frame ->
            FrameSummary(
                selectedDetails = frame.selectedDetails,
                // Without its position, so the summary doesn't change with every move
                selectedPlane = frame.selected?.let { MapAircraftDetails.from(it, IcaoCountries.countryName(it.hex)) },
                selectedSquawk = frame.selected?.squawk,
                selectedMissing = frame.selectedMissing,
                shownCount = frame.shownCount,
                drawnCount = frame.traffic.size + if (frame.selected != null) 1 else 0,
                capped = frame.capped,
                totalMatching = frame.totalMatching,
                cutoffAltitudeFeet = frame.cutoffAltitudeFeet,
                filterPending = frame.filterPending,
                selectedHex = frame.selectedHex,
                // Once it fades on the map its position is no longer current
                selectedFresh = frame.replay == null && frame.selected?.let { it.opacity >= 1f } == true,
                selectedDrawn = frame.replay == null && frame.selected != null,
                selectedSighting = frame.selectedSighting(),
            )
        }
        .distinctUntilChanged()
        // The sheet's state and its route both read it, mapping every frame once is enough
        .replayingShare(vmScope)

    // A selection from the search may not be in any answer this session
    @OptIn(ExperimentalCoroutinesApi::class)
    private val cachedSighting: Flow<Pair<AircraftHex, Sighting?>?> = selectedHex
        .flatMapLatest { hex ->
            if (hex == null) flowOf(null) else aircraftRepo.cachedByHex(hex).map { hex to it?.sighting() }
        }
        .distinctUntilChanged()

    val state: StateFlow<State?> = combine(
        combine(upgradeRepo.upgradeInfo, mapSettings.nativeMapStyle.flow) { upgrade, style ->
            (upgrade.isSettled && upgrade.isPro) to NativeMapStyle.fromKey(style)
        },
        combine(toggles, historyAction, compatibility.state) { toggles, historyAction, compat ->
            Triple(toggles, historyAction, compat == CompatState.UpdateRequired)
        },
        combine(frameSummary, historyDetails, cachedSighting) { summary, history, cached ->
            Triple(summary, history, cached)
        },
        provider.state,
        combine(selectedHex, myLocation, pinned, isolating) { hex, location, pins, isolating ->
            Selection(hex, location, pins, isolating)
        },
    ) { (isPro, style), (toggles, historyAction, updateRequired), (summary, history, cached), viewing, (hex, location, pins, isolating) ->
        // The history shows a past the live state says nothing about
        val live = history == null
        val waiting = (viewing as? AircraftRepo.MapViewingState.Waiting)?.reason?.takeIf { live }
        // A rate limit is retried within seconds, anything else leaves the positions aging. A refused
        // app explains itself, a failure from before the refusal must not read as a connection problem.
        val offline = live && !updateRequired && (viewing as? AircraftRepo.MapViewingState.Snapshot)?.error
            ?.let { !(it is ServerApiException && it.status == 429) } == true
        // With a filter the server's counts describe aircraft that are not drawn
        val filtered = toggles.militaryOnly || isolating
        // Without an answer coming, partial details are all there is
        val detailsLoading = live && hex != null && summary.selectedMissing != hex && waiting == null && !offline
        // The sheet opens on the tap and fills in as first the map and then the server know more
        val details = history?.details?.takeIf { it.hex == hex } ?: hex?.let {
            summary.selectedDetails
                ?.takeIf { it.hex == hex }
                ?.let { MapAircraftDetails.from(it, IcaoCountries.countryName(it.hex)) }
                ?: summary.selectedPlane?.takeIf { it.hex == hex }
                ?: MapAircraftDetails.pending(hex, IcaoCountries.countryName(hex))
        }
        // The frames can still describe the previous selection
        val current = summary.takeIf { live && hex != null && it.selectedHex == hex }
        // Until a frame describes this selection, the cache can't be weighed against the map
        val sighting = current?.let { c ->
            listOfNotNull(c.selectedSighting, cached?.takeIf { it.first == hex }?.second).maxByOrNull { it.observedAt }
        }
        // Pinned aircraft have reserved places, isolating them leaves nothing for the cap to hide.
        // An answer for the other filter has counts that don't apply.
        val capped = summary.capped && !isolating && !summary.filterPending
        State(
            tagline = tagline,
            isPro = isPro,
            style = style,
            toggles = toggles,
            selectedHex = hex,
            details = details,
            shownCount = if (filtered) summary.drawnCount else summary.shownCount,
            capped = capped,
            totalMatching = summary.totalMatching.takeIf { !filtered },
            // The cutoff is about the answer, so are its counts, also when military-only draws fewer
            capAltitude = capAltitude(capped, summary.cutoffAltitudeFeet, summary.shownCount, summary.totalMatching),
            detailsLoading = detailsLoading,
            waiting = waiting,
            offline = offline,
            filterPending = live && summary.filterPending,
            myLocation = location,
            // The frames can still describe the previous selection
            selectedMissing = live && hex != null && summary.selectedMissing == hex,
            pinnedCount = pins.size,
            isolating = isolating,
            historyAction = historyAction,
            historyNote = history?.note,
            seen = when {
                !live || hex == null -> null
                current?.selectedFresh == true -> Seen.Live
                sighting != null -> Seen.At(sighting.observedAt)
                // Answered without a position, or no answer is coming
                summary.selectedDetails?.hex == hex || !detailsLoading -> Seen.Unknown
                else -> null
            },
            lastSeenMark = sighting?.takeIf { !current.selectedDrawn }?.let {
                LastSeenMark(
                    hex = hex!!,
                    label = details?.let { d -> d.callsign ?: d.registration } ?: hex,
                    latitude = it.latitude,
                    longitude = it.longitude,
                    trackDegrees = it.trackDegrees,
                )
            },
        )
    }.stateIn(vmScope, SharingStarted.WhileSubscribed(), null)

    sealed interface RouteDisplay {
        data class Loading(val hex: AircraftHex) : RouteDisplay
        data class Result(val hex: AircraftHex, val route: FlightRoute?, val progress: RouteProgress) : RouteDisplay
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val routeDisplay: Flow<RouteDisplay?> = combine(selectedHex, frameSummary, mapFrames.replay) { hex, summary, replay ->
        // Today's route says nothing about the flight shown from the history
        if (replay != null) return@combine null to null
        val callsign = summary.selectedDetails?.takeIf { it.hex == hex }?.callsign?.trim()?.takeIf { it.isNotEmpty() }
            ?: summary.selectedPlane?.takeIf { it.hex == hex }?.callsign
        hex to callsign
    }
        .distinctUntilChanged()
        .transformLatest { (hex, callsign) ->
            if (hex == null) {
                emit(null)
                return@transformLatest
            }
            // A hex lookup is a charged search term, the map answers from what it already has
            flightRepo.prefetch(hex, callsign ?: aircraftRepo.findByHex(hex)?.callsign)
            emitAll(
                routeDisplays(hex, flightRepo.getByHex(hex), frameSummary.map { it.selectedDetails }) {
                    serverClock.now()
                }
            )
        }

    private val tagline: String = context.resources.getStringArray(R.array.map_taglines).random()

    private var initialized = false

    fun init() {
        if (initialized) return
        initialized = true

        launch {
            val requested = mapRequests.take()
            val (focus, pins) = show(requested?.filter)
            val camera = requested?.camera
                ?: focus?.let { cachedCamera(it) }
                ?: savedCamera().takeIf { requested == null }
                ?: DEFAULT_CAMERA
            _initialCamera.value = camera

            // Search results carry positions in the cache, fitting them beats guessing a zoom
            if (requested?.camera == null && pins.isNotEmpty()) fitToCached(pins)

            // Asked for while the map was already open, it replaces what the map showed
            mapRequests.consumeEach { options ->
                follow.value = false
                // On Main with the editor's state, so an editor about to open can't come up over the request
                val (focus, pins) = withContext(dispatcherProvider.Main) {
                    dropAreaDraft()
                    show(options.filter)
                }
                val camera = options.camera ?: focus?.let { cachedCamera(it) }
                if (camera != null) moveCamera(camera)
                if (options.camera == null && pins.isNotEmpty()) fitToCached(pins)
            }
        }

        launch { mapSettings.isAllTracksEnabled.flow.collect { provider.setTracksEnabled(it) } }
        launch {
            combine(mapSettings.isMilitaryOnlyEnabled.flow, isolate, pinned) { _, _, _ -> }.collect { applyFilter() }
        }
        // Follow mode moves the camera every frame, idle reports come far more often than a fetch or a save needs
        launch {
            cameraIdle.filterNotNull().sample(VIEWPORT_SAMPLE).collect { (viewport, _) -> provider.onViewport(viewport) }
        }
        launch {
            cameraIdle.filterNotNull().debounce(CAMERA_SAVE_DEBOUNCE).collect { (_, center) ->
                if (mapSettings.isRestoreLastViewEnabled.value()) {
                    mapSettings.lastCamera.update { SavedCamera.from(center) }
                }
            }
        }
    }

    /** Pins and selects what [filter] names instead of what was pinned and selected, returns the focus and pins. */
    private suspend fun show(filter: MapOptions.Filter?): Pair<AircraftHex?, List<AircraftHex>> {
        val filter = filter ?: MapOptions.Filter()
        val focus = filter.selected.firstOrNull()?.uppercase()
        val requestedPins = filter.filtered.map { it.uppercase() }.distinct()
        val pins = requestedPins.take(MapAircraftProvider.MAX_PINNED)

        if (pins != pinned.value) {
            pinned.value = pins
            provider.pin(pins)
        }
        isolate.value = pins.isNotEmpty() && filter.noIsolation != true
        if (requestedPins.size > pins.size) {
            events.emit(NativeMapEvents.PinnedTruncated(requestedPins.size, pins.size))
        }
        // Selecting also ends a history that is open and applies the filter
        if (focus != null || selectedHex.value != null || mapFrames.replay.value != null) select(focus) else applyFilter()
        return focus to pins
    }

    /** A map view created before the move runs, e.g. while the map's tab is not shown, starts there instead. */
    private suspend fun moveCamera(camera: MapOptions.Camera) {
        _initialCamera.value = camera
        events.emit(NativeMapEvents.MoveCamera(camera))
    }

    private suspend fun savedCamera(): MapOptions.Camera? {
        if (!mapSettings.isRestoreLastViewEnabled.value()) return null
        return mapSettings.lastCamera.value()?.toCamera()
    }

    private suspend fun cachedCamera(hex: AircraftHex): MapOptions.Camera? =
        aircraftRepo.findByHex(hex)?.location?.let { MapOptions.Camera(it.latitude, it.longitude, FOCUS_ZOOM) }

    private suspend fun fitToCached(hexes: List<AircraftHex>) {
        val cache = aircraftRepo.cache.first()
        fitPositions(hexes.mapNotNull { hex -> cache[hex]?.location?.let { it.latitude to it.longitude } })
    }

    /** With [aroundHistory] the bounds keep clear of that history's overlays, also across the antimeridian. */
    private suspend fun fitPositions(positions: List<Pair<Double, Double>>, aroundHistory: MapFrames.Replay? = null) {
        if (positions.isEmpty()) return
        if (positions.size == 1 && aroundHistory == null) {
            moveCamera(MapOptions.Camera(positions[0].first, positions[0].second, FOCUS_ZOOM))
            return
        }
        val south = positions.minOf { it.first }
        val north = positions.maxOf { it.first }
        val west = positions.minOf { it.second }
        val east = positions.maxOf { it.second }
        if (east - west <= 180.0) {
            // Fitting needs the view's size, a map view created before the fit runs starts close to it
            if (aroundHistory == null) {
                _initialCamera.value = MapOptions.Camera((south + north) / 2, (west + east) / 2, zoomToShow(east - west, north - south))
            }
            events.emit(NativeMapEvents.FitBounds(south = south, west = west, north = north, east = east, aroundHistory = aroundHistory))
            return
        }
        // Across the antimeridian the short way round is the one through ±180
        val shifted = positions.map { if (it.second < 0) it.second + 360.0 else it.second }
        if (aroundHistory != null) {
            // East past 180 keeps the bounds in one piece
            events.emit(NativeMapEvents.FitBounds(south, shifted.min(), north, shifted.max(), aroundHistory))
            return
        }
        val lonSpan = shifted.max() - shifted.min()
        val center = MapViewport.normalizeLongitude((shifted.max() + shifted.min()) / 2)
        moveCamera(MapOptions.Camera((south + north) / 2, center, zoomToShow(lonSpan, north - south)))
    }

    private fun applyFilter() {
        val isolated = if (isolate.value) (pinned.value + listOfNotNull(selectedHex.value)).toSet() else null
        provider.setFilter(
            MapAircraftProvider.Filter(
                militaryOnly = mapSettings.isMilitaryOnlyEnabled.valueBlocking,
                isolated = isolated?.takeIf { it.isNotEmpty() },
            )
        )
    }

    private val cameraIdle = MutableStateFlow<Pair<MapViewport, MapOptions.Camera>?>(null)

    fun onCameraIdle(viewport: MapViewport, center: MapOptions.Camera) {
        // A map view created later, e.g. after a rotation, starts where this one was
        _initialCamera.value = center
        cameraIdle.value = viewport to center
    }

    fun openWebsite() {
        val options = MapOptions(
            filter = MapOptions.Filter(selected = setOfNotNull(selectedHex.value)),
            camera = cameraIdle.value?.second ?: _initialCamera.value,
        )
        webpageTool.open(options.createUrl())
    }

    fun onAircraftTapped(hex: AircraftHex) {
        log(tag) { "onAircraftTapped($hex)" }
        // The editor holds the map until it is done, a tap would open the aircraft sheet under it
        if (_areaDraft.value != null) return
        // Only the shown aircraft is on the map, selecting it again would start the live map over
        if (mapFrames.replay.value != null) return
        if (multiSelect.value) {
            val current = pinned.value
            val next = if (hex in current) current - hex else (current + hex).take(MapAircraftProvider.MAX_PINNED)
            pinned.value = next
            if (next.isEmpty() && selectedHex.value == null) isolate.value = false
            provider.pin(next)
            return
        }
        // Selecting it again would drop the answer and trail that tell where it was last seen
        if (hex == selectedHex.value && state.value?.lastSeenMark?.hex == hex) return
        select(hex)
    }

    fun onMapTapped() {
        if (_areaDraft.value != null) return
        // Looking around the history's track must not end it
        if (mapFrames.replay.value != null) return
        if (!multiSelect.value && selectedHex.value != null) select(null)
    }

    /**
     * [docked] where the list stays beside the map: it lists what the map shows, so the aircraft is already in
     * view, and moving the map would change the list under the finger.
     */
    fun onSidebarAircraftClicked(hex: AircraftHex, docked: Boolean) = launch {
        if (_areaDraft.value != null) return@launch
        select(hex)
        if (docked) return@launch
        _isSidebarOpen.value = false
        val plane = frames.replayCache.lastOrNull()?.onScreen?.firstOrNull { it.hex == hex } ?: return@launch
        events.emit(NativeMapEvents.MoveCamera(MapOptions.Camera(plane.latitude, plane.longitude, FOCUS_ZOOM), keepZoom = true))
    }

    fun deselect() = select(null)

    private fun select(hex: AircraftHex?) {
        historyJob?.cancel()
        mapFrames.close()
        val normalized = hex?.uppercase()
        selectedHex.value = normalized
        provider.select(normalized)
        if (normalized == null) follow.value = false
        // Otherwise the next aircraft tapped would hide all others without any visible reason
        if (normalized == null && pinned.value.isEmpty()) isolate.value = false
        applyFilter()
    }

    fun toggleLabels() {
        mapSettings.isLabelsEnabled.valueBlocking = !mapSettings.isLabelsEnabled.valueBlocking
    }

    fun toggleAllTracks() {
        mapSettings.isAllTracksEnabled.valueBlocking = !mapSettings.isAllTracksEnabled.valueBlocking
    }

    fun toggleMilitaryOnly() {
        mapSettings.isMilitaryOnlyEnabled.valueBlocking = !mapSettings.isMilitaryOnlyEnabled.valueBlocking
    }

    fun toggleMilitaryHighlight() {
        mapSettings.isMilitaryHighlightEnabled.valueBlocking = !mapSettings.isMilitaryHighlightEnabled.valueBlocking
    }

    fun toggleWatchHighlight() {
        mapSettings.isWatchHighlightEnabled.valueBlocking = !mapSettings.isWatchHighlightEnabled.valueBlocking
    }

    /** Flips with every request, so it is kept out of [State]. */
    val viewingAttempt: StateFlow<Boolean> = aircraftRepo.viewingAttempt

    fun retryViewing() {
        log(tag) { "retryViewing()" }
        aircraftRepo.retryViewing()
    }

    private var followJumpJob: Job? = null

    fun toggleFollow() {
        val hex = selectedHex.value ?: return
        followJumpJob?.cancel()
        follow.value = !follow.value
        if (!follow.value || mapFrames.replay.value != null) return
        // Nothing to follow on the map: off to where it was last seen, following resumes once it shows up again
        followJumpJob = vmScope.launch {
            val frame = withTimeoutOrNull(FOLLOW_FRAME_WAIT) {
                frames.first { it.selectedHex == hex && it.replay == null }
            } ?: return@launch
            if (frame.selected != null) return@launch
            val sighting = listOfNotNull(frame.selectedSighting(), aircraftRepo.findByHex(hex)?.sighting())
                .maxByOrNull { it.observedAt }
                ?: return@launch
            if (!follow.value || selectedHex.value != hex || mapFrames.replay.value != null) return@launch
            val zoom = maxOf(cameraIdle.value?.second?.zoom ?: FOCUS_ZOOM, FOCUS_ZOOM)
            events.emit(NativeMapEvents.MoveCamera(MapOptions.Camera(sighting.latitude, sighting.longitude, zoom)))
        }
    }

    /** The reference for how long ago the selection was seen, the device clock may be off. */
    fun serverNowMillis(): Long = serverClock.now().toEpochMilli()

    fun toggleMultiSelect() {
        multiSelect.value = !multiSelect.value
    }

    fun toggleIsolate() {
        isolate.value = !isolate.value
    }

    fun clearPinned() {
        pinned.value = emptyList()
        provider.pin(emptyList())
        isolate.value = false
    }

    fun setListQuery(query: String) {
        _listControls.update { it.copy(query = query) }
    }

    fun toggleListWatchedOnly() {
        _listControls.update { it.copy(watchedOnly = !it.watchedOnly) }
    }

    /** Choosing the current field again reverses its direction. */
    fun setListSort(field: MapSidebarData.SortField) {
        _listControls.update {
            if (it.sort == field) it.copy(ascending = !it.ascending) else it.copy(sort = field, ascending = true)
        }
    }

    /** The list's own filters, the map's stay as they are. */
    fun clearListFilters() {
        _listControls.update { it.copy(query = "", watchedOnly = false) }
    }

    fun toggleSidebar() {
        // The list shows live aircraft, none of which are drawn during the history
        if (mapFrames.replay.value != null || _areaDraft.value != null) return
        _isSidebarOpen.value = !_isSidebarOpen.value
    }

    fun closeSidebar() {
        _isSidebarOpen.value = false
    }

    fun goToMyLocation() = launch {
        log(tag) { "goToMyLocation()" }
        if (!Permission.ACCESS_COARSE_LOCATION.isGranted(context)) {
            events.emit(NativeMapEvents.RequestLocationPermission)
            return@launch
        }

        val available = withTimeoutOrNull(10_000) {
            locationManager2.state.filterIsInstance<LocationManager2.State.Available>().first()
        }
        if (available == null) {
            log(tag, INFO) { "goToMyLocation(): Location unavailable" }
            events.emit(NativeMapEvents.LocationUnavailable)
            return@launch
        }
        val loc = available.location
        myLocation.value = loc.latitude to loc.longitude
        follow.value = false
        events.emit(NativeMapEvents.MoveCamera(MapOptions.Camera(loc.latitude, loc.longitude, MY_LOCATION_ZOOM)))
    }

    fun reset() = launch {
        log(tag) { "reset()" }
        clearPinned()
        select(null)
        events.emit(NativeMapEvents.MoveCamera(DEFAULT_CAMERA))
    }

    fun onOpenUrl(url: String) = webpageTool.open(url)

    fun shareAircraft(details: MapAircraftDetails) = launch {
        val link = AirplanesLive.aircraftUrl(details.hex)
        val name = details.callsign ?: details.registration
        val extras = listOfNotNull(details.registration.takeIf { it != name }, details.icaoType)
        val text = when {
            name == null -> context.getString(R.string.map_aircraft_details_share_text_unnamed, link)
            extras.isEmpty() -> context.getString(R.string.map_aircraft_details_share_text, name, link)
            else -> context.getString(
                R.string.map_aircraft_details_share_text,
                "$name (${extras.joinToString(", ")})",
                link,
            )
        }
        events.emit(NativeMapEvents.ShareAircraft(text))
    }

    /** Every opening asks again, each answer counts against the viewing allowance. */
    fun openHistory() {
        val hex = selectedHex.value ?: return
        val access = accessRepo.state.value
        if (access?.offersAircraftHistory != true) {
            if (access?.hasAircraftHistory == true) goUpgrade()
            return
        }
        if (historyJob?.isActive == true || mapFrames.replay.value != null) return
        historyLoading.value = hex
        historyJob = vmScope.launch {
            try {
                val viewing = provider.state.value as? AircraftRepo.MapViewingState.Waiting
                if (viewing?.reason is AircraftRepo.ViewingState.Reason.Exhausted) {
                    events.emit(NativeMapEvents.HistoryUnavailable(NativeMapEvents.HistoryUnavailable.Reason.LIMIT_REACHED))
                    return@launch
                }
                // A request held back by a used up allowance would otherwise wait for hours
                val replay = when (val opening = withTimeoutOrNull(HISTORY_TIMEOUT) { mapFrames.open(hex, ZoneId.systemDefault()) }) {
                    null -> {
                        events.emit(NativeMapEvents.HistoryUnavailable(NativeMapEvents.HistoryUnavailable.Reason.FAILED))
                        return@launch
                    }

                    MapFrames.Opening.Empty -> {
                        events.emit(NativeMapEvents.HistoryEmpty)
                        return@launch
                    }

                    MapFrames.Opening.Busy, MapFrames.Opening.Superseded -> return@launch
                    is MapFrames.Opening.Opened -> opening.replay
                }
                log(tag, INFO) { "History of $hex opened on ${replay.state.value.day}" }
                follow.value = false
                _isSidebarOpen.value = false
                fitHistoryDay(replay)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(tag, WARN) { "History of $hex failed: ${e.asLog()}" }
                val reason = when ((e as? ServerApiException)?.code) {
                    ServerCodes.TIER_RESTRICTED -> NativeMapEvents.HistoryUnavailable.Reason.NO_ACCESS
                    ServerCodes.DAILY_ALLOWANCE_EXHAUSTED -> NativeMapEvents.HistoryUnavailable.Reason.LIMIT_REACHED
                    ServerCodes.APP_UPDATE_REQUIRED -> NativeMapEvents.HistoryUnavailable.Reason.UPDATE_REQUIRED
                    else -> NativeMapEvents.HistoryUnavailable.Reason.FAILED
                }
                events.emit(NativeMapEvents.HistoryUnavailable(reason))
                // The access state said feeder, the server disagrees: it is out of date
                if (reason == NativeMapEvents.HistoryUnavailable.Reason.NO_ACCESS) {
                    this@NativeMapViewModel.launch { accessRepo.refreshThrottled("history-restricted") }
                }
            } finally {
                historyLoading.compareAndSet(hex, null)
            }
        }
    }

    /** Back to the live map, the aircraft stays selected. */
    fun closeHistory() {
        historyJob?.cancel()
        mapFrames.close()
        follow.value = false
    }

    fun showHistoryDay(forward: Boolean) {
        val replay = mapFrames.replay.value ?: return
        val days = replay.timeline.days
        val date = days.getOrNull(days.indexOf(replay.state.value.day) + if (forward) 1 else -1) ?: return
        mapFrames.showDay(replay, date)
        launch { fitHistoryDay(replay) }
    }

    fun seekHistory(time: Long) {
        val replay = mapFrames.replay.value ?: return
        mapFrames.seek(replay, time)
    }

    fun toggleHistoryPlaying() {
        val replay = mapFrames.replay.value ?: return
        mapFrames.setPlaying(replay, !replay.state.value.playing)
    }

    fun cycleHistorySpeed() {
        val replay = mapFrames.replay.value ?: return
        val speeds = MapFrames.SPEEDS
        mapFrames.setSpeed(replay, speeds[(speeds.indexOf(replay.state.value.speed) + 1) % speeds.size])
    }

    private suspend fun fitHistoryDay(replay: MapFrames.Replay) {
        val day = replay.timeline.day(replay.state.value.day)
        val positions = day.edges.flatMap { listOf(it.startPoint, it.endPoint) } + day.sightings
        fitPositions(positions.map { it.latitude to it.longitude } + day.gaps.flatMap { it.path() }, aroundHistory = replay)
    }

    /** Whether [replay] is still the history shown, a fit asked for an earlier one is out of date. */
    fun isShowing(replay: MapFrames.Replay): Boolean = mapFrames.replay.value === replay

    private fun historyDetails(view: HistoryView): HistoryDetails {
        val track = view.timeline.track
        val position = view.timeline.positionAt(view.cursor)
        val note = when {
            position == null -> {
                val span = view.day.spans.lastOrNull { view.cursor >= it.start && view.cursor < it.end }?.kind
                when (span) {
                    HistoryTimeline.SpanKind.NOT_RECORDED -> HistoryNote.NOT_RECORDED
                    HistoryTimeline.SpanKind.OMITTED -> HistoryNote.OMITTED
                    else -> HistoryNote.NOT_SEEN
                }
            }

            position.speedKnots != null -> HistoryNote.ESTIMATED
            else -> null
        }
        val details = MapAircraftDetails(
            hex = view.hex,
            isHistorical = true,
            callsign = view.callsign,
            registration = track.registration,
            country = IcaoCountries.countryName(view.hex),
            icaoType = track.aircraftType,
            altitudeFt = position?.point?.altitudeFeet?.roundToInt(),
            groundSpeedKt = position?.speedKnots?.toFloat(),
            trackDegrees = position?.trackDegrees,
            track = position?.trackDegrees?.let { "${it.roundToInt()}°" },
            position = position?.point?.let { "%.4f, %.4f".format(Locale.ROOT, it.latitude, it.longitude) },
        )
        return HistoryDetails(details, note)
    }

    fun showInSearch(hex: AircraftHex) =
        navTo(DestinationSearch(targetHexes = listOf(hex)))

    /** Uppercase and trimmed. */
    val watchedHexes: StateFlow<Set<AircraftHex>> = watchRepo.watches
        .map { watches -> watches.filterIsInstance<AircraftWatch>().map { it.hex.trim().uppercase() }.toSet() }
        .distinctUntilChanged()
        .stateIn(vmScope, SharingStarted.WhileSubscribed(), emptySet())

    val mapWatches: StateFlow<MapWatches> = watchRepo.watches
        .map { MapWatches.from(it) }
        .distinctUntilChanged()
        .stateIn(vmScope, SharingStarted.WhileSubscribed(), MapWatches())

    internal val watchZones: StateFlow<List<AreaCircle>> = watchRepo.watches
        .map { watches -> watches.filterIsInstance<LocationWatch>().map { AreaCircle(it.latitude, it.longitude, it.radiusInMeters) } }
        .distinctUntilChanged()
        .stateIn(vmScope, SharingStarted.WhileSubscribed(), emptyList())

    /** The callsign and squawk watches the selected aircraft matches, from the values the map draws it with. */
    val selectedWatchMatches: StateFlow<List<MapWatches.Match>> = combine(frameSummary, selectedHex, mapWatches) { summary, hex, watches ->
        watchMatchesOf(watches, hex, summary.selectedPlane, summary.selectedDrawn, summary.selectedSquawk)
    }
        .distinctUntilChanged()
        .stateIn(vmScope, SharingStarted.WhileSubscribed(), emptyList())

    fun openWatch(id: WatchId) = navTo(DestinationWatchDetails(watchId = id))

    private val _isSidebarOpen = MutableStateFlow(false)
    val isSidebarOpen: StateFlow<Boolean> = _isSidebarOpen

    private val _listControls = MutableStateFlow(MapSidebarData.Controls())
    val listControls: StateFlow<MapSidebarData.Controls> = _listControls

    private data class ListContext(
        val mapFilter: MapSidebarData.MapFilter?,
        val isolating: Boolean,
        val shapes: AircraftShapes?,
    )

    /** Null while the list is closed, it is only built while someone looks at it. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val sidebarData: StateFlow<MapSidebarData?> = _isSidebarOpen.flatMapLatest { open ->
        if (open) listData() else flowOf(null)
    }.stateIn(vmScope, SharingStarted.WhileSubscribed(), null)

    // The newest frame right away when the list opens, then at most one per period
    private fun listData(): Flow<MapSidebarData> = combine(
        frames.conflate().transform {
            emit(it)
            delay(SIDEBAR_REFRESH)
        },
        _listControls,
        mapWatches,
        combine(toggles, isolating, shapes) { toggles, isolating, shapes ->
            ListContext(
                mapFilter = when {
                    isolating -> MapSidebarData.MapFilter.ISOLATED
                    toggles.militaryOnly -> MapSidebarData.MapFilter.MILITARY_ONLY
                    else -> null
                },
                isolating = isolating,
                shapes = shapes,
            )
        },
    ) { frame, controls, watches, context ->
        MapSidebarData.build(
            planes = frame.onScreen,
            controls = controls,
            watches = watches,
            // Sampled when the list is built, following reports the camera several times a second
            center = cameraIdle.value?.second?.let { it.lat to it.lon },
            shapes = context.shapes,
            mapFilter = context.mapFilter,
            // Like the map's own note: isolated aircraft are complete, and a pending filter's cap is not known yet
            serverLimited = frame.capped && !context.isolating && !frame.filterPending,
        )
    }

    /** The hex [onWatchAction] last added, with [SystemClock.elapsedRealtime] at that moment. */
    private val lastQuickAdd = AtomicReference<Pair<AircraftHex, Long>?>(null)

    /** Adds an aircraft that has no watch yet, otherwise opens its watch. */
    private val visits = ScreenVisits()

    fun onScreenShown() = visits.shown()

    fun onScreenHidden() = visits.hidden()

    fun onWatchAction(hex: AircraftHex) {
        val visit = visits.current()
        launch {
            val result = try {
                watchRepo.addAircraft(hex)
            } catch (e: WatchLimitReachedException) {
                if (visits.isCurrent(visit)) events.emit(NativeMapEvents.WatchLimitReached(e))
                return@launch
            }
            when (result) {
                is WatchRepo.AircraftAdd.Created -> {
                    lastQuickAdd.set(result.watch.hex to SystemClock.elapsedRealtime())
                    val details = state.value?.details?.takeIf { it.hex.equals(hex, ignoreCase = true) }
                    val label = details?.registration ?: details?.callsign ?: result.watch.hex
                    if (visits.isCurrent(visit)) events.emit(NativeMapEvents.WatchAdded(result.watch.id, label))
                }

                is WatchRepo.AircraftAdd.Existing -> {
                    // The second tap of a double tap finds the watch the first one just added
                    val (lastHex, addedAt) = lastQuickAdd.get() ?: (null to 0L)
                    val repeated = lastHex.equals(result.watch.hex, ignoreCase = true) &&
                            SystemClock.elapsedRealtime() - addedAt < QUICK_ADD_REPEAT_MS
                    if (!repeated && visits.isCurrent(visit)) navTo(DestinationWatchDetails(watchId = result.watch.id))
                }
            }
        }
    }

    /** Through the dialog, so a note can go along. */
    fun onWatchWithNote(hex: AircraftHex) {
        val visit = visits.current()
        launch {
            val existing = watchRepo.findAircraft(hex)
            if (!visits.isCurrent(visit)) return@launch
            navTo(if (existing != null) DestinationWatchDetails(watchId = existing.id) else DestinationCreateAircraftWatch(hex = hex))
        }
    }

    fun undoWatch(id: WatchId) = launch {
        lastQuickAdd.set(null)
        watchRepo.delete(id)
    }

    private val _areaDraft = MutableStateFlow<AreaDraft?>(null)
    val areaDraft: StateFlow<AreaDraft?> = _areaDraft

    val areaMaxRadiusKm: StateFlow<Int> = locationWatchCreator.maxRadiusKm
        .stateIn(vmScope, SharingStarted.WhileSubscribed(), LocationWatch.DEFAULT_MAX_RADIUS_KM)

    private var areaIds = 0L
    private var areaEntry: Job? = null
    private var areaLabel: Job? = null

    /** Opens the area editor there, or moves its center while it is open. */
    fun onMapLongPressed(latitude: Double, longitude: Double) {
        log(tag) { "onMapLongPressed($latitude, $longitude)" }
        // Only the shown aircraft is on the map, and its history owns the sheet
        if (mapFrames.replay.value != null) return
        val draft = _areaDraft.value
        if (draft != null) {
            if (!draft.saving) placeAreaDraft(latitude, longitude, session = draft.session)
            return
        }
        if (areaEntry?.isActive == true) return
        areaEntry = vmScope.launch {
            if (!locationWatchCreator.isAllowedNow()) {
                events.emit(NativeMapEvents.LocationWatchNotAllowed)
                return@launch
            }
            watchRepo.limit.first().reached?.let {
                events.emit(NativeMapEvents.WatchLimitReached(WatchLimitReachedException(it)))
                return@launch
            }
            // The editor's state only changes on Main, where a map request dropping this entry cancels it first
            withContext(dispatcherProvider.Main) {
                ensureActive()
                // Opened meanwhile, the history keeps the map
                if (mapFrames.replay.value != null) return@withContext
                select(null)
                _isSidebarOpen.value = false
                // Unique across processes, counterpart: areaFitSession in NativeMapScreen
                placeAreaDraft(latitude, longitude, session = Random.nextLong())
            }
        }
    }

    private fun placeAreaDraft(latitude: Double, longitude: Double, session: Long) {
        val previous = _areaDraft.value
        val id = ++areaIds
        // A label the user wrote stays theirs, wherever the center goes
        val keepLabel = previous?.labelEdited == true
        _areaDraft.value = AreaDraft(
            session = session,
            id = id,
            latitude = latitude,
            longitude = longitude,
            pickedRadius = previous?.pickedRadius,
            label = if (keepLabel) previous.label else "",
            labelEdited = keepLabel,
            resolving = !keepLabel,
        )
        areaLabel?.cancel()
        if (keepLabel) return
        areaLabel = vmScope.launch {
            val label = locationWatchCreator.labelFor(latitude, longitude)
            // A lookup for an earlier center, or one the user already typed over, is stale
            _areaDraft.update { if (it?.id == id && !it.labelEdited) it.copy(label = label, resolving = false) else it }
        }
    }

    fun onAreaLabelChanged(label: String) = _areaDraft.update {
        if (it == null || it.saving) it else it.copy(label = label, labelEdited = true, resolving = false)
    }

    fun onAreaRadiusChanged(meters: Float) = _areaDraft.update {
        if (it == null || it.saving) it else it.copy(pickedRadius = meters)
    }

    fun cancelAreaDraft() {
        if (_areaDraft.value?.saving == true) return
        dropAreaDraft()
    }

    private fun dropAreaDraft() {
        // An editor still waiting to open would come up over whatever replaced it
        areaEntry?.cancel()
        areaLabel?.cancel()
        _areaDraft.value = null
    }

    /** [radiusInMeters] is what the editor shows, see [eu.darken.apl.watch.ui.RadiusStops.shown]. */
    fun createAreaWatch(radiusInMeters: Float) {
        val draft = _areaDraft.value ?: return
        if (draft.saving || draft.label.isBlank()) return
        log(tag) { "createAreaWatch($radiusInMeters) for $draft" }
        // Frozen while saving, the watch is created from exactly this
        _areaDraft.value = draft.copy(saving = true)
        launch {
            try {
                val watch = locationWatchCreator.create(draft.latitude, draft.longitude, radiusInMeters, draft.label, note = "")
                // Dropped meanwhile, another editor may be open with its own lookup running
                if (_areaDraft.value?.id == draft.id) dropAreaDraft()
                events.emit(NativeMapEvents.WatchAdded(watch.id, watch.label))
            } catch (e: LocationWatchNotAllowedException) {
                events.emit(NativeMapEvents.LocationWatchNotAllowed)
            } catch (e: WatchLimitReachedException) {
                events.emit(NativeMapEvents.WatchLimitReached(e))
            } finally {
                _areaDraft.update { if (it?.id == draft.id) it.copy(saving = false) else it }
            }
        }
    }

    fun goToAr() = navTo(DestinationAr)

    fun goUpgrade() = navTo(DestinationUpgrade)

    enum class HistoryAction { LOCKED, READY, LOADING }

    /** What the sheet says about the time shown in the history. */
    enum class HistoryNote { NOT_SEEN, NOT_RECORDED, OMITTED, ESTIMATED }

    data class HistoryView(
        val hex: AircraftHex,
        val timeline: HistoryTimeline,
        val day: HistoryTimeline.Day,
        val cursor: Long,
        val playing: Boolean,
        val speed: Int,
        val callsign: String?,
        val canGoBack: Boolean,
        val canGoForward: Boolean,
    )

    private data class HistoryDetails(val details: MapAircraftDetails, val note: HistoryNote?)

    data class Toggles(
        val labels: Boolean = true,
        val allTracks: Boolean = false,
        val militaryOnly: Boolean = false,
        val militaryHighlight: Boolean = true,
        val watchHighlight: Boolean = true,
        val follow: Boolean = false,
        val multiSelect: Boolean = false,
        val isolate: Boolean = false,
    )

    private data class Selection(
        val hex: AircraftHex?,
        val myLocation: Pair<Double, Double>?,
        val pins: List<AircraftHex>,
        val isolating: Boolean,
    )

    private data class FrameSummary(
        val selectedDetails: Aircraft?,
        val selectedPlane: MapAircraftDetails?,
        /** Only there to match watches, see [MapPlane.squawk]. */
        val selectedSquawk: String?,
        val selectedMissing: AircraftHex?,
        val shownCount: Int,
        val drawnCount: Int,
        val capped: Boolean,
        val totalMatching: Int?,
        val cutoffAltitudeFeet: Double?,
        val filterPending: Boolean,
        val selectedHex: AircraftHex?,
        val selectedFresh: Boolean,
        val selectedDrawn: Boolean,
        val selectedSighting: Sighting?,
    )


    /**
     * A location watch being set up on the map. [session] stays while it is open, [id] changes with
     * every new center.
     */
    data class AreaDraft(
        val session: Long,
        val id: Long,
        val latitude: Double,
        val longitude: Double,
        /** Null until the slider moves, see [eu.darken.apl.watch.ui.RadiusStops.shown]. */
        val pickedRadius: Float? = null,
        val label: String = "",
        val labelEdited: Boolean = false,
        val resolving: Boolean = false,
        val saving: Boolean = false,
    )

    /** Where the selection was last seen, while the map does not draw it. */
    data class LastSeenMark(
        val hex: AircraftHex,
        val label: String,
        val latitude: Double,
        val longitude: Double,
        val trackDegrees: Float?,
    )

    /** How current the selection's details are. */
    sealed interface Seen {
        data object Live : Seen

        /** [millis] is server time. */
        data class At(val millis: Long) : Seen

        /** Not on the map and no position known. */
        data object Unknown : Seen
    }

    data class State(
        val tagline: String,
        val isPro: Boolean,
        val style: NativeMapStyle,
        val toggles: Toggles,
        val selectedHex: AircraftHex?,
        val details: MapAircraftDetails?,
        val detailsLoading: Boolean,
        val shownCount: Int,
        val capped: Boolean,
        val totalMatching: Int?,
        val waiting: AircraftRepo.ViewingState.Reason?,
        val offline: Boolean,
        /** The aircraft drawn were fetched for the other military filter. */
        val filterPending: Boolean,
        val myLocation: Pair<Double, Double>?,
        /** The newest answer for the selection did not include it, no details are coming for now. */
        val selectedMissing: Boolean,
        val pinnedCount: Int,
        /** Isolation is on and has something to isolate. */
        val isolating: Boolean,
        /** Null while there is no history to offer for the selection. */
        val historyAction: HistoryAction? = null,
        /** Set while the history is shown, see [HistoryNote]. */
        val historyNote: HistoryNote? = null,
        /** Null in the history and while nothing is known yet. */
        val seen: Seen? = null,
        val lastSeenMark: LastSeenMark? = null,
        val capAltitude: CapAltitude? = null,
    ) {
        val inHistory: Boolean get() = details?.isHistorical == true
    }

    data class CapAltitude(
        val aboveFeet: Int,
        val shownCount: Int,
        val totalMatching: Int?,
    )

    companion object {
        private const val FOCUS_ZOOM = 9.0
        private const val MY_LOCATION_ZOOM = 9.0
        private val SIDEBAR_REFRESH = 1.seconds
        private const val QUICK_ADD_REPEAT_MS = 1_000L
        private val DEFAULT_CAMERA = MapOptions.Camera(lat = 30.0, lon = 0.0, zoom = 3.0)
        private val VIEWPORT_SAMPLE = 500.milliseconds
        private val CAMERA_SAVE_DEBOUNCE = 2.seconds
        private val HISTORY_TIMEOUT = 30.seconds
        private val HISTORY_DETAILS_PERIOD = 250.milliseconds
        private val FOLLOW_FRAME_WAIT = 1.seconds
        private val ROUTE_PROGRESS_TICK = 30.seconds

        /** The summary only changes with what the sheet shows, an aging position needs the ticks to expire. */
        internal fun routeDisplays(
            hex: AircraftHex,
            routes: Flow<FlightRoute?>,
            selected: Flow<Aircraft?>,
            now: () -> Instant,
        ): Flow<RouteDisplay> {
            val ticks = flow {
                while (true) {
                    emit(Unit)
                    delay(ROUTE_PROGRESS_TICK)
                }
            }
            // Selecting the aircraft again drops its details until the server answers, its last ones still place it
            val details = selected
                .map { it?.takeIf { ac -> ac.hex == hex } }
                .runningReduce { last, next -> next ?: last }
            return combine(routes, details, ticks) { route, ac, _ -> routeDisplay(hex, route, ac, now()) }
                .distinctUntilChanged()
        }

        internal fun routeDisplay(hex: AircraftHex, route: FlightRoute?, selected: Aircraft?, now: Instant): RouteDisplay {
            if (route == null) return RouteDisplay.Loading(hex)
            // The details can still be the previous selection's
            return RouteDisplay.Result(hex, route, route.progressOf(selected?.takeIf { it.hex == hex }, now))
        }

        /**
         * Rounds down to a whole 1,000 ft strictly below the cutoff, so "Above" holds for the
         * kept aircraft at exactly the cutoff: 24,125 gives 24,000, 24,000 gives 23,000.
         */
        internal fun capAltitude(
            capped: Boolean,
            cutoffFeet: Double?,
            shownCount: Int,
            totalMatching: Int?,
        ): CapAltitude? {
            if (!capped || cutoffFeet == null || !cutoffFeet.isFinite()) return null
            val aboveFeet = ((ceil(cutoffFeet / 1000) - 1) * 1000).toInt()
            if (aboveFeet < 1000) return null
            return CapAltitude(aboveFeet = aboveFeet, shownCount = shownCount, totalMatching = totalMatching)
        }

        /** Only for the selection the map draws live, a frame of another selection or a replay says nothing. */
        internal fun watchMatchesOf(
            watches: MapWatches,
            selectedHex: AircraftHex?,
            plane: MapAircraftDetails?,
            drawn: Boolean,
            squawk: String?,
        ): List<MapWatches.Match> = plane
            ?.takeIf { drawn && it.hex == selectedHex }
            ?.let { watches.matching(it.callsign, squawk) }
            .orEmpty()

        /**
         * A tar1090 zoom at which the spans fit a phone screen with some margin. At zoom z the world
         * is 256·2^z px wide, a phone shows roughly 400 of them; latitude counts double for mercator.
         */
        private fun zoomToShow(lonSpan: Double, latSpan: Double): Double {
            val span = maxOf(lonSpan, latSpan * 2).coerceAtLeast(0.01) * 1.3
            return log2(400 * 360 / (256 * span)).coerceIn(2.0, FOCUS_ZOOM)
        }
    }
}
