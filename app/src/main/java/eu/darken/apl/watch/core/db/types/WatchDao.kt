package eu.darken.apl.watch.core.db.types

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.main.core.aircraft.Registration
import eu.darken.apl.watch.core.WatchId
import eu.darken.apl.watch.core.db.WatchDatabase
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface WatchDao {
    @Query("SELECT * FROM watch_base WHERE id = :watchId")
    suspend fun getBase(watchId: WatchId): BaseWatchEntity?

    @Query("SELECT * FROM watch_aircraft WHERE id = :watchId")
    suspend fun getAircraft(watchId: WatchId): AircraftWatchEntity?

    /** Only the base table drives [current], this one tells it about subtype edits. */
    @Query("SELECT * FROM watch_aircraft")
    fun currentAircraft(): Flow<List<AircraftWatchEntity>>

    @Query("UPDATE watch_aircraft SET last_registration = :registration WHERE id = :watchId AND last_registration IS NOT :registration")
    suspend fun updateLastRegistration(watchId: WatchId, registration: Registration): Int

    @Query("UPDATE watch_aircraft SET last_registration = :registration WHERE id = :watchId AND last_registration IS NULL")
    suspend fun fillMissingRegistration(watchId: WatchId, registration: Registration): Int

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

    @Query("SELECT * FROM watch_base ORDER BY id")
    suspend fun getAll(): List<BaseWatchEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(watch: BaseWatchEntity): Long

    @Query("DELETE FROM watch_base WHERE id = :watchId")
    suspend fun delete(watchId: WatchId): Int

    @Query("DELETE FROM watch_base WHERE id IN (:watchIds)")
    suspend fun deleteAll(watchIds: Set<WatchId>): Int

    @Update
    suspend fun update(entity: BaseWatchEntity): Int

    @Transaction
    suspend fun updateNoteIfDifferent(watchId: WatchId, newNote: String) {
        // Removed while its details were open
        val entity = getBase(watchId) ?: run {
            log(WatchDatabase.TAG) { "updateNoteIfDifferent(): $watchId is gone, not updating." }
            return
        }

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

    @Query("UPDATE watch_base SET definition_revision = definition_revision + 1, local_generation = local_generation + 1, last_check_at = NULL, last_check_outcome = NULL, last_check_reason = NULL WHERE id = :watchId")
    suspend fun startNewDefinition(watchId: WatchId)

    @Query("UPDATE watch_base SET last_check_at = :at, last_check_outcome = :outcome, last_check_reason = :reason WHERE id = :watchId")
    suspend fun updateLastCheck(watchId: WatchId, at: Instant, outcome: String, reason: String?)

    @Query("UPDATE watch_base SET last_check_at = :at, last_check_outcome = :outcome, last_check_reason = :reason WHERE id = :watchId AND definition_revision = :revision")
    suspend fun updateLastCheckIfRevision(watchId: WatchId, revision: Long, at: Instant, outcome: String, reason: String?): Int

    @Query("SELECT COUNT(*) FROM watch_base")
    suspend fun count(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM watch_aircraft WHERE UPPER(TRIM(hex_code)) = :hex)")
    suspend fun hasAircraftWatch(hex: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM watch_flight WHERE UPPER(TRIM(callsign)) = :callsign)")
    suspend fun hasFlightWatch(callsign: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM watch_squawk WHERE UPPER(TRIM(code)) = :code)")
    suspend fun hasSquawkWatch(code: String): Boolean

    @Query(
        "SELECT EXISTS(SELECT 1 FROM watch_location JOIN watch_base ON watch_base.id = watch_location.id " +
                "WHERE location_latitude = :latitude AND location_longitude = :longitude AND location_radius = :radius)"
    )
    suspend fun hasLocationWatch(latitude: Double, longitude: Double, radius: Float): Boolean
}
