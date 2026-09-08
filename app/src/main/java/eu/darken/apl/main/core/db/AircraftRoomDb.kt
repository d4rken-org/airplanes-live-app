package eu.darken.apl.main.core.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import eu.darken.apl.common.room.InstantConverter
import eu.darken.apl.common.room.LocationConverter

@Database(
    entities = [
        CachedAircraftEntity::class,
        PendingOperationEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(InstantConverter::class, LocationConverter::class)
abstract class AircraftRoomDb : RoomDatabase() {
    abstract fun aircraft(): CachedAircraftDao

    abstract fun pendingOperations(): PendingOperationDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE aircraft_cache ADD COLUMN ground_track REAL")
            }
        }

        /** SQLite before 3.35 cannot drop columns, so the table is rebuilt. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(CREATE_AIRCRAFT_CACHE_V3.replace("`aircraft_cache`", "`aircraft_cache_new`"))
                db.execSQL(
                    """
                    INSERT INTO `aircraft_cache_new` (
                        `hex`, `source`, `registration`, `flight`, `operator`, `airframe`, `description`,
                        `squawk`, `emergency`, `military`, `ladd`, `pia`, `temperature_outside`,
                        `altitude_ft`, `on_ground`, `altitude_geom_ft`, `altitude_rate`, `speed_ground`,
                        `speed_air`, `track`, `ground_track`, `location`, `message_seen_at`,
                        `position_seen_at`, `fetched_at`
                    )
                    SELECT
                        `hex`, `message_type`, `registration`, `flight`, `operator`, `airframe`, `description`,
                        `squawk`, `emergency`, 0, 0, 0, `temperature_outside`,
                        CASE WHEN LOWER(TRIM(`altitude`)) = 'ground' THEN NULL ELSE CAST(`altitude` AS INTEGER) END,
                        CASE WHEN LOWER(TRIM(`altitude`)) = 'ground' THEN 1 ELSE NULL END,
                        NULL, `altitude_rate`, `speed_ground`, `speed_air`, `track`, `ground_track`,
                        `location`, `seen_at`, NULL, `seen_at`
                    FROM `aircraft_cache`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `aircraft_cache`")
                db.execSQL("ALTER TABLE `aircraft_cache_new` RENAME TO `aircraft_cache`")
                db.execSQL(CREATE_PENDING_OPERATIONS_V3)
            }
        }

        private const val CREATE_AIRCRAFT_CACHE_V3 =
            "CREATE TABLE IF NOT EXISTS `aircraft_cache` (" +
                    "`hex` TEXT NOT NULL, `source` TEXT, `registration` TEXT, `flight` TEXT, " +
                    "`operator` TEXT, `airframe` TEXT, `description` TEXT, `squawk` TEXT, " +
                    "`emergency` TEXT, `military` INTEGER NOT NULL, `ladd` INTEGER NOT NULL, " +
                    "`pia` INTEGER NOT NULL, `temperature_outside` INTEGER, `altitude_ft` INTEGER, " +
                    "`on_ground` INTEGER, `altitude_geom_ft` INTEGER, `altitude_rate` INTEGER, " +
                    "`speed_ground` REAL, `speed_air` INTEGER, `track` REAL, `ground_track` REAL, " +
                    "`location` TEXT, `message_seen_at` INTEGER, `position_seen_at` INTEGER, " +
                    "`fetched_at` INTEGER NOT NULL, PRIMARY KEY(`hex`))"

        private const val CREATE_PENDING_OPERATIONS_V3 =
            "CREATE TABLE IF NOT EXISTS `pending_operations` (" +
                    "`operation_id` TEXT NOT NULL, `kind` TEXT NOT NULL, `request_json` TEXT NOT NULL, " +
                    "`owner_ids` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `result_json` TEXT, " +
                    "PRIMARY KEY(`operation_id`))"
    }
}
