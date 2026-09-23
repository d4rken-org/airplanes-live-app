package eu.darken.apl.common.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.apl.common.coroutine.AppScope
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.flow.replayingShare
import eu.darken.apl.common.flow.setupCommonEventHandlers
import eu.darken.apl.common.permissions.Permission
import kotlinx.coroutines.CoroutineScope
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
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationManager2 @Inject constructor(
    @param:AppScope private val scope: CoroutineScope,
    @param:ApplicationContext private val context: Context,
    private val locationManager: LocationManager,
    private val dispatchers: DispatcherProvider,
) {
    private val executor: Executor = Executors.newSingleThreadExecutor()

    private val permissionFlow: Flow<Boolean> = flow {
        var lastCheck: Boolean? = null
        fun check() = Permission.ACCESS_COARSE_LOCATION.isGranted(context)
        while (currentCoroutineContext().isActive) {
            val newCheck = check()
            if (newCheck != lastCheck) emit(newCheck)
            lastCheck = newCheck
            if (!newCheck) delay(1000L) else break
        }
    }
    private val locationFlow: Flow<State> = callbackFlow {
        if (!Permission.ACCESS_COARSE_LOCATION.isGranted(context)) {
            throw SecurityException("ACCESS_COARSE_LOCATION has not been granted")
        }

        val locationListener = LocationListenerCompat { location ->
            log(TAG) { "New location $location" }
            trySend(State.Available(location))
        }

        trySend(State.Waiting)

        @Suppress("MissingPermission")
        val lastKnown = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        if (lastKnown != null) {
            log(TAG) { "Emitting last known location: $lastKnown" }
            trySend(State.Available(lastKnown, isLastKnown = true))
        }

        log(TAG) { "Requesting location updates" }
        @Suppress("MissingPermission")
        LocationManagerCompat.requestLocationUpdates(
            locationManager,
            LocationManager.NETWORK_PROVIDER,
            LocationRequestCompat.Builder(60 * 1000L).apply {
                setMinUpdateDistanceMeters(100f)
                setQuality(LocationRequestCompat.QUALITY_LOW_POWER)
            }.build(),
            executor,
            locationListener
        )


        awaitClose {
            log(TAG) { "Canceling location updates" }
            @Suppress("MissingPermission")
            LocationManagerCompat.removeUpdates(locationManager, locationListener)
        }
    }

    val state = permissionFlow
        .flatMapLatest { hasPermission ->
            if (hasPermission) {
                locationFlow
            } else {
                flowOf(State.Unavailable(SecurityException("ACCESS_COARSE_LOCATION has not been granted")))
            }
        }
        .catch {
            emit(State.Unavailable(error = it))
        }
        .setupCommonEventHandlers(TAG) { "location" }
        .replayingShare(scope)

    val canGeocode: Boolean
        get() = Geocoder.isPresent()

    private val geocoder by lazy {
        Geocoder(context, Locale.getDefault())
    }

    suspend fun toName(location: Location): Address? = withContext(dispatchers.IO) {
        log(TAG) { "toName($location)" }

        val addresses = try {
            @Suppress("DEPRECATION")
            geocoder.getFromLocation(location.latitude, location.longitude, 1)
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to get location name for $location: ${e.asLog()}" }
            null
        }

        addresses?.singleOrNull().also {
            log(TAG) { "toName($location) -> $it" }
        }
    }

    suspend fun fromName(locality: String): Location? = withContext(dispatchers.IO) {
        log(TAG) { "fromName($locality)" }

        val results = try {
            @Suppress("DEPRECATION")
            geocoder.getFromLocationName(locality, 1)
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to get location for name '$locality': ${e.asLog()}" }
            null
        }

        results?.singleOrNull()?.let {
            Location("geocoder").apply {
                latitude = it.latitude
                longitude = it.longitude
            }
        }.also {
            log(TAG) { "fromName($locality) -> $it" }
        }
    }

    sealed interface State {
        data object Waiting : State

        data class Available(
            val location: Location,
            val isLastKnown: Boolean = false,
        ) : State

        data class Unavailable(
            val error: Throwable,
        ) : State {
            val isPermissionIssue: Boolean
                get() = error is SecurityException
        }
    }

    companion object {
        private val TAG = logTag("LocationManager2")
    }
}