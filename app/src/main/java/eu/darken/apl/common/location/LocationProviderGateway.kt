package eu.darken.apl.common.location

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import eu.darken.apl.common.BuildWrap
import eu.darken.apl.common.permissions.Permission
import kotlinx.coroutines.DisposableHandle
import java.util.concurrent.Executor
import javax.inject.Inject

interface LocationProviderGateway {
    val sdkInt: Int

    fun elapsedRealtimeNanos(): Long

    fun hasCoarsePermission(): Boolean

    fun hasFinePermission(): Boolean

    fun existingProviders(): Set<String>

    fun lastKnown(provider: String): Location?

    fun requestUpdates(
        provider: String,
        request: LocationRequestCompat,
        executor: Executor,
        listener: LocationListenerCompat,
    )

    fun removeUpdates(listener: LocationListenerCompat)

    /**
     * [consumer] receives null when the provider is disabled or no fix arrives within the platform timeout.
     * Disposing the returned handle cancels the request.
     */
    fun currentLocation(
        provider: String,
        executor: Executor,
        consumer: (Location?) -> Unit,
    ): DisposableHandle
}

class PlatformLocationProviderGateway @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val locationManager: LocationManager,
) : LocationProviderGateway {

    override val sdkInt: Int
        get() = BuildWrap.VERSION.SDK_INT

    override fun elapsedRealtimeNanos(): Long = SystemClock.elapsedRealtimeNanos()

    override fun hasCoarsePermission(): Boolean = Permission.ACCESS_COARSE_LOCATION.isGranted(context)

    override fun hasFinePermission(): Boolean = Permission.ACCESS_FINE_LOCATION.isGranted(context)

    override fun existingProviders(): Set<String> = locationManager.allProviders.toSet()

    @Suppress("MissingPermission")
    override fun lastKnown(provider: String): Location? = locationManager.getLastKnownLocation(provider)

    @Suppress("MissingPermission")
    override fun requestUpdates(
        provider: String,
        request: LocationRequestCompat,
        executor: Executor,
        listener: LocationListenerCompat,
    ) {
        LocationManagerCompat.requestLocationUpdates(locationManager, provider, request, executor, listener)
    }

    override fun removeUpdates(listener: LocationListenerCompat) {
        LocationManagerCompat.removeUpdates(locationManager, listener)
    }

    @Suppress("MissingPermission")
    override fun currentLocation(
        provider: String,
        executor: Executor,
        consumer: (Location?) -> Unit,
    ): DisposableHandle {
        val signal = CancellationSignal()
        LocationManagerCompat.getCurrentLocation(locationManager, provider, signal, executor) { location: Location? ->
            consumer(location)
        }
        return DisposableHandle { signal.cancel() }
    }
}

@InstallIn(SingletonComponent::class)
@Module
abstract class LocationModule {
    @Binds
    abstract fun locationProviderGateway(gateway: PlatformLocationProviderGateway): LocationProviderGateway
}
