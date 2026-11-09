package eu.darken.apl.feeder.core.server

import eu.darken.apl.feeder.core.config.FeederConfig

/** A monitor the server should hold for a local feeder, [feederId] in its canonical form. */
data class DesiredFeederMonitor(
    val feederId: String,
    val offlineAfterMinutes: Int,
) {
    companion object {
        const val MIN_OFFLINE_AFTER_MINUTES = 15
        const val MAX_OFFLINE_AFTER_MINUTES = 2880

        /**
         * In registration order: the server activates the first monitors up to its cap. Of case variants
         * of one id the earliest added feeder counts. A feeder with offline alerts off is monitored too,
         * with the longest threshold.
         */
        fun from(configs: Collection<FeederConfig>): List<DesiredFeederMonitor> = configs
            .mapNotNull { config -> config.receiverId.toFeederMonitorId()?.let { config to it } }
            .sortedWith(compareBy({ it.first.addedAt }, { it.second }))
            .distinctBy { it.second }
            .map { (config, feederId) ->
                DesiredFeederMonitor(
                    feederId = feederId,
                    offlineAfterMinutes = config.offlineCheckTimeout
                        ?.toMinutes()
                        ?.coerceIn(MIN_OFFLINE_AFTER_MINUTES.toLong(), MAX_OFFLINE_AFTER_MINUTES.toLong())
                        ?.toInt()
                        ?: MAX_OFFLINE_AFTER_MINUTES,
                )
            }
    }
}
