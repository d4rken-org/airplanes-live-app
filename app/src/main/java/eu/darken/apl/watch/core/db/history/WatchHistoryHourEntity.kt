package eu.darken.apl.watch.core.db.history

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import eu.darken.apl.watch.core.WatchId
import java.time.Instant

/** One UTC hour of a watch as the server's history answered it, kept apart from the app's own checks. */
@Entity(
    tableName = "watch_history_hours",
    primaryKeys = ["watch_id", "hour"],
    indices = [Index(value = ["hour"])],
)
data class WatchHistoryHourEntity(
    @ColumnInfo(name = "watch_id") val watchId: WatchId,
    @ColumnInfo(name = "hour") val hour: Instant,
    @ColumnInfo(name = "aircraft_count") val aircraftCount: Int,
    /** `full`, `partial` or `none`. */
    @ColumnInfo(name = "coverage") val coverage: String,
    @ColumnInfo(name = "is_final") val isFinal: Boolean,
)
