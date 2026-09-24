package eu.darken.apl.map.ui

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.RectF
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import eu.darken.apl.R
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.map.core.MapAircraftProvider
import eu.darken.apl.map.core.MapOptions
import eu.darken.apl.map.core.MapPlane
import eu.darken.apl.map.core.MapViewport
import eu.darken.apl.map.core.RecentTracks
import eu.darken.apl.server.api.TrailPoint
import androidx.core.view.doOnLayout
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds
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

    fun fitBounds(south: Double, west: Double, north: Double, east: Double, paddingPx: Int) = run { map ->
        // A single point or a flat line has no extent to fit
        if (south == north && west == east) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(south, west), FOCUS_ZOOM.toMapLibreZoom()))
            return@run
        }
        val bounds = LatLngBounds.Builder()
            .include(LatLng(south, west))
            .include(LatLng(north, east))
            .build()
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, paddingPx))
    }

    companion object {
        private const val FOCUS_ZOOM = 9.0
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
    labels: Boolean,
    follow: Boolean,
    myLocation: Pair<Double, Double>?,
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

    // Only a style that finished loading and carries our layers accepts updates
    var style by remember { mutableStateOf<Style?>(null) }

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
        }
        map.moveCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(startCamera.lat, startCamera.lon), startCamera.zoom.toMapLibreZoom())
        )
        map.addOnCameraIdleListener {
            val (viewport, center) = map.currentView()
            currentOnCameraIdle(viewport, center)
        }
        map.addOnMapClickListener { point ->
            val screen = map.projection.toScreenLocation(point)
            val slop = TAP_SLOP_DP * context.resources.displayMetrics.density
            val hit = map.queryRenderedFeatures(
                RectF(screen.x - slop, screen.y - slop, screen.x + slop, screen.y + slop),
                LAYER_SELECTED,
                LAYER_TRAFFIC,
            ).firstNotNullOfOrNull { it.getStringProperty(PROP_HEX) }
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

    LaunchedEffect(style, myLocation) {
        val current = style ?: return@LaunchedEffect
        val features = myLocation
            ?.let { (lat, lon) -> FeatureCollection.fromFeature(Feature.fromGeometry(Point.fromLngLat(lon, lat))) }
            ?: FeatureCollection.fromFeatures(emptyList())
        current.getSourceAs<GeoJsonSource>(SOURCE_ME)?.setGeoJson(features)
    }

    LaunchedEffect(style) {
        val current = style ?: return@LaunchedEffect
        val target = map ?: return@LaunchedEffect
        // Not collecting while stopped is what lets the view model stop polling the server
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var trafficVersion = -1L
            var trail: List<List<TrailPoint>>? = null
            var tracks: Map<String, List<RecentTracks.Point>>? = null

            // Frames are complete, when drawing falls behind only the newest one matters
            frames.conflate().collect { frame ->
                val trafficUpdate = if (frame.trafficVersion != trafficVersion) {
                    withContext(Dispatchers.Default) { trafficFeatures(frame.traffic) }
                } else {
                    null
                }
                val trailUpdate = if (frame.selectedTrail !== trail) {
                    withContext(Dispatchers.Default) { trailFeatures(frame.selectedTrail, frame.selected) }
                } else {
                    null
                }
                val tracksUpdate = if (frame.tracks !== tracks) {
                    withContext(Dispatchers.Default) { trackFeatures(frame.tracks) }
                } else {
                    null
                }

                // A style switch drops our sources, the next style's effect takes over
                if (!current.isFullyLoaded) return@collect

                trafficUpdate?.let {
                    current.getSourceAs<GeoJsonSource>(SOURCE_TRAFFIC)?.setGeoJson(it)
                    trafficVersion = frame.trafficVersion
                }
                trailUpdate?.let {
                    current.getSourceAs<GeoJsonSource>(SOURCE_TRAIL)?.setGeoJson(it)
                    trail = frame.selectedTrail
                }
                tracksUpdate?.let {
                    current.getSourceAs<GeoJsonSource>(SOURCE_TRACKS)?.setGeoJson(it)
                    tracks = frame.tracks
                }
                current.getSourceAs<GeoJsonSource>(SOURCE_SELECTED)?.setGeoJson(
                    FeatureCollection.fromFeatures(listOfNotNull(frame.selected?.toFeature()))
                )

                val selected = frame.selected
                if (currentFollow && selected != null) {
                    target.moveCamera(CameraUpdateFactory.newLatLng(LatLng(selected.latitude, selected.longitude)))
                }
            }
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
    )
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
    style.addImage(IMAGE_AIRCRAFT, aircraftIcon(context), true)

    style.addSource(GeoJsonSource(SOURCE_TRACKS))
    style.addSource(GeoJsonSource(SOURCE_TRAIL))
    style.addSource(GeoJsonSource(SOURCE_TRAFFIC))
    style.addSource(GeoJsonSource(SOURCE_SELECTED))
    style.addSource(GeoJsonSource(SOURCE_ME))

    style.addLayer(
        LineLayer(LAYER_TRACKS, SOURCE_TRACKS).withProperties(
            PropertyFactory.lineColor(altitudeColor()),
            PropertyFactory.lineWidth(1.5f),
            PropertyFactory.lineOpacity(0.6f),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
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
    // Rings instead of icon halos: a halo only works on true distance-field images
    style.addLayer(
        CircleLayer(LAYER_MILITARY, SOURCE_TRAFFIC)
            .withFilter(Expression.eq(Expression.get(PROP_MILITARY), true))
            .withProperties(
                PropertyFactory.circleRadius(
                    Expression.interpolate(
                        Expression.linear(),
                        Expression.zoom(),
                        Expression.stop(2, 6f),
                        Expression.stop(6, 9f),
                        Expression.stop(9, 13f),
                    )
                ),
                PropertyFactory.circleOpacity(0f),
                PropertyFactory.circleStrokeColor(Color.parseColor("#D32F2F")),
                PropertyFactory.circleStrokeWidth(2f),
                PropertyFactory.circleStrokeOpacity(Expression.get(PROP_OPACITY)),
            )
    )
    style.addLayer(
        CircleLayer(LAYER_SELECTED_RING, SOURCE_SELECTED).withProperties(
            PropertyFactory.circleRadius(18f),
            PropertyFactory.circleColor(Color.WHITE),
            PropertyFactory.circleOpacity(0.7f),
            PropertyFactory.circleStrokeColor(Color.parseColor("#424242")),
            PropertyFactory.circleStrokeWidth(1.5f),
        )
    )
    style.addLayer(
        SymbolLayer(LAYER_TRAFFIC, SOURCE_TRAFFIC).withProperties(
            PropertyFactory.iconImage(IMAGE_AIRCRAFT),
            PropertyFactory.iconRotate(Expression.coalesce(Expression.get(PROP_TRACK), Expression.literal(0))),
            PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            // Whole continents at once need small icons, or they merge into one surface
            PropertyFactory.iconSize(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.zoom(),
                    Expression.stop(2, 0.35f),
                    Expression.stop(6, 0.6f),
                    Expression.stop(9, 0.8f),
                )
            ),
            PropertyFactory.iconColor(altitudeColor()),
            PropertyFactory.iconOpacity(Expression.get(PROP_OPACITY)),
        )
    )
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
            PropertyFactory.textOpacity(Expression.get(PROP_OPACITY)),
        )
    )
    style.addLayer(
        SymbolLayer(LAYER_SELECTED, SOURCE_SELECTED).withProperties(
            PropertyFactory.iconImage(IMAGE_AIRCRAFT),
            PropertyFactory.iconRotate(Expression.coalesce(Expression.get(PROP_TRACK), Expression.literal(0))),
            PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconSize(1.2f),
            PropertyFactory.iconColor(altitudeColor()),
            PropertyFactory.iconOpacity(Expression.get(PROP_OPACITY)),
            PropertyFactory.textField(Expression.get(PROP_LABEL)),
            PropertyFactory.textFont(arrayOf(LABEL_FONT)),
            PropertyFactory.textSize(12f),
            PropertyFactory.textOffset(arrayOf(0f, 1.8f)),
            PropertyFactory.textAllowOverlap(true),
            PropertyFactory.textColor(Color.BLACK),
            PropertyFactory.textHaloColor(Color.WHITE),
            PropertyFactory.textHaloWidth(2f),
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

/**
 * Grey on the ground or without an altitude, then from orange near the ground through green and
 * blue to magenta at cruise altitudes, close to tar1090's colours.
 */
private fun altitudeColor(): Expression = Expression.interpolate(
    Expression.linear(),
    Expression.coalesce(Expression.get(PROP_ALTITUDE), Expression.literal(GROUND_ALTITUDE)),
    Expression.stop(GROUND_ALTITUDE, Expression.color(Color.parseColor("#9E9E9E"))),
    Expression.stop(0, Expression.color(Color.parseColor("#FF6D00"))),
    Expression.stop(5_000, Expression.color(Color.parseColor("#FFC400"))),
    Expression.stop(10_000, Expression.color(Color.parseColor("#64DD17"))),
    Expression.stop(20_000, Expression.color(Color.parseColor("#00B8D4"))),
    Expression.stop(30_000, Expression.color(Color.parseColor("#2962FF"))),
    Expression.stop(40_000, Expression.color(Color.parseColor("#D500F9"))),
)

private fun MapPlane.toFeature(): Feature = Feature.fromGeometry(Point.fromLngLat(longitude, latitude)).apply {
    addStringProperty(PROP_HEX, hex)
    trackDegrees?.let { addNumberProperty(PROP_TRACK, it) }
    addNumberProperty(PROP_ALTITUDE, if (onGround) GROUND_ALTITUDE else altitudeFt ?: GROUND_ALTITUDE)
    addNumberProperty(PROP_OPACITY, opacity)
    addBooleanProperty(PROP_MILITARY, military)
    addStringProperty(PROP_LABEL, callsign ?: hex)
}

private fun trafficFeatures(planes: List<MapPlane>): FeatureCollection =
    FeatureCollection.fromFeatures(planes.map { it.toFeature() })

/** One feature per segment, a line can only have one colour per feature. */
private fun trailFeatures(segments: List<List<TrailPoint>>, selected: MapPlane?): FeatureCollection {
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
    // Joins the last known point to where the aircraft is drawn now
    val last = segments.lastOrNull()?.lastOrNull()
    if (last != null && selected != null) {
        features.add(
            Feature.fromGeometry(
                lineOf(listOf(last.latitude, selected.latitude), listOf(last.longitude, selected.longitude))
            ).apply { selected.altitudeFt?.let { addNumberProperty(PROP_ALTITUDE, it) } }
        )
    }
    return FeatureCollection.fromFeatures(features)
}

private fun trackFeatures(tracks: Map<String, List<RecentTracks.Point>>?): FeatureCollection {
    if (tracks == null) return FeatureCollection.fromFeatures(emptyList())
    val features = tracks.values.mapNotNull { points ->
        if (points.size < 2) return@mapNotNull null
        Feature.fromGeometry(lineOf(points.map { it.latitude }, points.map { it.longitude })).apply {
            points.last().altitudeFt?.let { addNumberProperty(PROP_ALTITUDE, it) }
        }
    }
    return FeatureCollection.fromFeatures(features)
}

private const val IMAGE_AIRCRAFT = "apl-aircraft"
private const val SOURCE_TRAFFIC = "apl-traffic"
private const val SOURCE_SELECTED = "apl-selected"
private const val SOURCE_TRAIL = "apl-trail"
private const val SOURCE_TRACKS = "apl-tracks"
private const val SOURCE_ME = "apl-me"
private const val LAYER_TRAFFIC = "apl-traffic-layer"
private const val LAYER_MILITARY = "apl-military-layer"
private const val LAYER_SELECTED_RING = "apl-selected-ring-layer"
private const val LAYER_LABELS = "apl-labels-layer"
private const val LAYER_SELECTED = "apl-selected-layer"
private const val LAYER_TRAIL = "apl-trail-layer"
private const val LAYER_TRACKS = "apl-tracks-layer"
private const val LAYER_ME = "apl-me-layer"
private const val PROP_HEX = "hex"
private const val PROP_TRACK = "track"
private const val PROP_ALTITUDE = "alt"
private const val PROP_OPACITY = "opacity"
private const val PROP_MILITARY = "mil"
private const val PROP_LABEL = "label"
private const val GROUND_ALTITUDE = -1_000
private const val LABEL_FONT = "Noto Sans Regular"
private const val TAP_SLOP_DP = 16
private val STYLE_RETRY_DELAY = 5.seconds

/** MapLibre scale; below this a label per aircraft covers the map. */
private const val LABELS_MIN_ZOOM = 6.5f
