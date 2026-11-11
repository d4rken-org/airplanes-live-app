package eu.darken.apl.watch.core

import eu.darken.apl.server.access.AccessState
import eu.darken.apl.server.session.SessionState
import eu.darken.apl.watch.core.db.types.LocationWatchEntity

enum class LocationWatchAvailability {
    AVAILABLE,
    TIER_EXCLUDED,
    NOT_LOADED,
    RESTRICTED,
    SESSION_UNUSABLE,
    REVOKED,
}

fun locationWatchAvailability(access: AccessState?, session: SessionState): LocationWatchAvailability {
    // The session goes first: a cached policy that still allows location watches can outlive a revoked session
    if (session is SessionState.Revoked) return LocationWatchAvailability.REVOKED
    if (session !is SessionState.Active) return LocationWatchAvailability.SESSION_UNUSABLE
    if (access?.restricted == true) return LocationWatchAvailability.RESTRICTED
    if (access == null || access.installationId != session.installationId) return LocationWatchAvailability.NOT_LOADED
    if (!access.allowsWatchType(LocationWatchEntity.TYPE_KEY)) return LocationWatchAvailability.TIER_EXCLUDED
    return LocationWatchAvailability.AVAILABLE
}
