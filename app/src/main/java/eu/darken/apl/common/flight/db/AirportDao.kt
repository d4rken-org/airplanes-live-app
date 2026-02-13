package eu.darken.apl.common.flight.db

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AirportDao {

    @Query("SELECT * FROM airports WHERE icao_code = :icao")
    fun byIcao(icao: String): Flow<AirportEntity?>

    @Query(
        """
        INSERT INTO airports (icao_code, iata_code, name, country) VALUES (:icao, :iata, :name, :country)
        ON CONFLICT(icao_code) DO UPDATE SET
            iata_code = COALESCE(:iata, airports.iata_code),
            name = COALESCE(:name, airports.name),
            country = COALESCE(:country, airports.country)
        """
    )
    suspend fun upsert(icao: String, iata: String?, name: String?, country: String?)
}
