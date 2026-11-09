package eu.darken.apl.feeder.core.server

import eu.darken.apl.server.api.FeederMonitorEntry
import eu.darken.apl.server.api.FeederMonitorLimits
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What the app knows about the server's feeder monitors of one installation. Times are epoch millis.
 * Every field has a default, so a value written by an older build still decodes.
 */
@Serializable
data class FeederServerState(
    val installationId: String? = null,
    /** Null forces a list read: a mutation may have changed other monitors too. */
    val listRevision: Long? = null,
    /** Null until the list was read once, the event poll starts from it. */
    val cursor: Long? = null,
    /** "active" or "expired". */
    val listState: String? = null,
    val limits: FeederMonitorLimits? = null,
    val sampleIntervalSeconds: Int? = null,
    val lastListReadAt: Long? = null,
    /** Keyed by canonical feeder id. */
    val monitors: Map<String, FeederMonitorEntry> = emptyMap(),
    /** Keyed by canonical feeder id. */
    val uploads: Map<String, Upload> = emptyMap(),
    /** The desired set and limits the refusals in [uploads] were recorded under. */
    val refusalContext: String? = null,
    val uploadsNotBefore: Long? = null,
    val createsNotBefore: Long? = null,
    val readsNotBefore: Long? = null,
    /** Keyed by canonical feeder id. */
    val notified: Map<String, Notified> = emptyMap(),
    val notifiedInitialized: Boolean = false,
) {

    @Serializable
    data class Upload(
        /** The threshold the server last acknowledged. */
        val syncedOfflineAfterMinutes: Int? = null,
        val refusal: Refusal? = null,
    )

    @Serializable
    enum class Refusal {
        @SerialName("storage_limit")
        STORAGE_LIMIT,

        @SerialName("server_full")
        SERVER_FULL,

        @SerialName("invalid")
        INVALID,
        ;
    }

    @Serializable
    data class Notified(
        val monitorId: String? = null,
        val offline: Boolean = false,
        val offlineSince: Long? = null,
        val problems: Set<String> = emptySet(),
    )
}
