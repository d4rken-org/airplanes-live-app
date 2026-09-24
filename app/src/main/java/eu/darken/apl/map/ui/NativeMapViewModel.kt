package eu.darken.apl.map.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.apl.R
import eu.darken.apl.ar.ui.DestinationAr
import eu.darken.apl.common.ClipboardHelper
import eu.darken.apl.common.WebpageTool
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.datastore.value
import eu.darken.apl.common.datastore.valueBlocking
import eu.darken.apl.common.debug.logging.Logging.Priority.INFO
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.flight.FlightRepo
import eu.darken.apl.common.flight.FlightRoute
import eu.darken.apl.common.flow.SingleEventFlow
import eu.darken.apl.common.location.LocationManager2
import eu.darken.apl.common.permissions.Permission
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.aircraft.AircraftHex
import eu.darken.apl.main.core.aircraft.IcaoCountries
import eu.darken.apl.main.core.findByHex
import eu.darken.apl.main.ui.settings.DestinationSettingsIndex
import eu.darken.apl.map.core.MapAircraftDetails
import eu.darken.apl.map.core.MapAircraftProvider
import eu.darken.apl.map.core.MapOptions
import eu.darken.apl.map.core.MapSettings
import eu.darken.apl.map.core.MapSidebarData
import eu.darken.apl.map.core.MapViewport
import eu.darken.apl.map.core.NativeMapStyle
import eu.darken.apl.map.core.SavedCamera
import eu.darken.apl.search.ui.DestinationSearch
import eu.darken.apl.upgrade.UpgradeRepo
import eu.darken.apl.upgrade.ui.DestinationUpgrade
import eu.darken.apl.watch.core.WatchRepo
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.ui.DestinationCreateAircraftWatch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withTimeoutOrNull
import eu.darken.apl.server.api.ServerApiException
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import javax.inject.Inject
import kotlin.math.log2
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class NativeMapViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    @param:ApplicationContext private val context: Context,
    sensorManager: SensorManager,
    private val clipboardHelper: ClipboardHelper,
    private val mapSettings: MapSettings,
    private val webpageTool: WebpageTool,
    private val watchRepo: WatchRepo,
    private val aircraftRepo: AircraftRepo,
    private val flightRepo: FlightRepo,
    private val locationManager2: LocationManager2,
    private val provider: MapAircraftProvider,
    upgradeRepo: UpgradeRepo,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Map", "Native", "ViewModel"),
) {

    val hasRotationSensor: Boolean =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null ||
                sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) != null

    val events = SingleEventFlow<NativeMapEvents>()

    /**
     * Collected by the map view itself, frames arrive too often to go through composition.
     *
     * Everything built on frames stops as soon as nothing shows the map, so returning to it starts
     * a new viewing loop, see the one-screen rule in [AircraftRepo.viewing].
     */
    val frames: SharedFlow<MapAircraftProvider.Frame> = provider.frames
        .shareIn(vmScope, SharingStarted.WhileSubscribed(), replay = 1)

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
        mapSettings.isMilitaryOnlyEnabled.flow,
        follow,
        combine(multiSelect, isolate) { m, i -> m to i },
    ) { labels, allTracks, militaryOnly, follow, (multiSelect, isolate) ->
        Toggles(labels, allTracks, militaryOnly, follow, multiSelect, isolate)
    }

    // Everything the sheet and banners show changes rarely compared to the frames
    private val frameSummary = frames
        .map { frame ->
            FrameSummary(
                selectedDetails = frame.selectedDetails,
                shownCount = frame.shownCount,
                drawnCount = frame.traffic.size + if (frame.selected != null) 1 else 0,
                capped = frame.capped,
                totalMatching = frame.totalMatching,
            )
        }
        .distinctUntilChanged()

    val state: StateFlow<State?> = combine(
        combine(upgradeRepo.upgradeInfo, mapSettings.nativeMapStyle.flow) { upgrade, style ->
            (upgrade.isSettled && upgrade.isPro) to NativeMapStyle.fromKey(style)
        },
        toggles,
        frameSummary,
        provider.state,
        combine(selectedHex, myLocation, pinned) { hex, location, pins -> Triple(hex, location, pins) },
    ) { (isPro, style), toggles, summary, viewing, (hex, location, pins) ->
        // With a filter the server's counts describe aircraft that are not drawn
        val filtered = toggles.militaryOnly || toggles.isolate
        State(
            tagline = tagline,
            isPro = isPro,
            style = style,
            toggles = toggles,
            selectedHex = hex,
            details = summary.selectedDetails
                ?.takeIf { it.hex == hex }
                ?.let { MapAircraftDetails.from(it, IcaoCountries.countryName(it.hex)) },
            shownCount = if (filtered) summary.drawnCount else summary.shownCount,
            // Pinned aircraft have reserved places, isolating them leaves nothing for the cap to hide
            capped = summary.capped && !toggles.isolate,
            totalMatching = summary.totalMatching.takeIf { !filtered },
            waiting = (viewing as? AircraftRepo.MapViewingState.Waiting)?.reason,
            // A rate limit is retried within seconds, anything else leaves the positions aging
            offline = (viewing as? AircraftRepo.MapViewingState.Snapshot)?.error
                ?.let { !(it is ServerApiException && it.status == 429) } == true,
            myLocation = location,
            pinnedCount = pins.size,
        )
    }.stateIn(vmScope, SharingStarted.WhileSubscribed(), null)

    private val _sidebarSort = MutableStateFlow<MapSidebarData.SortField?>(MapSidebarData.SortField.CALLSIGN)
    val sidebarSort: StateFlow<MapSidebarData.SortField?> = _sidebarSort

    private val _sidebarSortAscending = MutableStateFlow(true)
    val sidebarSortAscending: StateFlow<Boolean> = _sidebarSortAscending

    private val _isSidebarOpen = MutableStateFlow(false)
    val isSidebarOpen: StateFlow<Boolean> = _isSidebarOpen

    @OptIn(FlowPreview::class)
    val sidebarData: StateFlow<MapSidebarData?> = combine(
        frames.sample(SIDEBAR_REFRESH_MS),
        toggles,
        _sidebarSort,
        _sidebarSortAscending,
    ) { frame, toggles, sort, ascending ->
        val all = frame.onScreen.map { plane ->
            MapSidebarData.SidebarAircraft(
                hex = plane.hex,
                callsign = plane.callsign,
                icaoType = plane.aircraftType,
                country = IcaoCountries.countryName(plane.hex),
                altitude = if (plane.onGround) "ground" else plane.altitudeFt?.toString(),
                speed = plane.groundSpeedKnots?.toString(),
            )
        }
        // With a filter the server's total counts aircraft the list leaves out
        val filtered = toggles.militaryOnly || toggles.isolate
        val sorted = MapSidebarData(
            totalAircraft = if (filtered) all.size else frame.totalMatching ?: frame.shownCount,
            onScreen = all.size,
            aircraft = all,
        ).sortedBy(sort, ascending)
        sorted.copy(aircraft = sorted.aircraft.take(SIDEBAR_MAX_ROWS))
    }.stateIn(vmScope, SharingStarted.WhileSubscribed(), null)

    sealed interface RouteDisplay {
        data class Loading(val hex: AircraftHex) : RouteDisplay
        data class Result(val hex: AircraftHex, val route: FlightRoute?) : RouteDisplay
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val routeDisplay: Flow<RouteDisplay?> = combine(selectedHex, frameSummary) { hex, summary ->
        hex to summary.selectedDetails?.takeIf { it.hex == hex }?.callsign?.trim()?.takeIf { it.isNotEmpty() }
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
                flightRepo.getByHex(hex).map { route ->
                    if (route == null) RouteDisplay.Loading(hex) else RouteDisplay.Result(hex, route)
                }
            )
        }

    private val tagline: String = context.resources.getStringArray(R.array.map_taglines).random()

    private var initialized = false

    fun init(mapOptions: MapOptions?) {
        if (initialized) return
        initialized = true

        launch {
            val filter = mapOptions?.filter ?: MapOptions.Filter()
            val focus = filter.selected.firstOrNull()?.uppercase()
            val requestedPins = filter.filtered.map { it.uppercase() }.distinct()
            val pins = requestedPins.take(MapAircraftProvider.MAX_PINNED)

            if (pins.isNotEmpty()) {
                pinned.value = pins
                provider.pin(pins)
                isolate.value = filter.noIsolation != true
                if (requestedPins.size > pins.size) {
                    events.emit(NativeMapEvents.PinnedTruncated(requestedPins.size, pins.size))
                }
            }
            if (focus != null) select(focus)
            applyFilter()

            val camera = mapOptions?.camera
                ?: focus?.let { cachedCamera(it) }
                ?: savedCamera().takeIf { mapOptions == null }
                ?: DEFAULT_CAMERA
            _initialCamera.value = camera

            // Search results carry positions in the cache, fitting them beats guessing a zoom
            if (mapOptions?.camera == null && pins.isNotEmpty()) fitToCached(pins)
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

    private suspend fun savedCamera(): MapOptions.Camera? {
        if (!mapSettings.isRestoreLastViewEnabled.value()) return null
        return mapSettings.lastCamera.value()?.toCamera()
    }

    private suspend fun cachedCamera(hex: AircraftHex): MapOptions.Camera? =
        aircraftRepo.findByHex(hex)?.location?.let { MapOptions.Camera(it.latitude, it.longitude, FOCUS_ZOOM) }

    private suspend fun fitToCached(hexes: List<AircraftHex>) {
        val cache = aircraftRepo.cache.first()
        val positions = hexes.mapNotNull { cache[it]?.location }
        if (positions.isEmpty()) return
        if (positions.size == 1) {
            events.emit(NativeMapEvents.MoveCamera(MapOptions.Camera(positions[0].latitude, positions[0].longitude, FOCUS_ZOOM)))
            return
        }
        val south = positions.minOf { it.latitude }
        val north = positions.maxOf { it.latitude }
        val west = positions.minOf { it.longitude }
        val east = positions.maxOf { it.longitude }
        if (east - west <= 180.0) {
            events.emit(NativeMapEvents.FitBounds(south = south, west = west, north = north, east = east))
            return
        }
        // Across the antimeridian the short way round is the one through ±180
        val shifted = positions.map { if (it.longitude < 0) it.longitude + 360.0 else it.longitude }
        val lonSpan = shifted.max() - shifted.min()
        val center = MapViewport.normalizeLongitude((shifted.max() + shifted.min()) / 2)
        events.emit(
            NativeMapEvents.MoveCamera(
                MapOptions.Camera((south + north) / 2, center, zoomToShow(lonSpan, north - south))
            )
        )
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
        if (multiSelect.value) {
            val current = pinned.value
            val next = if (hex in current) current - hex else (current + hex).take(MapAircraftProvider.MAX_PINNED)
            pinned.value = next
            provider.pin(next)
            return
        }
        select(hex)
    }

    fun onMapTapped() {
        if (!multiSelect.value && selectedHex.value != null) select(null)
    }

    fun onSidebarAircraftClicked(hex: AircraftHex) = launch {
        _isSidebarOpen.value = false
        select(hex)
        val plane = frames.replayCache.lastOrNull()?.onScreen?.firstOrNull { it.hex == hex } ?: return@launch
        events.emit(NativeMapEvents.MoveCamera(MapOptions.Camera(plane.latitude, plane.longitude, FOCUS_ZOOM), keepZoom = true))
    }

    fun deselect() = select(null)

    private fun select(hex: AircraftHex?) {
        val normalized = hex?.uppercase()
        selectedHex.value = normalized
        provider.select(normalized)
        if (normalized == null) follow.value = false
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

    fun toggleFollow() {
        if (selectedHex.value == null) return
        follow.value = !follow.value
    }

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

    fun toggleSort(field: MapSidebarData.SortField) {
        if (_sidebarSort.value == field) {
            if (_sidebarSortAscending.value) {
                _sidebarSortAscending.value = false
            } else {
                _sidebarSort.value = null
                _sidebarSortAscending.value = true
            }
        } else {
            _sidebarSort.value = field
            _sidebarSortAscending.value = true
        }
    }

    fun toggleSidebar() {
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

    fun copyLink(hex: AircraftHex) {
        clipboardHelper.copyToClipboard("https://globe.airplanes.live/?icao=${hex.lowercase()}")
    }

    fun showInSearch(hex: AircraftHex) = navTo(DestinationSearch(targetHexes = listOf(hex)))

    fun addWatch(hex: AircraftHex) = launch {
        navTo(DestinationCreateAircraftWatch(hex = hex))
        launch {
            val added = withTimeoutOrNull(20_000) {
                watchRepo.status
                    .mapNotNull { watches ->
                        watches
                            .filterIsInstance<AircraftWatch.Status>()
                            .filter { it.hex == hex }
                            .firstOrNull { it.tracked.isNotEmpty() }
                    }
                    .firstOrNull()
            }
            if (added != null) events.emit(NativeMapEvents.WatchAdded(added))
        }
    }

    fun goToAr() = navTo(DestinationAr)

    fun goToSettings() = navTo(DestinationSettingsIndex)

    fun goUpgrade() = navTo(DestinationUpgrade)

    data class Toggles(
        val labels: Boolean = true,
        val allTracks: Boolean = false,
        val militaryOnly: Boolean = false,
        val follow: Boolean = false,
        val multiSelect: Boolean = false,
        val isolate: Boolean = false,
    )

    private data class FrameSummary(
        val selectedDetails: Aircraft?,
        val shownCount: Int,
        val drawnCount: Int,
        val capped: Boolean,
        val totalMatching: Int?,
    )

    data class State(
        val tagline: String,
        val isPro: Boolean,
        val style: NativeMapStyle,
        val toggles: Toggles,
        val selectedHex: AircraftHex?,
        val details: MapAircraftDetails?,
        val shownCount: Int,
        val capped: Boolean,
        val totalMatching: Int?,
        val waiting: AircraftRepo.ViewingState.Reason?,
        val offline: Boolean,
        val myLocation: Pair<Double, Double>?,
        val pinnedCount: Int,
    )

    companion object {
        private const val FOCUS_ZOOM = 9.0
        private const val MY_LOCATION_ZOOM = 9.0
        private const val SIDEBAR_REFRESH_MS = 1_000L
        private const val SIDEBAR_MAX_ROWS = 200
        private val DEFAULT_CAMERA = MapOptions.Camera(lat = 30.0, lon = 0.0, zoom = 3.0)
        private val VIEWPORT_SAMPLE = 500.milliseconds
        private val CAMERA_SAVE_DEBOUNCE = 2.seconds

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
