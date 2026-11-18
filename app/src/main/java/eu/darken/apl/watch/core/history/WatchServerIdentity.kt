package eu.darken.apl.watch.core.history

import eu.darken.apl.watch.core.WatchId

/** The server watch that evaluates a local watch's current definition, for the installation that holds it. */
data class WatchServerIdentity(
    val localId: WatchId,
    val serverWatchId: String,
    val revision: Long,
    val installationId: String,
)
