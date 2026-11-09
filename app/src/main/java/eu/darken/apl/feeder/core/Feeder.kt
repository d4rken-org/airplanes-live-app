package eu.darken.apl.feeder.core

import eu.darken.apl.feeder.core.config.FeederConfig
import eu.darken.apl.feeder.core.server.FeederServerView
import eu.darken.apl.feeder.core.stats.BeastStatsEntity
import eu.darken.apl.feeder.core.stats.MlatStatsEntity
import java.time.Instant

data class Feeder(
    val config: FeederConfig,
    val beastStats: BeastStatsEntity? = null,
    val mlatStats: MlatStatsEntity? = null,
    val observation: FeederObservation? = null,
    /** Null when the app polls the feeders itself. */
    val server: FeederServerView? = null,
) {

    val label: String
        get() = config.label ?: config.receiverId.takeLast(5)

    val lastSeen: Instant?
        get() = when {
            server != null -> server.entry?.lastConnectedAt?.let { Instant.ofEpochMilli(it) }
            observation != null -> observation.lastFeedingAt
            else -> beastStats?.receivedAt
        }

    val id: ReceiverId
        get() = config.receiverId

    val beastMessageRate: Double
        get() = messageRate ?: 0.0

    /** Null when unknown. */
    val messageRate: Double?
        get() = if (server != null) server.entry?.latest?.msgsPerSecond else beastStats?.messageRate

    val bandwidth: Double?
        get() = if (server != null) server.entry?.latest?.avgKbitPerSecond else beastStats?.bandwidth

    val hasMlat: Boolean
        get() {
            if (server == null) return mlatStats != null
            val latest = server.entry?.latest ?: return false
            return (latest.mlatClients ?: 0) > 0 ||
                    latest.mlatMessageRate != null ||
                    latest.mlatPeers != null ||
                    latest.mlatOutlierPercent != null
        }

    val mlatMessageRate: Double?
        get() = if (server != null) server.entry?.latest?.mlatMessageRate else mlatStats?.messageRate

    val mlatPeers: Int?
        get() = if (server != null) server.entry?.latest?.mlatPeers else mlatStats?.peerCount

    val mlatOutlierPercent: Double?
        get() = if (server != null) server.entry?.latest?.mlatOutlierPercent else mlatStats?.outlierPercent?.toDouble()

}
