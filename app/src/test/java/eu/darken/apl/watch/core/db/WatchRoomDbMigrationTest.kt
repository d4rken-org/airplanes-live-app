package eu.darken.apl.watch.core.db

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WatchRoomDbMigrationTest {

    @Test
    fun `migrate 3 to 4`() {
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
            )
            .build()

        val db = roomDb.openHelper.writableDatabase

        db.query("SELECT * FROM watch_base WHERE id = 'watch-1'").use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.getString(cursor.getColumnIndexOrThrow("user_note")) shouldBe "note"
            cursor.isNull(cursor.getColumnIndexOrThrow("last_check_at")) shouldBe true
            cursor.isNull(cursor.getColumnIndexOrThrow("last_check_outcome")) shouldBe true
            cursor.isNull(cursor.getColumnIndexOrThrow("last_check_reason")) shouldBe true
        }

        db.query("SELECT * FROM watch_checks WHERE id = 'check-1'").use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.getInt(cursor.getColumnIndexOrThrow("aircraft_count")) shouldBe 1
            cursor.getString(cursor.getColumnIndexOrThrow("seen_hexes")) shouldBe "3C65A3"
            cursor.isNull(cursor.getColumnIndexOrThrow("operation_id")) shouldBe true
        }

        roomDb.close()
    }

    companion object {
        private const val TEST_DB = "test-watch-db"
        private const val V3_IDENTITY_HASH = "3225db9d7db98f47ad811dd938ef7587"

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
