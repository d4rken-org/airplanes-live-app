package eu.darken.apl.server.access

import eu.darken.apl.server.api.AccessResponse
import eu.darken.apl.server.api.RequestLimits
import eu.darken.apl.server.api.TierPolicy
import eu.darken.apl.server.api.Usage
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.time.Duration
import java.time.Instant

@Serializable
data class AccessState(
    val installationId: String,
    @Contextual val fetchedAt: Instant,
    val tier: Tier,
    val restricted: Boolean,
    val allowanceScope: String,
    val limits: TierPolicy,
    val usage: Usage,
    val installationRequests: RequestLimits,
    val entitlementRequests: RequestLimits? = null,
    /**
     * The period each bucket's counter was last measured in, keyed by bucket name. Not persisted:
     * what is written to disk is one whole policy, whose buckets all share [Usage.resetsAt], so an
     * empty map restores exactly that.
     */
    @Transient val bucketPeriods: Map<String, Long> = emptyMap(),
) {

    enum class Tier {
        FREE,
        FEEDER,
        ;
    }

    /**
     * Whether the daily allowances are worth putting in front of the user outside the access screen.
     * Running out is still reported per search term and per watch either way.
     */
    val showsAllowances: Boolean
        get() = tier == Tier.FREE

    val viewingInterval: Duration
        get() = Duration.ofSeconds(limits.viewingIntervalSeconds.toLong())

    val maxArRadiusNm: Int
        get() = limits.maxRadiusNm

    val watchTypes: Set<String>
        get() = limits.watchTypes.toSet()

    fun allowsWatchType(type: String): Boolean = type in watchTypes

    val maxLocationWatchRadiusKm: Int?
        get() = limits.maxLocationWatchRadiusKm

    val resetsAt: Instant
        get() = Instant.ofEpochMilli(usage.resetsAt)

    companion object {
        fun from(response: AccessResponse, installationId: String, fetchedAt: Instant) = AccessState(
            installationId = installationId,
            fetchedAt = fetchedAt,
            // An unknown tier must not unlock anything, so it falls back to the most limited one
            tier = if (response.tier == "feeder") Tier.FEEDER else Tier.FREE,
            restricted = response.restricted,
            allowanceScope = response.allowanceScope,
            limits = response.limits,
            usage = response.usage,
            installationRequests = response.installationRequests,
            entitlementRequests = response.entitlementRequests,
        )
    }
}
