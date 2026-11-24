package eu.darken.apl.common.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.apl.common.coroutine.AppScope
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.flow.replayingShare
import eu.darken.apl.common.flow.setupCommonEventHandlers
import kotlinx.coroutines.CoroutineScope
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
    private val gateway: LocationProviderGateway,
    private val dispatchers: DispatcherProvider,
) {
    private val executor: Executor = Executors.newSingleThreadExecutor()

    val state = locationStates(gateway, executor)
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