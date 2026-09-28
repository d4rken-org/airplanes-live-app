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
import kotlin.math.cos
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Turns map answers into frames to draw.
 *
 * Fetching and drawing are independent, like in AR: requests follow the tier's pace and the
 * viewport, frames follow a steady tick. Handing the map every aircraft again costs it long frames,
 * so the traffic is rebuilt only for the first answer to a new request, other filters, a view that
 * left the drawn area, or once aircraft have visibly moved, see [moveInterval]. Fading needs no
 * rebuild, it travels in [Frame.fades], and the selected aircraft moves on every tick.
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
        /** The aircraft around the view, not all of the answer. */
        val traffic: List<MapPlane>,
        /** Changes only when [traffic] was rebuilt, so the layer can skip identical updates. */
        val trafficVersion: Long,
        /** Opacity of the [traffic] aircraft that faded since, 0 for those past hiding. */
        val fades: Map<AircraftHex, Float>,
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
            aircraftRepo.mapViewing(
                queries(),
                trailCursor = { query -> synchronized(lock) { trail.cursor(query) } },
                // Far out a second of flight moves nothing visibly, unless one aircraft is watched closely
                pace = { if (selection.value.hex != null) Duration.ZERO else moveInterval(viewport.value) },
            )
                .collect { state ->
                    if (state is AircraftRepo.MapViewingState.Snapshot) apply(state.value)
                    _state.value = state
                }
        }

        var seen: MapSnapshot? = null
        var built: Frame? = null
        var builtFor: Any? = null
        var trafficBuiltAt = Long.MIN_VALUE
        var fadedAt = Long.MIN_VALUE
        var drawnArea: MapViewport? = null
        var drawnAll = false
        var shown: List<MapPlane> = emptyList()
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
                // With nothing of the answer left out, a view that moved on has nothing more to show
                val leftDrawnArea = currentViewport != null &&
                        drawnArea?.let { currentViewport !in it && !drawnAll } != false
                // A newer answer to the same question waits like movement does, the selected aircraft
                // is drawn from every answer anyway
                val rebuild = previous == null ||
                        (snapshot !== seen && snapshot?.query != seen?.query) ||
                        inputs != builtFor ||
                        leftDrawnArea ||
                        elapsed - trafficBuiltAt >= moveInterval(currentViewport).inWholeMilliseconds

                if (rebuild) {
                    seen = snapshot
                    builtFor = inputs
                    trafficBuiltAt = elapsed
                    drawnArea = currentViewport?.padded()
                    val area = drawnArea
                    val matching = traffic.planes(now).filter { it.matches(currentFilter) }
                    shown = matching.filter { area == null || area.containsPoint(it.latitude, it.longitude) }
                    drawnAll = shown.size == matching.size
                    version++
                }
                val trafficNow = if (rebuild) {
                    shown.filter { it.hex != currentSelection.hex }
                } else {
                    previous.traffic
                }
                val fades = if (rebuild || elapsed - fadedAt >= FADE_TICK_MS) {
                    fadedAt = elapsed
                    traffic.fades(trafficNow.map { it.hex }, now)
                } else {
                    previous.fades
                }
                val onScreen = if (rebuild || currentViewport != previous.viewport) {
                    currentViewport?.let { vp -> shown.filter { vp.containsPoint(it.latitude, it.longitude) } } ?: shown
                } else {
                    previous.onScreen
                }

                Frame(
                    traffic = trafficNow,
                    trafficVersion = version,
                    fades = fades,
                    selectedHex = currentSelection.hex,
                    selected = currentSelection.hex?.let { traffic.plane(it, now) },
                    selectedDetails = selectedDetails,
                    selectedTrail = trail.segments(),
                    tracks = if (withTracks) tracksSnapshot() else null,
                    viewport = currentViewport,
                    onScreen = onScreen,
                    shownCount = snapshot?.aircraft?.size ?: 0,
                    capped = snapshot?.capped == true,
                    totalMatching = snapshot?.totalMatching,
                )
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
     * A new request only when the view left what was fetched, zoomed in on a capped answer, where a
     * smaller area can bring aircraft the cap left out, or zoomed in far enough that most of the
     * fetched area is out of sight.
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
                    // Zoomed far in, the old area would keep bringing aircraft nobody sees
                    val tooWide = fetched.area > vp.padded().area * MAX_FETCHED_AREA_RATIO
                    if (sameSelection && vp in fetched && !zoomedIntoCapped && !tooWide) return@scan previous
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
        private const val CAPPED_REFETCH_ZOOM = 1.0
        private const val MAX_FETCHED_AREA_RATIO = 4.0
        private const val FADE_TICK_MS = 1_000L
        private val TICK = 100.milliseconds
        private val MAX_MOVE_INTERVAL = 5.seconds

        /** tar1090's zoom 0: one 256 px tile, here one dp, spans the equator. */
        private const val EQUATOR_METERS_PER_DP = 156_543.03
        private const val AIRLINER_METERS_PER_SECOND = 231.5

        /**
         * How long aircraft can keep their drawn position: until an airliner at 450 kt has moved
         * about one dp. At 50 degrees north that is 1.7 s at zoom 8 and 0.1 s at zoom 12.
         */
        internal fun moveInterval(viewport: MapViewport?): Duration {
            viewport ?: return MAX_MOVE_INTERVAL
            val latitude = Math.toRadians((viewport.south + viewport.north) / 2)
            val metersPerDp = EQUATOR_METERS_PER_DP * cos(latitude) / 2.0.pow(viewport.zoom)
            return (metersPerDp / AIRLINER_METERS_PER_SECOND).seconds.coerceIn(TICK, MAX_MOVE_INTERVAL)
        }
        private val TAG = logTag("Map", "AircraftProvider")
    }
}
