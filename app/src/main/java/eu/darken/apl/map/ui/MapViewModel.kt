package eu.darken.apl.map.ui

import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.apl.common.ClipboardHelper
import eu.darken.apl.common.WebpageTool
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.Logging.Priority.INFO
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.flight.FlightRepo
import eu.darken.apl.common.flight.FlightRoute
import eu.darken.apl.common.flow.SingleEventFlow
import eu.darken.apl.common.permissions.Permission
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.main.core.aircraft.AircraftHex
import eu.darken.apl.main.core.findByHex
import eu.darken.apl.main.ui.settings.DestinationSettingsIndex
import eu.darken.apl.map.core.MapAircraftDetails
import eu.darken.apl.map.core.MapLayer
import eu.darken.apl.map.core.MapOptions
import eu.darken.apl.map.core.MapSettings
import eu.darken.apl.map.core.MapSidebarData
import eu.darken.apl.map.core.SavedCamera
import eu.darken.apl.search.core.SearchQuery
import eu.darken.apl.search.core.SearchRepo
import eu.darken.apl.search.ui.DestinationSearch
import eu.darken.apl.watch.core.WatchRepo
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.ui.DestinationCreateAircraftWatch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    @param:ApplicationContext private val context: Context,
    private val clipboardHelper: ClipboardHelper,
    private val mapSettings: MapSettings,
    private val webpageTool: WebpageTool,
    private val searchRepo: SearchRepo,
    private val watchRepo: WatchRepo,
    private val aircraftRepo: AircraftRepo,
    private val flightRepo: FlightRepo,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Map", "ViewModel"),
) {

    val useNativePanel: StateFlow<Boolean> = mapSettings.isNativeInfoPanelEnabled.flow
        .stateIn(vmScope, SharingStarted.Eagerly, true)

    val mapLayer: StateFlow<String> = mapSettings.mapLayer.flow
        .stateIn(vmScope, SharingStarted.Eagerly, MapLayer.OSM.key)

    private val _buttonStates = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val buttonStates: StateFlow<Map<String, Boolean>> = _buttonStates

    private val _sidebarDataRaw = MutableStateFlow<MapSidebarData?>(null)

    private val _sidebarSort = MutableStateFlow<MapSidebarData.SortField?>(MapSidebarData.SortField.CALLSIGN)
    val sidebarSort: StateFlow<MapSidebarData.SortField?> = _sidebarSort

    private val _sidebarSortAscending = MutableStateFlow(true)
    val sidebarSortAscending: StateFlow<Boolean> = _sidebarSortAscending

    val sidebarData: StateFlow<MapSidebarData?> = combine(
        _sidebarDataRaw,
        _sidebarSort,
        _sidebarSortAscending,
    ) { data, sort, ascending ->
        if (data == null || sort == null) return@combine data
        val sorted = data.aircraft.sortedWith(
            compareBy<MapSidebarData.SidebarAircraft> {
                when (sort) {
                    MapSidebarData.SortField.CALLSIGN -> it.callsign ?: it.hex
                    MapSidebarData.SortField.TYPE -> it.icaoType ?: ""
                    MapSidebarData.SortField.SQUAWK -> it.squawk ?: ""
                    MapSidebarData.SortField.ALTITUDE -> null
                    MapSidebarData.SortField.SPEED -> null
                }
            }.let { cmp ->
                when (sort) {
                    MapSidebarData.SortField.ALTITUDE -> compareBy<MapSidebarData.SidebarAircraft> { it.altitudeNumeric }
                    MapSidebarData.SortField.SPEED -> compareBy<MapSidebarData.SidebarAircraft> { it.speedNumeric }
                    else -> cmp
                }
            }.let { cmp -> if (ascending) cmp else cmp.reversed() }
        )
        data.copy(aircraft = sorted)
    }.stateIn(vmScope, SharingStarted.WhileSubscribed(5000), null)

    private val _isSidebarOpen = MutableStateFlow(false)
    val isSidebarOpen: StateFlow<Boolean> = _isSidebarOpen

    private val _aircraftDetails = MutableStateFlow<MapAircraftDetails?>(null)
    val aircraftDetails: StateFlow<MapAircraftDetails?> = _aircraftDetails

    fun onAircraftDetailsChanged(details: MapAircraftDetails) {
        _aircraftDetails.value = details
    }

    fun onAircraftDeselected() {
        _aircraftDetails.value = null
    }

    fun onButtonStatesChanged(jsonData: String) {
        try {
            val json = org.json.JSONObject(jsonData)
            val states = mutableMapOf<String, Boolean>()
            json.keys().forEach { key -> states[key] = json.getBoolean(key) }
            _buttonStates.value = states
        } catch (e: Exception) {
            log(tag) { "Failed to parse button states: $e" }
        }
    }

    fun clearButtonStates() {
        _buttonStates.value = emptyMap()
    }

    fun onAircraftListChanged(data: MapSidebarData) {
        _sidebarDataRaw.value = data
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

    fun selectAircraftOnMap(hex: String) {
        _isSidebarOpen.value = false
        events.emitBlocking(MapEvents.SelectAircraftOnMap(hex))
    }

    private var initialized = false
    private val currentOptions = MutableStateFlow(MapOptions())

    fun init(mapOptions: MapOptions?) {
        if (initialized) return
        initialized = true

        val options = mapOptions ?: MapOptions()
        currentOptions.value = options

        if (mapOptions == null) {
            launch {
                val isEnabled = mapSettings.isRestoreLastViewEnabled.flow.first()
                val savedCamera = mapSettings.lastCamera.flow.first()
                if (isEnabled && savedCamera != null) {
                    currentOptions.value = MapOptions(camera = savedCamera.toCamera())
                }
            }
        }
    }

    val events = SingleEventFlow<MapEvents>()

    val state = currentOptions
        .onEach { log(tag, INFO) { "New MapOptions: $it" } }
        .map { options -> State(options = options) }
        .asStateFlow()

    private val selectedHex = currentOptions
        .map { it.filter.selected.firstOrNull() }
        .distinctUntilChanged()

    sealed interface RouteDisplay {
        data class Loading(val hex: AircraftHex) : RouteDisplay
        data class Result(val hex: AircraftHex, val route: FlightRoute?) : RouteDisplay
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val routeDisplay: Flow<RouteDisplay?> = selectedHex
        .transformLatest { hex ->
            if (hex == null) {
                emit(null)
                return@transformLatest
            }
            val aircraft = aircraftRepo.findByHex(hex)
                ?: searchRepo.search(SearchQuery.Hex(hex)).aircraft.firstOrNull()
            flightRepo.prefetch(hex, aircraft?.callsign)
            emitAll(
                flightRepo.getByHex(hex).map { route ->
                    if (route == null) RouteDisplay.Loading(hex)
                    else RouteDisplay.Result(hex, route)
                }
            )
        }

    fun checkLocationPermission() {
        if (Permission.ACCESS_COARSE_LOCATION.isGranted(context)) {
            log(tag) { "checkLocationPermission(): Already granted" }
        } else {
            log(tag, INFO) { "checkLocationPermission(): Requesting location permission" }
            events.emitBlocking(MapEvents.RequestLocationPermission)
        }
    }

    fun homeMap() {
        log(tag) { "homeMap()" }
        events.emitBlocking(MapEvents.HomeMap)
    }

    fun onOpenUrl(url: String) {
        log(tag) { "onOpenUrl($url)" }
        webpageTool.open(url)
    }

    fun onOptionsUpdated(options: MapOptions) = launch {
        log(tag) { "onOptionsUpdated($options)" }
        currentOptions.value = options

        if (mapSettings.isRestoreLastViewEnabled.flow.first() && options.camera != null) {
            val savedCamera = SavedCamera.from(options.camera)
            mapSettings.lastCamera.update { savedCamera }
            log(tag) { "Saved last camera: $savedCamera" }
        }
    }

    fun showInSearch(hex: AircraftHex) {
        log(tag) { "showInSearch($hex)" }
        navTo(DestinationSearch(targetHexes = listOf(hex)))
    }

    fun addWatch(hex: AircraftHex) = launch {
        log(tag) { "addWatch($hex)" }
        aircraftRepo.findByHex(hex) ?: searchRepo.search(SearchQuery.Hex(hex)).aircraft.firstOrNull()
        navTo(DestinationCreateAircraftWatch(hex = hex))
        launch {
            val added = withTimeoutOrNull(20 * 1000) {
                watchRepo.status
                    .mapNotNull { watches ->
                        watches
                            .filterIsInstance<AircraftWatch.Status>()
                            .filter { it.hex == hex }
                            .filter { it.tracked.isNotEmpty() }
                            .firstOrNull()
                    }
                    .firstOrNull()
            }
            log(tag) { "addWatch(...): $added" }
            if (added != null) events.emit(MapEvents.WatchAdded(added))
        }
    }

    fun goToSettings() {
        navTo(DestinationSettingsIndex)
    }

    fun copyLink(hex: AircraftHex) {
        clipboardHelper.copyToClipboard("https://globe.airplanes.live/?icao=$hex")
    }

    fun reset() = launch {
        log(tag) { "reset()" }
        currentOptions.value = MapOptions()
        events.emit(MapEvents.ReloadMap)
    }

    data class State(
        val options: MapOptions,
    )
}
