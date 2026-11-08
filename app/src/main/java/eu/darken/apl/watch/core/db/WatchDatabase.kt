package eu.darken.apl.watch.core.db

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.main.core.aircraft.AircraftHex
import eu.darken.apl.main.core.aircraft.Callsign
import eu.darken.apl.main.core.aircraft.Registration
import eu.darken.apl.main.core.aircraft.SquawkCode
import eu.darken.apl.watch.core.LocationWatchChangedException
import eu.darken.apl.watch.core.WatchId
import eu.darken.apl.watch.core.db.server.WatchServerDao
import eu.darken.apl.watch.core.db.server.WatchServerTombstoneEntity
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
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
        ).addMigrations(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
        ).build()
    }

    private val watchDao: WatchDao
        get() = database.watches()

    private val allocationLock = Mutex()

    /** Two writers checking the caps at once could both take the last slot. Not reentrant, [block] must not import. */
    suspend fun <T> withAllocation(block: suspend () -> T): T = allocationLock.withLock { block() }

    /** Either table changing triggers a read of both together, so no watch pairs an old status with a new subtype row. */
    val watches: Flow<List<Watch>>
        get() = combine(watchDao.current(), watchDao.currentAircraft()) { _, _ -> }
            .map { database.withTransaction { watchDao.getAll().toWatches() } }

    private suspend fun List<BaseWatchEntity>.toWatches(): List<Watch> = mapNotNull { base ->
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

    val definitionRevisions: Flow<Map<WatchId, Long>>
        get() = watchDao.current()
            .map { bases -> bases.associate { it.id to it.definitionRevision } }
            .distinctUntilChanged()

    /** All watches as of one moment, each with the subtype row it had then. */
    suspend fun snapshot(): List<Watch> = database.withTransaction {
        watchDao.getAll().toWatches()
    }

    suspend fun createAircraft(hex: AircraftHex, note: String): AircraftWatch = withContext(NonCancellable) {
        log(TAG) { "createAircraft($hex, $note)" }
        database.withTransaction {
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
    }

    suspend fun createFlight(callsign: Callsign, note: String): FlightWatch = withContext(NonCancellable) {
        log(TAG) { "createFlight($callsign, $note)" }
        database.withTransaction {
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
    }

    suspend fun createSquawk(code: SquawkCode, note: String): SquawkWatch = withContext(NonCancellable) {
        log(TAG) { "createSquawk($code, $note)" }
        database.withTransaction {
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
    }

    suspend fun createLocation(
        latitude: Double,
        longitude: Double,
        radiusInMeters: Float,
        minAltitudeFt: Int?,
        maxAltitudeFt: Int?,
        label: String,
        note: String,
    ): LocationWatch = withContext(NonCancellable) {
        log(TAG) { "createLocation($latitude, $longitude, $radiusInMeters, $minAltitudeFt..$maxAltitudeFt, $label, $note)" }
        requireAltitudeBand(minAltitudeFt, maxAltitudeFt)
        database.withTransaction {
            val base = BaseWatchEntity(
                watchType = LocationWatchEntity.TYPE_KEY,
                userNote = note,
                latitude = latitude,
                longitude = longitude,
                radius = radiusInMeters,
                minAltitudeFt = minAltitudeFt,
                maxAltitudeFt = maxAltitudeFt,
            )
            val specific = LocationWatchEntity(
                id = base.id,
                label = label,
            )
            watchDao.insertLocationWatch(base, specific)
            LocationWatch(base, specific)
        }
    }

    suspend fun deleteWatch(id: WatchId) = withContext(NonCancellable) {
        log(TAG) { "deleteWatch($id)" }
        database.withTransaction {
            removeServerState(setOf(id))
            watchDao.delete(id)
        }
    }

    suspend fun deleteBatch(ids: Set<WatchId>) = withContext(NonCancellable) {
        log(TAG) { "deleteBatch(${ids.size} ids)" }
        database.withTransaction {
            removeServerState(ids)
            watchDao.deleteAll(ids)
        }
    }

    /** Only a watch the server has heard of leaves a tombstone, for the installation that sent it. */
    private suspend fun removeServerState(ids: Set<WatchId>) {
        val server = database.server()
        val deletedAt = Instant.now()
        val tombstones = server.getStates(ids).map { WatchServerTombstoneEntity(it.watchId, it.installationId, deletedAt) }
        if (tombstones.isNotEmpty()) server.insertTombstones(tombstones)
        server.deleteStates(ids)
        server.deleteRollupsFor(ids)
        server.deleteOutboxFor(ids)
    }

    suspend fun updateNote(id: WatchId, note: String) {
        log(TAG) { "updateNote($id, $note)" }
        watchDao.updateNoteIfDifferent(id, note)
    }

    suspend fun updateNotification(id: WatchId, enabled: Boolean) {
        log(TAG) { "updateNotification($id, $enabled)" }
        watchDao.updateNotification(id, enabled)
    }

    /**
     * Moving or resizing the area, or changing its altitude band, makes it a different watch: what was
     * seen in the old area is removed with the change, and every answer still on its way for the old
     * area is refused by the revision. A new label alone keeps everything.
     *
     * With [expectedRevision] nothing is saved over an area that changed since, see [LocationWatchChangedException].
     */
    suspend fun updateLocation(
        id: WatchId,
        latitude: Double,
        longitude: Double,
        radiusInMeters: Float,
        minAltitudeFt: Int?,
        maxAltitudeFt: Int?,
        label: String,
        expectedRevision: Long? = null,
    ) = withContext(NonCancellable) {
        log(TAG) {
            "updateLocation($id, $latitude, $longitude, $radiusInMeters, $minAltitudeFt..$maxAltitudeFt, $label, $expectedRevision)"
        }
        requireAltitudeBand(minAltitudeFt, maxAltitudeFt)
        database.withTransaction {
            val base = watchDao.getBase(id) ?: return@withTransaction
            if (expectedRevision != null && base.definitionRevision != expectedRevision) {
                throw LocationWatchChangedException(id)
            }
            val redefined = base.latitude != latitude || base.longitude != longitude || base.radius != radiusInMeters ||
                    base.minAltitudeFt != minAltitudeFt || base.maxAltitudeFt != maxAltitudeFt
            watchDao.updateLocation(id, latitude, longitude, radiusInMeters, minAltitudeFt, maxAltitudeFt, label)
            if (!redefined) return@withTransaction
            log(TAG) { "updateLocation($id): area or altitude band changed, starting over" }
            watchDao.startNewDefinition(id)
        }
    }

    suspend fun importAircraft(base: BaseWatchEntity, specific: AircraftWatchEntity): AircraftWatch =
        withContext(NonCancellable) {
            log(TAG) { "importAircraft(${base.id}, ${specific.hexCode})" }
            importWatch {
                watchDao.insertAircraftWatch(base, specific)
                AircraftWatch(base, specific)
            }
        }

    suspend fun importFlight(base: BaseWatchEntity, specific: FlightWatchEntity): FlightWatch =
        withContext(NonCancellable) {
            log(TAG) { "importFlight(${base.id}, ${specific.callsign})" }
            importWatch {
                watchDao.insertFlightWatch(base, specific)
                FlightWatch(base, specific)
            }
        }

    suspend fun importSquawk(base: BaseWatchEntity, specific: SquawkWatchEntity): SquawkWatch =
        withContext(NonCancellable) {
            log(TAG) { "importSquawk(${base.id}, ${specific.code})" }
            importWatch {
                watchDao.insertSquawkWatch(base, specific)
                SquawkWatch(base, specific)
            }
        }

    suspend fun importLocation(base: BaseWatchEntity, specific: LocationWatchEntity): LocationWatch =
        withContext(NonCancellable) {
            log(TAG) { "importLocation(${base.id}, ${specific.label})" }
            requireAltitudeBand(base.minAltitudeFt, base.maxAltitudeFt)
            importWatch {
                watchDao.insertLocationWatch(base, specific)
                LocationWatch(base, specific)
            }
        }

    private suspend fun <W : Watch> importWatch(insert: suspend () -> W): W = withAllocation {
        database.withTransaction { insert() }
    }

    suspend fun hasAircraftWatch(hex: String): Boolean = watchDao.hasAircraftWatch(hex.trim().uppercase())

    suspend fun hasFlightWatch(callsign: String): Boolean = watchDao.hasFlightWatch(callsign.trim().uppercase())

    suspend fun hasSquawkWatch(code: String): Boolean = watchDao.hasSquawkWatch(code.trim().uppercase())

    /** A different altitude band makes a different watch, two watches without one are the same. */
    suspend fun hasLocationWatch(
        latitude: Double,
        longitude: Double,
        radius: Float,
        minAltitudeFt: Int?,
        maxAltitudeFt: Int?,
    ): Boolean = watchDao.hasLocationWatch(latitude, longitude, radius, minAltitudeFt, maxAltitudeFt)

    private fun requireAltitudeBand(minAltitudeFt: Int?, maxAltitudeFt: Int?) {
        require(LocationWatch.altitudeBandAllowed(minAltitudeFt, maxAltitudeFt)) {
            "Altitude band $minAltitudeFt..$maxAltitudeFt ft is invalid"
        }
    }

    suspend fun watchCount(): Int = watchDao.count()

    val server: WatchServerDao
        get() = database.server()

    interface ServerWriter {
        val server: WatchServerDao
        suspend fun watches(): List<Watch>
    }

    /** Runs [block] as one transaction, the watches it reads cannot change under it. */
    suspend fun <T> serverTransaction(block: suspend ServerWriter.() -> T): T = withContext(NonCancellable) {
        database.withTransaction {
            val writer = object : ServerWriter {
                override val server: WatchServerDao = database.server()
                override suspend fun watches(): List<Watch> = watchDao.getAll().toWatches()
            }
            writer.block()
        }
    }

    /** For a new identity, whose server knows nothing of what was synced before. */
    suspend fun wipeServerState() = withContext(NonCancellable) {
        log(TAG) { "wipeServerState()" }
        database.withTransaction {
            val server = database.server()
            server.deleteAllStates()
            server.deleteAllTombstones()
            server.deleteAllSync()
            server.deleteAllEvents()
            server.deleteAllOutbox()
            server.deleteAllRollups()
        }
    }

    /** Only fills in watches without one, a stored registration is never replaced by a cache copy. */
    suspend fun backfillRegistrations(registrations: Map<WatchId, Registration?>) = withContext(NonCancellable) {
        database.withTransaction {
            registrations.forEach { (watchId, registration) ->
                if (!registration.isNullOrBlank()) watchDao.fillMissingRegistration(watchId, registration)
            }
        }
    }

    companion object {
        internal val TAG = logTag("Watch", "Database")

        /**
         * Drops the app's own check history and the server history it paged in, and adds the location
         * watches' altitude band (none for every existing watch). `watch_base` is rebuilt
         * under a new name and renamed back, never renamed away first: SQLite 3.26+ would then point
         * the subtype tables' foreign keys at the old table.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `watch_checks`")
                db.execSQL("DROP TABLE IF EXISTS `watch_history_hours`")
                db.execSQL("DROP TABLE IF EXISTS `watch_history_sync`")
                db.execSQL("DROP TABLE IF EXISTS `watch_history_applied`")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `_new_watch_base` (`id` TEXT NOT NULL, `created_at` INTEGER NOT NULL, " +
                            "`type` TEXT NOT NULL, `notification_enabled` INTEGER NOT NULL, `user_note` TEXT NOT NULL, " +
                            "`location_latitude` REAL, `location_longitude` REAL, `location_radius` REAL, " +
                            "`location_min_altitude_ft` INTEGER, `location_max_altitude_ft` INTEGER, " +
                            "`definition_revision` INTEGER NOT NULL DEFAULT 0, " +
                            "`local_generation` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))"
                )
                val columns = "`id`, `created_at`, `type`, `notification_enabled`, `user_note`, " +
                        "`location_latitude`, `location_longitude`, `location_radius`, " +
                        "`definition_revision`, `local_generation`"
                db.execSQL("INSERT INTO `_new_watch_base` ($columns) SELECT $columns FROM `watch_base`")
                db.execSQL("DROP TABLE `watch_base`")
                db.execSQL("ALTER TABLE `_new_watch_base` RENAME TO `watch_base`")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `watch_checks` ADD COLUMN `imported` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `watch_base` ADD COLUMN `local_generation` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `watch_server_state` (`watch_id` TEXT NOT NULL, " +
                            "`installation_id` TEXT NOT NULL, `server_watch_id` TEXT, `server_revision` INTEGER, " +
                            "`upload_state` TEXT NOT NULL, `sent_generation` INTEGER, " +
                            "`acked_definition_revision` INTEGER, `status` TEXT, `pause_reason` TEXT, `presence` TEXT, " +
                            "`event_seq` INTEGER NOT NULL DEFAULT 0, `last_seen_at` INTEGER, `last_seen_aircraft_id` TEXT, " +
                            "`backfill` TEXT, `last_match_seq` INTEGER, " +
                            "`last_match_at` INTEGER, `last_match_aircraft_ids` TEXT, `last_match_total` INTEGER, " +
                            "`updated_at` INTEGER NOT NULL, PRIMARY KEY(`watch_id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `watch_server_tombstones` (`watch_id` TEXT NOT NULL, " +
                            "`installation_id` TEXT NOT NULL, `deleted_at` INTEGER NOT NULL, PRIMARY KEY(`watch_id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `watch_server_sync` (`installation_id` TEXT NOT NULL, " +
                            "`list_revision` INTEGER, `list_state` TEXT, `event_cursor` INTEGER, `rollup_cursor` INTEGER, " +
                            "`phase` TEXT NOT NULL, `last_poll_at` INTEGER, `uploads_not_before` INTEGER, " +
                            "`events_not_before` INTEGER, `rollups_not_before` INTEGER, `poll_run_id` TEXT, " +
                            "`poll_run_individual` INTEGER NOT NULL DEFAULT 0, `updated_at` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`installation_id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `watch_server_events` (`seq` INTEGER NOT NULL, " +
                            "`installation_id` TEXT NOT NULL, `kind` TEXT NOT NULL, `observed_at` INTEGER NOT NULL, " +
                            "`server_watch_id` TEXT, `watch_id` TEXT, `revision` INTEGER, `aircraft_ids` TEXT, " +
                            "`total_matching` INTEGER, `gap_from` INTEGER, `gap_until` INTEGER, " +
                            "`received_at` INTEGER NOT NULL, PRIMARY KEY(`seq`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_watch_server_events_observed_at` " +
                            "ON `watch_server_events` (`observed_at`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_watch_server_events_server_watch_id_revision` " +
                            "ON `watch_server_events` (`server_watch_id`, `revision`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `watch_notification_outbox` (`id` TEXT NOT NULL, " +
                            "`installation_id` TEXT NOT NULL, `kind` TEXT NOT NULL, `watch_id` TEXT, `event_seq` INTEGER, " +
                            "`aircraft_ids` TEXT, `total_matching` INTEGER, `observed_at` INTEGER, " +
                            "`summary_count` INTEGER NOT NULL DEFAULT 0, `summary_watch_ids` TEXT, " +
                            "`created_at` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `watch_server_rollups` (`server_watch_id` TEXT NOT NULL, " +
                            "`revision` INTEGER NOT NULL, `hour` INTEGER NOT NULL, `source` TEXT NOT NULL, " +
                            "`watch_id` TEXT NOT NULL, `installation_id` TEXT NOT NULL, `seq` INTEGER NOT NULL, " +
                            "`evaluation_basis` TEXT NOT NULL, `aircraft_count` INTEGER NOT NULL, `coverage` TEXT NOT NULL, " +
                            "`covered_minutes` INTEGER, `is_final` INTEGER NOT NULL, `last_seen_at` INTEGER, " +
                            "`last_seen_aircraft_id` TEXT, PRIMARY KEY(`server_watch_id`, `revision`, `hour`, `source`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_watch_server_rollups_watch_id_hour` " +
                            "ON `watch_server_rollups` (`watch_id`, `hour`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_watch_server_rollups_hour` ON `watch_server_rollups` (`hour`)"
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `watch_aircraft` ADD COLUMN `last_registration` TEXT")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `watch_base` ADD COLUMN `definition_revision` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `watch_history_hours` (`watch_id` TEXT NOT NULL, " +
                            "`hour` INTEGER NOT NULL, `aircraft_count` INTEGER NOT NULL, `coverage` TEXT NOT NULL, " +
                            "`is_final` INTEGER NOT NULL, PRIMARY KEY(`watch_id`, `hour`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_watch_history_hours_hour` ON `watch_history_hours` (`hour`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `watch_history_sync` (`watch_id` TEXT NOT NULL, " +
                            "`definition_revision` INTEGER NOT NULL, `window_from` INTEGER, `window_until` INTEGER, " +
                            "`cursor_json` TEXT, `last_seen_at` INTEGER, `last_seen_aircraft_id` TEXT, " +
                            "`evaluation_basis` TEXT, `restricted_tier` TEXT, `next_eligible_at` INTEGER NOT NULL, " +
                            "`backoff_step` INTEGER NOT NULL, `last_pass_at` INTEGER, `updated_at` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`watch_id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `watch_history_applied` (`operation_id` TEXT NOT NULL, " +
                            "`applied_at` INTEGER NOT NULL, PRIMARY KEY(`operation_id`))"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `watch_base` ADD COLUMN `last_check_at` INTEGER")
                db.execSQL("ALTER TABLE `watch_base` ADD COLUMN `last_check_outcome` TEXT")
                db.execSQL("ALTER TABLE `watch_base` ADD COLUMN `last_check_reason` TEXT")
                db.execSQL("ALTER TABLE `watch_checks` ADD COLUMN `operation_id` TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_watch_checks_watch_id_checked_at` ON `watch_checks` (`watch_id`, `checked_at`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_watch_checks_checked_at` ON `watch_checks` (`checked_at`)")
            }
        }

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
