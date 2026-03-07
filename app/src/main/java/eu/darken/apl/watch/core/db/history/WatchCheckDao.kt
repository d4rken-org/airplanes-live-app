package eu.darken.apl.watch.core.db.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import eu.darken.apl.watch.core.WatchId
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchCheckDao {
    @Query("SELECT * FROM watch_checks WHERE id = :id")
    suspend fun get(id: String): WatchCheckEntity?

    @Query("SELECT * FROM watch_checks WHERE watch_id = :watchId ORDER BY checked_at DESC LIMIT 1")
    suspend fun getLastCheck(watchId: String): WatchCheckEntity?

    @Query("SELECT * FROM watch_checks WHERE watch_id = :watchId AND aircraft_count > 0 ORDER BY checked_at DESC LIMIT 1")
    suspend fun getLastHit(watchId: String): WatchCheckEntity?

    @Query("SELECT * FROM watch_checks")
    fun getAll(): List<WatchCheckEntity>

    @Query("SELECT * FROM watch_checks ORDER BY checked_at DESC LIMIT 1")
    fun firehose(): Flow<WatchCheckEntity?>

    @Query("SELECT * FROM watch_checks ORDER BY checked_at")
    fun current(): Flow<List<WatchCheckEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(watch: WatchCheckEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(checks: List<WatchCheckEntity>)

    @Query("DELETE FROM watch_checks WHERE id = :watchId")
    suspend fun deleteForWatch(watchId: WatchId): Int

    @Query("SELECT COUNT(*) FROM watch_checks")
    suspend fun count(): Int
}