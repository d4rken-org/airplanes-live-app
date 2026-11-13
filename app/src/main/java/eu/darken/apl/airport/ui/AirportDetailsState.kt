package eu.darken.apl.airport.ui

import eu.darken.apl.airport.core.Airport
import eu.darken.apl.airport.core.AirportRepo
import eu.darken.apl.airport.core.AirportTarget
import eu.darken.apl.airport.core.resolveAirportLookup
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.navigation.NavigationDestination
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.session.SessionManager
import eu.darken.apl.upgrade.ui.DestinationUpgrade
import eu.darken.apl.watch.core.LocationWatchAvailability
import eu.darken.apl.watch.core.locationWatchAvailability
import eu.darken.apl.watch.ui.DestinationCreateLocationWatch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

private val TAG = logTag("Airport", "Details")

/** What the details of [target] show, [resolved] is the server's airport once a lookup found it. */
data class AirportDetailsState(
    val target: AirportTarget,
    val display: AirportDetailsDisplay,
    val lookingUp: Boolean,
    val resolved: Airport? = null,
) {
    /** The airport as well as it is known. */
    val best: AirportTarget
        get() = resolved?.let { AirportTarget.Full(it) } ?: target

    companion object {
        /** What [target] itself knows, a partial one is still to be looked up, see [lookUpAirportDetails]. */
        fun of(target: AirportTarget) = AirportDetailsState(
            target = target,
            display = AirportDetailsDisplay.from(target),
            lookingUp = target is AirportTarget.Partial,
        )
    }
}

/** Caught here, a failed lookup is no error dialog: the details keep what the target knows. */
suspend fun lookUpAirportDetails(target: AirportTarget.Partial, airportRepo: AirportRepo): AirportDetailsState {
    val match = try {
        resolveAirportLookup(target.ref, airportRepo.search(listOf(target.ref.lookupTerm)))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log(TAG, WARN) { "lookUp(${target.ref.lookupTerm}) failed: ${e.asLog()}" }
        null
    }
    log(TAG) { "lookUp(${target.ref.lookupTerm}) found ${match?.id}" }
    return AirportDetailsState(target, AirportDetailsDisplay.from(target, match), lookingUp = false, resolved = match)
}

fun watchAreaAvailability(accessRepo: AccessRepo, sessionManager: SessionManager): Flow<LocationWatchAvailability> =
    combine(accessRepo.state, sessionManager.state, ::locationWatchAvailability)

fun currentWatchAreaAvailability(accessRepo: AccessRepo, sessionManager: SessionManager): LocationWatchAvailability =
    locationWatchAvailability(accessRepo.state.value, sessionManager.state.value)

/**
 * Where watching the area of [display] leads, null where it leads nowhere. Decide on the policy and session at
 * tap time, the rendered availability may already be stale.
 */
fun watchAreaDestination(availability: LocationWatchAvailability, display: AirportDetailsDisplay?): NavigationDestination? =
    when (availability) {
        LocationWatchAvailability.AVAILABLE -> {
            val latitude = display?.latitude
            val longitude = display?.longitude
            if (latitude != null && longitude != null) {
                log(TAG) { "watchArea(${display.title})" }
                DestinationCreateLocationWatch(latitude, longitude, label = display.title)
            } else {
                null
            }
        }

        LocationWatchAvailability.TIER_EXCLUDED -> {
            log(TAG) { "watchArea(): not part of this tier" }
            DestinationUpgrade
        }

        else -> {
            log(TAG) { "watchArea(): unavailable ($availability)" }
            null
        }
    }
