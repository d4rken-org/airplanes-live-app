package eu.darken.apl.airport.ui

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.airport.core.Airport
import eu.darken.apl.airport.core.AirportRepo
import eu.darken.apl.airport.core.AirportTarget
import eu.darken.apl.airport.core.resolveAirportLookup
import eu.darken.apl.airport.core.toMapOptions
import eu.darken.apl.common.WebpageTool
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.map.ui.DestinationMap
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.session.SessionManager
import eu.darken.apl.upgrade.ui.DestinationUpgrade
import eu.darken.apl.watch.core.LocationWatchAvailability
import eu.darken.apl.watch.core.locationWatchAvailability
import eu.darken.apl.watch.ui.DestinationCreateLocationWatch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

    data class State(
        val display: AirportDetailsDisplay,
        val lookingUp: Boolean,
    )

    private var target: AirportTarget? = null
    @Volatile
    private var resolved: Airport? = null

    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state

    val watchAreaAvailability: StateFlow<LocationWatchAvailability> =
        combine(accessRepo.state, sessionManager.state, ::locationWatchAvailability)
            .stateIn(vmScope, SharingStarted.WhileSubscribed(), currentWatchArea())

    private fun currentWatchArea() = locationWatchAvailability(accessRepo.state.value, sessionManager.state.value)

    /** Only the first target counts, a partial one is looked up once. */
    fun init(target: AirportTarget) {
        if (this.target != null) return
        this.target = target
        log(tag) { "init($target)" }
        when (target) {
            is AirportTarget.Full -> _state.value = State(AirportDetailsDisplay.from(target), lookingUp = false)
            is AirportTarget.Partial -> {
                _state.value = State(AirportDetailsDisplay.from(target), lookingUp = true)
                launch { lookUp(target) }
            }
        }
    }

    /** Caught here, a failed lookup is no error dialog: the sheet keeps what the target knows. */
    private suspend fun lookUp(target: AirportTarget.Partial) {
        val match = try {
            resolveAirportLookup(target.ref, airportRepo.search(listOf(target.ref.lookupTerm)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(tag, WARN) { "lookUp(${target.ref.lookupTerm}) failed: ${e.asLog()}" }
            null
        }
        log(tag) { "lookUp(${target.ref.lookupTerm}) found ${match?.id}" }
        resolved = match
        _state.value = State(AirportDetailsDisplay.from(target, match), lookingUp = false)
    }

    fun showOnMap() {
        val target = target ?: return
        val options = resolved?.let { AirportTarget.Full(it).toMapOptions() } ?: target.toMapOptions() ?: return
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

    /** Decides on the policy and session at tap time, the rendered availability may already be stale. */
    fun watchArea() {
        when (val availability = currentWatchArea()) {
            LocationWatchAvailability.AVAILABLE -> {
                val display = _state.value?.display ?: return
                val latitude = display.latitude ?: return
                val longitude = display.longitude ?: return
                log(tag) { "watchArea(${display.title})" }
                navTo(DestinationCreateLocationWatch(latitude, longitude, label = display.title))
            }

            LocationWatchAvailability.TIER_EXCLUDED -> {
                log(tag) { "watchArea(): not part of this tier" }
                navTo(DestinationUpgrade)
            }

            else -> log(tag) { "watchArea(): unavailable ($availability)" }
        }
    }
}
