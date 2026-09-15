package eu.darken.apl.feeder.core

import dagger.Reusable
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.feeder.core.api.FeederEndpoint
import eu.darken.apl.feeder.ui.add.DetectedFeeder
import javax.inject.Inject

/** Finds the feeders that report from the caller's own network via the public feed status. */
@Reusable
class FeederDiscovery @Inject constructor(
    private val feederEndpoint: FeederEndpoint,
) {

    /** [host] is the address the feeders were looked for on, which is worth showing when none were. */
    data class Detection(
        val host: String?,
        val feeders: List<DetectedFeeder>,
    )

    suspend fun scan(): Detection {
        val feedStatus = feederEndpoint.getFeedStatus()
        log(TAG) { "scan(): got feed status" }

        val mlatByUuid = feedStatus.mlatClients.associateBy { it.uuid }
        val feeders = feedStatus.beastClients.map { beastClient ->
            val mlatClient = mlatByUuid[beastClient.uuid]
            DetectedFeeder(
                uuid = beastClient.uuid,
                host = beastClient.host,
                label = mlatClient?.user?.takeIf { it.isNotBlank() },
                latitude = mlatClient?.latitude,
                longitude = mlatClient?.longitude,
            )
        }
        return Detection(host = feedStatus.host, feeders = feeders)
    }

    companion object {
        private val TAG = logTag("Feeder", "Discovery")
    }
}
