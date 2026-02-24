package eu.darken.apl.watch.core.db.types

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.watch.core.WatchId
import eu.darken.apl.watch.core.db.WatchDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchDao {
    @Query("SELECT * FROM watch_base WHERE id = :watchId")
    suspend fun getBase(watchId: WatchId): BaseWatchEntity?

    @Query("SELECT * FROM watch_aircraft WHERE id = :watchId")
    suspend fun getAircraft(watchId: WatchId): AircraftWatchEntity?

    @Query("SELECT * FROM watch_flight WHERE id = :watchId")
    suspend fun getFlight(watchId: WatchId): FlightWatchEntity?

    @Query("SELECT * FROM watch_squawk WHERE id = :watchId")
    suspend fun getSquawk(watchId: WatchId): SquawkWatchEntity?

    @Query("SELECT * FROM watch_location WHERE id = :watchId")
    suspend fun getLocation(watchId: WatchId): LocationWatchEntity?

    @Query("SELECT * FROM watch_base ORDER BY id DESC LIMIT 1")
    fun latest(): Flow<BaseWatchEntity?>

    @Query("SELECT * FROM watch_base ORDER BY id")
    fun current(): Flow<List<BaseWatchEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(watch: BaseWatchEntity): Long

    @Query("DELETE FROM watch_base WHERE id = :watchId")
    suspend fun delete(watchId: WatchId): Int

    @Update
    suspend fun update(entity: BaseWatchEntity): Int

    @Transaction
    suspend fun updateNoteIfDifferent(watchId: WatchId, newNote: String) {
        val entity = getBase(watchId) ?: throw IllegalArgumentException("No watch found for $watchId")

        if (entity.userNote == newNote) {
            log(WatchDatabase.TAG) { "AircraftWatchEntity note is the same, not updating." }
            return
        }

        update(entity.copy(userNote = newNote))
    }

    @Query("UPDATE watch_base SET notification_enabled = :enabled WHERE id = :watchId")
    suspend fun updateNotification(watchId: WatchId, enabled: Boolean)

    @Transaction
    suspend fun insertAircraftWatch(base: BaseWatchEntity, aircraft: AircraftWatchEntity) {
        insert(base)
        insert(aircraft)
    }

    @Transaction
    suspend fun insertFlightWatch(base: BaseWatchEntity, flight: FlightWatchEntity) {
        insert(base)
        insert(flight)
    }

    @Transaction
    suspend fun insertSquawkWatch(base: BaseWatchEntity, squawk: SquawkWatchEntity) {
        insert(base)
        insert(squawk)
    }

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(aircraft: AircraftWatchEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(flight: FlightWatchEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(squawk: SquawkWatchEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(location: LocationWatchEntity): Long

    @Transaction
    suspend fun insertLocationWatch(base: BaseWatchEntity, location: LocationWatchEntity) {
        insert(base)
        insert(location)
    }

    @Query("UPDATE watch_base SET location_latitude = :latitude, location_longitude = :longitude, location_radius = :radius WHERE id = :watchId")
    suspend fun updateLocationCoords(watchId: WatchId, latitude: Double, longitude: Double, radius: Float)

    @Query("UPDATE watch_location SET label = :label WHERE id = :watchId")
    suspend fun updateLocationLabel(watchId: WatchId, label: String)

    @Transaction
    suspend fun updateLocation(watchId: WatchId, latitude: Double, longitude: Double, radius: Float, label: String) {
        updateLocationCoords(watchId, latitude, longitude, radius)
        updateLocationLabel(watchId, label)
    }
}
