package eu.darken.apl.feeder.ui.preview

import eu.darken.apl.feeder.core.Feeder
import eu.darken.apl.feeder.core.ReceiverId
import eu.darken.apl.feeder.core.config.FeederConfig
import eu.darken.apl.feeder.core.server.FeederServerView
import eu.darken.apl.server.api.FeederMonitorEntry
import eu.darken.apl.server.api.FeederMonitorLatest
import java.time.Instant

/** Without server monitoring. */
fun mockFeeder(
    label: String = "Home Feeder",
    id: ReceiverId = "abc12",
) = Feeder(config = FeederConfig.newFeeder(id).copy(label = label))

/** A feeder the server monitors and reports [online] or offline since [lastConnectedAt]. */
fun mockMonitoredFeeder(
    label: String = "Home Feeder",
    id: ReceiverId = "abc12",
    online: Boolean = true,
    lastConnectedAt: Instant = Instant.now().minusSeconds(60),
    latest: FeederMonitorLatest = FeederMonitorLatest(
        beastClients = 1,
        msgsPerSecond = 612.4,
        avgKbitPerSecond = 93.8,
        mlatClients = 1,
        mlatMessageRate = 18.6,
        mlatPeers = 27,
        mlatOutlierPercent = 1.3,
    ),
): Feeder {
    val at = lastConnectedAt.toEpochMilli()
    val entry = FeederMonitorEntry(
        feederId = id,
        monitorId = "monitor-$id",
        offlineAfterMinutes = FeederConfig.DEFAULT_OFFLINE_LIMIT.toMinutes().toInt(),
        status = "active",
        createdAt = at,
        updatedAt = at,
        health = if (online) "online" else "offline",
        healthSince = at,
        lastSampleAt = at,
        lastSuccessAt = at,
        lastConnectedAt = at,
        latest = latest,
        eventSeq = 0,
    )
    return mockFeeder(label = label, id = id).copy(server = FeederServerView(feederId = id, entry = entry))
}
