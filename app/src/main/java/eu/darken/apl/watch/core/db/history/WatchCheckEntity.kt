package eu.darken.apl.watch.core.db.history

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import eu.darken.apl.watch.core.WatchId
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "watch_checks",
)
data class WatchCheckEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "checked_at") val checkedAt: Instant = Instant.now(),
    @ColumnInfo(name = "watch_id") val watchId: WatchId,
    @ColumnInfo(name = "aircraft_count") val aircraftcount: Int,
    @ColumnInfo(name = "seen_hexes") val seenHexes: String? = null,
)