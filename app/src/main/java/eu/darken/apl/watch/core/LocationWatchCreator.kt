package eu.darken.apl.watch.core

import android.content.Context
import android.location.Location
import eu.darken.apl.R
import eu.darken.apl.common.ca.toCaString
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.error.HasLocalizedError
import eu.darken.apl.common.error.LocalizedError
import eu.darken.apl.common.location.LocationManager2
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.watch.core.db.types.LocationWatchEntity
import eu.darken.apl.watch.core.types.LocationWatch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** What every editor that creates a location watch has to agree on. */
class LocationWatchCreator @Inject constructor(
    private val watchRepo: WatchRepo,
    private val locationManager2: LocationManager2,
    private val accessRepo: AccessRepo,
) {

    /** An access policy that is not loaded yet denies nothing. */
    val isAllowed: Flow<Boolean> = accessRepo.state.map { it.allowsLocation() }

    /** Beyond this the server rejects the definition, so no input may offer it. */
    val maxRadiusKm: Flow<Int> = accessRepo.state.map { it.maxRadiusKm() }

    fun isAllowedNow(): Boolean = accessRepo.state.value.allowsLocation()

    /** "Aachen, DE", or the coordinates where the place has no name. */
    suspend fun labelFor(latitude: Double, longitude: Double): String {
        val location = Location("watch").apply {
            this.latitude = latitude
            this.longitude = longitude
        }
        val address = locationManager2.toName(location)
        return address?.let { listOfNotNull(it.locality, it.countryCode).joinToString(", ").ifBlank { null } }
            ?: LocationWatch.formatCoordinates(latitude, longitude)
    }

    suspend fun create(
        latitude: Double,
        longitude: Double,
        radiusInMeters: Float,
        label: String,
        note: String,
    ): LocationWatch {
        log(TAG) { "create($latitude, $longitude, $radiusInMeters, $label, $note)" }
        // The editor checked when it was shown, the policy may have changed since
        val access = accessRepo.state.value
        if (!access.allowsLocation()) throw LocationWatchNotAllowedException()
        val maxRadiusKm = access.maxRadiusKm()
        if (!LocationWatch.radiusSaveAllowed(radiusInMeters, storedMeters = null, maxRadiusKm)) {
            throw LocationRadiusException(radiusInMeters, maxRadiusKm)
        }
        return watchRepo.createLocation(latitude, longitude, radiusInMeters, label.trim(), note.trim())
    }

    private fun AccessState?.allowsLocation(): Boolean = this == null || allowsWatchType(LocationWatchEntity.TYPE_KEY)

    private fun AccessState?.maxRadiusKm(): Int = this?.maxLocationWatchRadiusKm ?: LocationWatch.DEFAULT_MAX_RADIUS_KM

    companion object {
        private val TAG = logTag("Watch", "Location", "Creator")
    }
}

class LocationWatchNotAllowedException : IllegalStateException("Location watches are not part of this tier"),
    HasLocalizedError {

    override fun getLocalizedError(context: Context) = LocalizedError(
        throwable = this,
        label = R.string.watch_create_feeder_required.toCaString(),
        description = R.string.watch_location_not_allowed_msg.toCaString(),
    )
}
