package eu.darken.apl.feeder.core

import eu.darken.apl.feeder.core.api.FeedInfos
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant

/**
 * What this device's checks found out about one feeder. Kept apart from the stats rows, which are
 * trimmed by retention, and to which a check that found nothing or failed adds no row at all.
 */
@Serializable
data class FeederObservation(
    /** The newest check that got an answer. */
    @Contextual @SerialName("checkedAt") val checkedAt: Instant? = null,
    @SerialName("status") val status: Status? = null,
    /** The newest check that got no answer. Only means something while it is newer than [checkedAt]. */
    @Contextual @SerialName("failedAt") val failedAt: Instant? = null,
    @Contextual @SerialName("lastFeedingAt") val lastFeedingAt: Instant? = null,
    /** The first answer since [lastFeedingAt] that said the feeder is not feeding. */
    @Contextual @SerialName("inactiveSince") val inactiveSince: Instant? = null,
) {

    @Serializable
    enum class Status {
        /** At least one beast connection carries messages. */
        @SerialName("feeding") FEEDING,

        /** Connected, but no connection carries messages. */
        @SerialName("idle") IDLE,

        /** No beast connection at all. */
        @SerialName("not_seen") NOT_SEEN,
    }

    val isCheckFailing: Boolean
        get() = failedAt != null && (checkedAt == null || failedAt > checkedAt)

    fun withAnswer(infos: FeedInfos, at: Instant): FeederObservation {
        val newStatus = when {
            infos.beast.any { it.messageRate > 0 } -> Status.FEEDING
            infos.beast.isNotEmpty() -> Status.IDLE
            else -> Status.NOT_SEEN
        }
        return copy(
            checkedAt = at,
            status = newStatus,
            lastFeedingAt = if (newStatus == Status.FEEDING) at else lastFeedingAt,
            inactiveSince = if (newStatus == Status.FEEDING) null else inactiveSince ?: at,
        )
    }

    fun withFailure(at: Instant): FeederObservation = copy(failedAt = at)

    /**
     * Offline only on a fresh answer that says so. A failing check is unknown, not offline, and an
     * answer older than [timeout] is too old to alert on.
     */
    fun isOffline(timeout: Duration, now: Instant): Boolean {
        if (isCheckFailing) return false
        val checked = checkedAt ?: return false
        if (status == null || status == Status.FEEDING) return false
        if (Duration.between(checked, now) > timeout) return false
        // A feeder never seen feeding counts from its first inactive answer, so a wrong ID still alerts
        val since = lastFeedingAt ?: inactiveSince ?: return false
        return Duration.between(since, now) > timeout
    }
}
