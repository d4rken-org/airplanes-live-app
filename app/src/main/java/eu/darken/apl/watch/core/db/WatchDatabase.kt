package eu.darken.apl.watch.core.db

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.main.core.aircraft.AircraftHex
import eu.darken.apl.main.core.aircraft.Callsign
import eu.darken.apl.main.core.aircraft.SquawkCode
import eu.darken.apl.watch.core.WatchId
import eu.darken.apl.watch.core.db.history.WatchCheckDao
import eu.darken.apl.watch.core.db.types.AircraftWatchEntity
import eu.darken.apl.watch.core.db.types.BaseWatchEntity
import eu.darken.apl.watch.core.db.types.FlightWatchEntity
import eu.darken.apl.watch.core.db.types.LocationWatchEntity
import eu.darken.apl.watch.core.db.types.SquawkWatchEntity
import eu.darken.apl.watch.core.db.types.WatchDao
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.FlightWatch
import eu.darken.apl.watch.core.types.LocationWatch
import eu.darken.apl.watch.core.types.SquawkWatch
import eu.darken.apl.watch.core.types.Watch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchDatabase @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val database by lazy {
        Room.databaseBuilder(
            context,
            WatchRoomDb::class.java, "watch"
        ).addMigrations(MIGRATION_1_2).build()
    }

    private val watchDao: WatchDao
        get() = database.watches()

    val watches: Flow<List<Watch>>
        get() = watchDao.current().map { bases ->
            bases.mapNotNull { base ->
                try {
                    when (base.watchType) {
                        AircraftWatchEntity.TYPE_KEY -> AircraftWatch(base, watchDao.getAircraft(base.id)!!)
                        FlightWatchEntity.TYPE_KEY -> FlightWatch(base, watchDao.getFlight(base.id)!!)
                        SquawkWatchEntity.TYPE_KEY -> SquawkWatch(base, watchDao.getSquawk(base.id)!!)
                        LocationWatchEntity.TYPE_KEY -> LocationWatch(base, watchDao.getLocation(base.id)!!)
                        else -> {
                            log(TAG, WARN) { "Unknown watch type: ${base.watchType} for ${base.id}" }
                            null
                        }
                    }
                } catch (e: NullPointerException) {
                    log(TAG, WARN) { "Missing subtype row for ${base.id} (${base.watchType})" }
                    null
                }
            }
        }

    suspend fun createAircraft(hex: AircraftHex, note: String): AircraftWatch = withContext(NonCancellable) {
        log(TAG) { "createAircraft($hex, $note)" }
        val base = BaseWatchEntity(
            watchType = AircraftWatchEntity.TYPE_KEY,
            userNote = note,
        )
        val specific = AircraftWatchEntity(
            id = base.id,
            hexCode = hex,
        )
        watchDao.insertAircraftWatch(base, specific)
        AircraftWatch(base, specific)
    }

    suspend fun createFlight(callsign: Callsign, note: String): FlightWatch = withContext(NonCancellable) {
        log(TAG) { "createFlight($callsign, $note)" }
        val base = BaseWatchEntity(
            watchType = FlightWatchEntity.TYPE_KEY,
            userNote = note,
        )
        val specific = FlightWatchEntity(
            id = base.id,
            callsign = callsign,
        )
        watchDao.insertFlightWatch(base, specific)
        FlightWatch(base, specific)
    }

    suspend fun createSquawk(code: SquawkCode, note: String): SquawkWatch = withContext(NonCancellable) {
        log(TAG) { "createSquawk($code, $note)" }
        val base = BaseWatchEntity(
            watchType = SquawkWatchEntity.TYPE_KEY,
            userNote = note,
        )
        val specific = SquawkWatchEntity(
            id = base.id,
            code = code,
        )
        watchDao.insertSquawkWatch(base, specific)
        SquawkWatch(base, specific)
    }

    suspend fun createLocation(
        latitude: Double,
        longitude: Double,
        radiusInMeters: Float,
        label: String,
        note: String,
    ): LocationWatch = withContext(NonCancellable) {
        log(TAG) { "createLocation($latitude, $longitude, $radiusInMeters, $label, $note)" }
        val base = BaseWatchEntity(
            watchType = LocationWatchEntity.TYPE_KEY,
            userNote = note,
            latitude = latitude,
            longitude = longitude,
            radius = radiusInMeters,
        )
        val specific = LocationWatchEntity(
            id = base.id,
            label = label,
        )
        watchDao.insertLocationWatch(base, specific)
        LocationWatch(base, specific)
    }

    suspend fun deleteWatch(id: WatchId) = withContext(NonCancellable) {
        log(TAG) { "deleteWatch($id)" }
        watchDao.delete(id)
    }

    suspend fun updateNote(id: WatchId, note: String) {
        log(TAG) { "updateNote($id, $note)" }
        watchDao.updateNoteIfDifferent(id, note)
    }

    suspend fun updateNotification(id: WatchId, enabled: Boolean) {
        log(TAG) { "updateNotification($id, $enabled)" }
        watchDao.updateNotification(id, enabled)
    }

    suspend fun updateLocation(id: WatchId, latitude: Double, longitude: Double, radiusInMeters: Float, label: String) {
        log(TAG) { "updateLocation($id, $latitude, $longitude, $radiusInMeters, $label)" }
        watchDao.updateLocation(id, latitude, longitude, radiusInMeters, label)
    }

    val checks: WatchCheckDao
        get() = database.checks()

    companion object {
        internal val TAG = logTag("Watch", "Database")

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `watch_location` (
                        `id` TEXT NOT NULL,
                        `label` TEXT NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`id`) REFERENCES `watch_base`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )"""
                )
                db.execSQL("ALTER TABLE `watch_checks` ADD COLUMN `seen_hexes` TEXT")
            }
        }
    }
}
