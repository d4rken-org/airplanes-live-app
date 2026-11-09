package eu.darken.apl.feeder.core.config

import eu.darken.apl.feeder.core.ReceiverId
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant

@Serializable
data class FeederConfig(
    @SerialName("receiverId") val receiverId: ReceiverId,
    @Contextual @SerialName("addedAt") val addedAt: Instant = Instant.now(),
    @SerialName("label") val label: String? = null,
    @SerialName("position") val position: FeederPosition? = null,
    // Must default to null: the app's Json omits null properties on encode, so a non-null default would
    // resurrect itself on decode and make disabling offline checks impossible.
    @Contextual @SerialName("offlineCheckTimeout") val offlineCheckTimeout: Duration? = null,
    @Contextual @SerialName("offlineCheckSnoozedAt") val offlineCheckSnoozedAt: Instant? = null,
    @SerialName("address") val address: String? = null,
) {
    companion object {
        val DEFAULT_OFFLINE_LIMIT = Duration.ofHours(48)

        /** What the user can pick besides off. The server accepts at most 48 hours. */
        val OFFLINE_THRESHOLD_CHOICES: List<Duration> = listOf(
            Duration.ofHours(6),
            Duration.ofHours(12),
            Duration.ofHours(24),
            Duration.ofHours(48),
        )

        fun newFeeder(receiverId: ReceiverId) = FeederConfig(
            receiverId = receiverId,
            offlineCheckTimeout = DEFAULT_OFFLINE_LIMIT,
        )
    }
}
