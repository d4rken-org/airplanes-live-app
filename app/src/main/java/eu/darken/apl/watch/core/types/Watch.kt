package eu.darken.apl.watch.core.types

import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.watch.core.WatchId
import eu.darken.apl.watch.core.history.WatchCheck
import java.time.Instant

sealed interface Watch {

    val id: WatchId
    val addedAt: Instant
    val note: String
    val isNotificationEnabled: Boolean

    val lastCheckAt: Instant?
    val lastCheckOutcome: WatchCheckOutcome?
    val lastCheckReason: String?

    fun matches(ac: Aircraft): Boolean

    sealed interface Status {
        val watch: Watch
        val id: WatchId
            get() = watch.id
        val note: String
            get() = watch.note

        val lastCheck: WatchCheck?
        val lastHit: WatchCheck?

        val tracked: Set<Aircraft>
    }
}