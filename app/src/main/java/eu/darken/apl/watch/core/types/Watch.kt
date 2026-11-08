package eu.darken.apl.watch.core.types

import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.watch.core.WatchId
import eu.darken.apl.watch.core.history.WatchCheck
import eu.darken.apl.watch.core.server.WatchServerStatus
import java.time.Instant

sealed interface Watch {

    val id: WatchId
    val addedAt: Instant
    val note: String
    val isNotificationEnabled: Boolean

    /** Changes whenever what the watch matches changes, and never returns to an earlier value. */
    val definitionRevision: Long

    val localGeneration: Long

    sealed interface Status {
        val watch: Watch
        val id: WatchId
            get() = watch.id
        val note: String
            get() = watch.note

        /** The server's last match, only for a watch it holds at the current definition. */
        val lastHit: WatchCheck?

        val tracked: Set<Aircraft>

        /** Null outside server mode and for a watch the server was never told about. */
        val server: WatchServerStatus?
    }
}