package eu.darken.apl.feeder.core

import eu.darken.apl.feeder.core.config.FeederConfig
import eu.darken.apl.feeder.core.stats.BeastStatsEntity
import eu.darken.apl.feeder.core.stats.MlatStatsEntity
import java.time.Instant

data class Feeder(
    val config: FeederConfig,
    val beastStats: BeastStatsEntity? = null,
    val mlatStats: MlatStatsEntity? = null,
    val observation: FeederObservation? = null,
) {

    val label: String
        get() = config.label ?: config.receiverId.takeLast(5)

    val lastSeen: Instant?
        get() = if (observation != null) observation.lastFeedingAt else beastStats?.receivedAt

    val id: ReceiverId
        get() = config.receiverId

    val beastMessageRate: Double
        get() = beastStats?.messageRate ?: 0.0

}
