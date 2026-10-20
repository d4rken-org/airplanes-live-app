package eu.darken.apl.watch.core.server

import eu.darken.apl.watch.core.db.server.WatchServerStateEntity
import eu.darken.apl.watch.core.history.WatchCheck

/** What the server last said about one watch. The monitoring fields are null while it does not hold the watch. */
data class WatchServerStatus(
    val isHeld: Boolean,
    val status: Status?,
    /** Set when [status] is [Status.PAUSED]. */
    val pauseReason: PauseReason?,
    /** The server refused the last upload; for a held watch it keeps evaluating its previous definition. */
    val rejection: Rejection?,
    val presence: Presence?,
    /** The newest match the app applied, the aircraft ids are hex codes. */
    val lastMatch: WatchCheck?,
) {
    enum class Status(val raw: String) {
        ACTIVE("active"),
        PAUSED("paused"),
        ;
    }

    enum class PauseReason(val raw: String) {
        USER("user"),
        LEASE("lease"),
        RESTRICTED("restricted"),
        TIER("tier"),
        OVER_LIMIT("over_limit"),
        ;
    }

    enum class Rejection {
        INVALID,
        STORAGE,
        ;
    }

    enum class Presence(val raw: String) {
        UNKNOWN("unknown"),
        ABSENT("absent"),
        PRESENT("present"),
        ;
    }

    companion object {
        fun from(state: WatchServerStateEntity): WatchServerStatus {
            val isHeld = state.serverWatchId != null
            val matchIds = state.lastMatchAircraftIds?.split(',')?.filter { it.isNotEmpty() }.orEmpty()
            return WatchServerStatus(
                isHeld = isHeld,
                status = Status.entries.firstOrNull { it.raw == state.status }?.takeIf { isHeld },
                pauseReason = PauseReason.entries.firstOrNull { it.raw == state.pauseReason }?.takeIf { isHeld },
                rejection = when (state.uploadState) {
                    WatchServerStateEntity.UPLOAD_REJECTED_INVALID -> Rejection.INVALID
                    WatchServerStateEntity.UPLOAD_REJECTED_STORAGE -> Rejection.STORAGE
                    else -> null
                },
                presence = Presence.entries.firstOrNull { it.raw == state.presence }?.takeIf { isHeld },
                lastMatch = state.lastMatchAt?.takeIf { isHeld }?.let { at ->
                    WatchCheck(
                        watchId = state.watchId,
                        checkAt = at,
                        aircraftCount = state.lastMatchTotal ?: matchIds.size,
                        seenHexes = matchIds.toSet(),
                    )
                },
            )
        }
    }
}
