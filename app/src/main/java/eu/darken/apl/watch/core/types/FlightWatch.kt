package eu.darken.apl.watch.core.types

import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.aircraft.Callsign
import eu.darken.apl.watch.core.WatchId
import eu.darken.apl.watch.core.db.types.BaseWatchEntity
import eu.darken.apl.watch.core.db.types.FlightWatchEntity
import eu.darken.apl.watch.core.history.WatchCheck
import java.time.Instant


data class FlightWatch(
    private val base: BaseWatchEntity,
    private val specific: FlightWatchEntity,
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

    val callsign: Callsign
        get() = specific.callsign

    override fun matches(ac: Aircraft): Boolean {
        return ac.callsign?.uppercase() == callsign.uppercase()
    }

    data class Status(
        override val watch: FlightWatch,
        override val lastCheck: WatchCheck?,
        override val lastHit: WatchCheck?,
        override val tracked: Set<Aircraft> = emptySet(),
    ) : Watch.Status {

        val callsign: Callsign
            get() = watch.callsign
    }
}