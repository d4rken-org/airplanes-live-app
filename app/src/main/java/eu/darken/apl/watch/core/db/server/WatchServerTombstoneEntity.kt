package eu.darken.apl.watch.core.db.server

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import eu.darken.apl.watch.core.WatchId
import java.time.Instant

/** A locally deleted watch the server may still hold, kept until its delete reached the installation that sent it. */
@Entity(tableName = "watch_server_tombstones")
data class WatchServerTombstoneEntity(
    @PrimaryKey @ColumnInfo(name = "watch_id") val watchId: WatchId,
    @ColumnInfo(name = "installation_id") val installationId: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: Instant,
)
