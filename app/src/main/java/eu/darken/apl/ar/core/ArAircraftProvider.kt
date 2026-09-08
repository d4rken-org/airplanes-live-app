package eu.darken.apl.ar.core

import android.location.Location
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.main.core.query.ViewingSnapshot
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.access.AccessRepo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

/**
 * Turns the server's viewing snapshots into positions for the current frame.
 *
 * Fetching and drawing are independent: the ticker interpolates from the newest snapshot at a
 * steady rate while the network runs at whatever pace the tier allows. Ages come from server time,
 * so a snapshot that is delivered again does not make its observations look fresh.
 */
class ArAircraftProvider(
    private val locationState: StateFlow<Location?>,
    private val aircraftRepo: AircraftRepo,
    private val arSettings: ArSettings,
    private val accessRepo: AccessRepo,
    private val serverClock: ServerClock,
    private val dispatcherProvider: DispatcherProvider,
    private val maxRangeNm: Double,
) {

    private val latest = MutableStateFlow<AircraftRepo.ViewingState?>(null)

    val state: StateFlow<AircraftRepo.ViewingState?> = latest

    val aircraft: Flow<List<InterpolatedAircraft>> = callbackFlow {
        val queries = locationState.filterNotNull().map { location ->
            val radiusNm = min(maxRangeNm, accessRepo.state.value?.maxArRadiusNm?.toDouble() ?: maxRangeNm)
            AircraftRepo.ViewingQuery.Ar(location.latitude, location.longitude, radiusNm)
        }

        val poller = launch {
            aircraftRepo.viewing(queries).collect { latest.value = it }
        }

        // The ticker only reads state, a stalled request never freezes the display
        while (currentCoroutineContext().isActive) {
            val snapshot = (latest.value as? AircraftRepo.ViewingState.Snapshot)?.value
            send(snapshot?.let { interpolate(it, locationState.value) } ?: emptyList())
            delay(TICK)
        }

        awaitClose { poller.cancel() }
    }.flowOn(dispatcherProvider.Default)

    private fun interpolate(snapshot: ViewingSnapshot, viewer: Location?): List<InterpolatedAircraft> {
        val serverNow = serverClock.now()
        return snapshot.aircraft.mapNotNull { ac ->
            // Without a position age the observation cannot be placed in time, so it is not shown
            val positionSeenAt = ac.positionSeenAt ?: return@mapNotNull null
            val acLoc = ac.location ?: return@mapNotNull null
            val ageSec = (serverNow.toEpochMilli() - positionSeenAt.toEpochMilli()) / 1000f
            if (ageSec >= HIDE_AGE_SEC) return@mapNotNull null

            val extrapolate = ageSec <= EXTRAPOLATE_AGE_SEC
            val speed = ac.groundSpeed
            val track = ac.groundTrack
            val canExtrapolate = extrapolate && speed != null && track != null &&
                    speed.isFinite() && track.isFinite() &&
                    speed in 0f..2000f && track in 0f..360f

            val (lat, lon) = if (canExtrapolate) {
                ScreenProjection.extrapolatePosition(acLoc.latitude, acLoc.longitude, track!!, speed!!, ageSec)
            } else {
                acLoc.latitude to acLoc.longitude
            }

            val altFt = ac.altitudeFt
            val altRate = ac.altitudeRate
            val extrapolatedAltFt = if (canExtrapolate && altFt != null && altRate != null) {
                ScreenProjection.extrapolateAltitudeFt(altFt, altRate, ageSec)
            } else {
                altFt
            }

            val distance = viewer
                ?.let { ScreenProjection.haversineDistanceM(it.latitude, it.longitude, lat, lon) }
                ?: Double.MAX_VALUE

            InterpolatedAircraft(
                source = ac,
                interpolatedLat = lat,
                interpolatedLon = lon,
                altitudeFt = extrapolatedAltFt,
                distanceM = distance,
                ageSec = ageSec,
                positionAgeSec = ageSec,
                isStale = !extrapolate,
                opacity = opacityFor(ageSec),
            )
        }
            .sortedBy { it.distanceM }
            .take(MAX_AIRCRAFT)
            .also { log(TAG, VERBOSE) { "snapshot: ${it.size} aircraft" } }
    }

    /** Between the extrapolation limit and the hide age the label fades instead of jumping away. */
    private fun opacityFor(ageSec: Float): Float = when {
        ageSec <= EXTRAPOLATE_AGE_SEC -> 1f
        else -> {
            val progress = (ageSec - EXTRAPOLATE_AGE_SEC) / (HIDE_AGE_SEC - EXTRAPOLATE_AGE_SEC)
            (1f - progress * (1f - MIN_OPACITY)).coerceIn(MIN_OPACITY, 1f)
        }
    }

    companion object {
        const val MAX_AIRCRAFT = 75
        const val EXTRAPOLATE_AGE_SEC = 15f
        const val HIDE_AGE_SEC = 30f
        private const val MIN_OPACITY = 0.3f
        private val TICK = 100.milliseconds
        private val TAG = logTag("AR", "AircraftProvider")
    }
}
