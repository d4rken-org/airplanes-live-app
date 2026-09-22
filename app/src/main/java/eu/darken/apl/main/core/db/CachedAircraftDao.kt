package eu.darken.apl.main.core.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import eu.darken.apl.main.core.aircraft.AircraftHex
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface CachedAircraftDao {
    @Query("SELECT * FROM aircraft_cache WHERE hex = :hex")
    fun byHex(hex: AircraftHex): Flow<CachedAircraftEntity?>

    @Query("SELECT * FROM aircraft_cache WHERE hex IN (:hexes)")
    suspend fun byHexes(hexes: List<AircraftHex>): List<CachedAircraftEntity>

    @Query("SELECT * FROM aircraft_cache ")
    fun current(): Flow<List<CachedAircraftEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(aircraft: List<CachedAircraftEntity>)

    data class MessageSeenAt(
        @ColumnInfo(name = "hex") val hex: AircraftHex,
        @ColumnInfo(name = "message_seen_at") val messageSeenAt: Instant?,
    )

    @Query("SELECT hex, message_seen_at FROM aircraft_cache WHERE hex IN (:hexes)")
    suspend fun getMessageSeenAt(hexes: List<AircraftHex>): List<MessageSeenAt>

    /**
     * The server may answer two requests from snapshots of different age, so a response can carry an
     * observation older than the cached one. Reading and writing in one transaction keeps overlapping
     * writers from resurrecting an older observation.
     */
    @Transaction
    suspend fun upsertNewerWins(aircraft: List<CachedAircraftEntity>) {
        if (aircraft.isEmpty()) return
        val stored = getMessageSeenAt(aircraft.map { it.hex }).associate { it.hex to it.messageSeenAt }
        val accepted = aircraft.filter { incoming ->
            val storedSeenAt = stored[incoming.hex] ?: return@filter true
            // An observation of unknown age cannot be proven newer, so it does not replace a known one
            val incomingSeenAt = incoming.messageSeenAt ?: return@filter false
            !incomingSeenAt.isBefore(storedSeenAt)
        }
        upsertAll(accepted)
    }

    @Query("SELECT COUNT(*) FROM aircraft_cache")
    suspend fun count(): Int

    @Query("DELETE FROM aircraft_cache WHERE hex = :hex")
    suspend fun delete(hex: AircraftHex): Int

    @Query("DELETE FROM aircraft_cache WHERE fetched_at < :cutoff")
    suspend fun deleteFetchedBefore(cutoff: Long): Int
}
