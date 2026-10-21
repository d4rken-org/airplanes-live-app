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
import eu.darken.apl.watch.core.WatchId
import eu.darken.apl.watch.core.db.history.WatchCheckDao
import eu.darken.apl.watch.core.db.history.WatchCheckEntity
import eu.darken.apl.watch.core.db.history.WatchHistoryAppliedEntity
import eu.darken.apl.watch.core.db.history.WatchHistoryDao
import eu.darken.apl.watch.core.db.history.WatchHistoryHourEntity
import eu.darken.apl.watch.core.db.history.WatchHistorySyncEntity
import eu.darken.apl.watch.core.db.server.WatchServerDao
import eu.darken.apl.watch.core.db.server.WatchServerStateEntity
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
import eu.darken.apl.watch.core.types.WatchCheckOutcome
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

    /** Watches and their checks as of one moment, so a backup never pairs checks with an edited area. */
    suspend fun snapshot(): Pair<List<Watch>, List<WatchCheckEntity>> = database.withTransaction {
        watchDao.getAll().toWatches() to database.checks().getAll()
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
        label: String,
        note: String,
    ): LocationWatch = withContext(NonCancellable) {
        log(TAG) { "createLocation($latitude, $longitude, $radiusInMeters, $label, $note)" }
        database.withTransaction {
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
    }

    suspend fun deleteWatch(id: WatchId) = withContext(NonCancellable) {
        log(TAG) { "deleteWatch($id)" }
        database.withTransaction {
            database.checks().deleteForWatch(id)
            database.history().deleteHoursFor(setOf(id))
            database.history().deleteSyncFor(setOf(id))
            removeServerState(setOf(id))
            watchDao.delete(id)
        }
    }

    suspend fun deleteBatch(ids: Set<WatchId>) = withContext(NonCancellable) {
        log(TAG) { "deleteBatch(${ids.size} ids)" }
        database.withTransaction {
            database.checks().deleteForWatches(ids)
            database.history().deleteHoursFor(ids)
            database.history().deleteSyncFor(ids)
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
     * Moving or resizing the area makes it a different watch: what was seen in the old area is
     * removed with the change, and every answer still on its way for the old area is refused by the
     * revision. A new label alone keeps everything.
     */
    suspend fun updateLocation(
        id: WatchId,
        latitude: Double,
        longitude: Double,
        radiusInMeters: Float,
        label: String,
    ) = withContext(NonCancellable) {
        log(TAG) { "updateLocation($id, $latitude, $longitude, $radiusInMeters, $label)" }
        database.withTransaction {
            val base = watchDao.getBase(id) ?: return@withTransaction
            val moved = base.latitude != latitude || base.longitude != longitude || base.radius != radiusInMeters
            watchDao.updateLocation(id, latitude, longitude, radiusInMeters, label)
            if (!moved) return@withTransaction
            log(TAG) { "updateLocation($id): area changed, starting over" }
            watchDao.startNewDefinition(id)
            database.checks().deleteForWatch(id)
            database.history().deleteHoursFor(setOf(id))
            database.history().deleteSyncFor(setOf(id))
        }
    }

    /** An imported watch's [checks] land in the same transaction, so no reader sees the watch without them. */
    suspend fun importAircraft(
        base: BaseWatchEntity,
        specific: AircraftWatchEntity,
        checks: List<WatchCheckEntity> = emptyList(),
    ): AircraftWatch = withContext(NonCancellable) {
        log(TAG) { "importAircraft(${base.id}, ${specific.hexCode})" }
        importWatch(base, checks) {
            watchDao.insertAircraftWatch(base, specific)
            AircraftWatch(base, specific)
        }
    }

    suspend fun importFlight(
        base: BaseWatchEntity,
        specific: FlightWatchEntity,
        checks: List<WatchCheckEntity> = emptyList(),
    ): FlightWatch = withContext(NonCancellable) {
        log(TAG) { "importFlight(${base.id}, ${specific.callsign})" }
        importWatch(base, checks) {
            watchDao.insertFlightWatch(base, specific)
            FlightWatch(base, specific)
        }
    }

    suspend fun importSquawk(
        base: BaseWatchEntity,
        specific: SquawkWatchEntity,
        checks: List<WatchCheckEntity> = emptyList(),
    ): SquawkWatch = withContext(NonCancellable) {
        log(TAG) { "importSquawk(${base.id}, ${specific.code})" }
        importWatch(base, checks) {
            watchDao.insertSquawkWatch(base, specific)
            SquawkWatch(base, specific)
        }
    }

    suspend fun importLocation(
        base: BaseWatchEntity,
        specific: LocationWatchEntity,
        checks: List<WatchCheckEntity> = emptyList(),
    ): LocationWatch = withContext(NonCancellable) {
        log(TAG) { "importLocation(${base.id}, ${specific.label})" }
        importWatch(base, checks) {
            watchDao.insertLocationWatch(base, specific)
            LocationWatch(base, specific)
        }
    }

    private suspend fun <W : Watch> importWatch(
        base: BaseWatchEntity,
        checks: List<WatchCheckEntity>,
        insert: suspend () -> W,
    ): W = withAllocation {
        database.withTransaction {
            val watch = insert()
            if (checks.isNotEmpty()) database.checks().insertAll(checks.map { it.copy(watchId = base.id, imported = true) })
            watch
        }
    }

    suspend fun hasAircraftWatch(hex: String): Boolean = watchDao.hasAircraftWatch(hex.trim().uppercase())

    suspend fun hasFlightWatch(callsign: String): Boolean = watchDao.hasFlightWatch(callsign.trim().uppercase())

    suspend fun hasSquawkWatch(code: String): Boolean = watchDao.hasSquawkWatch(code.trim().uppercase())

    suspend fun hasLocationWatch(latitude: Double, longitude: Double, radius: Float): Boolean =
        watchDao.hasLocationWatch(latitude, longitude, radius)

    suspend fun watchCount(): Int = watchDao.count()

    val checks: WatchCheckDao
        get() = database.checks()

    val history: WatchHistoryDao
        get() = database.history()

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

    sealed interface CheckApplication {
        /** The watch is gone or now matches something else than what was evaluated. */
        data object Stale : CheckApplication

        /** This operation's answer is already in, nothing was written. */
        data object Duplicate : CheckApplication

        data class Applied(val previousCount: Int?) : CheckApplication
    }

    /**
     * Writes a conclusive check and the watch's status together, for the [revision] the request was
     * built from only. The previous check is read in the same transaction, it decides whether the
     * new one is a fresh hit. With [legacyOnly], a watch the app no longer checks itself is stale too.
     */
    suspend fun applyCheck(
        watchId: WatchId,
        revision: Long,
        at: Instant,
        outcome: WatchCheckOutcome,
        aircraftCount: Int,
        seenHexes: Set<String>? = null,
        operationId: String? = null,
        registration: Registration? = null,
        legacyOnly: Boolean = false,
    ): CheckApplication = withContext(NonCancellable) {
        database.withTransaction {
            val base = watchDao.getBase(watchId)
            if (base == null || base.definitionRevision != revision) return@withTransaction CheckApplication.Stale
            if (legacyOnly && !WatchServerStateEntity.isAppChecked(database.server().getState(watchId))) {
                return@withTransaction CheckApplication.Stale
            }
            val checks = database.checks()
            if (operationId != null && checks.countForOperation(watchId, operationId) > 0) {
                return@withTransaction CheckApplication.Duplicate
            }
            val previous = checks.getLastLiveCheck(watchId)
            checks.insert(
                WatchCheckEntity(
                    watchId = watchId,
                    checkedAt = at,
                    aircraftcount = aircraftCount,
                    seenHexes = seenHexes?.takeIf { it.isNotEmpty() }?.joinToString(","),
                    operationId = operationId,
                )
            )
            watchDao.updateLastCheck(watchId, at, outcome.raw, null)
            if (base.watchType == AircraftWatchEntity.TYPE_KEY && !registration.isNullOrBlank()) {
                watchDao.updateLastRegistration(watchId, registration)
            }
            CheckApplication.Applied(previous?.aircraftcount)
        }
    }

    /** Only fills in watches without one, a registration a check wrote is newer than any cache copy. */
    suspend fun backfillRegistrations(registrations: Map<WatchId, Registration?>) = withContext(NonCancellable) {
        database.withTransaction {
            registrations.forEach { (watchId, registration) ->
                if (!registration.isNullOrBlank()) watchDao.fillMissingRegistration(watchId, registration)
            }
        }
    }

    /**
     * Every check attempt lands here, for the [revision] it was made for; false when it no longer applies.
     * With [legacyOnly], it no longer applies to a watch the app does not check itself anymore.
     */
    suspend fun updateLastCheck(
        watchId: WatchId,
        revision: Long,
        at: Instant,
        outcome: WatchCheckOutcome,
        reason: String? = null,
        legacyOnly: Boolean = false,
    ): Boolean = withContext(NonCancellable) {
        database.withTransaction {
            if (legacyOnly && !WatchServerStateEntity.isAppChecked(database.server().getState(watchId))) {
                return@withTransaction false
            }
            watchDao.updateLastCheckIfRevision(watchId, revision, at, outcome.raw, reason) > 0
        }
    }

    interface HistoryWriter {
        suspend fun revisionOf(watchId: WatchId): Long?
        suspend fun syncOf(watchId: WatchId): WatchHistorySyncEntity?

        /** For a legacy-only answer, whether the app still checks [watchId] itself; otherwise always true. */
        suspend fun isAppChecked(watchId: WatchId): Boolean

        /** A final hour never changes again, a later provisional answer for it is ignored. */
        suspend fun writeHours(hours: List<WatchHistoryHourEntity>)
        suspend fun writeSync(sync: WatchHistorySyncEntity)
    }

    /** Applies one history answer at most once; false when [operationId] was applied before. */
    suspend fun applyHistory(
        operationId: String,
        at: Instant,
        legacyOnly: Boolean = false,
        block: suspend HistoryWriter.() -> Unit,
    ): Boolean = withContext(NonCancellable) {
        database.withTransaction {
            val dao = database.history()
            if (dao.isApplied(operationId)) return@withTransaction false
            val writer = object : HistoryWriter {
                override suspend fun revisionOf(watchId: WatchId): Long? = watchDao.getBase(watchId)?.definitionRevision
                override suspend fun syncOf(watchId: WatchId): WatchHistorySyncEntity? = dao.getSync(watchId)
                override suspend fun isAppChecked(watchId: WatchId): Boolean {
                    if (!legacyOnly) return true
                    if (watchDao.getBase(watchId) == null) return false
                    return WatchServerStateEntity.isAppChecked(database.server().getState(watchId))
                }
                override suspend fun writeHours(hours: List<WatchHistoryHourEntity>) {
                    hours.groupBy { it.watchId }.forEach { (watchId, incoming) ->
                        val finalHours = dao.getHours(watchId, incoming.map { it.hour })
                            .filter { it.isFinal }
                            .map { it.hour }
                            .toSet()
                        dao.upsertHours(incoming.filter { it.hour !in finalHours })
                    }
                }

                override suspend fun writeSync(sync: WatchHistorySyncEntity) = dao.upsertSync(sync)
            }
            writer.block()
            dao.insertApplied(WatchHistoryAppliedEntity(operationId, at))
            true
        }
    }

    /** Server hours are kept as long as the app's own checks, the applied markers past any replay. */
    suspend fun cleanupHistory(hoursBefore: Instant, appliedBefore: Instant) = withContext(NonCancellable) {
        val hours = database.history().deleteHoursBefore(hoursBefore)
        val applied = database.history().deleteAppliedBefore(appliedBefore)
        log(TAG) { "cleanupHistory() deleted $hours hours and $applied applied markers" }
    }

    companion object {
        internal val TAG = logTag("Watch", "Database")

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
