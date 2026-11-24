package eu.darken.apl.common.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.apl.common.BuildWrap
import eu.darken.apl.common.coroutine.AppScope
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.flow.replayingShare
import eu.darken.apl.common.flow.setupCommonEventHandlers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Singleton
class LocationManager2 @Inject constructor(
    @param:AppScope private val scope: CoroutineScope,
    @param:ApplicationContext private val context: Context,
    private val gateway: LocationProviderGateway,
    private val dispatchers: DispatcherProvider,
) {
    private val executor: Executor = Executors.newSingleThreadExecutor()

    val state = locationStates(gateway, executor)
        .setupCommonEventHandlers(TAG) { "location" }
        .replayingShare(scope)

    /** Asks every usable provider for a fresh fix, never a cached one. The first fix wins. */
    suspend fun currentLocation(timeout: Duration): Location? = requestCurrentLocation(gateway, executor, timeout)

    val canGeocode: Boolean
        get() = Geocoder.isPresent()

    private val geocoder by lazy {
        Geocoder(context, Locale.getDefault())
    }

    @SuppressLint("NewApi")
    suspend fun toName(location: Location): Address? = withContext(dispatchers.IO) {
        log(TAG) { "toName($location)" }

        val outcome = if (BuildWrap.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            withTimeoutOrNull(GEOCODE_TIMEOUT) { geocode(location) }
        } else {
            awaitBounded(scope, dispatchers.IO, GEOCODE_TIMEOUT) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(location.latitude, location.longitude, 1)
            }
        }

        val addresses = when (outcome) {
            null -> {
                log(TAG, WARN) { "Timed out after $GEOCODE_TIMEOUT getting location name for $location" }
                null
            }

            else -> outcome.getOrElse { e ->
                log(TAG, WARN) { "Failed to get location name for $location: ${e.asLog()}" }
                null
            }
        }

        addresses?.singleOrNull().also {
            log(TAG) { "toName($location) -> $it" }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun geocode(location: Location): Result<List<Address>?> = suspendCancellableCoroutine { cont ->
        val listener = object : Geocoder.GeocodeListener {
            override fun onGeocode(addresses: MutableList<Address>) {
                cont.resume(Result.success(addresses))
            }

            override fun onError(errorMessage: String?) {
                cont.resume(Result.failure(IOException(errorMessage)))
            }
        }
        try {
            geocoder.getFromLocation(location.latitude, location.longitude, 1, listener)
        } catch (e: Exception) {
            cont.resume(Result.failure(e))
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
        private val GEOCODE_TIMEOUT = 5.seconds
    }
}