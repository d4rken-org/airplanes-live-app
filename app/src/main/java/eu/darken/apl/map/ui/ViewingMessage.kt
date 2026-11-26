package eu.darken.apl.map.ui

import eu.darken.apl.main.core.AircraftRepo.ViewingState.Reason
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.status.ServerStatus
import java.time.Instant

/** What the map's or AR's overlay says about the aircraft data, see [viewingMessage]. */
sealed interface ViewingMessage {
    data class AllowanceUsedUp(val resetsAt: Instant) : ViewingMessage

    /** No aircraft data, for a reason shown elsewhere or not at all. */
    data object Unavailable : ViewingMessage

    /** Positions are aging and the server gave no reason. */
    data object ConnectionProblem : ViewingMessage

    /** Positions are aging, the bar above the navigation says why. */
    data object PositionsOutdated : ViewingMessage

    /** Aircraft data is missing or aging because of [status], never [ServerStatus.Ok]. */
    data class Server(val status: ServerStatus) : ViewingMessage
}

/**
 * Null while nothing needs saying. [waiting] is why no answers arrive, [stale] that the last poll failed
 * and the positions are aging. While [barVisible] the bar above the navigation shows [serverStatus],
 * the overlay only says what it means for the data.
 *
 * | waiting / stale       | server Ok         | not Ok, bar visible | not Ok, no bar |
 * |-----------------------|-------------------|---------------------|----------------|
 * | Offline               | Unavailable       | Unavailable         | Server         |
 * | AccessUnknown         | null              | Unavailable         | Server         |
 * | stale                 | ConnectionProblem | PositionsOutdated   | Server         |
 */
fun viewingMessage(
    waiting: Reason?,
    stale: Boolean,
    serverStatus: ServerStatus,
    barVisible: Boolean,
): ViewingMessage? {
    when (waiting) {
        is Reason.Exhausted -> return ViewingMessage.AllowanceUsedUp(waiting.resetsAt)
        Reason.Restricted, Reason.Revoked -> return ViewingMessage.Unavailable
        // The update notice explains it
        Reason.UpdateRequired -> return null
        Reason.Offline, Reason.AccessUnknown, null -> Unit
    }
    val noData = waiting != null
    if (!noData && !stale) return null
    return when {
        serverStatus == ServerStatus.Ok -> when (waiting) {
            Reason.Offline -> ViewingMessage.Unavailable
            Reason.AccessUnknown -> null
            else -> ViewingMessage.ConnectionProblem
        }

        !barVisible -> ViewingMessage.Server(serverStatus)
        noData -> ViewingMessage.Unavailable
        else -> ViewingMessage.PositionsOutdated
    }
}

/** A rate limit is retried within seconds, any other failure leaves the positions aging. */
fun isStaleAfter(error: Throwable?): Boolean =
    error != null && !(error is ServerApiException && error.status == 429)
