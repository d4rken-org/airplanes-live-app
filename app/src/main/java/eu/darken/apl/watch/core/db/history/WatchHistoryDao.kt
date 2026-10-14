package eu.darken.apl.watch.core.db.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import eu.darken.apl.watch.core.WatchId
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface WatchHistoryDao {
    @Query("SELECT * FROM watch_history_hours WHERE watch_id = :watchId AND hour >= :since ORDER BY hour")
    fun hoursSince(watchId: WatchId, since: Instant): Flow<List<WatchHistoryHourEntity>>

    @Query("SELECT * FROM watch_history_hours WHERE hour >= :since ORDER BY hour")
    fun allHoursSince(since: Instant): Flow<List<WatchHistoryHourEntity>>

    @Query("SELECT * FROM watch_history_hours WHERE watch_id = :watchId AND hour >= :from AND hour < :until")
    suspend fun getHours(watchId: WatchId, from: Instant, until: Instant): List<WatchHistoryHourEntity>

    @Query("SELECT * FROM watch_history_hours WHERE watch_id = :watchId AND hour IN (:hours)")
    suspend fun getHours(watchId: WatchId, hours: List<Instant>): List<WatchHistoryHourEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHours(hours: List<WatchHistoryHourEntity>)

    @Query("DELETE FROM watch_history_hours WHERE watch_id IN (:watchIds)")
    suspend fun deleteHoursFor(watchIds: Set<WatchId>): Int

    @Query("DELETE FROM watch_history_hours WHERE hour < :before")
    suspend fun deleteHoursBefore(before: Instant): Int

    @Query("SELECT * FROM watch_history_sync WHERE watch_id = :watchId")
    suspend fun getSync(watchId: WatchId): WatchHistorySyncEntity?

    @Query("SELECT * FROM watch_history_sync")
    suspend fun getAllSync(): List<WatchHistorySyncEntity>

    @Query("SELECT * FROM watch_history_sync")
    fun allSync(): Flow<List<WatchHistorySyncEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSync(sync: WatchHistorySyncEntity)

    @Query("DELETE FROM watch_history_sync WHERE watch_id IN (:watchIds)")
    suspend fun deleteSyncFor(watchIds: Set<WatchId>): Int

    @Query("SELECT EXISTS(SELECT 1 FROM watch_history_applied WHERE operation_id = :operationId)")
    suspend fun isApplied(operationId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertApplied(applied: WatchHistoryAppliedEntity)

    @Query("DELETE FROM watch_history_applied WHERE applied_at < :before")
    suspend fun deleteAppliedBefore(before: Instant): Int
}
