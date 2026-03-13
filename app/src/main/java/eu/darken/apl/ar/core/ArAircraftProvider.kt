package eu.darken.apl.ar.core

import android.location.Location
import android.os.SystemClock
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.aircraft.altitudeFt
import eu.darken.apl.main.core.api.AirplanesLiveEndpoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ArAircraftProvider(
    private val locationState: StateFlow<Location?>,
    private val endpoint: AirplanesLiveEndpoint,
    private val dispatcherProvider: DispatcherProvider,
    private val maxRangeM: Long = 100_000L,
) {

    private data class TimestampedAircraft(
        val aircraft: Aircraft,
        val fetchedAt: Instant,
    )

    val aircraft: Flow<List<InterpolatedAircraft>> = flow {
        var cachedList: List<TimestampedAircraft> = emptyList()
        var consecutiveFailures = 0
        var lastPollTime = 0L

        while (currentCoroutineContext().isActive) {
            val location = locationState.value
            val now = SystemClock.elapsedRealtime()

            // Polling
            val backoff = when {
                consecutiveFailures <= 0 -> 4.seconds
                consecutiveFailures == 1 -> 8.seconds
                consecutiveFailures == 2 -> 16.seconds
                else -> 32.seconds
            }
            if (location != null && now - lastPollTime >= backoff.inWholeMilliseconds) {
                lastPollTime = now
                try {
                    val result = endpoint.getByLocation(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        radiusInMeter = maxRangeM,
                    )
                    val fetchTime = Instant.now()
                    cachedList = result.mapNotNull { ac ->
                        if (ac.location == null) return@mapNotNull null
                        TimestampedAircraft(aircraft = ac, fetchedAt = fetchTime)
                    }
                    consecutiveFailures = 0
                    log(TAG, VERBOSE) { "Fetched ${cachedList.size} aircraft" }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    consecutiveFailures++
                    log(TAG, WARN) { "API poll failed (failures=$consecutiveFailures): $e" }
                }
            }

            // Use raw API positions (interpolation disabled for debugging)
            val nowInstant = Instant.now()
            val interpolated = cachedList.mapNotNull { item ->
                val ageSec = java.time.Duration.between(item.fetchedAt, nowInstant).toMillis() / 1000f
                if (ageSec > 60f) return@mapNotNull null

                val acLoc = item.aircraft.location ?: return@mapNotNull null
                val altFt = item.aircraft.altitudeFt

                val dist = if (location != null) {
                    ScreenProjection.haversineDistanceM(
                        location.latitude, location.longitude, acLoc.latitude, acLoc.longitude
                    )
                } else {
                    Double.MAX_VALUE
                }

                InterpolatedAircraft(
                    source = item.aircraft,
                    interpolatedLat = acLoc.latitude,
                    interpolatedLon = acLoc.longitude,
                    altitudeFt = altFt,
                    distanceM = dist,
                    ageSec = ageSec,
                )
            }
                .sortedBy { it.distanceM }
                .take(50)

            emit(interpolated)
            delay(100.milliseconds) // 10Hz
        }
    }.flowOn(dispatcherProvider.Default)

    companion object {
        private val TAG = logTag("AR", "AircraftProvider")
    }
}
