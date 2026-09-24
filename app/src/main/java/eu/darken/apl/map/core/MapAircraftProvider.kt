package eu.darken.apl.map.core

import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.aircraft.AircraftHex
import eu.darken.apl.main.core.query.MapSnapshot
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.api.TrailPoint
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * Turns map answers into frames to draw.
 *
 * Fetching and drawing are independent, like in AR: requests follow the tier's pace and the
 * viewport, frames follow a steady tick. Moving every aircraft on every tick is only worth it when
 * few are visible and the motion is more than a pixel, otherwise the traffic layer is rebuilt when
 * an answer arrives and once a second for fading, and only the selected aircraft moves per tick.
 */
class MapAircraftProvider @Inject constructor(
    private val aircraftRepo: AircraftRepo,
    private val serverClock: ServerClock,
    private val dispatcherProvider: DispatcherProvider,
) {

    data class Filter(
        val militaryOnly: Boolean = false,
        /** Only these aircraft are shown when set. */
        val isolated: Set<AircraftHex>? = null,
    )

    data class Frame(
        val traffic: List<MapPlane>,
        /** Changes only when [traffic] was rebuilt, so the layer can skip identical updates. */
        val trafficVersion: Long,
        val selectedHex: AircraftHex?,
        val selected: MapPlane?,
        val selectedDetails: Aircraft?,
        val selectedTrail: List<List<TrailPoint>>,
        val tracks: Map<AircraftHex, List<RecentTracks.Point>>?,
        val viewport: MapViewport?,
        val onScreen: List<MapPlane>,
        val shownCount: Int,
        val capped: Boolean,
        val totalMatching: Int?,
    )

    private data class Selection(val hex: AircraftHex?, val generation: Long)

    private val viewport = MutableStateFlow<MapViewport?>(null)
    private val selection = MutableStateFlow(Selection(null, 0))
    private val pinned = MutableStateFlow<List<AircraftHex>>(emptyList())
    private val filter = MutableStateFlow(Filter())
    private val tracksEnabled = MutableStateFlow(false)

    private val _state = MutableStateFlow<AircraftRepo.MapViewingState?>(null)
    val state: StateFlow<AircraftRepo.MapViewingState?> = _state

    // Answers arrive on the polling coroutine while frames are built on the ticker
    private val lock = Any()
    private val traffic = MapTraffic()
    private val trail = SelectedTrail()
    private val recentTracks = RecentTracks()
    private var latest: MapSnapshot? = null
    private var selectedDetails: Aircraft? = null
    private var tracksCopy: Map<AircraftHex, List<RecentTracks.Point>> = emptyMap()
    private var tracksCopyVersion = -1L

    fun onViewport(value: MapViewport) {
        viewport.value = value
    }

    /** Every call is a new selection, also for the aircraft that is already selected. */
    fun select(hex: AircraftHex?) {
        val next = Selection(hex?.uppercase(), selection.value.generation + 1)
        synchronized(lock) {
            trail.select(next.generation)
            selectedDetails = null
        }
        selection.value = next
    }

    /** The server takes at most [MAX_PINNED], the first ones in order win. */
    fun pin(hexes: List<AircraftHex>) {
        pinned.value = hexes.map { it.uppercase() }.distinct().take(MAX_PINNED)
    }

    fun setFilter(value: Filter) {
        filter.value = value
    }

    fun setTracksEnabled(enabled: Boolean) {
        tracksEnabled.value = enabled
        if (!enabled) synchronized(lock) { recentTracks.clear() }
    }

    val frames: Flow<Frame> = channelFlow {
        val poller = launch {
            aircraftRepo.mapViewing(queries(), trailCursor = { query -> synchronized(lock) { trail.cursor(query) } })
                .collect { state ->
                    if (state is AircraftRepo.MapViewingState.Snapshot) apply(state.value)
                    _state.value = state
                }
        }

        var seen: MapSnapshot? = null
        var built: Frame? = null
        var builtAt = Long.MIN_VALUE
        var builtFor: Any? = null
        var version = 0L

        while (currentCoroutineContext().isActive) {
            val now = serverClock.now().toEpochMilli()
            val elapsed = serverClock.elapsed()
            val currentViewport = viewport.value
            val currentFilter = filter.value
            val currentSelection = selection.value
            val withTracks = tracksEnabled.value

            val frame = synchronized(lock) {
                val snapshot = latest
                val inputs = listOf(currentFilter, currentSelection, withTracks)
                val previous = built
                val moveAll = previous != null &&
                        previous.onScreen.size <= MOVE_ALL_MAX_AIRCRAFT &&
                        (currentViewport?.zoom ?: 0.0) >= MOVE_ALL_MIN_ZOOM
                val rebuild = previous == null ||
                        snapshot !== seen ||
                        inputs != builtFor ||
                        currentViewport != previous.viewport ||
                        moveAll ||
                        elapsed - builtAt >= FADE_REBUILD_MS

                val selectedPlane = currentSelection.hex?.let { traffic.plane(it, now) }
                val trailSegments = trail.segments()

                if (!rebuild) {
                    previous!!.copy(selected = selectedPlane, selectedTrail = trailSegments)
                } else {
                    seen = snapshot
                    builtAt = elapsed
                    builtFor = inputs
                    val shown = traffic.planes(now).filter { it.matches(currentFilter) }
                    val onScreen = currentViewport
                        ?.let { vp -> shown.filter { vp.containsPoint(it.latitude, it.longitude) } }
                        ?: shown
                    Frame(
                        traffic = shown.filter { it.hex != currentSelection.hex },
                        trafficVersion = ++version,
                        selectedHex = currentSelection.hex,
                        selected = selectedPlane,
                        selectedDetails = selectedDetails,
                        selectedTrail = trailSegments,
                        tracks = if (withTracks) tracksSnapshot() else null,
                        viewport = currentViewport,
                        onScreen = onScreen,
                        shownCount = snapshot?.aircraft?.size ?: 0,
                        capped = snapshot?.capped == true,
                        totalMatching = snapshot?.totalMatching,
                    )
                }
            }
            built = frame
            send(frame)
            delay(TICK)
        }

        poller.cancel()
    }.flowOn(dispatcherProvider.Default)

    /** Copying up to 60k points is kept to when the tracks changed; call with [lock] held. */
    private fun tracksSnapshot(): Map<AircraftHex, List<RecentTracks.Point>> {
        if (recentTracks.version != tracksCopyVersion) {
            tracksCopy = recentTracks.snapshot()
            tracksCopyVersion = recentTracks.version
        }
        return tracksCopy
    }

    private fun apply(snapshot: MapSnapshot) = synchronized(lock) {
        latest = snapshot
        traffic.replace(snapshot.aircraft)
        if (tracksEnabled.value) recentTracks.record(traffic.aircraft)
        trail.apply(snapshot)
        if (snapshot.query.selectionGeneration == selection.value.generation && snapshot.selected != null) {
            selectedDetails = snapshot.selected
        }
        log(TAG, VERBOSE) { "Answer: ${snapshot.aircraft.size} aircraft, capped=${snapshot.capped}" }
    }

    /**
     * A new request only when the view left what was fetched, or zoomed in on a capped answer,
     * where a smaller area can bring aircraft the cap left out.
     */
    private fun queries(): Flow<AircraftRepo.ViewingQuery.Map> =
        combine(viewport.filterNotNull(), selection, pinned) { vp, sel, pins -> Triple(vp, sel, pins) }
            .scan<Triple<MapViewport, Selection, List<AircraftHex>>, Pair<AircraftRepo.ViewingQuery.Map, MapViewport>?>(null) { previous, (vp, sel, pins) ->
                val selected = sel.hex?.lowercase()
                val pinnedIds = pins.map { it.lowercase() }
                if (previous != null) {
                    val (query, fetched) = previous
                    val sameSelection = query.selected == selected &&
                            query.selectionGeneration == sel.generation &&
                            query.pinned == pinnedIds
                    val zoomedIntoCapped = synchronized(lock) { latest?.capped == true } &&
                            vp.zoom >= fetched.zoom + CAPPED_REFETCH_ZOOM
                    if (sameSelection && vp in fetched && !zoomedIntoCapped) return@scan previous
                }
                val area = vp.padded()
                AircraftRepo.ViewingQuery.Map(
                    south = area.south,
                    north = area.north,
                    west = area.west,
                    east = area.east,
                    selected = selected,
                    pinned = pinnedIds,
                    selectionGeneration = sel.generation,
                ) to area
            }
            .filterNotNull()
            .map { it.first }
            .distinctUntilChanged()

    private fun MapPlane.matches(filter: Filter): Boolean {
        if (filter.militaryOnly && !military) return false
        filter.isolated?.let { if (hex !in it) return false }
        return true
    }

    companion object {
        const val MAX_PINNED = 100
        private const val MOVE_ALL_MAX_AIRCRAFT = 1_500
        private const val MOVE_ALL_MIN_ZOOM = 7.0
        private const val CAPPED_REFETCH_ZOOM = 1.0
        private const val FADE_REBUILD_MS = 1_000L
        private val TICK = 100.milliseconds
        private val TAG = logTag("Map", "AircraftProvider")
    }
}
