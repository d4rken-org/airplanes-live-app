package eu.darken.apl.feeder.core

import dagger.Reusable
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.feeder.core.api.FeederEndpoint
import eu.darken.apl.feeder.ui.add.DetectedFeeder
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Finds the feeders that report from the caller's own network via the public feed status. */
@Reusable
class FeederDiscovery @Inject constructor(
    private val feederEndpoint: FeederEndpoint,
    private val feederRepo: FeederRepo,
) {

    /** [host] is the address the feeders were looked for on, which is worth showing when none were. */
    data class Detection(
        val host: String?,
        val feeders: List<DetectedFeeder>,
    )

    /** Which monitored feeders this network could register, and the address that was asked about. */
    data class Registerable(
        val host: String?,
        val ids: Set<ReceiverId>,
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

    /**
     * Registration only succeeds for a feeder on the caller's network, so a feeder is offered for
     * registration only while this network is the one it reports from.
     *
     * Costs nothing while no feeder is monitored: there is nothing an answer could be about.
     */
    suspend fun findRegisterable(): Registerable {
        val monitored = feederRepo.feeders.first().map { it.id }
        if (monitored.isEmpty()) {
            log(TAG) { "findRegisterable(): no monitored feeders" }
            return Registerable(host = null, ids = emptySet())
        }

        val detection = scan()
        val detected = detection.feeders.map { it.uuid.toString() }.toSet()
        // A monitored id is whatever was typed or scanned into it, so it is matched, not trusted
        val ids = monitored.filter { monitoredId ->
            detected.any { it.equals(monitoredId, ignoreCase = true) }
        }.toSet()
        log(TAG) { "findRegisterable(): ${ids.size} of ${monitored.size} monitored feeders on ${detection.host}" }
        return Registerable(host = detection.host, ids = ids)
    }

    companion object {
        private val TAG = logTag("Feeder", "Discovery")
    }
}
