package eu.darken.apl.feeder.core.server

import eu.darken.apl.feeder.core.config.FeederConfig
import eu.darken.apl.server.api.FeederMonitorEntry

/** What the server says about one local feeder, only built in server mode. */
data class FeederServerView(
    /** Canonical id, null when the server would refuse the local id. */
    val feederId: String?,
    val entry: FeederMonitorEntry? = null,
    val refusal: FeederServerState.Refusal? = null,
) {

    enum class Status {
        ONLINE,
        OFFLINE,
        /** No monitor yet, or the feeder was seen connected but the server has no verdict stored yet. */
        WAITING,
        /** No check has started since this monitor was created. */
        NOT_CHECKED_YET,
        /** A check succeeded, but none saw the feeder connected since then. */
        CHECKED_NO_DATA,
        /** Checks started, but none succeeded since this monitor was created. */
        CHECKS_FAILING,
        UPSTREAM_UNAVAILABLE,
        OUTAGE_SUSPECTED,
        OVER_LIMIT,
        PAUSED,
        SERVER_FULL,
        NOT_ACCEPTED,
    }

    /** The server's own entry wins over a refusal: a monitor it holds is what it actually does. */
    val status: Status
        get() {
            if (feederId == null) return Status.NOT_ACCEPTED
            val entry = entry ?: return when (refusal) {
                FeederServerState.Refusal.STORAGE_LIMIT -> Status.OVER_LIMIT
                FeederServerState.Refusal.SERVER_FULL -> Status.SERVER_FULL
                FeederServerState.Refusal.INVALID -> Status.NOT_ACCEPTED
                null -> Status.WAITING
            }
            if (entry.status == STATUS_PAUSED) {
                return if (entry.pauseReason == PAUSE_OVER_LIMIT) Status.OVER_LIMIT else Status.PAUSED
            }
            return when (entry.health) {
                HEALTH_ONLINE -> Status.ONLINE
                HEALTH_OFFLINE -> Status.OFFLINE
                else -> when (entry.unknownReason) {
                    UNKNOWN_UPSTREAM -> Status.UPSTREAM_UNAVAILABLE
                    UNKNOWN_OUTAGE_SUSPECTED -> Status.OUTAGE_SUSPECTED
                    UNKNOWN_PAUSED -> Status.PAUSED
                    UNKNOWN_PENDING -> pendingStatus(entry)
                    else -> Status.WAITING
                }
            }
        }

    /**
     * The sample timestamps are shared by every installation monitoring the feeder, so ones older than
     * this monitor say nothing about it. `updatedAt` also moves on threshold edits and is no reference.
     */
    private fun pendingStatus(entry: FeederMonitorEntry): Status {
        val ref = entry.createdAt
        val sampleAt = entry.lastSampleAt
        val successAt = entry.lastSuccessAt
        val connectedAt = entry.lastConnectedAt
        return when {
            sampleAt == null || sampleAt < ref -> Status.NOT_CHECKED_YET
            successAt == null || successAt < ref -> Status.CHECKS_FAILING
            connectedAt == null || connectedAt < successAt -> Status.CHECKED_NO_DATA
            else -> Status.WAITING
        }
    }

    /** Online, but the server measured no messages. */
    val isIdle: Boolean
        get() = status == Status.ONLINE && entry?.latest?.msgsPerSecond == 0.0

    /** The server alerts on it: an active monitor that found the feeder offline. */
    val isReportedOffline: Boolean
        get() = entry != null && entry.status == STATUS_ACTIVE && entry.health == HEALTH_OFFLINE

    companion object {
        private const val STATUS_ACTIVE = "active"
        private const val STATUS_PAUSED = "paused"
        private const val PAUSE_OVER_LIMIT = "over_limit"
        private const val HEALTH_ONLINE = "online"
        private const val HEALTH_OFFLINE = "offline"
        private const val UNKNOWN_PAUSED = "paused"
        private const val UNKNOWN_PENDING = "pending"
        private const val UNKNOWN_UPSTREAM = "upstream"
        private const val UNKNOWN_OUTAGE_SUSPECTED = "outage_suspected"

        /** Case variants of one id get the same entry. */
        fun of(config: FeederConfig, state: FeederServerState): FeederServerView {
            val feederId = config.receiverId.toFeederMonitorId() ?: return FeederServerView(feederId = null)
            return FeederServerView(
                feederId = feederId,
                entry = state.monitors[feederId],
                refusal = state.uploads[feederId]?.refusal,
            )
        }
    }
}
