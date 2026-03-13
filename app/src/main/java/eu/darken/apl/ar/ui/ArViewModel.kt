package eu.darken.apl.ar.ui

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.apl.ar.core.ArAircraftProvider
import eu.darken.apl.ar.core.ArAircraftProvider.Companion.MAX_AIRCRAFT
import eu.darken.apl.ar.core.ArDwellTracker
import eu.darken.apl.ar.core.ArLocationProvider
import eu.darken.apl.ar.core.ArSettings
import eu.darken.apl.ar.core.DeviceOrientationProvider
import eu.darken.apl.ar.core.InterpolatedAircraft
import eu.darken.apl.ar.core.ScreenProjection
import eu.darken.apl.R
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.datastore.valueBlocking
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.flight.FlightRepo
import eu.darken.apl.common.flight.FlightRoute
import eu.darken.apl.common.flow.combine
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.main.core.aircraft.AircraftHex
import eu.darken.apl.main.core.api.AirplanesLiveEndpoint
import eu.darken.apl.map.core.MapOptions
import eu.darken.apl.map.ui.DestinationMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.tan
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class ArViewModel @Inject constructor(
    @ApplicationContext context: Context,
    dispatcherProvider: DispatcherProvider,
    locationManager: LocationManager,
    endpoint: AirplanesLiveEndpoint,
    private val orientationProvider: DeviceOrientationProvider,
    private val arSettings: ArSettings,
    private val flightRepo: FlightRepo,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("AR", "ViewModel"),
) {

    val hasSensor: Boolean = orientationProvider.hasGameSensor || orientationProvider.hasRotationSensor

    private val locationProvider = ArLocationProvider(locationManager)

    private val permissionGranted = MutableStateFlow(false)

    private val locationState = permissionGranted
        .flatMapLatest { granted ->
            if (granted) locationProvider.location else flowOf(null)
        }
        .stateIn(vmScope, SharingStarted.Eagerly, null)

    private val aircraftProvider = ArAircraftProvider(
        locationState = locationState,
        endpoint = endpoint,
        dispatcherProvider = dispatcherProvider,
        maxRangeM = arSettings.maxRangeM.valueBlocking,
    )

    private val maxLabels = arSettings.maxVisibleLabels.valueBlocking

    private val _displayRangeNm = MutableStateFlow(arSettings.displayRangeNm.valueBlocking)

    private val dwellTracker = ArDwellTracker(ROUTE_DWELL)
    private val prefetchedHexes = mutableSetOf<AircraftHex>()
    private val routeStates = MutableStateFlow<Map<AircraftHex, RouteUiState>>(emptyMap())

    private var prevCompassDeg = Float.NaN
    private val prevLabelPositions = mutableMapOf<AircraftHex, Pair<Float, Float>>()

    private val groundMessages: Array<String> = context.resources.getStringArray(R.array.ar_ground_messages)
    private var wasGroundVisible = false
    private var currentGroundMessage = ""

    private val orientationFlow: Flow<eu.darken.apl.ar.core.DeviceOrientation?> = if (hasSensor) {
        orientationProvider.orientation
    } else {
        flowOf(null)
    }

    val state = combine(
        locationState,
        orientationFlow,
        aircraftProvider.aircraft,
        _displayRangeNm,
        routeStates,
    ) { location, orientation, aircraft, rangeNm, routes ->
        if (location != null) {
            orientationProvider.updateLocation(location)
        }

        val rangeM = rangeNm * METERS_PER_NM
        val filtered = aircraft.filter { it.distanceM <= rangeM }

        val allProjected = if (location != null && orientation != null) {
            projectAllLabels(location, orientation, filtered)
        } else {
            emptyList()
        }

        // Track dwell on ALL visible hexes before label-count limits
        val visibleHexes = allProjected.mapTo(mutableSetOf()) { it.hex }
        val dwelledHexes = dwellTracker.update(visibleHexes, SystemClock.elapsedRealtime())
        for (hex in dwelledHexes) {
            if (hex in prefetchedHexes) continue
            val label = allProjected.firstOrNull { it.hex == hex } ?: continue
            val cs = label.callsign?.trim()?.takeIf { it.isNotBlank() } ?: continue
            startRouteLookup(hex, cs)
        }

        // Sort, take, resolve collisions, smooth, stamp route state
        val rawLabels = resolveCollisions(
            allProjected.sortedBy { it.distanceM }.take(maxLabels)
        )
        val labels = smoothLabelPositions(rawLabels).map { label ->
            label.copy(routeState = routes[label.hex] ?: RouteUiState.Idle)
        }

        val compassDeg = if (orientation != null) {
            var deg = Math.toDegrees(orientation.azimuthRad.toDouble()).toFloat()
            if (deg < 0) deg += 360f
            deg = smoothCompassDisplay(deg)
            deg
        } else {
            0f
        }

        val groundEasterEgg = if (orientation != null && labels.isEmpty()) {
            projectGroundPoint(orientation.rotationMatrix)
        } else {
            wasGroundVisible = false
            null
        }

        val radarBlips = if (location != null) {
            filtered.map { ac ->
                RadarBlip(
                    bearingRad = ScreenProjection.bearingRad(
                        location.latitude, location.longitude,
                        ac.interpolatedLat, ac.interpolatedLon,
                    ).toFloat(),
                    fractionOfRange = (ac.distanceM / rangeM).toFloat().coerceIn(0f, 1f),
                )
            }
        } else {
            emptyList()
        }

        State(
            labels = labels,
            compassHeadingDeg = compassDeg,
            locationAvailable = location != null,
            isGpsAccurate = location != null && location.hasAccuracy() && location.accuracy < 20f,
            sensorAvailable = hasSensor,
            totalNearbyCount = filtered.size,
            aircraftCapped = aircraft.size >= MAX_AIRCRAFT,
            isLoading = location == null && aircraft.isEmpty(),
            displayRangeNm = rangeNm,
            groundEasterEgg = groundEasterEgg,
            radarBlips = radarBlips,
        )
    }.stateIn(
        vmScope,
        SharingStarted.WhileSubscribed(5000),
        State(),
    )

    private fun projectAllLabels(
        location: Location,
        orientation: eu.darken.apl.ar.core.DeviceOrientation,
        aircraft: List<InterpolatedAircraft>,
    ): List<ArLabel> {
        val userAltM = location.altitude
        return aircraft.mapNotNull { ac ->
            val acAltM = ac.altitudeFt?.let { it * 0.3048 } ?: 0.0
            val result = ScreenProjection.project(
                userLat = location.latitude,
                userLon = location.longitude,
                userAltM = userAltM,
                acLat = ac.interpolatedLat,
                acLon = ac.interpolatedLon,
                acAltM = acAltM,
                rotationMatrix = orientation.rotationMatrix,
            ) ?: return@mapNotNull null

            if (!result.isVisible) return@mapNotNull null
            if (result.elevationDeg < 0f) return@mapNotNull null

            ArLabel(
                hex = ac.source.hex,
                callsign = ac.source.callsign,
                registration = ac.source.registration,
                description = ac.source.description,
                altitudeFt = ac.altitudeFt,
                speedKts = ac.source.groundSpeed,
                distanceM = result.distanceM,
                screenXNorm = result.screenXNorm,
                screenYNorm = result.screenYNorm,
            )
        }
    }

    private fun resolveCollisions(labels: List<ArLabel>): List<ArLabel> {
        if (labels.isEmpty()) return labels
        val labelHeight = 0.08f
        val result = mutableListOf<ArLabel>()

        for (label in labels) {
            var y = label.screenYNorm
            for (placed in result) {
                if (abs(label.screenXNorm - placed.screenXNorm) < 0.12f &&
                    abs(y - placed.screenYNorm) < labelHeight
                ) {
                    y = placed.screenYNorm + labelHeight
                }
            }
            if (y < 0f || y > 1f) continue
            result.add(label.copy(screenYNorm = y))
        }

        return result
    }

    fun onAircraftTapped(hex: AircraftHex) {
        log(tag) { "onAircraftTapped($hex)" }
        navTo(
            destination = DestinationMap(mapOptions = MapOptions.focus(hex)),
            popUpTo = DestinationAr,
            inclusive = true,
        )
    }

    fun onDisplayRangeChanged(rangeNm: Int) {
        _displayRangeNm.value = rangeNm
        launch { arSettings.displayRangeNm.update { rangeNm } }
    }

    fun onPermissionsGranted() {
        log(tag) { "onPermissionsGranted()" }
        permissionGranted.value = true
    }

    fun onDisplayRotationChanged(rotation: Int) {
        orientationProvider.updateDisplayRotation(rotation)
    }

    fun onClose() {
        navUp()
    }

    private fun projectGroundPoint(rotationMatrix: FloatArray): GroundEasterEgg? {
        // ENU direction for "straight down": (east=0, north=0, up=-1)
        val camX = -rotationMatrix[8]
        val camY = -rotationMatrix[9]
        val camZ = -rotationMatrix[10]

        // Behind camera
        if (camZ >= 0f) {
            wasGroundVisible = false
            return null
        }

        val ndcX = camX / -camZ
        val ndcY = camY / -camZ
        val screenX = (ndcX / TAN_HALF_H_FOV + 1f) / 2f
        val screenY = (1f - ndcY / TAN_HALF_V_FOV) / 2f

        val isOnScreen = screenX in 0f..1f && screenY in 0f..1f
        if (!isOnScreen) {
            wasGroundVisible = false
            return null
        }

        if (!wasGroundVisible) {
            currentGroundMessage = if (groundMessages.size >= 2) {
                groundMessages.filter { it != currentGroundMessage }.random()
            } else if (groundMessages.isNotEmpty()) {
                groundMessages.random()
            } else {
                return null
            }
        }
        wasGroundVisible = true

        return GroundEasterEgg(
            text = currentGroundMessage,
            screenXNorm = screenX,
            screenYNorm = screenY,
        )
    }

    data class GroundEasterEgg(
        val text: String,
        val screenXNorm: Float,
        val screenYNorm: Float,
    )

    private fun smoothCompassDisplay(rawDeg: Float): Float {
        if (prevCompassDeg.isNaN()) {
            prevCompassDeg = rawDeg
            return rawDeg
        }
        var diff = rawDeg - prevCompassDeg
        if (diff > 180f) diff -= 360f
        if (diff < -180f) diff += 360f
        var smoothed = prevCompassDeg + COMPASS_DISPLAY_ALPHA * diff
        if (smoothed < 0f) smoothed += 360f
        if (smoothed >= 360f) smoothed -= 360f
        prevCompassDeg = smoothed
        return smoothed
    }

    private fun smoothLabelPositions(labels: List<ArLabel>): List<ArLabel> {
        val currentHexes = mutableSetOf<AircraftHex>()
        val result = labels.map { label ->
            currentHexes.add(label.hex)
            val prev = prevLabelPositions[label.hex]
            if (prev != null) {
                val smoothX = prev.first + LABEL_ALPHA * (label.screenXNorm - prev.first)
                val smoothY = prev.second + LABEL_ALPHA * (label.screenYNorm - prev.second)
                prevLabelPositions[label.hex] = smoothX to smoothY
                label.copy(screenXNorm = smoothX, screenYNorm = smoothY)
            } else {
                prevLabelPositions[label.hex] = label.screenXNorm to label.screenYNorm
                label
            }
        }
        prevLabelPositions.keys.retainAll(currentHexes)
        return result
    }

    private fun startRouteLookup(hex: AircraftHex, callsign: String) {
        prefetchedHexes.add(hex)
        routeStates.update { it + (hex to RouteUiState.Loading) }
        launch {
            val route = withTimeoutOrNull(15.seconds) {
                flightRepo.lookup(hex, callsign)
            }
            val state = route?.toArRouteText()
                ?.let { RouteUiState.Ready(it) }
                ?: RouteUiState.Unavailable
            routeStates.update { it + (hex to state) }
        }
    }

    private fun FlightRoute.toArRouteText(): String? {
        val originLabel = origin?.displayLabel
        val destLabel = destination?.displayLabel
        return when {
            originLabel != null && destLabel != null -> "$originLabel \u2192 $destLabel"
            originLabel != null -> "$originLabel \u2192"
            destLabel != null -> "\u2192 $destLabel"
            else -> null
        }
    }

    data class RadarBlip(
        val bearingRad: Float,
        val fractionOfRange: Float,
    )

    data class State(
        val labels: List<ArLabel> = emptyList(),
        val compassHeadingDeg: Float = 0f,
        val locationAvailable: Boolean = false,
        val isGpsAccurate: Boolean = false,
        val sensorAvailable: Boolean = true,
        val totalNearbyCount: Int = 0,
        val aircraftCapped: Boolean = false,
        val isLoading: Boolean = true,
        val displayRangeNm: Int = ArSettings.MAX_RANGE_NM,
        val groundEasterEgg: GroundEasterEgg? = null,
        val radarBlips: List<RadarBlip> = emptyList(),
    )

    companion object {
        private val ROUTE_DWELL = 3.seconds
        private const val METERS_PER_NM = 1852.0
        private const val COMPASS_DISPLAY_ALPHA = 0.3f
        private const val LABEL_ALPHA = 0.4f
        private val TAN_HALF_H_FOV = tan(Math.toRadians(ScreenProjection.H_FOV_DEG.toDouble() / 2).toFloat())
        private val TAN_HALF_V_FOV = tan(Math.toRadians(50.0 / 2).toFloat())
    }
}
