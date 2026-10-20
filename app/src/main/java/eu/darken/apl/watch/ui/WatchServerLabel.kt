package eu.darken.apl.watch.ui

import androidx.annotation.StringRes
import eu.darken.apl.R
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.watch.core.server.WatchServerStatus
import eu.darken.apl.watch.core.types.Watch

/** Why the server's watch list does not check a watch. */
enum class WatchServerLabel(@StringRes val textRes: Int) {
    NOT_CHECKED_OVER_LIMIT(R.string.watch_server_not_checked_over_limit),
    NOT_CHECKED_LEASE(R.string.watch_server_not_checked_lease),
    NOT_CHECKED_RESTRICTED(R.string.watch_server_not_checked_restricted),
    NOT_CHECKED_TIER(R.string.watch_server_not_checked_tier),
    NOT_CHECKED(R.string.watch_server_not_checked),
    REJECTED_INVALID(R.string.watch_server_rejected_invalid),
    REJECTED_STORAGE(R.string.watch_server_rejected_storage),
    ;

    companion object {
        /**
         * Null outside server mode and for a checked watch. A refusal is only shown while the server does
         * not hold the watch, a held one goes on with its previous definition.
         */
        fun of(status: Watch.Status, isServerMode: Boolean): WatchServerLabel? {
            if (!isServerMode) return null
            val server = status.server
            if (server?.isHeld == true && server.status == WatchServerStatus.Status.PAUSED) {
                return when (server.pauseReason) {
                    WatchServerStatus.PauseReason.OVER_LIMIT -> NOT_CHECKED_OVER_LIMIT
                    WatchServerStatus.PauseReason.LEASE -> NOT_CHECKED_LEASE
                    WatchServerStatus.PauseReason.RESTRICTED -> NOT_CHECKED_RESTRICTED
                    WatchServerStatus.PauseReason.TIER -> NOT_CHECKED_TIER
                    WatchServerStatus.PauseReason.USER, null -> NOT_CHECKED
                }
            }
            if (server?.isHeld != true) {
                when (server?.rejection) {
                    WatchServerStatus.Rejection.INVALID -> return REJECTED_INVALID
                    WatchServerStatus.Rejection.STORAGE -> return REJECTED_STORAGE
                    null -> {}
                }
            }
            return null
        }
    }
}

/** The list's banner while the server does not check some watches because they are over the active limit. */
data class OverLimitBanner(
    @StringRes val messageRes: Int,
    /** The active limit [messageRes] names, null when it names none. */
    val count: Int? = null,
) {
    companion object {
        /** Null while no watch is over the limit. A Feeder is not shown a number. */
        fun of(isServerMode: Boolean, access: AccessState?, statuses: Collection<Watch.Status>): OverLimitBanner? {
            if (!isServerMode) return null
            if (statuses.none { it.server?.pauseReason == WatchServerStatus.PauseReason.OVER_LIMIT }) return null
            val shown = access?.takeIf { it.tier == AccessState.Tier.FREE }?.limits?.maxActiveWatches
            return if (shown != null) {
                OverLimitBanner(R.string.watch_over_limit_banner_msg, shown)
            } else {
                OverLimitBanner(R.string.watch_over_limit_banner_unnumbered_msg)
            }
        }
    }
}

/** A watch the server holds is not described by the app's own checks, also not by ones from before. */
val Watch.Status.isServerHeld: Boolean
    get() = server?.isHeld == true

/** For a held watch the server's presence decides, otherwise the tracked aircraft heard from recently. */
fun Watch.Status.liveCountShown(liveCount: Int): Int {
    val server = server?.takeIf { it.isHeld } ?: return liveCount
    if (server.presence != WatchServerStatus.Presence.PRESENT) return 0
    return maxOf(liveCount, server.lastMatch?.aircraftCount ?: 0, 1)
}
