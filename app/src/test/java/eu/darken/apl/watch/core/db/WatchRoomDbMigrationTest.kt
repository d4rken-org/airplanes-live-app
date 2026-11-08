package eu.darken.apl.watch.core.db

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.collections.shouldBeEmpty
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
    fun `migrate 3 to 9`() {
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
                WatchDatabase.MIGRATION_8_9,
            )
            .build()

        val db = roomDb.openHelper.writableDatabase

        db.query("SELECT * FROM watch_base WHERE id = 'watch-1'").use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.getString(cursor.getColumnIndexOrThrow("user_note")) shouldBe "note"
            // Every existing watch starts at the revision unedited watches have
            cursor.getLong(cursor.getColumnIndexOrThrow("definition_revision")) shouldBe 0L
            cursor.getLong(cursor.getColumnIndexOrThrow("local_generation")) shouldBe 0L
        }
        db.columnsOf("watch_base").intersect(DROPPED_BASE_COLUMNS) shouldBe emptySet()
        db.columnsOf("watch_base").intersect(DROPPED_CHECK_COLUMNS) shouldBe emptySet()
        db.columnsOf("watch_server_state") shouldContain "sent_generation"
        db.columnsOf("watch_server_state").intersect(DROPPED_SERVER_STATE_COLUMNS) shouldBe emptySet()

        db.query("SELECT * FROM watch_aircraft WHERE id = 'watch-1'").use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.getString(cursor.getColumnIndexOrThrow("hex_code")) shouldBe "3C65A3"
            cursor.isNull(cursor.getColumnIndexOrThrow("last_registration")) shouldBe true
        }

        SERVER_TABLES.forEach { table ->
            db.query("SELECT COUNT(*) FROM `$table`").use { cursor ->
                cursor.moveToFirst() shouldBe true
                cursor.getInt(0) shouldBe 0
            }
        }
        db.tables().intersect(LEGACY_TABLES.toSet()).shouldBeEmpty()

        roomDb.close()
    }

    @Test
    fun `migrate 6 to 9`() {
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
                WatchDatabase.MIGRATION_8_9,
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
        db.columnsOf("watch_base").intersect(DROPPED_CHECK_COLUMNS) shouldBe emptySet()
        db.columnsOf("watch_server_state") shouldContain "sent_generation"
        db.columnsOf("watch_server_state").intersect(DROPPED_SERVER_STATE_COLUMNS) shouldBe emptySet()

        db.query("SELECT * FROM watch_aircraft WHERE id = 'watch-a'").use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.getString(cursor.getColumnIndexOrThrow("hex_code")) shouldBe "3C65A3"
            cursor.getString(cursor.getColumnIndexOrThrow("last_registration")) shouldBe "D-AIMA"
        }

        db.tables().intersect(LEGACY_TABLES.toSet()).shouldBeEmpty()

        SERVER_TABLES.forEach { table ->
            db.query("SELECT COUNT(*) FROM `$table`").use { cursor ->
                cursor.moveToFirst() shouldBe true
                cursor.getInt(0) shouldBe 0
            }
        }

        roomDb.close()
    }

    @Test
    fun `migrate 8 to 9 drops the app's own history and keeps every watch`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.deleteDatabase(TEST_DB)

        val dbPath = context.getDatabasePath(TEST_DB)
        dbPath.parentFile?.mkdirs()

        val before = SQLiteDatabase.openOrCreateDatabase(dbPath, null).use { db ->
            V8_SCHEMA.forEach { db.execSQL(it) }
            db.execSQL(CREATE_ROOM_MASTER)
            db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '$V8_IDENTITY_HASH')")
            db.execSQL(
                """
                INSERT INTO watch_base (id, created_at, type, notification_enabled, user_note, location_latitude,
                    location_longitude, location_radius, last_check_at, last_check_outcome, last_check_reason,
                    definition_revision, local_generation)
                VALUES
                    ('watch-a', 1000, 'aircraft', 1, 'plane', NULL, NULL, NULL, 1500, 'matched', NULL, 2, 5),
                    ('watch-f', 2000, 'flight', 0, '', NULL, NULL, NULL, NULL, NULL, NULL, 0, 1),
                    ('watch-s', 3000, 'squawk', 1, 'emergency', NULL, NULL, NULL, 3500, 'failed', 'IOException', 1, 0),
                    ('watch-l', 4000, 'location', 0, 'home', 50.11, 8.68, 2500.5, 4500, 'absent', NULL, 4, 7)
                """.trimIndent()
            )
            db.execSQL("INSERT INTO watch_aircraft (id, hex_code, last_registration) VALUES ('watch-a', '3C65A3', 'D-AIMA')")
            db.execSQL("INSERT INTO watch_flight (id, callsign) VALUES ('watch-f', 'DLH453')")
            db.execSQL("INSERT INTO watch_squawk (id, code) VALUES ('watch-s', '7700')")
            db.execSQL("INSERT INTO watch_location (id, label) VALUES ('watch-l', 'Frankfurt')")
            db.execSQL(
                "INSERT INTO watch_checks (id, checked_at, watch_id, aircraft_count, seen_hexes, operation_id, imported) " +
                        "VALUES ('check-1', 1500, 'watch-a', 1, '3C65A3', 'op-1', 0)"
            )
            db.execSQL(
                "INSERT INTO watch_history_hours (watch_id, hour, aircraft_count, coverage, is_final) " +
                        "VALUES ('watch-l', 3600000, 2, 'full', 1)"
            )
            db.execSQL(
                "INSERT INTO watch_history_sync (watch_id, definition_revision, next_eligible_at, backoff_step, updated_at) " +
                        "VALUES ('watch-l', 4, 5000, 0, 5000)"
            )
            db.execSQL("INSERT INTO watch_history_applied (operation_id, applied_at) VALUES ('history-1', 5000)")
            db.execSQL(
                "INSERT INTO watch_server_state (watch_id, installation_id, server_watch_id, server_revision, upload_state, " +
                        "sent_generation, acked_definition_revision, status, presence, event_seq, last_seen_at, " +
                        "last_match_at, last_match_aircraft_ids, last_match_total, updated_at) " +
                        "VALUES ('watch-l', 'installation-a', 'server-l', 3, 'synced', 7, 4, 'active', 'present', 9, " +
                        "4400, 4300, '3c65a3', 1, 4600)"
            )
            db.execSQL(
                "INSERT INTO watch_notification_outbox (id, installation_id, kind, watch_id, event_seq, aircraft_ids, " +
                        "total_matching, observed_at, created_at) " +
                        "VALUES ('individual-9', 'installation-a', 'individual', 'watch-l', 9, '3c65a3', 1, 4300, 4600)"
            )
            db.execSQL(
                "INSERT INTO watch_server_rollups (server_watch_id, revision, hour, source, watch_id, installation_id, seq, " +
                        "evaluation_basis, aircraft_count, coverage, covered_minutes, is_final, last_seen_at) " +
                        "VALUES ('server-l', 3, 3600000, 'live', 'watch-l', 'installation-a', 2, 'recent_messages', 1, " +
                        "'partial', 30, 0, 4400)"
            )
            db.version = 8
            KEPT_TABLES.associateWith { table -> db.rawQuery("SELECT * FROM `$table` ORDER BY 1", null).use { it.rows() } }
        }

        val roomDb = Room.databaseBuilder(context, WatchRoomDb::class.java, TEST_DB)
            .addMigrations(WatchDatabase.MIGRATION_8_9)
            .build()
        val db = roomDb.openHelper.writableDatabase

        db.tables().intersect(LEGACY_TABLES.toSet()).shouldBeEmpty()
        ("_new_watch_base" in db.tables()) shouldBe false
        db.columnsOf("watch_base") shouldBe listOf(
            "id", "created_at", "type", "notification_enabled", "user_note", "location_latitude", "location_longitude",
            "location_radius", "definition_revision", "local_generation",
        )
        before.getValue("watch_base").size shouldBe 4
        KEPT_TABLES.forEach { table ->
            val expected = before.getValue(table).map { row -> row - DROPPED_CHECK_COLUMNS.toSet() }
            db.query("SELECT * FROM `$table` ORDER BY 1").use { it.rows() } shouldBe expected
        }
        db.query("SELECT * FROM watch_base WHERE id = 'watch-l'").use { it.rows() }.single() shouldBe mapOf(
            "id" to "watch-l",
            "created_at" to 4000L,
            "type" to "location",
            "notification_enabled" to 0L,
            "user_note" to "home",
            "location_latitude" to 50.11,
            "location_longitude" to 8.68,
            "location_radius" to 2500.5,
            "definition_revision" to 4L,
            "local_generation" to 7L,
        )

        db.query("PRAGMA foreign_key_check").use { it.count } shouldBe 0
        SUBTYPE_TABLES.forEach { table ->
            db.query("PRAGMA foreign_key_list(`$table`)").use { cursor ->
                cursor.moveToFirst() shouldBe true
                cursor.getString(cursor.getColumnIndexOrThrow("table")) shouldBe "watch_base"
                cursor.getString(cursor.getColumnIndexOrThrow("on_delete")) shouldBe "CASCADE"
            }
        }

        // Room switched foreign keys on when it opened the migrated database
        db.execSQL("DELETE FROM watch_base WHERE id = 'watch-l'")
        db.query("SELECT COUNT(*) FROM watch_location").use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.getInt(0) shouldBe 0
        }
        listOf("watch_aircraft", "watch_flight", "watch_squawk").forEach { table ->
            db.query("SELECT COUNT(*) FROM `$table`").use { cursor ->
                cursor.moveToFirst() shouldBe true
                cursor.getInt(0) shouldBe 1
            }
        }

        roomDb.close()
    }

    private fun SupportSQLiteDatabase.columnsOf(table: String): List<String> =
        query("SELECT * FROM `$table` LIMIT 0").use { it.columnNames.toList() }

    private fun SupportSQLiteDatabase.tables(): Set<String> =
        query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }

    private fun Cursor.rows(): List<Map<String, Any?>> = buildList {
        while (moveToNext()) {
            add(
                columnNames.indices.associate { index ->
                    columnNames[index] to when (getType(index)) {
                        Cursor.FIELD_TYPE_NULL -> null
                        Cursor.FIELD_TYPE_INTEGER -> getLong(index)
                        Cursor.FIELD_TYPE_FLOAT -> getDouble(index)
                        else -> getString(index)
                    }
                }
            )
        }
    }

    companion object {
        private const val TEST_DB = "test-watch-db"
        private const val V3_IDENTITY_HASH = "3225db9d7db98f47ad811dd938ef7587"
        private const val V6_IDENTITY_HASH = "ee67f457dbf58f9800697d7194c0186f"
        private const val V8_IDENTITY_HASH = "f087fd37c6800d077203e4dc6a2cf3b2"

        private val DROPPED_BASE_COLUMNS = listOf("priority", "enabled", "paused_over_limit")
        private val DROPPED_SERVER_STATE_COLUMNS = listOf("server_priority", "server_enabled", "acked_generation")

        private val LEGACY_TABLES = listOf("watch_checks", "watch_history_hours", "watch_history_sync", "watch_history_applied")
        private val DROPPED_CHECK_COLUMNS = listOf("last_check_at", "last_check_outcome", "last_check_reason")
        private val SUBTYPE_TABLES = listOf("watch_aircraft", "watch_flight", "watch_squawk", "watch_location")
        private val KEPT_TABLES = listOf("watch_base") + SUBTYPE_TABLES +
                listOf("watch_server_state", "watch_notification_outbox", "watch_server_rollups")

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

        /** As Room created it, copied from schemas/.../WatchRoomDb/8.json. */
        private val V8_SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS `watch_base` (`id` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `type` TEXT NOT NULL, `notification_enabled` INTEGER NOT NULL, `user_note` TEXT NOT NULL, `location_latitude` REAL, `location_longitude` REAL, `location_radius` REAL, `last_check_at` INTEGER, `last_check_outcome` TEXT, `last_check_reason` TEXT, `definition_revision` INTEGER NOT NULL DEFAULT 0, `local_generation` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS `watch_flight` (`id` TEXT NOT NULL, `callsign` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`id`) REFERENCES `watch_base`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `watch_squawk` (`id` TEXT NOT NULL, `code` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`id`) REFERENCES `watch_base`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `watch_aircraft` (`id` TEXT NOT NULL, `hex_code` TEXT NOT NULL, `last_registration` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`id`) REFERENCES `watch_base`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `watch_location` (`id` TEXT NOT NULL, `label` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`id`) REFERENCES `watch_base`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `watch_checks` (`id` TEXT NOT NULL, `checked_at` INTEGER NOT NULL, `watch_id` TEXT NOT NULL, `aircraft_count` INTEGER NOT NULL, `seen_hexes` TEXT, `operation_id` TEXT, `imported` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_watch_checks_watch_id_checked_at` ON `watch_checks` (`watch_id`, `checked_at`)",
            "CREATE INDEX IF NOT EXISTS `index_watch_checks_checked_at` ON `watch_checks` (`checked_at`)",
            "CREATE TABLE IF NOT EXISTS `watch_history_hours` (`watch_id` TEXT NOT NULL, `hour` INTEGER NOT NULL, `aircraft_count` INTEGER NOT NULL, `coverage` TEXT NOT NULL, `is_final` INTEGER NOT NULL, PRIMARY KEY(`watch_id`, `hour`))",
            "CREATE INDEX IF NOT EXISTS `index_watch_history_hours_hour` ON `watch_history_hours` (`hour`)",
            "CREATE TABLE IF NOT EXISTS `watch_history_sync` (`watch_id` TEXT NOT NULL, `definition_revision` INTEGER NOT NULL, `window_from` INTEGER, `window_until` INTEGER, `cursor_json` TEXT, `last_seen_at` INTEGER, `last_seen_aircraft_id` TEXT, `evaluation_basis` TEXT, `restricted_tier` TEXT, `next_eligible_at` INTEGER NOT NULL, `backoff_step` INTEGER NOT NULL, `last_pass_at` INTEGER, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`watch_id`))",
            "CREATE TABLE IF NOT EXISTS `watch_history_applied` (`operation_id` TEXT NOT NULL, `applied_at` INTEGER NOT NULL, PRIMARY KEY(`operation_id`))",
            "CREATE TABLE IF NOT EXISTS `watch_server_state` (`watch_id` TEXT NOT NULL, `installation_id` TEXT NOT NULL, `server_watch_id` TEXT, `server_revision` INTEGER, `upload_state` TEXT NOT NULL, `sent_generation` INTEGER, `acked_definition_revision` INTEGER, `status` TEXT, `pause_reason` TEXT, `presence` TEXT, `event_seq` INTEGER NOT NULL DEFAULT 0, `last_seen_at` INTEGER, `last_seen_aircraft_id` TEXT, `backfill` TEXT, `last_match_seq` INTEGER, `last_match_at` INTEGER, `last_match_aircraft_ids` TEXT, `last_match_total` INTEGER, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`watch_id`))",
            "CREATE TABLE IF NOT EXISTS `watch_server_tombstones` (`watch_id` TEXT NOT NULL, `installation_id` TEXT NOT NULL, `deleted_at` INTEGER NOT NULL, PRIMARY KEY(`watch_id`))",
            "CREATE TABLE IF NOT EXISTS `watch_server_sync` (`installation_id` TEXT NOT NULL, `list_revision` INTEGER, `list_state` TEXT, `event_cursor` INTEGER, `rollup_cursor` INTEGER, `phase` TEXT NOT NULL, `last_poll_at` INTEGER, `uploads_not_before` INTEGER, `events_not_before` INTEGER, `rollups_not_before` INTEGER, `poll_run_id` TEXT, `poll_run_individual` INTEGER NOT NULL DEFAULT 0, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`installation_id`))",
            "CREATE TABLE IF NOT EXISTS `watch_server_events` (`seq` INTEGER NOT NULL, `installation_id` TEXT NOT NULL, `kind` TEXT NOT NULL, `observed_at` INTEGER NOT NULL, `server_watch_id` TEXT, `watch_id` TEXT, `revision` INTEGER, `aircraft_ids` TEXT, `total_matching` INTEGER, `gap_from` INTEGER, `gap_until` INTEGER, `received_at` INTEGER NOT NULL, PRIMARY KEY(`seq`))",
            "CREATE INDEX IF NOT EXISTS `index_watch_server_events_observed_at` ON `watch_server_events` (`observed_at`)",
            "CREATE INDEX IF NOT EXISTS `index_watch_server_events_server_watch_id_revision` ON `watch_server_events` (`server_watch_id`, `revision`)",
            "CREATE TABLE IF NOT EXISTS `watch_notification_outbox` (`id` TEXT NOT NULL, `installation_id` TEXT NOT NULL, `kind` TEXT NOT NULL, `watch_id` TEXT, `event_seq` INTEGER, `aircraft_ids` TEXT, `total_matching` INTEGER, `observed_at` INTEGER, `summary_count` INTEGER NOT NULL DEFAULT 0, `summary_watch_ids` TEXT, `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS `watch_server_rollups` (`server_watch_id` TEXT NOT NULL, `revision` INTEGER NOT NULL, `hour` INTEGER NOT NULL, `source` TEXT NOT NULL, `watch_id` TEXT NOT NULL, `installation_id` TEXT NOT NULL, `seq` INTEGER NOT NULL, `evaluation_basis` TEXT NOT NULL, `aircraft_count` INTEGER NOT NULL, `coverage` TEXT NOT NULL, `covered_minutes` INTEGER, `is_final` INTEGER NOT NULL, `last_seen_at` INTEGER, `last_seen_aircraft_id` TEXT, PRIMARY KEY(`server_watch_id`, `revision`, `hour`, `source`))",
            "CREATE INDEX IF NOT EXISTS `index_watch_server_rollups_watch_id_hour` ON `watch_server_rollups` (`watch_id`, `hour`)",
            "CREATE INDEX IF NOT EXISTS `index_watch_server_rollups_hour` ON `watch_server_rollups` (`hour`)",
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
