package eu.darken.apl.watch.core.db.server

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import eu.darken.apl.watch.core.WatchId
import java.time.Instant

/** What the server last said about one local watch, for the installation that sent it. */
@Entity(tableName = "watch_server_state")
data class WatchServerStateEntity(
    @PrimaryKey @ColumnInfo(name = "watch_id") val watchId: WatchId,
    @ColumnInfo(name = "installation_id") val installationId: String,
    /** Null until the server answered an upload. */
    @ColumnInfo(name = "server_watch_id") val serverWatchId: String? = null,
    @ColumnInfo(name = "server_revision") val serverRevision: Long? = null,
    /** One of the `UPLOAD_*` constants. */
    @ColumnInfo(name = "upload_state") val uploadState: String,
    /** The local generation of the upload in flight or sent last. */
    @ColumnInfo(name = "sent_generation") val sentGeneration: Long? = null,
    /** The local definition revision that [serverRevision] stands for. */
    @ColumnInfo(name = "acked_definition_revision") val ackedDefinitionRevision: Long? = null,
    @ColumnInfo(name = "status") val status: String? = null,
    @ColumnInfo(name = "pause_reason") val pauseReason: String? = null,
    @ColumnInfo(name = "presence") val presence: String? = null,
    @ColumnInfo(name = "event_seq", defaultValue = "0") val eventSeq: Long = 0,
    @ColumnInfo(name = "last_seen_at") val lastSeenAt: Instant? = null,
    @ColumnInfo(name = "last_seen_aircraft_id") val lastSeenAircraftId: String? = null,
    @ColumnInfo(name = "backfill") val backfill: String? = null,
    @ColumnInfo(name = "last_match_seq") val lastMatchSeq: Long? = null,
    @ColumnInfo(name = "last_match_at") val lastMatchAt: Instant? = null,
    /** Comma separated, like the seen hexes of a check. */
    @ColumnInfo(name = "last_match_aircraft_ids") val lastMatchAircraftIds: String? = null,
    @ColumnInfo(name = "last_match_total") val lastMatchTotal: Int? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
) {
    companion object {
        const val UPLOAD_PENDING = "pending"
        const val UPLOAD_SYNCED = "synced"
        const val UPLOAD_REJECTED_INVALID = "rejected_invalid"
        const val UPLOAD_REJECTED_STORAGE = "rejected_storage"

        /** The server neither stores nor monitors a watch in these states. */
        val UPLOAD_REFUSED = setOf(UPLOAD_REJECTED_INVALID, UPLOAD_REJECTED_STORAGE)

        /**
         * In server mode, whether the app still checks a watch itself: a watch the server holds is
         * monitored there, a refused one by nobody.
         */
        fun isAppChecked(state: WatchServerStateEntity?): Boolean =
            state == null || (state.serverWatchId == null && state.uploadState !in UPLOAD_REFUSED)
    }
}
