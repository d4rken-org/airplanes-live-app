package eu.darken.apl.airport.ui

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.airport.core.Airport
import eu.darken.apl.airport.core.AirportRepo
import eu.darken.apl.airport.core.AirportTarget
import eu.darken.apl.airport.core.resolveAirportLookup
import eu.darken.apl.airport.core.toMapOptions
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.map.ui.DestinationMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class AirportDetailsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val airportRepo: AirportRepo,
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
}
