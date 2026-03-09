package eu.darken.apl.watch.core.db.history

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import eu.darken.apl.watch.core.WatchId
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface WatchCheckDao {
    @Query("SELECT * FROM watch_checks WHERE watch_id = :watchId ORDER BY checked_at DESC LIMIT 1")
    suspend fun getLastCheck(watchId: String): WatchCheckEntity?

    @Query("SELECT * FROM watch_checks WHERE watch_id = :watchId AND aircraft_count > 0 ORDER BY checked_at DESC LIMIT 1")
    suspend fun getLastHit(watchId: String): WatchCheckEntity?

    @Query("SELECT * FROM watch_checks")
    suspend fun getAll(): List<WatchCheckEntity>

    @Query("SELECT * FROM watch_checks ORDER BY checked_at DESC LIMIT 1")
    fun firehose(): Flow<WatchCheckEntity?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(watch: WatchCheckEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(checks: List<WatchCheckEntity>)

    @Query("DELETE FROM watch_checks WHERE watch_id = :watchId")
    suspend fun deleteForWatch(watchId: WatchId): Int

    @Query("DELETE FROM watch_checks WHERE watch_id IN (:watchIds)")
    suspend fun deleteForWatches(watchIds: Set<WatchId>): Int

    @Query("SELECT COUNT(*) FROM watch_checks")
    suspend fun count(): Int

    data class WatchCheckChartRow(
        @ColumnInfo(name = "watch_id") val watchId: String,
        @ColumnInfo(name = "checked_at") val checkedAt: Instant,
        @ColumnInfo(name = "aircraft_count") val aircraftCount: Int,
    )

    @Query("SELECT watch_id, checked_at, aircraft_count FROM watch_checks WHERE watch_id = :watchId AND checked_at >= :since ORDER BY checked_at")
    suspend fun getChartDataSince(watchId: String, since: Instant): List<WatchCheckChartRow>

    @Query("SELECT watch_id, checked_at, aircraft_count FROM watch_checks WHERE watch_id IN (:watchIds) AND checked_at >= :since ORDER BY checked_at")
    suspend fun getChartDataSince(watchIds: Set<String>, since: Instant): List<WatchCheckChartRow>

    @Query("DELETE FROM watch_checks WHERE checked_at < :before")
    suspend fun deleteBefore(before: Instant): Int
}
