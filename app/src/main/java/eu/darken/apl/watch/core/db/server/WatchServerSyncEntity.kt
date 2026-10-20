package eu.darken.apl.watch.core.db.server

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/** The list-wide sync position for one installation. */
@Entity(tableName = "watch_server_sync")
data class WatchServerSyncEntity(
    @PrimaryKey @ColumnInfo(name = "installation_id") val installationId: String,
    @ColumnInfo(name = "list_revision") val listRevision: Long? = null,
    @ColumnInfo(name = "list_state") val listState: String? = null,
    @ColumnInfo(name = "event_cursor") val eventCursor: Long? = null,
    @ColumnInfo(name = "rollup_cursor") val rollupCursor: Long? = null,
    /** One of the `PHASE_*` constants. */
    @ColumnInfo(name = "phase") val phase: String,
    /** The last events poll that succeeded. */
    @ColumnInfo(name = "last_poll_at") val lastPollAt: Instant? = null,
    @ColumnInfo(name = "uploads_not_before") val uploadsNotBefore: Instant? = null,
    @ColumnInfo(name = "events_not_before") val eventsNotBefore: Instant? = null,
    @ColumnInfo(name = "rollups_not_before") val rollupsNotBefore: Instant? = null,
    /** The events poll run that has not read up to the newest event yet, with the individual alerts it used. */
    @ColumnInfo(name = "poll_run_id") val pollRunId: String? = null,
    @ColumnInfo(name = "poll_run_individual", defaultValue = "0") val pollRunIndividual: Int = 0,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
) {
    companion object {
        const val PHASE_HYDRATE = "hydrate"
        const val PHASE_UPLOAD = "upload"
        const val PHASE_LIVE = "live"
    }
}
