package eu.darken.apl.feeder.core

import eu.darken.apl.feeder.core.config.FeederConfig
import eu.darken.apl.feeder.core.server.FeederProblemCategory
import eu.darken.apl.feeder.core.server.FeederServerView
import eu.darken.apl.feeder.core.server.problemCategories
import java.time.Instant

data class Feeder(
    val config: FeederConfig,
    /** Null when the server does not monitor feeders for this installation. */
    val server: FeederServerView? = null,
    /** Problems notified for this feeder: shared by all local variants of its id, so not always [config]'s own. */
    val problemCategories: Set<FeederProblemCategory> = config.problemCategories,
) {

    val label: String
        get() = config.label ?: config.receiverId.takeLast(5)

    val lastSeen: Instant?
        get() = server?.entry?.lastConnectedAt?.let { Instant.ofEpochMilli(it) }

    val id: ReceiverId
        get() = config.receiverId

    val beastMessageRate: Double
        get() = messageRate ?: 0.0

    /** Null when unknown. */
    val messageRate: Double?
        get() = server?.entry?.latest?.msgsPerSecond

    val bandwidth: Double?
        get() = server?.entry?.latest?.avgKbitPerSecond

    /** Connection round-trip time in milliseconds, null when unknown. */
    val latency: Long?
        get() = server?.entry?.latest?.rttMs

    val hasMlat: Boolean
        get() {
            val latest = server?.entry?.latest ?: return false
            return (latest.mlatClients ?: 0) > 0 ||
                    latest.mlatMessageRate != null ||
                    latest.mlatPeers != null ||
                    latest.mlatOutlierPercent != null
        }

    val mlatMessageRate: Double?
        get() = server?.entry?.latest?.mlatMessageRate

    val mlatPeers: Int?
        get() = server?.entry?.latest?.mlatPeers

    val mlatOutlierPercent: Double?
        get() = server?.entry?.latest?.mlatOutlierPercent

}
