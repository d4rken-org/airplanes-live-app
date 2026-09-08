package eu.darken.apl.main.core.db

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AircraftRoomDbMigrationTest {

    private fun openRoomDb() = Room.databaseBuilder(
        ApplicationProvider.getApplicationContext(),
        AircraftRoomDb::class.java,
        TEST_DB,
    )
        .addMigrations(AircraftRoomDb.MIGRATION_1_2, AircraftRoomDb.MIGRATION_2_3)
        .build()

    @Test
    fun `migrate 1 to 3`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.deleteDatabase(TEST_DB)

        val dbPath = context.getDatabasePath(TEST_DB)
        dbPath.parentFile?.mkdirs()

        SQLiteDatabase.openOrCreateDatabase(dbPath, null).use { db ->
            db.execSQL(V1_CREATE_AIRCRAFT_CACHE)
            db.execSQL(V1_CREATE_ROOM_MASTER)
            db.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '$V1_IDENTITY_HASH')"
            )
            db.execSQL(
                """
                INSERT INTO aircraft_cache (
                    hex, message_type, db_flags, registration, flight, operator, airframe,
                    description, squawk, emergency, temperature_outside, altitude, altitude_rate,
                    speed_ground, speed_air, track, location, messages, seen_at, rssi
                ) VALUES (
                    'ABC123', 'adsb_icao', 1, 'N12345', 'UAL123', 'United Airlines', 'Boeing 737',
                    'B737', '1200', 'none', -40, '35000', 1500,
                    450.5, 420, 180.5, '40.7128,-74.0060', 100, 1710000000, -3.5
                )
                """.trimIndent()
            )
            db.version = 1
        }

        val roomDb = openRoomDb()

        val db = roomDb.openHelper.writableDatabase
        db.query("SELECT * FROM aircraft_cache WHERE hex = 'ABC123'").use { cursor ->
            cursor.moveToFirst() shouldBe true

            cursor.getString(cursor.getColumnIndexOrThrow("hex")) shouldBe "ABC123"
            cursor.getString(cursor.getColumnIndexOrThrow("source")) shouldBe "adsb_icao"
            cursor.getString(cursor.getColumnIndexOrThrow("registration")) shouldBe "N12345"
            cursor.getString(cursor.getColumnIndexOrThrow("flight")) shouldBe "UAL123"
            cursor.getString(cursor.getColumnIndexOrThrow("operator")) shouldBe "United Airlines"
            cursor.getString(cursor.getColumnIndexOrThrow("airframe")) shouldBe "Boeing 737"
            cursor.getString(cursor.getColumnIndexOrThrow("description")) shouldBe "B737"
            cursor.getString(cursor.getColumnIndexOrThrow("squawk")) shouldBe "1200"
            cursor.getString(cursor.getColumnIndexOrThrow("emergency")) shouldBe "none"
            cursor.getInt(cursor.getColumnIndexOrThrow("temperature_outside")) shouldBe -40
            cursor.getInt(cursor.getColumnIndexOrThrow("altitude_ft")) shouldBe 35000
            cursor.isNull(cursor.getColumnIndexOrThrow("on_ground")) shouldBe true
            cursor.getInt(cursor.getColumnIndexOrThrow("altitude_rate")) shouldBe 1500
            cursor.getDouble(cursor.getColumnIndexOrThrow("speed_ground")) shouldBe 450.5
            cursor.getInt(cursor.getColumnIndexOrThrow("speed_air")) shouldBe 420
            cursor.getDouble(cursor.getColumnIndexOrThrow("track")) shouldBe 180.5
            cursor.getString(cursor.getColumnIndexOrThrow("location")) shouldBe "40.7128,-74.0060"
            cursor.getLong(cursor.getColumnIndexOrThrow("message_seen_at")) shouldBe 1710000000L
            cursor.getLong(cursor.getColumnIndexOrThrow("fetched_at")) shouldBe 1710000000L
            cursor.isNull(cursor.getColumnIndexOrThrow("position_seen_at")) shouldBe true

            cursor.isNull(cursor.getColumnIndexOrThrow("ground_track")) shouldBe true
            // db_flags 1 is the military bit
            cursor.getInt(cursor.getColumnIndexOrThrow("military")) shouldBe 1
            cursor.getInt(cursor.getColumnIndexOrThrow("ladd")) shouldBe 0
            cursor.getInt(cursor.getColumnIndexOrThrow("pia")) shouldBe 0
        }

        roomDb.close()
    }

    @Test
    fun `migrate 2 to 3`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.deleteDatabase(TEST_DB)

        val dbPath = context.getDatabasePath(TEST_DB)
        dbPath.parentFile?.mkdirs()

        SQLiteDatabase.openOrCreateDatabase(dbPath, null).use { db ->
            db.execSQL(V2_CREATE_AIRCRAFT_CACHE)
            db.execSQL(V1_CREATE_ROOM_MASTER)
            db.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '$V2_IDENTITY_HASH')"
            )
            db.execSQL(
                """
                INSERT INTO aircraft_cache (hex, message_type, altitude, messages, seen_at, rssi)
                VALUES ('AAAAAA', 'adsb_icao', 'ground', 10, 1710000001, -1.0)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO aircraft_cache (hex, message_type, altitude, messages, seen_at, rssi)
                VALUES ('BBBBBB', 'mlat', '35000', 20, 1710000002, -2.0)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO aircraft_cache (hex, message_type, db_flags, altitude, messages, seen_at, rssi)
                VALUES ('CCCCCC', 'adsb_icao', 9, '10000', 30, 1710000003, -3.0)
                """.trimIndent()
            )
            db.version = 2
        }

        val roomDb = openRoomDb()
        val db = roomDb.openHelper.writableDatabase

        db.query("SELECT * FROM aircraft_cache WHERE hex = 'AAAAAA'").use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.isNull(cursor.getColumnIndexOrThrow("altitude_ft")) shouldBe true
            cursor.getInt(cursor.getColumnIndexOrThrow("on_ground")) shouldBe 1
            cursor.getLong(cursor.getColumnIndexOrThrow("message_seen_at")) shouldBe 1710000001L
            cursor.getLong(cursor.getColumnIndexOrThrow("fetched_at")) shouldBe 1710000001L
        }

        db.query("SELECT * FROM aircraft_cache WHERE hex = 'BBBBBB'").use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.getInt(cursor.getColumnIndexOrThrow("altitude_ft")) shouldBe 35000
            cursor.isNull(cursor.getColumnIndexOrThrow("on_ground")) shouldBe true
            cursor.getString(cursor.getColumnIndexOrThrow("source")) shouldBe "mlat"
        }

        db.query("SELECT * FROM aircraft_cache WHERE hex = 'CCCCCC'").use { cursor ->
            cursor.moveToFirst() shouldBe true
            // db_flags 9 is the military and the LADD bit
            cursor.getInt(cursor.getColumnIndexOrThrow("military")) shouldBe 1
            cursor.getInt(cursor.getColumnIndexOrThrow("ladd")) shouldBe 1
            cursor.getInt(cursor.getColumnIndexOrThrow("pia")) shouldBe 0
        }

        db.query("SELECT COUNT(*) FROM pending_operations").use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.getInt(0) shouldBe 0
        }

        roomDb.close()
    }

    companion object {
        private const val TEST_DB = "test-aircraft-db"
        private const val V1_IDENTITY_HASH = "ad2dcbe8d92605fb4e167c8c495a52e1"
        private const val V2_IDENTITY_HASH = "317ce82e92a8dedef25ace0cad789dd0"

        private const val V1_CREATE_AIRCRAFT_CACHE = """
            CREATE TABLE IF NOT EXISTS `aircraft_cache` (
                `hex` TEXT NOT NULL,
                `message_type` TEXT NOT NULL,
                `db_flags` INTEGER,
                `registration` TEXT,
                `flight` TEXT,
                `operator` TEXT,
                `airframe` TEXT,
                `description` TEXT,
                `squawk` TEXT,
                `emergency` TEXT,
                `temperature_outside` INTEGER,
                `altitude` TEXT,
                `altitude_rate` INTEGER,
                `speed_ground` REAL,
                `speed_air` INTEGER,
                `track` REAL,
                `location` TEXT,
                `messages` INTEGER NOT NULL,
                `seen_at` INTEGER NOT NULL,
                `rssi` REAL NOT NULL,
                PRIMARY KEY(`hex`)
            )
        """

        private const val V2_CREATE_AIRCRAFT_CACHE = """
            CREATE TABLE IF NOT EXISTS `aircraft_cache` (
                `hex` TEXT NOT NULL,
                `message_type` TEXT NOT NULL,
                `db_flags` INTEGER,
                `registration` TEXT,
                `flight` TEXT,
                `operator` TEXT,
                `airframe` TEXT,
                `description` TEXT,
                `squawk` TEXT,
                `emergency` TEXT,
                `temperature_outside` INTEGER,
                `altitude` TEXT,
                `altitude_rate` INTEGER,
                `speed_ground` REAL,
                `speed_air` INTEGER,
                `track` REAL,
                `ground_track` REAL,
                `location` TEXT,
                `messages` INTEGER NOT NULL,
                `seen_at` INTEGER NOT NULL,
                `rssi` REAL NOT NULL,
                PRIMARY KEY(`hex`)
            )
        """

        private const val V1_CREATE_ROOM_MASTER = """
            CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)
        """
    }
}
