package eu.darken.apl.ar.ui

import eu.darken.apl.main.core.AircraftRepo.ViewingState.Reason
import eu.darken.apl.map.ui.ViewingMessage
import eu.darken.apl.map.ui.viewingMessage
import eu.darken.apl.server.status.ServerStatus

/** AR has no navigation bar to show the server's status, its overlay always names it. */
internal fun arViewingMessage(waiting: Reason?, stale: Boolean, serverStatus: ServerStatus): ViewingMessage? =
    viewingMessage(waiting, stale, serverStatus, barVisible = false)
