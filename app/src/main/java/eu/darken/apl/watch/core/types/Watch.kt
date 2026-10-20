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

    val lastCheckAt: Instant?
    val lastCheckOutcome: WatchCheckOutcome?
    val lastCheckReason: String?

    /** Changes whenever what the watch matches changes, and never returns to an earlier value. */
    val definitionRevision: Long

    val localGeneration: Long

    fun matches(ac: Aircraft): Boolean

    sealed interface Status {
        val watch: Watch
        val id: WatchId
            get() = watch.id
        val note: String
            get() = watch.note

        /** Null for a watch the server holds, the app's own last check is not current for it. */
        val lastCheck: WatchCheck?
        val lastHit: WatchCheck?

        val tracked: Set<Aircraft>

        /** Null outside server mode and for a watch the server was never told about. */
        val server: WatchServerStatus?
    }
}