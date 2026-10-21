package eu.darken.apl.map.ui

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.os.SystemClock
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.google.gson.JsonObject
import eu.darken.apl.R
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.map.core.AircraftShapes
import eu.darken.apl.map.core.HistoryTimeline
import eu.darken.apl.map.core.MapAircraftProvider
import eu.darken.apl.map.core.MapOptions
import eu.darken.apl.map.core.MapPlane
import eu.darken.apl.map.core.MapViewport
import eu.darken.apl.map.core.MapWatches
import eu.darken.apl.map.core.SelectedMotion
import eu.darken.apl.server.api.TrailPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val TAG = logTag("Map", "Native", "View")

/**
 * Commands for the map that come from outside it. Zoom levels are on tar1090's scale, MapLibre's
 * 512 px tiles put the same view one zoom level lower.
 */
@Stable
class NativeMapController {
    private var ready: MapLibreMap? by mutableStateOf(null)

    // A command sent while the map is still loading applies once it is ready, only the newest counts
    private var pending: ((MapLibreMap) -> Unit)? = null

    internal var map: MapLibreMap?
        get() = ready
        set(value) {
            ready = value
            if (value != null) pending?.invoke(value)
            pending = null
        }

    private fun run(command: (MapLibreMap) -> Unit) {
        val current = ready
        if (current != null) command(current) else pending = command
    }

    fun moveTo(camera: MapOptions.Camera, keepZoom: Boolean = false) = run { map ->
        val target = LatLng(camera.lat, camera.lon)
        val update = if (keepZoom) {
            CameraUpdateFactory.newLatLng(target)
        } else {
            CameraUpdateFactory.newLatLngZoom(target, camera.zoom.toMapLibreZoom())
        }
        map.animateCamera(update)
    }

    /** [padding] keeps the bounds clear of what covers the map's edges. */
    fun fitBounds(south: Double, west: Double, north: Double, east: Double, padding: Padding) = run { map ->
        // A single point has no extent to fit, a small area around it lands at about the focus zoom
        val margin = if (south == north && west == east) POINT_MARGIN_DEG else 0.0
        // An east past 180 is kept as given, it spans the antimeridian in one piece
        val bounds = LatLngBounds.from(
            (north + margin).coerceAtMost(90.0),
            east + margin,
            (south - margin).coerceAtLeast(-90.0),
            west - margin,
        )
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding.left, padding.top, padding.right, padding.bottom))
    }

    data class Padding(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        constructor(all: Int) : this(all, all, all, all)
    }

    companion object {
        private const val POINT_MARGIN_DEG = 0.05
    }
}

internal fun Double.toMapLibreZoom(): Double = this - 1.0
internal fun Double.toTar1090Zoom(): Double = this + 1.0

@Composable
internal fun NativeMapView(
    controller: NativeMapController,
    styleUrl: String,
    startCamera: MapOptions.Camera,
    frames: Flow<MapAircraftProvider.Frame>,
    shapes: AircraftShapes?,
    labels: Boolean,
    militaryHighlight: Boolean,
    /** Null draws no watch badges. */
    watches: MapWatches?,
    follow: Boolean,
    myLocation: Pair<Double, Double>?,
    lastSeen: NativeMapViewModel.LastSeenMark?,
    onCameraIdle: (MapViewport, MapOptions.Camera) -> Unit,
    onAircraftTapped: (String) -> Unit,
    onMapTapped: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        MapView(context).apply { onCreate(null) }
    }

    val currentOnCameraIdle by rememberUpdatedState(onCameraIdle)
    val currentOnAircraftTapped by rememberUpdatedState(onAircraftTapped)
    val currentOnMapTapped by rememberUpdatedState(onMapTapped)
    val currentFollow by rememberUpdatedState(follow)
    val currentMilitaryHighlight by rememberUpdatedState(militaryHighlight)
    val currentWatches by rememberUpdatedState(watches)
    // Outlives the setup effect, which is done once the listeners are in place
    val viewScope = rememberCoroutineScope()

    // Only a style that finished loading and carries our layers accepts updates
    var style by remember { mutableStateOf<Style?>(null) }

    val fadeStates = remember { FadeStates() }
    val motion = remember { SelectedMotion() }
    val moving = remember { MutableStateFlow(false) }
    val followedMark = remember { mutableStateOf<FollowedMark?>(null) }
    // A paused history sends no frames, turning Follow on has to move the camera by itself
    val followed = remember { Followed() }
    LaunchedEffect(follow, controller.map) {
        val plane = followed.plane ?: return@LaunchedEffect
        if (follow) controller.map?.moveCamera(CameraUpdateFactory.newLatLng(LatLng(plane.latitude, plane.longitude)))
    }

    val density = LocalDensity.current.density
    val silhouettes = remember(density, shapes) { HashMap<String, ImageBitmap>() }
    val militaryBadge = remember(context) { militaryBadge(context).asImageBitmap() }
    val watchBadge = remember(context) { watchBadge(context).asImageBitmap() }
    val shapeImages by produceState<Map<String, Bitmap>?>(null, shapes, density) {
        value = shapes?.let { withContext(Dispatchers.Default) { renderShapeImages(it, density) } }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        val memory = object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) {}

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = mapView.onLowMemory()

            override fun onTrimMemory(level: Int) {}
        }
        // Adding the observer replays the events up to the current state
        lifecycleOwner.lifecycle.addObserver(observer)
        context.registerComponentCallbacks(memory)
        onDispose {
            context.unregisterComponentCallbacks(memory)
            lifecycleOwner.lifecycle.removeObserver(observer)
            val current = lifecycleOwner.lifecycle.currentState
            if (current.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (current.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
            controller.map = null
        }
    }

    LaunchedEffect(mapView) {
        val map = suspendCancellableCoroutine { cont -> mapView.getMapAsync { cont.resume(it) } }
        map.uiSettings.apply {
            isRotateGesturesEnabled = false
            isTiltGesturesEnabled = false
            isCompassEnabled = false
            isLogoEnabled = false
            // MapLibre's default margins leave room for the logo hidden above
            val margin = (ATTRIBUTION_MARGIN_DP * density).roundToInt()
            setAttributionMargins(margin, margin, margin, margin)
        }
        map.setMaxZoomPreference(MAX_ZOOM)
        map.moveCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(startCamera.lat, startCamera.lon), startCamera.zoom.toMapLibreZoom())
        )
        var reported: MapViewport? = null
        var reportedAt: Long? = null
        var trailing: Job? = null
        fun report() {
            trailing?.cancel()
            reportedAt = SystemClock.elapsedRealtime()
            val (viewport, center) = map.currentView()
            reported = viewport
            currentOnCameraIdle(viewport, center)
        }
        // MapLibre does not send the idle event after every gesture, some pinches out end without one,
        // so a camera that stopped changing counts as idle too
        var settling: Job? = null
        mapView.addOnCameraDidChangeListener {
            settling?.cancel()
            settling = viewScope.launch {
                delay(CAMERA_SETTLE_DELAY)
                if (map.currentView().first == reported) return@launch
                log(TAG) { "Camera settled without an idle event" }
                report()
            }
        }
        map.addOnCameraIdleListener {
            settling?.cancel()
            // Every camera move ends idle, and following moves it on every display frame
            val wait = reportedAt?.let { it + REPORT_INTERVAL.inWholeMilliseconds - SystemClock.elapsedRealtime() } ?: 0L
            when {
                wait <= 0 -> report()
                trailing?.isActive != true -> trailing = viewScope.launch {
                    delay(wait)
                    report()
                }
            }
        }
        map.addOnMapClickListener { point ->
            val screen = map.projection.toScreenLocation(point)
            val slop = TAP_SLOP_DP * context.resources.displayMetrics.density
            val box = RectF(screen.x - slop, screen.y - slop, screen.x + slop, screen.y + slop)
            val mark = followedMark.value
            // The selected aircraft is drawn enlarged, so it only takes taps on its icon, ring or badge
            val selected = if (mark != null) {
                emptyList()
            } else {
                map.queryRenderedFeatures(
                    screen,
                    LAYER_SELECTED,
                    LAYER_SELECTED_RING,
                    LAYER_SELECTED_MILITARY,
                    LAYER_SELECTED_WATCH,
                    LAYER_LAST_SEEN,
                    LAYER_LAST_SEEN_RING,
                )
            }
            val traffic = map.queryRenderedFeatures(box, LAYER_TRAFFIC).filter { feature ->
                fadeStates.shown[feature.getStringProperty(PROP_HEX)] != 0f
            }
            // MapLibre's order is not the distance, a neighbour can come before the aircraft under the finger
            val candidates = (selected + traffic).mapNotNull { feature ->
                val hex = feature.getStringProperty(PROP_HEX) ?: return@mapNotNull null
                val position = (feature.geometry() as? Point) ?: return@mapNotNull null
                val at = map.projection.toScreenLocation(LatLng(position.latitude(), position.longitude()))
                hex to hypot(at.x - screen.x, at.y - screen.y)
            }.toMutableList()
            if (mark != null) {
                val distance = hypot(mark.center.x - screen.x, mark.center.y - screen.y)
                if (distance <= mark.reach || mark.badges.any { it.hit(mark.center, screen.x, screen.y) }) candidates.add(mark.hex to distance)
            }
            val hit = candidates.minByOrNull { it.second }?.first
            if (hit != null) currentOnAircraftTapped(hit) else currentOnMapTapped()
            true
        }
        // Queued commands like fitting bounds need a view that has a size
        mapView.doOnLayout { controller.map = map }
    }

    val map = controller.map
    var loadedStyleUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(map, styleUrl) {
        if (map == null) return@LaunchedEffect
        // Retrying a failed style load stops with the screen, like everything else here
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (loadedStyleUrl == styleUrl) return@repeatOnLifecycle
            style = null
            while (true) {
                val loaded = loadStyle(mapView, map, styleUrl)
                if (loaded != null) {
                    installLayers(context, loaded)
                    style = loaded
                    loadedStyleUrl = styleUrl
                    // The first idle may have passed before anything listened
                    val (viewport, center) = map.currentView()
                    currentOnCameraIdle(viewport, center)
                    break
                }
                log(TAG) { "Style failed to load, retrying" }
                delay(STYLE_RETRY_DELAY)
            }
        }
    }

    LaunchedEffect(style, labels) {
        style?.getLayer(LAYER_LABELS)?.setProperties(
            PropertyFactory.visibility(if (labels) Property.VISIBLE else Property.NONE)
        )
    }

    LaunchedEffect(style, militaryHighlight) {
        val filter = if (militaryHighlight) Expression.eq(Expression.get(PROP_MILITARY), true) else Expression.literal(false)
        listOf(LAYER_MILITARY, LAYER_SELECTED_MILITARY).forEach { style?.getLayerAs<SymbolLayer>(it)?.setFilter(filter) }
    }

    LaunchedEffect(style, watches) {
        val filter = watchFilter(watches)
        listOf(LAYER_WATCH, LAYER_SELECTED_WATCH).forEach { style?.getLayerAs<SymbolLayer>(it)?.setFilter(filter) }
    }

    LaunchedEffect(style, myLocation) {
        val current = style ?: return@LaunchedEffect
        val features = myLocation
            ?.let { (lat, lon) -> FeatureCollection.fromFeature(Feature.fromGeometry(Point.fromLngLat(lon, lat))) }
            ?: FeatureCollection.fromFeatures(emptyList())
        current.getSourceAs<GeoJsonSource>(SOURCE_ME)?.setGeoJson(features)
    }

    LaunchedEffect(style, lastSeen) {
        val current = style ?: return@LaunchedEffect
        current.getSourceAs<GeoJsonSource>(SOURCE_LAST_SEEN)?.setGeoJson(lastSeenFeatures(lastSeen))
    }

    LaunchedEffect(style, shapes, shapeImages) {
        val current = style ?: return@LaunchedEffect
        val target = map ?: return@LaunchedEffect
        val images = shapeImages
        val drawn = if (shapes != null && images != null) {
            current.addImages(HashMap(images), true)
            DrawnShapes(shapes, images.keys)
        } else {
            null
        }
        // A new style starts with fresh sources that carry no state, new shapes on the same one do not
        if (fadeStates.style !== current) {
            fadeStates.style = current
            fadeStates.shown.clear()
            fadeStates.departed = emptySet()
            fadeStates.retiring = emptySet()
        }
        // Not collecting while stopped is what lets the view model stop polling the server
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // What the display-frame loop last wrote, null once something else wrote the selection
            var movedPlane: MapPlane? = null
            var headPlane: MapPlane? = null
            var movedHead: TrailPoint? = null
            var liveHead: TrailPoint? = null
            var liveCleared = false
            var writtenAt = Long.MIN_VALUE / 2
            var marked = false
            var handoffEnd: Long? = null
            fun dropMark() {
                if (marked && current.isFullyLoaded) current.showSelectedLayers(true)
                marked = false
                handoffEnd = null
                followedMark.value = null
            }

            fun markOf(plane: MapPlane): FollowedMark {
                val size = zoomScale(target.cameraPosition.zoom, SELECTED_ICON_FACTOR)
                val icon = plane.icon(drawn)
                val silhouette = silhouettes.getOrPut(icon.image) {
                    (icon.shape?.let { renderShapeSilhouette(it, density) } ?: aircraftIcon(context)).asImageBitmap()
                }
                val center = target.projection.toScreenLocation(LatLng(plane.latitude, plane.longitude))
                val ringRadius = size * SELECTED_RING_RADIUS_PER_ICON_SIZE
                val badgeSize = zoomScale(target.cameraPosition.zoom, SELECTED_ICON_FACTOR, ::badgeSize)
                val badgeOffset = BADGE_OFFSET * size * density
                fun badge(image: ImageBitmap, side: Float) = FollowedMark.Badge(
                    image = image,
                    width = image.width * badgeSize,
                    height = image.height * badgeSize,
                    offset = Offset(side * badgeOffset, -badgeOffset),
                )
                return FollowedMark(
                    hex = plane.hex,
                    center = Offset(center.x, center.y),
                    ringRadius = ringRadius * density,
                    shadowRadius = (ringRadius + SELECTED_SHADOW_WIDTH) * density,
                    shadowBlur = zoomScale(target.cameraPosition.zoom, SELECTED_ICON_FACTOR, ::selectedShadowBlur),
                    silhouette = silhouette,
                    iconWidth = silhouette.width * size * icon.scale,
                    iconHeight = silhouette.height * size * icon.scale,
                    rotation = if (icon.rotates) (plane.trackDegrees ?: 0f) - target.cameraPosition.bearing.toFloat() else 0f,
                    color = altitudeColor(plane.drawnAltitude),
                    iconAlpha = plane.opacity,
                    label = plane.callsign ?: plane.hex,
                    labelTop = (ringRadius + SELECTED_LABEL_GAP) * density,
                    badges = listOfNotNull(
                        badge(militaryBadge, MILITARY_BADGE_SIDE).takeIf { plane.military && currentMilitaryHighlight },
                        badge(watchBadge, WATCH_BADGE_SIDE).takeIf { currentWatches?.matches(plane) == true },
                    ),
                )
            }
            // Kept while stopped, the positions from before are out of date by now
            motion.clear()
            moving.value = false
            // The layers outlive this collector, a mark it had is gone
            marked = true
            dropMark()
            launch {
                try {
                    while (true) {
                        // first() ends its collection by throwing, too costly to do every frame
                        if (!moving.value) moving.first { it }
                        val frameNanos = withFrameNanos { it }
                        // A style switch drops our sources, the next style's effect takes over
                        if (!current.isFullyLoaded) continue
                        // Frame times are on System.nanoTime, the aircraft are placed on elapsedRealtime
                        val now = (frameNanos + SystemClock.elapsedRealtimeNanos() - System.nanoTime()) / 1_000_000.0
                        val plane = motion.planeAt(now)
                        if (plane == null) {
                            // Its newest answer got too old to show while no frame came
                            if (movedPlane != null || headPlane != null) {
                                current.getSourceAs<GeoJsonSource>(SOURCE_SELECTED)?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
                                current.getSourceAs<GeoJsonSource>(SOURCE_TRAIL_HEAD)?.setGeoJson(trailHeadFeatures(null, null))
                                movedPlane = null
                                headPlane = null
                                followed.plane = null
                            }
                            dropMark()
                            continue
                        }
                        val following = currentFollow
                        if (following) {
                            // Also when the aircraft holds still, the map may have been dragged away from it
                            val at = target.cameraPosition.target
                            if (at == null || abs(at.latitude - plane.latitude) > CAMERA_EPSILON || abs(at.longitude - plane.longitude) > CAMERA_EPSILON) {
                                target.moveCamera(CameraUpdateFactory.newLatLng(LatLng(plane.latitude, plane.longitude)))
                            }
                            if (!marked) {
                                current.showSelectedLayers(false)
                                marked = true
                            }
                            handoffEnd = null
                        } else if (marked && handoffEnd == null) {
                            // The map draws its own marker some frames after it is written, the mark covers until then
                            handoffEnd = frameNanos + MARK_HANDOFF.inWholeNanoseconds
                            movedPlane = null
                            writtenAt = Long.MIN_VALUE / 2
                        }
                        handoffEnd?.let { if (frameNanos >= it) dropMark() }
                        if (marked) followedMark.value = markOf(plane)
                        // Every write redraws the map. Hidden under the mark or out of view, a few a second keep it current enough
                        val sparse = frameNanos - writtenAt < SPARSE_WRITE_INTERVAL.inWholeNanoseconds
                        val offscreen = !marked && sparse && run {
                            val at = target.projection.toScreenLocation(LatLng(plane.latitude, plane.longitude))
                            val margin = OFFSCREEN_MARGIN_DP * density
                            at.x <= -margin || at.y <= -margin || at.x >= mapView.width + margin || at.y >= mapView.height + margin
                        }
                        if (!offscreen && !(following && sparse)) {
                            if (plane != movedPlane) {
                                current.getSourceAs<GeoJsonSource>(SOURCE_SELECTED)?.setGeoJson(
                                    FeatureCollection.fromFeature(plane.toFeature(drawn))
                                )
                            }
                            movedPlane = plane
                            writtenAt = frameNanos
                        }
                        // The trail reaches the mark too
                        if (!offscreen && (plane != headPlane || liveHead != movedHead)) {
                            current.getSourceAs<GeoJsonSource>(SOURCE_TRAIL_HEAD)?.setGeoJson(trailHeadFeatures(liveHead, plane))
                            headPlane = plane
                            movedHead = liveHead
                        }
                        followed.plane = plane
                    }
                } finally {
                    // A style switch or a stop ends this loop, its mark must not stay behind
                    dropMark()
                }
            }
            var trafficVersion = -1L
            var drawnHexes: Set<String> = emptySet()
            var fades: Map<String, Float>? = null
            val fadeQueue = LinkedHashMap<String, Float>()
            var trail: List<List<TrailPoint>>? = null
            var tracks: List<MapAircraftProvider.Track>? = null
            var tracksWritten = false
            var selectedShown: MapPlane? = null
            var headShown: TrailPoint? = null
            var selectedSet = false
            // The sources outlive this collector, its first frame writes them whatever they hold
            var replayDay: HistoryTimeline.Day? = null
            var replayWritten = false
            var replayCursor: Long? = null
            var producer: Long? = null

            // Frames are complete, when drawing falls behind only the newest one matters
            frames.conflate().collect { frame ->
                // Another producer counts its versions from scratch and knows nothing of what the last one drew
                if (frame.producer != producer) {
                    producer = frame.producer
                    trafficVersion = -1L
                    fades = null
                    fadeQueue.clear()
                    trail = null
                    tracks = null
                    tracksWritten = false
                    selectedSet = false
                    replayWritten = false
                    motion.clear()
                    moving.value = false
                    dropMark()
                    if (current.isFullyLoaded) {
                        val trafficSource = current.getSourceAs<GeoJsonSource>(SOURCE_TRAFFIC)
                        fadeStates.shown.keys.forEach { trafficSource?.removeFeatureState(it) }
                    }
                    fadeStates.shown.clear()
                    fadeStates.departed = emptySet()
                    fadeStates.retiring = emptySet()
                }
                val trafficUpdate = if (frame.trafficVersion != trafficVersion) {
                    withContext(Dispatchers.Default) { trafficFeatures(frame.traffic, drawn) }
                } else {
                    null
                }
                val trailUpdate = if (frame.selectedTrail !== trail) {
                    withContext(Dispatchers.Default) { trailFeatures(frame.selectedTrail) }
                } else {
                    null
                }
                // Null is a value too: no tracks still has to clear what another producer drew
                val tracksUpdate = if (frame.tracks !== tracks || !tracksWritten) {
                    withContext(Dispatchers.Default) { trackFeatures(frame.tracks) }
                } else {
                    null
                }
                val replay = frame.replay
                val replayUpdate = if (replay?.day !== replayDay || !replayWritten) {
                    withContext(Dispatchers.Default) { replayFeatures(replay?.day) }
                } else {
                    null
                }

                // A style switch drops our sources, the next style's effect takes over
                if (!current.isFullyLoaded) return@collect

                val trafficSource = current.getSourceAs<GeoJsonSource>(SOURCE_TRAFFIC)
                trafficUpdate?.let {
                    trafficSource?.setGeoJson(it)
                    drawnHexes = frame.traffic.mapTo(HashSet()) { plane -> plane.hex }
                    // The old data keeps drawing until the new one is laid out, so states are only ever
                    // overwritten, and those of aircraft that left go one rebuild later
                    fadeStates.retiring = fadeStates.departed.filterTo(HashSet()) { hex -> hex !in drawnHexes }
                    fadeStates.departed = fadeStates.shown.keys.filterTo(HashSet()) { hex ->
                        hex !in drawnHexes && hex !in fadeStates.retiring
                    }
                    trafficVersion = frame.trafficVersion
                }
                if (frame.fades !== fades || trafficUpdate != null) {
                    fades = frame.fades
                    val wanted = HashMap<String, Float>()
                    frame.fades.forEach { (hex, opacity) -> if (fadeStates.shown[hex] != opacity) wanted[hex] = opacity }
                    fadeStates.shown.keys.forEach { hex ->
                        // Fresh again after a new answer, or gone from the traffic for good
                        if (hex !in frame.fades && (hex in drawnHexes || hex in fadeStates.retiring)) wanted[hex] = 1f
                    }
                    // Updates keep their place in line, so those still waiting are not overtaken again
                    fadeQueue.keys.retainAll(wanted.keys)
                    wanted.forEach { (hex, opacity) -> fadeQueue[hex] = opacity }
                }
                // Every state update is a call on this thread, hundreds at once stall it
                val pending = fadeQueue.entries.iterator()
                repeat(MAX_FADES_PER_FRAME) {
                    if (!pending.hasNext()) return@repeat
                    val (hex, opacity) = pending.next()
                    pending.remove()
                    if (opacity == 1f) {
                        trafficSource?.removeFeatureState(hex)
                        fadeStates.shown.remove(hex)
                    } else {
                        trafficSource?.setFeatureState(hex, JsonObject().apply { addProperty(PROP_OPACITY, opacity) })
                        fadeStates.shown[hex] = opacity
                    }
                }
                trailUpdate?.let {
                    current.getSourceAs<GeoJsonSource>(SOURCE_TRAIL)?.setGeoJson(it)
                    trail = frame.selectedTrail
                }
                tracksUpdate?.let {
                    current.getSourceAs<GeoJsonSource>(SOURCE_TRACKS)?.setGeoJson(it)
                    tracks = frame.tracks
                    tracksWritten = true
                }
                replayUpdate?.let { (edges, dots) ->
                    current.getSourceAs<GeoJsonSource>(SOURCE_HISTORY)?.setGeoJson(edges)
                    current.getSourceAs<GeoJsonSource>(SOURCE_HISTORY_DOTS)?.setGeoJson(dots)
                    replayDay = replay?.day
                    replayWritten = true
                    replayCursor = null
                    showCursor(current, replay)
                    replayCursor = replay?.cursor
                }
                if (replay?.cursor != replayCursor) {
                    showCursor(current, replay)
                    replayCursor = replay?.cursor
                }
                val sampledAt = frame.sampledAt
                val selected = frame.selected
                if (frame.replay == null && sampledAt != null) {
                    // So that a history frame redraws its aircraft over what the loop drew
                    selectedSet = false
                    liveHead = frame.selectedTrailHead
                    val aircraft = frame.selectedAircraft
                    val serverTime = frame.sampledServerTime
                    if (selected != null && aircraft != null && serverTime != null) {
                        motion.update(SystemClock.elapsedRealtime().toDouble(), selected.hex, aircraft, serverTime - sampledAt)
                        moving.value = true
                        liveCleared = false
                    } else if (!liveCleared) {
                        motion.clear()
                        moving.value = false
                        current.getSourceAs<GeoJsonSource>(SOURCE_SELECTED)?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
                        current.getSourceAs<GeoJsonSource>(SOURCE_TRAIL_HEAD)?.setGeoJson(trailHeadFeatures(null, null))
                        movedPlane = null
                        headPlane = null
                        followed.plane = null
                        dropMark()
                        liveCleared = true
                    }
                    return@collect
                }

                motion.clear()
                moving.value = false
                liveCleared = false
                dropMark()
                if (!selectedSet || selected != selectedShown || frame.selectedTrailHead != headShown) {
                    current.getSourceAs<GeoJsonSource>(SOURCE_SELECTED)?.setGeoJson(
                        FeatureCollection.fromFeatures(listOfNotNull(selected?.toFeature(drawn)))
                    )
                    current.getSourceAs<GeoJsonSource>(SOURCE_TRAIL_HEAD)?.setGeoJson(
                        trailHeadFeatures(frame.selectedTrailHead, selected)
                    )
                    selectedShown = selected
                    headShown = frame.selectedTrailHead
                    selectedSet = true
                    movedPlane = null
                    headPlane = null
                }

                followed.plane = selected
                if (currentFollow && selected != null) {
                    target.moveCamera(CameraUpdateFactory.newLatLng(LatLng(selected.latitude, selected.longitude)))
                }
            }
        }
    }

    Box(modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        FollowedMarkOverlay(followedMark, Modifier.fillMaxSize())
    }
}

/** Null when MapLibre reports the load failed, e.g. without a connection at start. */
private suspend fun loadStyle(mapView: MapView, map: MapLibreMap, url: String): Style? =
    suspendCancellableCoroutine { cont ->
        lateinit var failure: MapView.OnDidFailLoadingMapListener
        failure = MapView.OnDidFailLoadingMapListener {
            mapView.removeOnDidFailLoadingMapListener(failure)
            if (cont.isActive) cont.resume(null)
        }
        mapView.addOnDidFailLoadingMapListener(failure)
        cont.invokeOnCancellation { mapView.removeOnDidFailLoadingMapListener(failure) }
        map.setStyle(Style.Builder().fromUri(url)) { loaded ->
            mapView.removeOnDidFailLoadingMapListener(failure)
            if (cont.isActive) cont.resume(loaded)
        }
    }

/**
 * Longitudes along one line, continued past ±180 so that crossing the antimeridian stays a short
 * segment: 179.5, -179.5 becomes 179.5, 180.5.
 */
private fun unwrapLongitudes(longitudes: List<Double>): List<Double> {
    var previous = longitudes.firstOrNull() ?: return emptyList()
    return longitudes.map { longitude ->
        var value = longitude
        while (value - previous > 180.0) value -= 360.0
        while (value - previous < -180.0) value += 360.0
        previous = value
        value
    }
}

private fun lineOf(latitudes: List<Double>, longitudes: List<Double>): LineString =
    LineString.fromLngLats(unwrapLongitudes(longitudes).zip(latitudes) { lon, lat -> Point.fromLngLat(lon, lat) })

private fun MapLibreMap.currentView(): Pair<MapViewport, MapOptions.Camera> {
    val region = projection.visibleRegion
    val corners = listOf(region.farLeft, region.farRight, region.nearLeft, region.nearRight).filterNotNull()
    val zoom = cameraPosition.zoom.toTar1090Zoom()
    val viewport = MapViewport.fromVisibleRegion(
        south = corners.minOf { it.latitude },
        north = corners.maxOf { it.latitude },
        west = minOf(region.farLeft?.longitude ?: 0.0, region.nearLeft?.longitude ?: 0.0),
        east = maxOf(region.farRight?.longitude ?: 0.0, region.nearRight?.longitude ?: 0.0),
        zoom = zoom,
    )
    val target = cameraPosition.target ?: LatLng(0.0, 0.0)
    return viewport to MapOptions.Camera(target.latitude, MapViewport.normalizeLongitude(target.longitude), zoom)
}

private fun installLayers(context: Context, style: Style) {
    style.addImage(IMAGE_AIRCRAFT, aircraftIcon(context).toDistanceField(), true)
    style.addImage(IMAGE_MILITARY, militaryBadge(context))
    style.addImage(IMAGE_WATCH, watchBadge(context))

    style.addSource(GeoJsonSource(SOURCE_TRACKS))
    style.addSource(GeoJsonSource(SOURCE_HISTORY))
    style.addSource(GeoJsonSource(SOURCE_HISTORY_DOTS))
    style.addSource(GeoJsonSource(SOURCE_HISTORY_CURSOR))
    style.addSource(GeoJsonSource(SOURCE_TRAIL))
    style.addSource(GeoJsonSource(SOURCE_TRAIL_HEAD))
    style.addSource(GeoJsonSource(SOURCE_TRAFFIC))
    style.addSource(GeoJsonSource(SOURCE_SELECTED))
    style.addSource(GeoJsonSource(SOURCE_LAST_SEEN))
    style.addSource(GeoJsonSource(SOURCE_ME))

    style.addLayer(
        LineLayer(LAYER_TRACKS, SOURCE_TRACKS).withProperties(
            PropertyFactory.lineColor(altitudeColor()),
            PropertyFactory.lineWidth(1.5f),
            PropertyFactory.lineOpacity(Expression.product(Expression.literal(0.6f), Expression.get(PROP_OPACITY))),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
    )
    // A day of history: what is still to come dimmed, what was flown up to the cursor solid
    style.addLayer(
        LineLayer(LAYER_HISTORY_FUTURE, SOURCE_HISTORY)
            .withFilter(Expression.literal(false))
            .withProperties(
                PropertyFactory.lineColor(altitudeColor()),
                PropertyFactory.lineWidth(2f),
                PropertyFactory.lineOpacity(HISTORY_FUTURE_OPACITY),
                // Round caps of see-through edges overlap at every point and draw it darker
                PropertyFactory.lineCap(Property.LINE_CAP_BUTT),
            )
    )
    style.addLayer(
        LineLayer(LAYER_HISTORY_PAST, SOURCE_HISTORY)
            .withFilter(Expression.literal(false))
            .withProperties(
                PropertyFactory.lineColor(altitudeColor()),
                PropertyFactory.lineWidth(3f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            )
    )
    style.addLayer(
        LineLayer(LAYER_HISTORY_CURSOR, SOURCE_HISTORY_CURSOR).withProperties(
            PropertyFactory.lineColor(altitudeColor()),
            PropertyFactory.lineWidth(Expression.get(PROP_WIDTH)),
            PropertyFactory.lineOpacity(Expression.get(PROP_OPACITY)),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        )
    )
    style.addLayer(
        CircleLayer(LAYER_HISTORY_DOTS, SOURCE_HISTORY_DOTS)
            .withFilter(Expression.literal(false))
            .withProperties(
                PropertyFactory.circleColor(altitudeColor()),
                PropertyFactory.circleRadius(3.5f),
            )
    )
    style.addLayer(
        LineLayer(LAYER_TRAIL, SOURCE_TRAIL).withProperties(
            PropertyFactory.lineColor(altitudeColor()),
            PropertyFactory.lineWidth(3f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
    )
    style.addLayer(
        LineLayer(LAYER_TRAIL_HEAD, SOURCE_TRAIL_HEAD).withProperties(
            PropertyFactory.lineColor(altitudeColor()),
            PropertyFactory.lineWidth(3f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        )
    )
    // Fades out from the ring's edge, so it lifts the ring off light and dark maps alike
    style.addLayer(
        CircleLayer(LAYER_SELECTED_SHADOW, SOURCE_SELECTED).withProperties(
            PropertyFactory.circleRadius(
                zoomScaled(SELECTED_ICON_FACTOR) { Expression.literal(it * SELECTED_RING_RADIUS_PER_ICON_SIZE + SELECTED_SHADOW_WIDTH) }
            ),
            PropertyFactory.circleBlur(zoomScaled(SELECTED_ICON_FACTOR) { Expression.literal(selectedShadowBlur(it)) }),
            PropertyFactory.circleColor(Color.BLACK),
            PropertyFactory.circleOpacity(SELECTED_SHADOW_OPACITY),
        )
    )
    // Like the selected aircraft, dimmed and grey: the selection where it was last seen
    style.addLayer(
        CircleLayer(LAYER_LAST_SEEN_RING, SOURCE_LAST_SEEN).withProperties(
            PropertyFactory.circleRadius(
                zoomScaled(SELECTED_ICON_FACTOR) { Expression.literal(it * SELECTED_RING_RADIUS_PER_ICON_SIZE) }
            ),
            PropertyFactory.circleColor(Color.WHITE),
            PropertyFactory.circleOpacity(SELECTED_RING_OPACITY * LAST_SEEN_OPACITY),
        )
    )
    style.addLayer(
        SymbolLayer(LAYER_LAST_SEEN, SOURCE_LAST_SEEN).withProperties(
            PropertyFactory.iconImage(Expression.get(PROP_ICON)),
            PropertyFactory.iconRotate(Expression.coalesce(Expression.get(PROP_TRACK), Expression.literal(0))),
            PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconSize(aircraftIconSize(SELECTED_ICON_FACTOR)),
            PropertyFactory.iconColor(altitudeColor()),
            PropertyFactory.iconOpacity(LAST_SEEN_OPACITY),
        )
    )
    style.addLayer(
        SymbolLayer(LAYER_LAST_SEEN_LABEL, SOURCE_LAST_SEEN).withProperties(
            PropertyFactory.textField(Expression.get(PROP_LABEL)),
            PropertyFactory.textFont(arrayOf(LABEL_FONT)),
            PropertyFactory.textSize(SELECTED_LABEL_SIZE),
            PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
            PropertyFactory.textOffset(
                zoomScaled(SELECTED_ICON_FACTOR) {
                    val ems = (it * SELECTED_RING_RADIUS_PER_ICON_SIZE + SELECTED_LABEL_GAP) / SELECTED_LABEL_SIZE
                    Expression.literal(arrayOf(0f, ems))
                }
            ),
            PropertyFactory.textAllowOverlap(true),
            PropertyFactory.textIgnorePlacement(true),
            PropertyFactory.textColor(Color.BLACK),
            PropertyFactory.textHaloColor(Color.WHITE),
            PropertyFactory.textHaloWidth(SELECTED_LABEL_HALO),
            PropertyFactory.textOpacity(LAST_SEEN_OPACITY),
        )
    )
    style.addLayer(
        CircleLayer(LAYER_SELECTED_RING, SOURCE_SELECTED).withProperties(
            PropertyFactory.circleRadius(
                zoomScaled(SELECTED_ICON_FACTOR) { Expression.literal(it * SELECTED_RING_RADIUS_PER_ICON_SIZE) }
            ),
            PropertyFactory.circleColor(Color.WHITE),
            PropertyFactory.circleOpacity(SELECTED_RING_OPACITY),
        )
    )
    style.addLayer(
        SymbolLayer(LAYER_TRAFFIC, SOURCE_TRAFFIC).withProperties(
            PropertyFactory.iconImage(Expression.get(PROP_ICON)),
            PropertyFactory.iconRotate(Expression.coalesce(Expression.get(PROP_TRACK), Expression.literal(0))),
            PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconSize(aircraftIconSize(1f)),
            PropertyFactory.iconColor(altitudeColor()),
            PropertyFactory.iconOpacity(fadeOpacity()),
        )
    )
    style.addLayer(badgeLayer(LAYER_MILITARY, SOURCE_TRAFFIC, IMAGE_MILITARY, MILITARY_BADGE_SIDE, 1f, fadeOpacity()))
    style.addLayer(badgeLayer(LAYER_WATCH, SOURCE_TRAFFIC, IMAGE_WATCH, WATCH_BADGE_SIDE, 1f, fadeOpacity()))
    style.addLayer(
        SymbolLayer(LAYER_LABELS, SOURCE_TRAFFIC).apply { setMinZoom(LABELS_MIN_ZOOM) }.withProperties(
            PropertyFactory.textField(Expression.get(PROP_LABEL)),
            PropertyFactory.textFont(arrayOf(LABEL_FONT)),
            PropertyFactory.textSize(11f),
            PropertyFactory.textOffset(arrayOf(0f, 1.6f)),
            PropertyFactory.textAllowOverlap(false),
            PropertyFactory.textOptional(true),
            PropertyFactory.textColor(Color.BLACK),
            PropertyFactory.textHaloColor(Color.WHITE),
            PropertyFactory.textHaloWidth(1.5f),
            PropertyFactory.textOpacity(fadeOpacity()),
        )
    )
    style.addLayer(
        SymbolLayer(LAYER_SELECTED, SOURCE_SELECTED).withProperties(
            PropertyFactory.iconImage(Expression.get(PROP_ICON)),
            PropertyFactory.iconRotate(Expression.coalesce(Expression.get(PROP_TRACK), Expression.literal(0))),
            PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconSize(aircraftIconSize(SELECTED_ICON_FACTOR)),
            PropertyFactory.iconColor(altitudeColor()),
            PropertyFactory.iconOpacity(Expression.get(PROP_OPACITY)),
        )
    )
    style.addLayer(
        badgeLayer(LAYER_SELECTED_MILITARY, SOURCE_SELECTED, IMAGE_MILITARY, MILITARY_BADGE_SIDE, SELECTED_ICON_FACTOR, Expression.get(PROP_OPACITY))
    )
    style.addLayer(
        badgeLayer(LAYER_SELECTED_WATCH, SOURCE_SELECTED, IMAGE_WATCH, WATCH_BADGE_SIDE, SELECTED_ICON_FACTOR, Expression.get(PROP_OPACITY))
    )
    // Its own layer, so tapping the label does not count as tapping the aircraft
    style.addLayer(
        SymbolLayer(LAYER_SELECTED_LABEL, SOURCE_SELECTED).withProperties(
            PropertyFactory.textField(Expression.get(PROP_LABEL)),
            PropertyFactory.textFont(arrayOf(LABEL_FONT)),
            PropertyFactory.textSize(SELECTED_LABEL_SIZE),
            // Just below the ring, which shrinks with the zoom
            PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
            PropertyFactory.textOffset(
                zoomScaled(SELECTED_ICON_FACTOR) {
                    val ems = (it * SELECTED_RING_RADIUS_PER_ICON_SIZE + SELECTED_LABEL_GAP) / SELECTED_LABEL_SIZE
                    Expression.literal(arrayOf(0f, ems))
                }
            ),
            PropertyFactory.textAllowOverlap(true),
            PropertyFactory.textColor(Color.BLACK),
            PropertyFactory.textHaloColor(Color.WHITE),
            PropertyFactory.textHaloWidth(SELECTED_LABEL_HALO),
        )
    )
    style.addLayer(
        CircleLayer(LAYER_ME, SOURCE_ME).withProperties(
            PropertyFactory.circleRadius(7f),
            PropertyFactory.circleColor(Color.parseColor("#1E88E5")),
            PropertyFactory.circleStrokeColor(Color.WHITE),
            PropertyFactory.circleStrokeWidth(2.5f),
        )
    )
    log(TAG) { "Layers installed" }
}

private fun aircraftIcon(context: Context): Bitmap =
    AppCompatResources.getDrawable(context, R.drawable.ic_map_aircraft)!!.toBitmap()

private fun militaryBadge(context: Context): Bitmap =
    AppCompatResources.getDrawable(context, R.drawable.ic_map_military_badge)!!.toBitmap()

private fun watchBadge(context: Context): Bitmap =
    AppCompatResources.getDrawable(context, R.drawable.ic_map_watch_badge)!!.toBitmap()

/**
 * Upright above the aircraft whatever its heading, on the right for [side] 1 and the left for -1. The
 * settings hide it by filter, visibility is what hides the selected layers under the follow mark.
 */
private fun badgeLayer(id: String, source: String, image: String, side: Float, factor: Float, opacity: Expression): SymbolLayer =
    SymbolLayer(id, source)
        .withFilter(Expression.literal(false))
        .withProperties(
            PropertyFactory.iconImage(image),
            PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_VIEWPORT),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconSize(zoomScaled(factor) { Expression.literal(badgeSize(it)) }),
            PropertyFactory.iconTranslate(
                zoomScaled(factor) { Expression.literal(arrayOf(side * BADGE_OFFSET * it, -BADGE_OFFSET * it)) }
            ),
            PropertyFactory.iconTranslateAnchor(Property.ICON_TRANSLATE_ANCHOR_VIEWPORT),
            PropertyFactory.iconOpacity(opacity),
        )

private fun selectedShadowBlur(iconSize: Float): Float =
    SELECTED_SHADOW_WIDTH / (iconSize * SELECTED_RING_RADIUS_PER_ICON_SIZE + SELECTED_SHADOW_WIDTH)

/** Far out the aircraft get too small to carry a readable badge at their scale. */
private fun badgeSize(iconSize: Float): Float = maxOf(iconSize, BADGE_MIN_SIZE)

// counterpart: MapWatches.matches, for the followed mark
private fun watchFilter(watches: MapWatches?): Expression {
    if (watches == null || watches.isEmpty) return Expression.literal(false)
    fun member(value: Expression, values: Collection<String>) =
        values.takeIf { it.isNotEmpty() }?.let { Expression.`in`(value, Expression.literal(it.toTypedArray())) }
    // toFeature leaves out what the aircraft doesn't send
    val terms = listOfNotNull(
        member(Expression.coalesce(Expression.get(PROP_WATCH_HEX), Expression.literal("")), watches.hexes),
        member(Expression.coalesce(Expression.get(PROP_CALLSIGN), Expression.literal("")), watches.callsigns.keys),
        member(Expression.coalesce(Expression.get(PROP_SQUAWK), Expression.literal("")), watches.squawks.keys),
    )
    return Expression.any(*terms.toTypedArray())
}

/** Whole continents at once need small icons, or they merge into one surface. */
private fun aircraftIconSize(factor: Float): Expression =
    zoomScaled(factor) { Expression.product(Expression.literal(it), Expression.get(PROP_ICON_SCALE)) }

private fun zoomScaled(factor: Float, value: (Float) -> Expression): Expression = Expression.interpolate(
    Expression.linear(),
    Expression.zoom(),
    *ZOOM_SCALES.map { (zoom, scale) -> Expression.stop(zoom, value(scale * factor)) }.toTypedArray(),
)

/**
 * What [zoomScaled] gives at [zoom], for drawing outside the map. Like the map, it interpolates [value]
 * between the stops, which is not [value] of the interpolated scale.
 */
private fun zoomScale(zoom: Double, factor: Float, value: (Float) -> Float = { it }): Float {
    val (firstZoom, firstScale) = ZOOM_SCALES.first()
    if (zoom <= firstZoom) return value(firstScale * factor)
    ZOOM_SCALES.zipWithNext().forEach { (from, to) ->
        if (zoom <= to.first) {
            val fraction = ((zoom - from.first) / (to.first - from.first)).toFloat()
            val start = value(from.second * factor)
            return start + (value(to.second * factor) - start) * fraction
        }
    }
    return value(ZOOM_SCALES.last().second * factor)
}

private val ZOOM_SCALES = listOf(2.0 to 0.35f, 6.0 to 0.6f, 9.0 to 0.8f)

/** Fading reaches the traffic layer as feature state between rebuilds, see [MapAircraftProvider.Frame.fades]. */
private fun fadeOpacity(): Expression = Expression.coalesce(
    Expression.featureState(PROP_OPACITY),
    Expression.get(PROP_OPACITY),
)

/**
 * Grey on the ground or without an altitude, then from orange near the ground through green and
 * blue to magenta at cruise altitudes, close to tar1090's colours.
 */
private fun altitudeColor(): Expression = Expression.interpolate(
    Expression.linear(),
    Expression.coalesce(Expression.get(PROP_ALTITUDE), Expression.literal(GROUND_ALTITUDE)),
    *ALTITUDE_COLORS.map { (altitude, color) -> Expression.stop(altitude, Expression.color(color)) }.toTypedArray(),
)

/** What [altitudeColor] gives for [altitudeFt], for drawing outside the map. */
private fun altitudeColor(altitudeFt: Int): Int {
    val (firstAltitude, firstColor) = ALTITUDE_COLORS.first()
    if (altitudeFt <= firstAltitude) return firstColor
    ALTITUDE_COLORS.zipWithNext().forEach { (from, to) ->
        if (altitudeFt <= to.first) {
            val fraction = (altitudeFt - from.first).toFloat() / (to.first - from.first)
            fun channel(shift: Int): Int {
                val a = (from.second shr shift) and 0xFF
                val b = (to.second shr shift) and 0xFF
                return (a + (b - a) * fraction).roundToInt() shl shift
            }
            return channel(24) or channel(16) or channel(8) or channel(0)
        }
    }
    return ALTITUDE_COLORS.last().second
}

private val ALTITUDE_COLORS = listOf(
    GROUND_ALTITUDE to Color.parseColor("#9E9E9E"),
    0 to Color.parseColor("#FF6D00"),
    5_000 to Color.parseColor("#FFC400"),
    10_000 to Color.parseColor("#64DD17"),
    20_000 to Color.parseColor("#00B8D4"),
    30_000 to Color.parseColor("#2962FF"),
    40_000 to Color.parseColor("#D500F9"),
)

/** The fade state set on each aircraft of a style's traffic layer, 0 once hidden. */
private class FadeStates {
    var style: Style? = null
    val shown = mutableMapOf<String, Float>()
    var departed: Set<String> = emptySet()
    var retiring: Set<String> = emptySet()
}

/** The selected aircraft as last drawn. */
private class Followed {
    var plane: MapPlane? = null
}

/** The silhouettes the current style has images for. */
private class DrawnShapes(val shapes: AircraftShapes, val images: Set<String>)

/** [shape] is null for the generic aircraft image. */
private class AircraftIcon(val image: String, val shape: AircraftShapes.Shape?, val scale: Float, val rotates: Boolean)

private fun MapPlane.icon(drawn: DrawnShapes?): AircraftIcon {
    val ref = drawn?.shapes?.refFor(aircraftType, category)?.takeIf { shapeImageName(it.shape) in drawn.images }
    val shape = ref?.let { drawn.shapes.shapes[it.shape] }
    if (ref == null || shape == null) return AircraftIcon(IMAGE_AIRCRAFT, null, 1f, true)
    return AircraftIcon(shapeImageName(ref.shape), shape, ref.scale, shape.rotates)
}

private val MapPlane.drawnAltitude: Int get() = if (onGround) GROUND_ALTITUDE else altitudeFt ?: GROUND_ALTITUDE

private fun MapPlane.toFeature(drawn: DrawnShapes?): Feature = Feature.fromGeometry(Point.fromLngLat(longitude, latitude), null, hex).apply {
    val icon = icon(drawn)
    addStringProperty(PROP_HEX, hex)
    addStringProperty(PROP_ICON, icon.image)
    addNumberProperty(PROP_ICON_SCALE, icon.scale)
    // Balloons and ground markers look the same from every direction
    if (icon.rotates) trackDegrees?.let { addNumberProperty(PROP_TRACK, it) }
    addNumberProperty(PROP_ALTITUDE, drawnAltitude)
    addNumberProperty(PROP_OPACITY, opacity)
    addBooleanProperty(PROP_MILITARY, military)
    // Matching has its own hex, the one in PROP_HEX is what a tap reports
    MapWatches.normalize(hex)?.let { addStringProperty(PROP_WATCH_HEX, it) }
    MapWatches.normalize(callsign)?.let { addStringProperty(PROP_CALLSIGN, it) }
    MapWatches.normalize(squawk)?.let { addStringProperty(PROP_SQUAWK, it) }
    addStringProperty(PROP_LABEL, callsign ?: hex)
}

/** The generic icon without an altitude, so grey: how it looked then is no longer current. */
private fun lastSeenFeatures(mark: NativeMapViewModel.LastSeenMark?): FeatureCollection {
    mark ?: return FeatureCollection.fromFeatures(emptyList())
    val feature = Feature.fromGeometry(Point.fromLngLat(mark.longitude, mark.latitude), null, mark.hex).apply {
        addStringProperty(PROP_HEX, mark.hex)
        addStringProperty(PROP_ICON, IMAGE_AIRCRAFT)
        addNumberProperty(PROP_ICON_SCALE, 1f)
        mark.trackDegrees?.let { addNumberProperty(PROP_TRACK, it) }
        addStringProperty(PROP_LABEL, mark.label)
    }
    return FeatureCollection.fromFeature(feature)
}

private fun trafficFeatures(planes: List<MapPlane>, drawn: DrawnShapes?): FeatureCollection =
    FeatureCollection.fromFeatures(planes.map { it.toFeature(drawn) })

/** One feature per segment, a line can only have one colour per feature. */
private fun trailFeatures(segments: List<List<TrailPoint>>): FeatureCollection {
    val features = mutableListOf<Feature>()
    segments.forEach { segment ->
        segment.zipWithNext { from, to ->
            features.add(
                Feature.fromGeometry(
                    lineOf(listOf(from.latitude, to.latitude), listOf(from.longitude, to.longitude))
                ).apply { to.altitudeFeet?.let { addNumberProperty(PROP_ALTITUDE, it) } }
            )
        }
    }
    return FeatureCollection.fromFeatures(features)
}

/**
 * Every edge and sighting of [day], timed relative to the day's start so the layers can split them
 * at the cursor by filter alone, see [showCursor]. Empty without a day.
 */
private fun replayFeatures(day: HistoryTimeline.Day?): Pair<FeatureCollection, FeatureCollection> {
    if (day == null) return FeatureCollection.fromFeatures(emptyList()) to FeatureCollection.fromFeatures(emptyList())
    val edges = day.edges.map { edge ->
        val from = edge.startPoint
        val to = edge.endPoint
        Feature.fromGeometry(lineOf(listOf(from.latitude, to.latitude), listOf(from.longitude, to.longitude))).apply {
            edge.bandFeet?.let { addNumberProperty(PROP_ALTITUDE, it) }
            addNumberProperty(PROP_START, (edge.start - day.start).toDouble())
            addNumberProperty(PROP_END, (edge.end - day.start).toDouble())
        }
    }
    val dots = day.sightings.map { point ->
        Feature.fromGeometry(Point.fromLngLat(point.longitude, point.latitude)).apply {
            point.altitudeFeet?.let { addNumberProperty(PROP_ALTITUDE, it) }
            addNumberProperty(PROP_START, (point.observedAt - day.start).toDouble())
        }
    }
    return FeatureCollection.fromFeatures(edges) to FeatureCollection.fromFeatures(dots)
}

/**
 * Edges that ended by the cursor are solid, those that start after it dimmed, and the one it is on
 * is drawn in two pieces from its own source. Without [replay] the history layers show nothing.
 */
private fun showCursor(style: Style, replay: MapAircraftProvider.Replay?) {
    val past = style.getLayerAs<LineLayer>(LAYER_HISTORY_PAST)
    val future = style.getLayerAs<LineLayer>(LAYER_HISTORY_FUTURE)
    val dots = style.getLayerAs<CircleLayer>(LAYER_HISTORY_DOTS)
    val cursorSource = style.getSourceAs<GeoJsonSource>(SOURCE_HISTORY_CURSOR)
    if (replay == null) {
        past?.setFilter(Expression.literal(false))
        future?.setFilter(Expression.literal(false))
        dots?.setFilter(Expression.literal(false))
        cursorSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        return
    }
    val at = (replay.cursor - replay.day.start).toDouble()
    past?.setFilter(Expression.lte(Expression.get(PROP_END), Expression.literal(at)))
    future?.setFilter(Expression.gte(Expression.get(PROP_START), Expression.literal(at)))
    dots?.setFilter(Expression.literal(true))
    dots?.setProperties(
        PropertyFactory.circleOpacity(
            Expression.switchCase(
                Expression.lte(Expression.get(PROP_START), Expression.literal(at)),
                Expression.literal(1f),
                Expression.literal(HISTORY_FUTURE_OPACITY),
            )
        )
    )
    val edge = replay.day.edges.firstOrNull { replay.cursor > it.start && replay.cursor < it.end }
    val pieces = if (edge == null) {
        emptyList()
    } else {
        val split = edge.pointAt(replay.cursor)
        listOf(
            Triple(edge.startPoint, split, true),
            Triple(split, edge.endPoint, false),
        ).map { (from, to, done) ->
            Feature.fromGeometry(lineOf(listOf(from.latitude, to.latitude), listOf(from.longitude, to.longitude))).apply {
                edge.bandFeet?.let { addNumberProperty(PROP_ALTITUDE, it) }
                addNumberProperty(PROP_WIDTH, if (done) 3f else 2f)
                addNumberProperty(PROP_OPACITY, if (done) 1f else HISTORY_FUTURE_OPACITY)
            }
        }
    }
    cursorSource?.setGeoJson(FeatureCollection.fromFeatures(pieces))
}

private fun Style.showSelectedLayers(visible: Boolean) {
    val visibility = PropertyFactory.visibility(if (visible) Property.VISIBLE else Property.NONE)
    listOf(LAYER_SELECTED, LAYER_SELECTED_SHADOW, LAYER_SELECTED_RING, LAYER_SELECTED_MILITARY, LAYER_SELECTED_WATCH, LAYER_SELECTED_LABEL)
        .forEach { getLayer(it)?.setProperties(visibility) }
}

/** Joins the trail to where the aircraft is drawn now, which moves on every frame unlike the trail. */
private fun trailHeadFeatures(head: TrailPoint?, selected: MapPlane?): FeatureCollection {
    if (head == null || selected == null) return FeatureCollection.fromFeatures(emptyList())
    val line = Feature.fromGeometry(
        lineOf(listOf(head.latitude, selected.latitude), listOf(head.longitude, selected.longitude))
    ).apply { selected.altitudeFt?.let { addNumberProperty(PROP_ALTITUDE, it) } }
    return FeatureCollection.fromFeature(line)
}

private fun trackFeatures(tracks: List<MapAircraftProvider.Track>?): FeatureCollection {
    if (tracks == null) return FeatureCollection.fromFeatures(emptyList())
    val features = tracks.map { track ->
        val plane = track.plane
        Feature.fromGeometry(lineOf(track.latitudes, track.longitudes)).apply {
            addNumberProperty(PROP_ALTITUDE, if (plane.onGround) GROUND_ALTITUDE else plane.altitudeFt ?: GROUND_ALTITUDE)
            addNumberProperty(PROP_OPACITY, plane.opacity)
        }
    }
    return FeatureCollection.fromFeatures(features)
}

private const val IMAGE_AIRCRAFT = "apl-aircraft"
private const val IMAGE_MILITARY = "apl-military"
private const val IMAGE_WATCH = "apl-watch"
private const val SOURCE_TRAFFIC = "apl-traffic"
private const val SOURCE_SELECTED = "apl-selected"
private const val SOURCE_TRAIL = "apl-trail"
private const val SOURCE_TRAIL_HEAD = "apl-trail-head"
private const val SOURCE_HISTORY = "apl-history"
private const val SOURCE_HISTORY_DOTS = "apl-history-dots"
private const val SOURCE_HISTORY_CURSOR = "apl-history-cursor"
private const val SOURCE_TRACKS = "apl-tracks"
private const val SOURCE_ME = "apl-me"
private const val SOURCE_LAST_SEEN = "apl-last-seen"
private const val LAYER_TRAFFIC = "apl-traffic-layer"
private const val LAYER_MILITARY = "apl-military-layer"
private const val LAYER_SELECTED_MILITARY = "apl-selected-military-layer"
private const val LAYER_WATCH = "apl-watch-layer"
private const val LAYER_SELECTED_WATCH = "apl-selected-watch-layer"
private const val LAYER_SELECTED_SHADOW = "apl-selected-shadow-layer"
private const val LAYER_SELECTED_RING = "apl-selected-ring-layer"
private const val LAYER_SELECTED_LABEL = "apl-selected-label-layer"
private const val LAYER_LABELS = "apl-labels-layer"
private const val LAYER_SELECTED = "apl-selected-layer"
private const val LAYER_TRAIL = "apl-trail-layer"
private const val LAYER_TRAIL_HEAD = "apl-trail-head-layer"
private const val LAYER_HISTORY_PAST = "apl-history-past-layer"
private const val LAYER_HISTORY_FUTURE = "apl-history-future-layer"
private const val LAYER_HISTORY_CURSOR = "apl-history-cursor-layer"
private const val LAYER_HISTORY_DOTS = "apl-history-dots-layer"
private const val LAYER_TRACKS = "apl-tracks-layer"
private const val LAYER_ME = "apl-me-layer"
private const val LAYER_LAST_SEEN = "apl-last-seen-layer"
private const val LAYER_LAST_SEEN_RING = "apl-last-seen-ring-layer"
private const val LAYER_LAST_SEEN_LABEL = "apl-last-seen-label-layer"
private const val PROP_HEX = "hex"
private const val PROP_TRACK = "track"
private const val PROP_ICON = "icon"
private const val PROP_ICON_SCALE = "icon-scale"
private const val PROP_ALTITUDE = "alt"
private const val PROP_OPACITY = "opacity"
private const val PROP_MILITARY = "mil"
private const val PROP_WATCH_HEX = "watch-hex"
private const val PROP_CALLSIGN = "callsign"
private const val PROP_SQUAWK = "squawk"
private const val PROP_LABEL = "label"
private const val PROP_START = "start"
private const val PROP_END = "end"
private const val PROP_WIDTH = "width"
private const val HISTORY_FUTURE_OPACITY = 0.35f
private const val LAST_SEEN_OPACITY = 0.5f
private const val GROUND_ALTITUDE = -1_000
private const val LABEL_FONT = "Noto Sans Regular"
private const val TAP_SLOP_DP = 16
private const val ATTRIBUTION_MARGIN_DP = 8
private const val SELECTED_ICON_FACTOR = 1.25f
private const val SELECTED_RING_RADIUS_PER_ICON_SIZE = 15f
private const val SELECTED_SHADOW_WIDTH = 4f
private const val SELECTED_LABEL_GAP = 4f

/** Badge centre from the aircraft's, in the aircraft image's dp at icon size 1, both ways. */
private const val BADGE_OFFSET = 10f
private const val BADGE_MIN_SIZE = 0.6f
private const val MILITARY_BADGE_SIDE = 1f
private const val WATCH_BADGE_SIDE = -1f
private const val MAX_FADES_PER_FRAME = 30
private val CAMERA_SETTLE_DELAY = 300.milliseconds
private val REPORT_INTERVAL = 250.milliseconds

private const val OFFSCREEN_MARGIN_DP = 48f
private val SPARSE_WRITE_INTERVAL = 100.milliseconds
private val MARK_HANDOFF = 200.milliseconds

/** Degrees, well below a pixel at the closest zoom. */
private const val CAMERA_EPSILON = 1e-7
private val STYLE_RETRY_DELAY = 5.seconds

/** MapLibre scale; below this a label per aircraft covers the map. */
private const val LABELS_MIN_ZOOM = 6.5f

/** MapLibre scale, about an airport's apron. Closer, positions are less exact than the map shows them. */
private const val MAX_ZOOM = 14.4
