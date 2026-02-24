package eu.darken.apl.feeder.core.stats

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import eu.darken.apl.feeder.core.ReceiverId
import kotlinx.coroutines.flow.Flow

@Dao
interface BeastStatsDao {
    @Query("SELECT * FROM stats_beast")
    fun getAll(): List<BeastStatsEntity>

    @Query("SELECT * FROM stats_beast ORDER BY id DESC LIMIT 1")
    fun firehose(): Flow<BeastStatsEntity?>

    @Query("SELECT * FROM stats_beast WHERE receiver_id = :receiverId ORDER BY id DESC LIMIT 1")
    fun getLatest(receiverId: ReceiverId): Flow<BeastStatsEntity?>

    @Insert
    suspend fun insert(stats: BeastStatsEntity): Long

    @Query("SELECT * FROM stats_beast WHERE receiver_id = :receiverId AND received_at >= :since ORDER BY received_at ASC")
    suspend fun getSince(receiverId: ReceiverId, since: Long): List<BeastStatsEntity>

    @Query("DELETE FROM stats_beast WHERE receiver_id = :receiverId")
    suspend fun delete(receiverId: ReceiverId): Int

    @Query("DELETE FROM stats_beast WHERE received_at < :before")
    suspend fun deleteBefore(before: Long): Int
}