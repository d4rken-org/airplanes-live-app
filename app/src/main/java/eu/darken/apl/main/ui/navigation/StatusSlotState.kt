package eu.darken.apl.main.ui.navigation

import eu.darken.apl.server.status.PlannedMaintenance
import eu.darken.apl.server.status.ServerStatus
import java.time.Instant

/** The one message the slot above the navigation bar shows, if any. */
sealed interface StatusSlotState {
    data object Hidden : StatusSlotState
    data object Offline : StatusSlotState
    data class Maintenance(val until: Instant?) : StatusSlotState
    data class Incident(val until: Instant?) : StatusSlotState
    data object ServerProblem : StatusSlotState
    data object Unreachable : StatusSlotState
    data class Planned(val notice: PlannedMaintenance) : StatusSlotState

    companion object {
        /** Any server trouble outranks an announcement. Never [Offline], see [shownWhen]. */
        fun of(serverStatus: ServerStatus, upcoming: PlannedMaintenance?): StatusSlotState = when (serverStatus) {
            is ServerStatus.Maintenance -> Maintenance(serverStatus.until)
            is ServerStatus.Incident -> Incident(serverStatus.until)
            ServerStatus.ServerProblem -> ServerProblem
            ServerStatus.Unreachable -> Unreachable
            ServerStatus.Ok -> upcoming?.let { Planned(it) } ?: Hidden
        }
    }
}

/** Without internet the phone's own connection is the reason, whatever the server said last. */
fun StatusSlotState.shownWhen(isInternetAvailable: Boolean): StatusSlotState =
    if (isInternetAvailable) this else StatusSlotState.Offline

internal val StatusSlotState.until: Instant?
    get() = when (this) {
        is StatusSlotState.Maintenance -> until
        is StatusSlotState.Incident -> until
        else -> null
    }
