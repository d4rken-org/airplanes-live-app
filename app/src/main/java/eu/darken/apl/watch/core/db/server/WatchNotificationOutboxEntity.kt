package eu.darken.apl.watch.core.db.server

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import eu.darken.apl.watch.core.WatchId
import java.time.Instant

/** A notification that is due but not shown yet; its id is derived from what caused it, so a replay replaces it. */
@Entity(tableName = "watch_notification_outbox")
data class WatchNotificationOutboxEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "installation_id") val installationId: String,
    /** [KIND_INDIVIDUAL] or [KIND_SUMMARY]. */
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "watch_id") val watchId: WatchId? = null,
    @ColumnInfo(name = "event_seq") val eventSeq: Long? = null,
    /** Comma separated, like the seen hexes of a check. */
    @ColumnInfo(name = "aircraft_ids") val aircraftIds: String? = null,
    @ColumnInfo(name = "total_matching") val totalMatching: Int? = null,
    @ColumnInfo(name = "observed_at") val observedAt: Instant? = null,
    @ColumnInfo(name = "summary_count", defaultValue = "0") val summaryCount: Int = 0,
    /** Comma separated. */
    @ColumnInfo(name = "summary_watch_ids") val summaryWatchIds: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
) {
    companion object {
        const val KIND_INDIVIDUAL = "individual"
        const val KIND_SUMMARY = "summary"

        fun individualId(eventSeq: Long) = "event:$eventSeq"
        fun summaryId(pollRunId: String) = "summary:$pollRunId"
    }
}
