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
        val accepted = aircraft.filter { incoming -> incoming.replaces(stored[incoming.hex]) }
        upsertAll(accepted)
    }

    /**
     * Like [upsertNewerWins], but registration, operator, type and description keep their cached
     * value when the incoming observation lacks them: those drop in and out between observations of
     * one aircraft. Everything describing the moment is replaced, a cleared emergency must not linger.
     */
    @Transaction
    suspend fun upsertKeepingReference(aircraft: List<CachedAircraftEntity>) {
        if (aircraft.isEmpty()) return
        val stored = byHexes(aircraft.map { it.hex }).associateBy { it.hex }
        val merged = aircraft.mapNotNull { incoming ->
            val previous = stored[incoming.hex] ?: return@mapNotNull incoming
            if (!incoming.replaces(previous.messageSeenAt)) return@mapNotNull null
            incoming.copy(
                registration = incoming.registration ?: previous.registration,
                operator = incoming.operator ?: previous.operator,
                airframe = incoming.airframe ?: previous.airframe,
                description = incoming.description ?: previous.description,
            )
        }
        upsertAll(merged)
    }

    private fun CachedAircraftEntity.replaces(storedSeenAt: Instant?): Boolean {
        if (storedSeenAt == null) return true
        // An observation of unknown age cannot be proven newer, so it does not replace a known one
        val incomingSeenAt = messageSeenAt ?: return false
        return !incomingSeenAt.isBefore(storedSeenAt)
    }

    @Query("SELECT COUNT(*) FROM aircraft_cache")
    suspend fun count(): Int

    @Query("DELETE FROM aircraft_cache WHERE hex = :hex")
    suspend fun delete(hex: AircraftHex): Int

    @Query("DELETE FROM aircraft_cache WHERE fetched_at < :cutoff")
    suspend fun deleteFetchedBefore(cutoff: Long): Int
}
