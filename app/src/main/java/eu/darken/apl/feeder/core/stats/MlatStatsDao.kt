package eu.darken.apl.feeder.core.stats

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import eu.darken.apl.feeder.core.ReceiverId
import kotlinx.coroutines.flow.Flow

@Dao
interface MlatStatsDao {
    @Query("SELECT * FROM stats_mlat")
    fun getAll(): List<MlatStatsEntity>

    @Query("SELECT * FROM stats_mlat ORDER BY id DESC LIMIT 1")
    fun firehose(): Flow<MlatStatsEntity?>

    @Query("SELECT * FROM stats_mlat WHERE receiver_id = :receiverId ORDER BY id DESC LIMIT 1")
    fun getLatest(receiverId: ReceiverId): Flow<MlatStatsEntity?>

    @Insert
    suspend fun insert(stats: MlatStatsEntity): Long

    @Query("SELECT * FROM stats_mlat WHERE receiver_id = :receiverId AND received_at >= :since ORDER BY received_at ASC")
    suspend fun getSince(receiverId: ReceiverId, since: Long): List<MlatStatsEntity>

    @Query("DELETE FROM stats_mlat WHERE receiver_id = :receiverId")
    suspend fun delete(receiverId: ReceiverId): Int

    @Query("DELETE FROM stats_mlat WHERE received_at < :before")
    suspend fun deleteBefore(before: Long): Int
}