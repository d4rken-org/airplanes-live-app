package eu.darken.apl.watch.core.db

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class WatchRoomDbMigrationTest {

    @Test
    fun `migrate 3 to 8`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.deleteDatabase(TEST_DB)

        val dbPath = context.getDatabasePath(TEST_DB)
        dbPath.parentFile?.mkdirs()

        SQLiteDatabase.openOrCreateDatabase(dbPath, null).use { db ->
            db.execSQL(V3_CREATE_WATCH_BASE)
            db.execSQL(V3_CREATE_WATCH_AIRCRAFT)
            db.execSQL(V3_CREATE_WATCH_FLIGHT)
            db.execSQL(V3_CREATE_WATCH_SQUAWK)
            db.execSQL(V3_CREATE_WATCH_LOCATION)
            db.execSQL(V3_CREATE_WATCH_CHECKS)
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_watch_checks_watch_id_checked_at` ON `watch_checks` (`watch_id`, `checked_at`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_watch_checks_checked_at` ON `watch_checks` (`checked_at`)")
            db.execSQL(CREATE_ROOM_MASTER)
            db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '$V3_IDENTITY_HASH')")
            db.execSQL(
                """
                INSERT INTO watch_base (id, created_at, type, notification_enabled, user_note)
                VALUES ('watch-1', 1710000000, 'aircraft', 1, 'note')
                """.trimIndent()
            )
            db.execSQL("INSERT INTO watch_aircraft (id, hex_code) VALUES ('watch-1', '3C65A3')")
            db.execSQL(
                """
                INSERT INTO watch_checks (id, checked_at, watch_id, aircraft_count, seen_hexes)
                VALUES ('check-1', 1710000001, 'watch-1', 1, '3C65A3')
                """.trimIndent()
            )
            db.version = 3
        }

        val roomDb = Room.databaseBuilder(context, WatchRoomDb::class.java, TEST_DB)
            .addMigrations(
                WatchDatabase.MIGRATION_1_2,
                WatchDatabase.MIGRATION_2_3,
                WatchDatabase.MIGRATION_3_4,
                WatchDatabase.MIGRATION_4_5,
                WatchDatabase.MIGRATION_5_6,
                WatchDatabase.MIGRATION_6_7,
                WatchDatabase.MIGRATION_7_8,
            )
            .build()

        val db = roomDb.openHelper.writableDatabase

        db.query("SELECT * FROM watch_base WHERE id = 'watch-1'").use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.getString(cursor.getColumnIndexOrThrow("user_note")) shouldBe "note"
            cursor.isNull(cursor.getColumnIndexOrThrow("last_check_at")) shouldBe true
            cursor.isNull(cursor.getColumnIndexOrThrow("last_check_outcome")) shouldBe true
            cursor.isNull(cursor.getColumnIndexOrThrow("last_check_reason")) shouldBe true
            // Every existing watch starts at the revision unedited watches have
            cursor.getLong(cursor.getColumnIndexOrThrow("definition_revision")) shouldBe 0L
            cursor.getLong(cursor.getColumnIndexOrThrow("local_generation")) shouldBe 0L
        }
        db.columnsOf("watch_base").intersect(DROPPED_BASE_COLUMNS) shouldBe emptySet()
        db.columnsOf("watch_server_state") shouldContain "sent_generation"
        db.columnsOf("watch_server_state").intersect(DROPPED_SERVER_STATE_COLUMNS) shouldBe emptySet()

        db.query("SELECT * FROM watch_aircraft WHERE id = 'watch-1'").use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.getString(cursor.getColumnIndexOrThrow("hex_code")) shouldBe "3C65A3"
            cursor.isNull(cursor.getColumnIndexOrThrow("last_registration")) shouldBe true
        }

        (listOf("watch_history_hours", "watch_history_sync", "watch_history_applied") + SERVER_TABLES).forEach { table ->
            db.query("SELECT COUNT(*) FROM `$table`").use { cursor ->
                cursor.moveToFirst() shouldBe true
                cursor.getInt(0) shouldBe 0
            }
        }

        db.query("SELECT * FROM watch_checks WHERE id = 'check-1'").use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.getInt(cursor.getColumnIndexOrThrow("aircraft_count")) shouldBe 1
            cursor.getString(cursor.getColumnIndexOrThrow("seen_hexes")) shouldBe "3C65A3"
            cursor.isNull(cursor.getColumnIndexOrThrow("operation_id")) shouldBe true
            cursor.getInt(cursor.getColumnIndexOrThrow("imported")) shouldBe 0
        }

        roomDb.close()
    }

    @Test
    fun `migrate 6 to 8`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.deleteDatabase(TEST_DB)

        val dbPath = context.getDatabasePath(TEST_DB)
        dbPath.parentFile?.mkdirs()

        SQLiteDatabase.openOrCreateDatabase(dbPath, null).use { db ->
            V6_SCHEMA.forEach { db.execSQL(it) }
            db.execSQL(CREATE_ROOM_MASTER)
            db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '$V6_IDENTITY_HASH')")
            db.execSQL(
                """
                INSERT INTO watch_base (id, created_at, type, notification_enabled, user_note, definition_revision)
                VALUES
                    ('watch-c', 3000, 'aircraft', 0, 'newest', 0),
                    ('watch-a', 1000, 'aircraft', 1, 'oldest', 0),
                    ('watch-z', 2000, 'squawk', 0, '', 3),
                    ('watch-b', 2000, 'squawk', 0, '', 0)
                """.trimIndent()
            )
            db.execSQL("INSERT INTO watch_aircraft (id, hex_code, last_registration) VALUES ('watch-a', '3C65A3', 'D-AIMA')")
            db.execSQL("INSERT INTO watch_aircraft (id, hex_code) VALUES ('watch-c', '4B1805')")
            db.execSQL("INSERT INTO watch_squawk (id, code) VALUES ('watch-z', '7700')")
            db.execSQL("INSERT INTO watch_squawk (id, code) VALUES ('watch-b', '7600')")
            db.execSQL(
                """
                INSERT INTO watch_checks (id, checked_at, watch_id, aircraft_count, seen_hexes, operation_id)
                VALUES ('check-1', 1500, 'watch-a', 1, '3C65A3', 'op-1')
                """.trimIndent()
            )
            db.version = 6
        }

        val roomDb = Room.databaseBuilder(context, WatchRoomDb::class.java, TEST_DB)
            .addMigrations(
                WatchDatabase.MIGRATION_1_2,
                WatchDatabase.MIGRATION_2_3,
                WatchDatabase.MIGRATION_3_4,
                WatchDatabase.MIGRATION_4_5,
                WatchDatabase.MIGRATION_5_6,
                WatchDatabase.MIGRATION_6_7,
                WatchDatabase.MIGRATION_7_8,
            )
            .build()

        val db = roomDb.openHelper.writableDatabase

        val rows = db.query("SELECT * FROM watch_base ORDER BY id").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    cursor.getLong(cursor.getColumnIndexOrThrow("local_generation")) shouldBe 0L
                    add(
                        listOf(
                            cursor.getString(cursor.getColumnIndexOrThrow("id")),
                            cursor.getString(cursor.getColumnIndexOrThrow("user_note")),
                            cursor.getLong(cursor.getColumnIndexOrThrow("definition_revision")),
                        )
                    )
                }
            }
        }
        rows shouldBe listOf(
            listOf("watch-a", "oldest", 0L),
            listOf("watch-b", "", 0L),
            listOf("watch-c", "newest", 0L),
            listOf("watch-z", "", 3L),
        )
        db.columnsOf("watch_base").intersect(DROPPED_BASE_COLUMNS) shouldBe emptySet()
        db.columnsOf("watch_server_state") shouldContain "sent_generation"
        db.columnsOf("watch_server_state").intersect(DROPPED_SERVER_STATE_COLUMNS) shouldBe emptySet()

        db.query("SELECT * FROM watch_aircraft WHERE id = 'watch-a'").use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.getString(cursor.getColumnIndexOrThrow("hex_code")) shouldBe "3C65A3"
            cursor.getString(cursor.getColumnIndexOrThrow("last_registration")) shouldBe "D-AIMA"
        }

        db.query("SELECT * FROM watch_checks WHERE id = 'check-1'").use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.getString(cursor.getColumnIndexOrThrow("watch_id")) shouldBe "watch-a"
            cursor.getString(cursor.getColumnIndexOrThrow("operation_id")) shouldBe "op-1"
            cursor.getInt(cursor.getColumnIndexOrThrow("imported")) shouldBe 0
        }

        SERVER_TABLES.forEach { table ->
            db.query("SELECT COUNT(*) FROM `$table`").use { cursor ->
                cursor.moveToFirst() shouldBe true
                cursor.getInt(0) shouldBe 0
            }
        }

        roomDb.close()
    }

    private fun SupportSQLiteDatabase.columnsOf(table: String): List<String> =
        query("SELECT * FROM `$table` LIMIT 0").use { it.columnNames.toList() }

    companion object {
        private const val TEST_DB = "test-watch-db"
        private const val V3_IDENTITY_HASH = "3225db9d7db98f47ad811dd938ef7587"
        private const val V6_IDENTITY_HASH = "ee67f457dbf58f9800697d7194c0186f"

        private val DROPPED_BASE_COLUMNS = listOf("priority", "enabled", "paused_over_limit")
        private val DROPPED_SERVER_STATE_COLUMNS = listOf("server_priority", "server_enabled", "acked_generation")

        private val SERVER_TABLES = listOf(
            "watch_server_state",
            "watch_server_tombstones",
            "watch_server_sync",
            "watch_server_events",
            "watch_notification_outbox",
            "watch_server_rollups",
        )

        /** As Room created it, copied from schemas/.../WatchRoomDb/6.json. */
        private val V6_SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS `watch_base` (`id` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `type` TEXT NOT NULL, `notification_enabled` INTEGER NOT NULL, `user_note` TEXT NOT NULL, `location_latitude` REAL, `location_longitude` REAL, `location_radius` REAL, `last_check_at` INTEGER, `last_check_outcome` TEXT, `last_check_reason` TEXT, `definition_revision` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS `watch_flight` (`id` TEXT NOT NULL, `callsign` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`id`) REFERENCES `watch_base`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `watch_squawk` (`id` TEXT NOT NULL, `code` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`id`) REFERENCES `watch_base`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `watch_aircraft` (`id` TEXT NOT NULL, `hex_code` TEXT NOT NULL, `last_registration` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`id`) REFERENCES `watch_base`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `watch_location` (`id` TEXT NOT NULL, `label` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`id`) REFERENCES `watch_base`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `watch_checks` (`id` TEXT NOT NULL, `checked_at` INTEGER NOT NULL, `watch_id` TEXT NOT NULL, `aircraft_count` INTEGER NOT NULL, `seen_hexes` TEXT, `operation_id` TEXT, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_watch_checks_watch_id_checked_at` ON `watch_checks` (`watch_id`, `checked_at`)",
            "CREATE INDEX IF NOT EXISTS `index_watch_checks_checked_at` ON `watch_checks` (`checked_at`)",
            "CREATE TABLE IF NOT EXISTS `watch_history_hours` (`watch_id` TEXT NOT NULL, `hour` INTEGER NOT NULL, `aircraft_count` INTEGER NOT NULL, `coverage` TEXT NOT NULL, `is_final` INTEGER NOT NULL, PRIMARY KEY(`watch_id`, `hour`))",
            "CREATE INDEX IF NOT EXISTS `index_watch_history_hours_hour` ON `watch_history_hours` (`hour`)",
            "CREATE TABLE IF NOT EXISTS `watch_history_sync` (`watch_id` TEXT NOT NULL, `definition_revision` INTEGER NOT NULL, `window_from` INTEGER, `window_until` INTEGER, `cursor_json` TEXT, `last_seen_at` INTEGER, `last_seen_aircraft_id` TEXT, `evaluation_basis` TEXT, `restricted_tier` TEXT, `next_eligible_at` INTEGER NOT NULL, `backoff_step` INTEGER NOT NULL, `last_pass_at` INTEGER, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`watch_id`))",
            "CREATE TABLE IF NOT EXISTS `watch_history_applied` (`operation_id` TEXT NOT NULL, `applied_at` INTEGER NOT NULL, PRIMARY KEY(`operation_id`))",
        )

        private const val CREATE_ROOM_MASTER = """
            CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)
        """

        private const val V3_CREATE_WATCH_BASE = """
            CREATE TABLE IF NOT EXISTS `watch_base` (
                `id` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                `type` TEXT NOT NULL,
                `notification_enabled` INTEGER NOT NULL,
                `user_note` TEXT NOT NULL,
                `location_latitude` REAL,
                `location_longitude` REAL,
                `location_radius` REAL,
                PRIMARY KEY(`id`)
            )
        """

        private const val V3_CREATE_WATCH_AIRCRAFT = """
            CREATE TABLE IF NOT EXISTS `watch_aircraft` (
                `id` TEXT NOT NULL,
                `hex_code` TEXT NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`id`) REFERENCES `watch_base`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """

        private const val V3_CREATE_WATCH_FLIGHT = """
            CREATE TABLE IF NOT EXISTS `watch_flight` (
                `id` TEXT NOT NULL,
                `callsign` TEXT NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`id`) REFERENCES `watch_base`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """

        private const val V3_CREATE_WATCH_SQUAWK = """
            CREATE TABLE IF NOT EXISTS `watch_squawk` (
                `id` TEXT NOT NULL,
                `code` TEXT NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`id`) REFERENCES `watch_base`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """

        private const val V3_CREATE_WATCH_LOCATION = """
            CREATE TABLE IF NOT EXISTS `watch_location` (
                `id` TEXT NOT NULL,
                `label` TEXT NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`id`) REFERENCES `watch_base`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """

        private const val V3_CREATE_WATCH_CHECKS = """
            CREATE TABLE IF NOT EXISTS `watch_checks` (
                `id` TEXT NOT NULL,
                `checked_at` INTEGER NOT NULL,
                `watch_id` TEXT NOT NULL,
                `aircraft_count` INTEGER NOT NULL,
                `seen_hexes` TEXT,
                PRIMARY KEY(`id`)
            )
        """
    }
}
