package eu.darken.apl.watch.core.types

import android.location.Location
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.watch.core.WatchId
import eu.darken.apl.watch.core.db.types.BaseWatchEntity
import eu.darken.apl.watch.core.db.types.LocationWatchEntity
import eu.darken.apl.watch.core.history.WatchCheck
import eu.darken.apl.watch.core.server.WatchServerStatus
import java.time.Instant
import java.util.Locale

data class LocationWatch(
    private val base: BaseWatchEntity,
    private val specific: LocationWatchEntity,
) : Watch {
    override val id: WatchId
        get() = base.id
    override val addedAt: Instant
        get() = base.createdAt
    override val note: String
        get() = base.userNote
    override val isNotificationEnabled: Boolean
        get() = base.notificationEnabled

    override val lastCheckAt: Instant?
        get() = base.lastCheckAt
    override val lastCheckOutcome: WatchCheckOutcome?
        get() = WatchCheckOutcome.fromRaw(base.lastCheckOutcome)
    override val lastCheckReason: String?
        get() = base.lastCheckReason
    override val definitionRevision: Long
        get() = base.definitionRevision
    override val localGeneration: Long
        get() = base.localGeneration

    val label: String
        get() = specific.label

    val latitude: Double
        get() = base.latitude!!

    val longitude: Double
        get() = base.longitude!!

    val radiusInMeters: Float
        get() = base.radius!!

    val center: Location
        get() = Location("watch").apply {
            latitude = this@LocationWatch.latitude
            longitude = this@LocationWatch.longitude
        }

    override fun matches(ac: Aircraft): Boolean {
        val acLocation = ac.location ?: return false
        return center.distanceTo(acLocation) <= radiusInMeters
    }

    data class Status(
        override val watch: LocationWatch,
        override val lastCheck: WatchCheck?,
        override val lastHit: WatchCheck?,
        override val tracked: Set<Aircraft> = emptySet(),
        override val server: WatchServerStatus? = null,
    ) : Watch.Status {
        val label: String
            get() = watch.label
        val radiusInMeters: Float
            get() = watch.radiusInMeters
    }

    companion object {
        /** The radius limit when the server has not stated one. */
        const val DEFAULT_MAX_RADIUS_KM = 250
        const val MIN_RADIUS_KM = 2

        /** A new watch starts at the radius stop nearest to this. */
        const val DEFAULT_RADIUS_METERS = 25_000f

        /** Within the allowed range, give or take what converting a radius between units rounds off. */
        fun radiusAllowed(meters: Float, maxRadiusKm: Int): Boolean =
            meters >= MIN_RADIUS_KM * 1000f - RADIUS_TOLERANCE_METERS && meters <= maxRadiusKm * 1000f + RADIUS_TOLERANCE_METERS

        /** A radius already stored may be one an older, higher limit allowed. */
        fun radiusSaveAllowed(meters: Float, storedMeters: Float?, maxRadiusKm: Int): Boolean =
            meters == storedMeters || radiusAllowed(meters, maxRadiusKm)

        private const val RADIUS_TOLERANCE_METERS = 0.5f

        /** Locale independent, a decimal comma would make "50,81, 6,07". */
        fun formatCoordinates(latitude: Double, longitude: Double, decimals: Int = 2): String =
            "%.${decimals}f, %.${decimals}f".format(Locale.ROOT, latitude, longitude)
    }
}
