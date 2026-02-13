package eu.darken.apl.common.flight.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface FlightRouteDao {

    @Query("SELECT * FROM route_cache WHERE aircraft_hex = :hex ORDER BY seen_at DESC LIMIT 1")
    fun byHex(hex: String): Flow<FlightRouteEntity?>

    @Query("SELECT * FROM route_cache WHERE aircraft_hex = :hex ORDER BY seen_at DESC")
    fun historyByHex(hex: String): Flow<List<FlightRouteEntity>>

    @Query("SELECT * FROM route_cache WHERE origin_icao = :icao OR destination_icao = :icao ORDER BY seen_at DESC")
    fun byAirport(icao: String): Flow<List<FlightRouteEntity>>

    @Query("SELECT * FROM route_cache WHERE origin_icao = :icao ORDER BY seen_at DESC")
    fun byOrigin(icao: String): Flow<List<FlightRouteEntity>>

    @Query("SELECT * FROM route_cache WHERE destination_icao = :icao ORDER BY seen_at DESC")
    fun byDestination(icao: String): Flow<List<FlightRouteEntity>>

    @Insert
    suspend fun insert(entity: FlightRouteEntity)

    @Query("DELETE FROM route_cache WHERE fetched_at < :instant")
    suspend fun deleteOlderThan(instant: Instant)
}
