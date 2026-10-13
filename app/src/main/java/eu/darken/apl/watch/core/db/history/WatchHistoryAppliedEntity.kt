package eu.darken.apl.watch.core.db.history

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * A history operation whose answer is committed here. Its pending row lives in another database
 * and is retired after this commit, so a crash in between must find the answer already applied.
 */
@Entity(tableName = "watch_history_applied")
data class WatchHistoryAppliedEntity(
    @PrimaryKey @ColumnInfo(name = "operation_id") val operationId: String,
    @ColumnInfo(name = "applied_at") val appliedAt: Instant,
)
