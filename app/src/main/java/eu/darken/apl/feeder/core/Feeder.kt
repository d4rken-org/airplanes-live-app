package eu.darken.apl.feeder.core

import eu.darken.apl.feeder.core.config.FeederPosition
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * A feeder owned by the logged-in airplanes.live account (from `GET /api/v1/me/feeders`).
 * [id] is the server `feeder_id` (UUID). Serializable so it can be cached in DataStore.
 */
@Serializable
data class Feeder(
    @SerialName("id") val id: ReceiverId,
    @SerialName("name") val name: String? = null,
    @SerialName("status") val status: FeederStatus = FeederStatus.UNKNOWN,
    @Contextual @SerialName("lastSeen") val lastSeen: Instant? = null,
    @SerialName("country") val country: String? = null,
    @SerialName("position") val position: FeederPosition? = null,
    // Server semantics (dashboard parity): firstSeen predates the current claim, and lastSeenIp
    // is device-lifetime state that survives an ownership transfer until the feeder reconnects.
    @SerialName("lastSeenIp") val lastSeenIp: String? = null,
    @Contextual @SerialName("firstSeen") val firstSeen: Instant? = null,
) {

    val label: String
        get() = name?.takeIf { it.isNotBlank() } ?: id.takeLast(5)

    val shortId: String
        get() = id.toShortFeederId()

    // IDLE means connected-but-silent, not an outage — it must not render with offline styling
    // while the list header simultaneously reports "all feeders online" (only INACTIVE counts).
    val isOnline: Boolean
        get() = status == FeederStatus.ACTIVE || status == FeederStatus.IDLE
}
