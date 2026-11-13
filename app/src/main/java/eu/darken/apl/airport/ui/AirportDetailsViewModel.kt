package eu.darken.apl.airport.ui

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.airport.core.AirportRepo
import eu.darken.apl.airport.core.AirportTarget
import eu.darken.apl.airport.core.toMapOptions
import eu.darken.apl.common.WebpageTool
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.map.ui.DestinationMap
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.session.SessionManager
import eu.darken.apl.watch.core.LocationWatchAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AirportDetailsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val airportRepo: AirportRepo,
    private val webpageTool: WebpageTool,
    private val accessRepo: AccessRepo,
    private val sessionManager: SessionManager,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Airport", "Details", "ViewModel"),
) {

    private var target: AirportTarget? = null

    private val _state = MutableStateFlow<AirportDetailsState?>(null)
    val state: StateFlow<AirportDetailsState?> = _state

    val watchAreaAvailability: StateFlow<LocationWatchAvailability> = watchAreaAvailability(accessRepo, sessionManager)
        .stateIn(vmScope, SharingStarted.WhileSubscribed(), currentWatchAreaAvailability(accessRepo, sessionManager))

    /** Only the first target counts, a partial one is looked up once. */
    fun init(target: AirportTarget) {
        if (this.target != null) return
        this.target = target
        log(tag) { "init($target)" }
        _state.value = AirportDetailsState.of(target)
        if (target is AirportTarget.Partial) launch { _state.value = lookUpAirportDetails(target, airportRepo) }
    }

    fun showOnMap() {
        val options = _state.value?.best?.toMapOptions() ?: return
        log(tag) { "showOnMap(${options.airport?.code})" }
        navTo(DestinationMap(mapOptions = options))
    }

    fun openWebsite() {
        val website = _state.value?.display?.website ?: return
        log(tag) { "openWebsite($website)" }
        webpageTool.open(website)
    }

    fun openWikipedia() {
        val wikipedia = _state.value?.display?.wikipedia ?: return
        log(tag) { "openWikipedia($wikipedia)" }
        webpageTool.open(wikipedia)
    }

    fun watchArea() {
        watchAreaDestination(currentWatchAreaAvailability(accessRepo, sessionManager), _state.value?.display)?.let { navTo(it) }
    }
}
