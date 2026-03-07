package eu.darken.apl.main.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import eu.darken.apl.main.core.aircraft.AircraftHex
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedAircraftDao {
    @Query("SELECT * FROM aircraft_cache WHERE hex = :hex")
    fun byHex(hex: AircraftHex): Flow<CachedAircraftEntity?>

    @Query("SELECT * FROM aircraft_cache ")
    fun current(): Flow<List<CachedAircraftEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateUsers(aircraft: List<CachedAircraftEntity>)

    @Query("SELECT COUNT(*) FROM aircraft_cache")
    suspend fun count(): Int

    @Query("DELETE FROM aircraft_cache WHERE hex = :hex")
    suspend fun delete(hex: AircraftHex): Int
}