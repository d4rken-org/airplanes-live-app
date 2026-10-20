package eu.darken.apl.watch.core.db.server

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import eu.darken.apl.watch.core.WatchId
import java.time.Instant

/** One UTC hour of a server watch revision, as the server's rollups answered it. */
@Entity(
    tableName = "watch_server_rollups",
    primaryKeys = ["server_watch_id", "revision", "hour", "source"],
    indices = [
        Index(value = ["watch_id", "hour"]),
        Index(value = ["hour"]),
    ],
)
data class WatchServerRollupEntity(
    @ColumnInfo(name = "server_watch_id") val serverWatchId: String,
    @ColumnInfo(name = "revision") val revision: Long,
    @ColumnInfo(name = "hour") val hour: Instant,
    @ColumnInfo(name = "source") val source: String,
    @ColumnInfo(name = "watch_id") val watchId: WatchId,
    @ColumnInfo(name = "installation_id") val installationId: String,
    @ColumnInfo(name = "seq") val seq: Long,
    @ColumnInfo(name = "evaluation_basis") val evaluationBasis: String,
    @ColumnInfo(name = "aircraft_count") val aircraftCount: Int,
    @ColumnInfo(name = "coverage") val coverage: String,
    @ColumnInfo(name = "covered_minutes") val coveredMinutes: Int? = null,
    @ColumnInfo(name = "is_final") val isFinal: Boolean,
    @ColumnInfo(name = "last_seen_at") val lastSeenAt: Instant? = null,
    @ColumnInfo(name = "last_seen_aircraft_id") val lastSeenAircraftId: String? = null,
)
