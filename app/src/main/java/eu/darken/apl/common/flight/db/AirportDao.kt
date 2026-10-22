package eu.darken.apl.common.flight.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface AirportDao {

    @Query("SELECT * FROM airports WHERE icao_code = :icao")
    fun byIcao(icao: String): Flow<AirportEntity?>

    @Query("INSERT OR IGNORE INTO airports (icao_code) VALUES (:icao)")
    suspend fun insertIfMissing(icao: String)

    @Query(
        """
        UPDATE airports SET
            iata_code = COALESCE(:iata, iata_code),
            name = COALESCE(:name, name),
            country = COALESCE(:country, country),
            latitude = CASE WHEN :latitude IS NOT NULL AND :longitude IS NOT NULL THEN :latitude ELSE latitude END,
            longitude = CASE WHEN :latitude IS NOT NULL AND :longitude IS NOT NULL THEN :longitude ELSE longitude END
        WHERE icao_code = :icao
        """
    )
    suspend fun update(
        icao: String,
        iata: String?,
        name: String?,
        country: String?,
        latitude: Double?,
        longitude: Double?,
    )

    /** SQLite before 3.24, Android 10 and older, has no INSERT ... ON CONFLICT DO UPDATE. */
    @Transaction
    suspend fun upsert(
        icao: String,
        iata: String?,
        name: String?,
        country: String?,
        latitude: Double?,
        longitude: Double?,
    ) {
        insertIfMissing(icao)
        update(icao, iata, name, country, latitude, longitude)
    }
}
