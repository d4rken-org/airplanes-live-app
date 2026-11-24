package eu.darken.apl.common.location

import android.location.Location
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationRequestCompat
import kotlinx.coroutines.DisposableHandle
import java.util.concurrent.Executor

internal class FakeLocationProviderGateway(
    override val sdkInt: Int = 36,
    var coarse: Boolean = true,
    var fine: Boolean = false,
    var providers: Set<String> = setOf("network", "fused", "gps"),
) : LocationProviderGateway {
    val lastKnownFixes = mutableMapOf<String, Location?>()
    val lastKnownErrors = mutableMapOf<String, Exception>()
    val registerErrors = mutableMapOf<String, Exception>()
    val removeErrors = mutableMapOf<String, Exception>()
    val oneShotErrors = mutableMapOf<String, Exception>()
    val cancelErrors = mutableMapOf<String, Exception>()

    val listeners = mutableMapOf<String, LocationListenerCompat>()
    val removed = mutableListOf<String>()
    val oneShotRequests = mutableListOf<String>()
    val oneShotConsumers = mutableMapOf<String, (Location?) -> Unit>()
    val cancelledOneShots = mutableListOf<String>()

    override fun hasCoarsePermission(): Boolean = coarse

    override fun hasFinePermission(): Boolean = fine

    override fun existingProviders(): Set<String> = providers

    override fun lastKnown(provider: String): Location? {
        lastKnownErrors[provider]?.let { throw it }
        return lastKnownFixes[provider]
    }

    override fun requestUpdates(
        provider: String,
        request: LocationRequestCompat,
        executor: Executor,
        listener: LocationListenerCompat,
    ) {
        registerErrors[provider]?.let { throw it }
        listeners[provider] = listener
    }

    override fun removeUpdates(listener: LocationListenerCompat) {
        val provider = listeners.entries.single { it.value === listener }.key
        removed.add(provider)
        removeErrors[provider]?.let { throw it }
    }

    override fun currentLocation(
        provider: String,
        executor: Executor,
        consumer: (Location?) -> Unit,
    ): DisposableHandle {
        oneShotErrors[provider]?.let { throw it }
        oneShotRequests.add(provider)
        oneShotConsumers[provider] = consumer
        return DisposableHandle {
            cancelledOneShots.add(provider)
            cancelErrors[provider]?.let { throw it }
        }
    }

    fun deliver(provider: String, location: Location) = listeners.getValue(provider).onLocationChanged(location)

    fun answer(provider: String, location: Location?) = oneShotConsumers.getValue(provider).invoke(location)
}
