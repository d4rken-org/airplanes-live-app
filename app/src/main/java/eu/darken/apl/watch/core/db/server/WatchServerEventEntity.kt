package eu.darken.apl.watch.core.db.server

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import eu.darken.apl.watch.core.WatchId
import java.time.Instant

@Entity(
    tableName = "watch_server_events",
    indices = [
        Index(value = ["observed_at"]),
        Index(value = ["server_watch_id", "revision"]),
    ],
)
data class WatchServerEventEntity(
    @PrimaryKey @ColumnInfo(name = "seq") val seq: Long,
    @ColumnInfo(name = "installation_id") val installationId: String,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "observed_at") val observedAt: Instant,
    @ColumnInfo(name = "server_watch_id") val serverWatchId: String? = null,
    @ColumnInfo(name = "watch_id") val watchId: WatchId? = null,
    @ColumnInfo(name = "revision") val revision: Long? = null,
    /** Comma separated, like the seen hexes of a check. */
    @ColumnInfo(name = "aircraft_ids") val aircraftIds: String? = null,
    @ColumnInfo(name = "total_matching") val totalMatching: Int? = null,
    @ColumnInfo(name = "gap_from") val gapFrom: Instant? = null,
    @ColumnInfo(name = "gap_until") val gapUntil: Instant? = null,
    @ColumnInfo(name = "received_at") val receivedAt: Instant,
)
