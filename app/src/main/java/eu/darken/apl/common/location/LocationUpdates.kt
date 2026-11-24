package eu.darken.apl.common.location

import android.location.Location
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationRequestCompat
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.location.LocationManager2.State
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

internal fun locationStates(
    gateway: LocationProviderGateway,
    executor: Executor,
): Flow<State> {
    val permissionFlow: Flow<Boolean> = flow {
        var lastCheck: Boolean? = null
        while (currentCoroutineContext().isActive) {
            val newCheck = gateway.hasCoarsePermission()
            if (newCheck != lastCheck) emit(newCheck)
            lastCheck = newCheck
            if (!newCheck) delay(1000L) else break
        }
    }

    return permissionFlow
        .flatMapLatest { hasPermission ->
            if (hasPermission) {
                locationUpdates(gateway, executor)
            } else {
                flowOf(State.Unavailable(SecurityException("ACCESS_COARSE_LOCATION has not been granted")))
            }
        }
        .catch {
            emit(State.Unavailable(error = it))
        }
}

private fun locationUpdates(
    gateway: LocationProviderGateway,
    executor: Executor,
): Flow<State> = callbackFlow {
    if (!gateway.hasCoarsePermission()) {
        throw SecurityException("ACCESS_COARSE_LOCATION has not been granted")
    }

    val providers = selectLocationProviders(
        sdkInt = gateway.sdkInt,
        hasFineLocation = gateway.hasFinePermission(),
        existingProviders = gateway.existingProviders(),
    )
    log(TAG) { "Selected location providers: $providers" }

    // The seed is sent from the collector, later fixes from the executor thread.
    val newestNanos = AtomicLong(Long.MIN_VALUE)
    fun emitFix(location: Location, isLastKnown: Boolean) {
        val nanos = location.elapsedRealtimeNanos
        if (nanos < newestNanos.get()) {
            log(TAG) { "Dropping older location from ${location.provider}: $location" }
            return
        }
        newestNanos.set(nanos)
        log(TAG) { "New location from ${location.provider} (lastKnown=$isLastKnown): $location" }
        trySend(State.Available(location, isLastKnown = isLastKnown))
    }

    val listeners = mutableListOf<Pair<String, LocationListenerCompat>>()
    val oneShots = mutableListOf<Pair<String, DisposableHandle>>()

    fun release() {
        listeners.forEach { (provider, listener) ->
            try {
                gateway.removeUpdates(listener)
            } catch (e: Exception) {
                log(TAG, WARN) { "Failed to remove location updates for $provider: ${e.asLog()}" }
            }
        }
        oneShots.forEach { (provider, handle) ->
            try {
                handle.dispose()
            } catch (e: Exception) {
                log(TAG, WARN) { "Failed to cancel current location request for $provider: ${e.asLog()}" }
            }
        }
    }

    try {
        trySend(State.Waiting)

        providers.lastKnown
            .mapNotNull { provider -> perProvider(provider, "last known location") { gateway.lastKnown(provider) } }
            .maxByOrNull { it.elapsedRealtimeNanos }
            ?.let { emitFix(it, isLastKnown = true) }

        val request = LocationRequestCompat.Builder(60 * 1000L).apply {
            setMinUpdateDistanceMeters(100f)
            setQuality(LocationRequestCompat.QUALITY_LOW_POWER)
        }.build()

        providers.continuous.forEach { provider ->
            val listener = LocationListenerCompat { emitFix(it, isLastKnown = false) }
            perProvider(provider, "location updates") {
                gateway.requestUpdates(provider, request, executor, listener)
                listeners.add(provider to listener)
            }
        }

        providers.oneShot.forEach { provider ->
            perProvider(provider, "current location") {
                val handle = gateway.currentLocation(provider, executor) { location ->
                    if (location == null) {
                        log(TAG) { "No current location from $provider" }
                    } else {
                        emitFix(location, isLastKnown = false)
                    }
                }
                oneShots.add(provider to handle)
            }
        }
    } catch (e: Throwable) {
        release()
        throw e
    }

    awaitClose {
        log(TAG) { "Canceling location updates" }
        release()
    }
}

internal suspend fun requestCurrentLocation(
    gateway: LocationProviderGateway,
    executor: Executor,
    timeout: Duration,
): Location? {
    if (!gateway.hasCoarsePermission()) {
        log(TAG, WARN) { "currentLocation(): ACCESS_COARSE_LOCATION has not been granted" }
        return null
    }

    val providers = selectLocationProviders(
        sdkInt = gateway.sdkInt,
        hasFineLocation = gateway.hasFinePermission(),
        existingProviders = gateway.existingProviders(),
    )
    log(TAG) { "currentLocation(): Asking ${providers.current}" }
    val cached = recentLastKnown(gateway, providers.lastKnown)

    val answers = Channel<Pair<String, Location?>>(Channel.UNLIMITED)
    val handles = mutableListOf<Pair<String, DisposableHandle>>()
    try {
        providers.current.forEach { provider ->
            perProvider(provider, "current location") {
                val handle = gateway.currentLocation(provider, executor) { answers.trySend(provider to it) }
                handles.add(provider to handle)
            }
        }

        var miss: String? = null
        if (handles.isEmpty()) {
            log(TAG, WARN) { "currentLocation(): No provider could be asked" }
        } else {
            val wait = if (cached != null) minOf(CACHED_WAIT, timeout) else timeout
            val pending = handles.map { it.first }.toMutableSet()
            val answer = withTimeoutOrNull(wait) {
                while (pending.isNotEmpty()) {
                    val (provider, location) = answers.receive()
                    if (location != null) return@withTimeoutOrNull provider to location
                    log(TAG) { "currentLocation(): No location from $provider" }
                    pending.remove(provider)
                }
                null
            }

            if (answer != null) {
                val (asked, fix) = answer
                log(TAG) { "currentLocation(): $asked answered with ${fix.provider}: $fix" }
                return fix
            }
            miss = if (pending.isEmpty()) {
                "None of ${providers.current} had a location"
            } else {
                "No location within $wait, still waiting on $pending"
            }
        }

        val fallback = recentLastKnown(gateway, providers.lastKnown)
        if (fallback != null) {
            val age = fixAge(gateway.elapsedRealtimeNanos(), fallback)
            log(TAG) { "currentLocation(): Using last known location from ${fallback.provider}, $age old" }
            return fallback
        }
        if (miss != null) log(TAG) { "currentLocation(): $miss" }
        return null
    } finally {
        handles.forEach { (provider, handle) ->
            try {
                handle.dispose()
            } catch (e: Exception) {
                log(TAG, WARN) { "Failed to cancel current location request for $provider: ${e.asLog()}" }
            }
        }
    }
}

private fun recentLastKnown(gateway: LocationProviderGateway, providers: List<String>): Location? {
    val now = gateway.elapsedRealtimeNanos()
    return providers
        .mapNotNull { provider -> perProvider(provider, "last known location") { gateway.lastKnown(provider) } }
        .filter { fixAge(now, it) <= MAX_CACHED_AGE }
        .maxByOrNull { it.elapsedRealtimeNanos }
}

private fun fixAge(nowNanos: Long, fix: Location): Duration =
    (nowNanos - fix.elapsedRealtimeNanos).coerceAtLeast(0L).nanoseconds

private inline fun <T> perProvider(provider: String, what: String, block: () -> T): T? = try {
    block()
} catch (e: SecurityException) {
    log(TAG, WARN) { "Skipping $provider for $what: ${e.asLog()}" }
    null
} catch (e: IllegalArgumentException) {
    log(TAG, WARN) { "Skipping $provider for $what: ${e.asLog()}" }
    null
}

private val MAX_CACHED_AGE = 10.minutes
private val CACHED_WAIT = 3.seconds

private val TAG = logTag("Location", "Updates")
