package eu.darken.apl.watch.core.db.server

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import eu.darken.apl.watch.core.WatchId
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface WatchServerDao {
    @Query("SELECT * FROM watch_server_state WHERE watch_id = :watchId")
    suspend fun getState(watchId: WatchId): WatchServerStateEntity?

    @Query("SELECT * FROM watch_server_state WHERE watch_id IN (:watchIds)")
    suspend fun getStates(watchIds: Set<WatchId>): List<WatchServerStateEntity>

    @Query("SELECT * FROM watch_server_state")
    suspend fun getAllStates(): List<WatchServerStateEntity>

    @Query("SELECT * FROM watch_server_state")
    fun allStates(): Flow<List<WatchServerStateEntity>>

    @Query("SELECT * FROM watch_server_state WHERE watch_id = :watchId")
    fun state(watchId: WatchId): Flow<WatchServerStateEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertState(state: WatchServerStateEntity)

    @Query("DELETE FROM watch_server_state WHERE watch_id = :watchId")
    suspend fun deleteState(watchId: WatchId): Int

    @Query("DELETE FROM watch_server_state WHERE watch_id IN (:watchIds)")
    suspend fun deleteStates(watchIds: Set<WatchId>): Int

    @Query("SELECT * FROM watch_server_tombstones WHERE installation_id = :installationId")
    suspend fun getTombstones(installationId: String): List<WatchServerTombstoneEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTombstones(tombstones: List<WatchServerTombstoneEntity>)

    @Query("DELETE FROM watch_server_tombstones WHERE watch_id = :watchId")
    suspend fun deleteTombstone(watchId: WatchId): Int

    @Query("SELECT * FROM watch_server_sync WHERE installation_id = :installationId")
    suspend fun getSync(installationId: String): WatchServerSyncEntity?

    @Query(
        "SELECT EXISTS(SELECT 1 FROM watch_server_sync WHERE installation_id != :installationId) " +
                "OR EXISTS(SELECT 1 FROM watch_server_state WHERE installation_id != :installationId) " +
                "OR EXISTS(SELECT 1 FROM watch_server_tombstones WHERE installation_id != :installationId)"
    )
    suspend fun hasOtherInstallation(installationId: String): Boolean

    @Query(
        "SELECT EXISTS(SELECT 1 FROM watch_server_sync WHERE installation_id != :installationId) " +
                "OR EXISTS(SELECT 1 FROM watch_server_state WHERE installation_id != :installationId) " +
                "OR EXISTS(SELECT 1 FROM watch_server_tombstones WHERE installation_id != :installationId)"
    )
    fun otherInstallation(installationId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSync(sync: WatchServerSyncEntity)

    /** Returns the row id per event, -1 for a seq that was stored before. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvents(events: List<WatchServerEventEntity>): List<Long>

    @Query("SELECT * FROM watch_server_events ORDER BY seq")
    suspend fun getEvents(): List<WatchServerEventEntity>

    @Query("DELETE FROM watch_server_events WHERE observed_at < :before")
    suspend fun deleteEventsBefore(before: Instant): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOutbox(entries: List<WatchNotificationOutboxEntity>)

    @Query("SELECT * FROM watch_notification_outbox ORDER BY created_at, id")
    suspend fun getOutbox(): List<WatchNotificationOutboxEntity>

    @Query("DELETE FROM watch_notification_outbox WHERE id = :id")
    suspend fun deleteOutbox(id: String): Int

    @Query("DELETE FROM watch_notification_outbox WHERE watch_id IN (:watchIds)")
    suspend fun deleteOutboxFor(watchIds: Set<WatchId>): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRollups(rollups: List<WatchServerRollupEntity>)

    @Query("SELECT * FROM watch_server_rollups WHERE watch_id = :watchId AND hour >= :since ORDER BY hour")
    fun rollupsSince(watchId: WatchId, since: Instant): Flow<List<WatchServerRollupEntity>>

    @Query("SELECT * FROM watch_server_rollups WHERE hour >= :since ORDER BY hour")
    fun allRollupsSince(since: Instant): Flow<List<WatchServerRollupEntity>>

    data class RollupLastSeen(
        @ColumnInfo(name = "watch_id") val watchId: WatchId,
        @ColumnInfo(name = "server_watch_id") val serverWatchId: String,
        @ColumnInfo(name = "revision") val revision: Long,
        @ColumnInfo(name = "last_seen_at") val lastSeenAt: Instant,
    )

    @Query(
        "SELECT watch_id, server_watch_id, revision, MAX(last_seen_at) AS last_seen_at FROM watch_server_rollups " +
                "WHERE last_seen_at IS NOT NULL GROUP BY watch_id, server_watch_id, revision"
    )
    fun rollupsLastSeen(): Flow<List<RollupLastSeen>>

    @Query("DELETE FROM watch_server_rollups WHERE installation_id = :installationId")
    suspend fun deleteRollupsOf(installationId: String): Int

    @Query("DELETE FROM watch_server_rollups WHERE hour < :before")
    suspend fun deleteRollupsBefore(before: Instant): Int

    @Query("DELETE FROM watch_server_rollups WHERE server_watch_id NOT IN (:serverWatchIds)")
    suspend fun deleteRollupsExcept(serverWatchIds: Set<String>): Int

    @Query("DELETE FROM watch_server_rollups WHERE watch_id IN (:watchIds)")
    suspend fun deleteRollupsFor(watchIds: Set<WatchId>): Int

    @Query("DELETE FROM watch_server_state")
    suspend fun deleteAllStates()

    @Query("DELETE FROM watch_server_tombstones")
    suspend fun deleteAllTombstones()

    @Query("DELETE FROM watch_server_sync")
    suspend fun deleteAllSync()

    @Query("DELETE FROM watch_server_events")
    suspend fun deleteAllEvents()

    @Query("DELETE FROM watch_notification_outbox")
    suspend fun deleteAllOutbox()

    @Query("DELETE FROM watch_server_rollups")
    suspend fun deleteAllRollups()
}
