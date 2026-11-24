package eu.darken.apl.common.location

import android.location.LocationManager
import android.os.Build

internal data class LocationProviders(
    val lastKnown: List<String>,
    val continuous: List<String>,
    val oneShot: List<String>,
    val current: List<String>,
)

// Existing rather than enabled providers: a registration on a disabled provider starts delivering once it is enabled.
internal fun selectLocationProviders(
    sdkInt: Int,
    hasFineLocation: Boolean,
    existingProviders: Set<String>,
): LocationProviders {
    val network = LocationManager.NETWORK_PROVIDER.takeIf { it in existingProviders }
    val fused = FUSED_PROVIDER.takeIf { sdkInt >= Build.VERSION_CODES.S && it in existingProviders }
    // Below API 31 the GPS provider throws SecurityException without fine permission, from 31 it coarsens the fix.
    val gps = LocationManager.GPS_PROVIDER.takeIf {
        it in existingProviders && (sdkInt >= Build.VERSION_CODES.S || hasFineLocation)
    }
    return LocationProviders(
        lastKnown = listOfNotNull(network, fused, gps),
        continuous = listOfNotNull(network, fused),
        oneShot = listOfNotNull(gps),
        current = listOfNotNull(network, fused, gps),
    )
}

// LocationManager.FUSED_PROVIDER is only public from API 31.
private const val FUSED_PROVIDER = "fused"
