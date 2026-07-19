package eu.darken.apl.feeder.core.monitor

import eu.darken.apl.feeder.core.FeederStatus
import eu.darken.apl.feeder.core.stats.HealthMetric
import eu.darken.apl.feeder.core.stats.HealthSeverity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Monitor-internal bookkeeping, written only by [FeederMonitor]. Deliberately NOT part of
 * FeederCache, which is wholesale-replaced on every refresh — this state must survive those.
 */
@Serializable
data class FeederMonitorState(
    @SerialName("feeders") val feeders: Map<String, FeederTransitionState> = emptyMap(),
)

@Serializable
data class FeederTransitionState(
    @SerialName("lastStatus") val lastStatus: FeederStatus = FeederStatus.UNKNOWN,
    /** The feeder is in an outage — truth about the feeder, tracked even while muted. */
    @SerialName("outageActive") val outageActive: Boolean = false,
    /** An offline notification was actually shown for the current outage (false while muted). */
    @SerialName("offlineNotificationPosted") val offlineNotificationPosted: Boolean = false,
    /** Last evaluated non-OK device-health metrics; empty = healthy or never evaluated. */
    @SerialName("healthWarnings") val healthWarnings: Map<HealthMetric, HealthSeverity> = emptyMap(),
)
