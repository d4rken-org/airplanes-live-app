package eu.darken.apl.ar.core

import android.annotation.SuppressLint
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.time.Duration.Companion.seconds

class ArLocationProvider(
    private val locationManager: LocationManager,
) {

    @SuppressLint("MissingPermission")
    val location: Flow<Location?> = callbackFlow {
        val hasGps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val hasNetwork = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!hasGps && !hasNetwork) {
            log(TAG, WARN) { "No location providers available" }
            trySend(null)
            awaitClose()
            return@callbackFlow
        }

        val provider = if (hasGps) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
        log(TAG) { "Using provider: $provider" }

        try {
            val lastKnown = locationManager.getLastKnownLocation(provider)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (lastKnown != null) {
                log(TAG) { "Emitting last known location" }
                trySend(lastKnown)
            }

            val listener = LocationListener { location ->
                trySend(location)
            }

            locationManager.requestLocationUpdates(provider, LOCATION_UPDATE_INTERVAL.inWholeMilliseconds, 0f, listener, Looper.getMainLooper())
            log(TAG) { "Location updates requested on $provider" }

            awaitClose {
                log(TAG) { "Location updates removed" }
                locationManager.removeUpdates(listener)
            }
        } catch (e: SecurityException) {
            log(TAG, WARN) { "Location permission not granted: ${e.message}" }
            trySend(null)
            awaitClose()
        }
    }

    companion object {
        private val LOCATION_UPDATE_INTERVAL = 1.seconds
        private val TAG = logTag("AR", "LocationProvider")
    }
}
