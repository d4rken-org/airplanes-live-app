package eu.darken.apl.airport.ui.flights

import eu.darken.apl.airport.core.AirportFlightsRepo
import eu.darken.apl.airport.core.AirportTarget
import eu.darken.apl.airport.ui.AirportDetailsState
import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.server.ServerClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId

/**
 * The flights section of the airport sheet a view model shows in [details], with one [AirportFlightsController]
 * per airport id: a lookup confirming the id keeps it, another airport gets a new one. Call [sync] after every
 * change of [details], and [close] when the view model ends.
 */
class AirportSheetFlights(
    private val scope: CoroutineScope,
    private val details: StateFlow<AirportDetailsState?>,
    private val repo: AirportFlightsRepo,
    private val isPro: Flow<Boolean?>,
    private val serverClock: ServerClock,
    private val monotonicClock: MonotonicClock,
    private val deviceZone: () -> ZoneId,
) {

    private val lock = Any()
    private var controller: AirportFlightsController? = null
    private var controllerAirportId: String? = null
    private var visible = false
    private var closed = false

    /** A new instance for every change, so the same placeholder again is still a change. */
    private val source = MutableStateFlow<StateFlow<AirportFlightsUiState>?>(null)

    /** Null while no airport sheet is shown. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<AirportFlightsUiState?> = source
        .flatMapLatest { it ?: flowOf(null) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** Reads [details] itself, so calls racing from different threads still end on its newest value. */
    fun sync() {
        synchronized(lock) {
            if (closed) return
            val current = details.value
            val airport = current?.best
            val airportId = when (airport) {
                is AirportTarget.Full -> airport.airport.id
                is AirportTarget.Partial -> airport.ref.id
                null -> null
            }
            if (airportId != null && airportId == controllerAirportId) return

            drop()
            source.value = when {
                current == null -> null
                airportId == null -> MutableStateFlow(
                    if (current.lookingUp) AirportFlightsUiState.Resolving else AirportFlightsUiState.NoList
                )

                else -> {
                    val timeZone = when (airport) {
                        is AirportTarget.Full -> airport.airport.timeZone
                        is AirportTarget.Partial -> airport.ref.timeZone
                        null -> null
                    }
                    log(TAG) { "Flights for $airportId" }
                    val created = AirportFlightsController(
                        scope = scope,
                        airportId = airportId,
                        airportTimeZone = timeZone,
                        repo = repo,
                        isPro = isPro,
                        serverClock = serverClock,
                        monotonicClock = monotonicClock,
                        deviceZone = deviceZone,
                    )
                    controller = created
                    controllerAirportId = airportId
                    if (visible) created.setVisible(true)
                    created.state
                }
            }
        }
    }

    fun setVisible(visible: Boolean) {
        synchronized(lock) {
            if (closed || this.visible == visible) return
            this.visible = visible
            controller?.setVisible(visible)
        }
    }

    fun showFlights() {
        current()?.showFlights()
    }

    fun refresh() {
        current()?.refresh()
    }

    /** [direction] is the wire direction whose next page to load. */
    fun showMore(direction: String) {
        current()?.showMore(direction)
    }

    fun selectSection(kind: AirportFlightsDisplay.SectionKind) {
        current()?.selectSection(kind)
    }

    fun retry() {
        current()?.retry()
    }

    fun reload() {
        current()?.reload()
    }

    fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            drop()
            source.value = null
        }
    }

    private fun current(): AirportFlightsController? = synchronized(lock) { controller }

    private fun drop() {
        controller?.close()
        controller = null
        controllerAirportId = null
    }

    companion object {
        private val TAG = logTag("Airport", "Flights", "Sheet")
    }
}
