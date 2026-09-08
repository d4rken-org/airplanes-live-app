package eu.darken.apl.server.access

import eu.darken.apl.server.api.AccessResponse
import eu.darken.apl.server.api.RequestLimits
import eu.darken.apl.server.api.TierPolicy
import eu.darken.apl.server.api.Usage
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
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
) {

    enum class Tier {
        FREE,
        FEEDER,
        ;
    }

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
