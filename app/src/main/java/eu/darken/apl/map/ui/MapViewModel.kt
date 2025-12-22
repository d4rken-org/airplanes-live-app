package eu.darken.apl.map.ui

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.apl.common.WebpageTool
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.Logging.Priority.INFO
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.flow.SingleEventFlow
import eu.darken.apl.common.permissions.Permission
import eu.darken.apl.common.uix.ViewModel3
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.main.core.aircraft.AircraftHex
import eu.darken.apl.main.core.findByHex
import eu.darken.apl.map.core.MapOptions
import eu.darken.apl.map.core.MapSettings
import eu.darken.apl.map.core.SavedCamera
import eu.darken.apl.search.core.SearchQuery
import eu.darken.apl.search.core.SearchRepo
import eu.darken.apl.watch.core.WatchRepo
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.Watch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    @param:ApplicationContext private val context: Context,
    private val mapSettings: MapSettings,
    private val webpageTool: WebpageTool,
    private val searchRepo: SearchRepo,
    private val watchRepo: WatchRepo,
    private val aircraftRepo: AircraftRepo,
) : ViewModel3(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Map", "ViewModel"),
) {

    private val args = MapFragmentArgs.fromSavedStateHandle(handle)
    private val initialOptions: MapOptions = run {
        // If navigation args specify a target, use those (e.g., focusing on a specific aircraft)
        args.mapOptions?.let { return@run it }

        // If last position is enabled and we have a saved camera, restore it
        val isEnabled = runBlocking { mapSettings.isRestoreLastViewEnabled.flow.first() }
        val savedCamera = runBlocking { mapSettings.lastCamera.flow.first() }

        if (isEnabled && savedCamera != null) {
            MapOptions(camera = savedCamera.toCamera())
        } else {
            MapOptions()
        }
    }
    private val currentOptions = MutableStateFlow(initialOptions)

    val events = SingleEventFlow<MapEvents>()
    private val refreshTrigger = MutableStateFlow(UUID.randomUUID())

    val state = combine(
        refreshTrigger,
        watchRepo.watches,
        currentOptions.onEach { log(tag, INFO) { "New MapOptions: $it" } },
    ) { _, alerts, options ->

        State(
            options = options,
            alerts = alerts,
        )
    }.asStateFlow()

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

        // Save camera if last position setting is enabled and we have camera data
        if (mapSettings.isRestoreLastViewEnabled.flow.first() && options.camera != null) {
            val savedCamera = SavedCamera.from(options.camera)
            mapSettings.lastCamera.update { savedCamera }
            log(tag) { "Saved last camera: $savedCamera" }
        }
    }

    fun showInSearch(hex: AircraftHex) {
        log(tag) { "showInSearch($hex)" }
        MapFragmentDirections.actionMapToSearch(
            targetHexes = arrayOf(hex)
        ).navigate()
    }

    fun addWatch(hex: AircraftHex) = launch {
        log(tag) { "addWatch($hex)" }
        aircraftRepo.findByHex(hex) ?: searchRepo.search(SearchQuery.Hex(hex)).aircraft.single()
        MapFragmentDirections.actionMapToCreateAircraftWatchFragment(
            hex = hex,
        ).navigate()
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

    fun reset() = launch {
        log(tag) { "reset()" }
        currentOptions.value = MapOptions()
    }

    data class State(
        val options: MapOptions,
        val alerts: Collection<Watch>,
    )
}