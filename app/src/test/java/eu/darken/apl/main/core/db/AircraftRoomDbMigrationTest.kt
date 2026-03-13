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

    @Test
    fun `migrate 1 to 2`() {
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

        val roomDb = Room.databaseBuilder(context, AircraftRoomDb::class.java, TEST_DB)
            .addMigrations(AircraftRoomDb.MIGRATION_1_2)
            .build()

        val db = roomDb.openHelper.writableDatabase
        db.query("SELECT * FROM aircraft_cache WHERE hex = 'ABC123'").use { cursor ->
            cursor.moveToFirst() shouldBe true

            cursor.getString(cursor.getColumnIndexOrThrow("hex")) shouldBe "ABC123"
            cursor.getString(cursor.getColumnIndexOrThrow("message_type")) shouldBe "adsb_icao"
            cursor.getInt(cursor.getColumnIndexOrThrow("db_flags")) shouldBe 1
            cursor.getString(cursor.getColumnIndexOrThrow("registration")) shouldBe "N12345"
            cursor.getString(cursor.getColumnIndexOrThrow("flight")) shouldBe "UAL123"
            cursor.getString(cursor.getColumnIndexOrThrow("operator")) shouldBe "United Airlines"
            cursor.getString(cursor.getColumnIndexOrThrow("airframe")) shouldBe "Boeing 737"
            cursor.getString(cursor.getColumnIndexOrThrow("description")) shouldBe "B737"
            cursor.getString(cursor.getColumnIndexOrThrow("squawk")) shouldBe "1200"
            cursor.getString(cursor.getColumnIndexOrThrow("emergency")) shouldBe "none"
            cursor.getInt(cursor.getColumnIndexOrThrow("temperature_outside")) shouldBe -40
            cursor.getString(cursor.getColumnIndexOrThrow("altitude")) shouldBe "35000"
            cursor.getInt(cursor.getColumnIndexOrThrow("altitude_rate")) shouldBe 1500
            cursor.getDouble(cursor.getColumnIndexOrThrow("speed_ground")) shouldBe 450.5
            cursor.getInt(cursor.getColumnIndexOrThrow("speed_air")) shouldBe 420
            cursor.getDouble(cursor.getColumnIndexOrThrow("track")) shouldBe 180.5
            cursor.getString(cursor.getColumnIndexOrThrow("location")) shouldBe "40.7128,-74.0060"
            cursor.getInt(cursor.getColumnIndexOrThrow("messages")) shouldBe 100
            cursor.getLong(cursor.getColumnIndexOrThrow("seen_at")) shouldBe 1710000000L
            cursor.getDouble(cursor.getColumnIndexOrThrow("rssi")) shouldBe -3.5

            cursor.isNull(cursor.getColumnIndexOrThrow("ground_track")) shouldBe true
        }

        roomDb.close()
    }

    companion object {
        private const val TEST_DB = "test-aircraft-db"
        private const val V1_IDENTITY_HASH = "ad2dcbe8d92605fb4e167c8c495a52e1"

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

        private const val V1_CREATE_ROOM_MASTER = """
            CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)
        """
    }
}
