package eu.darken.apl.feeder.core.stats

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeederStatsMigrationTest {

    private val testDb = "feeder-stats-migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FeederStatsRoomDb::class.java,
    )

    @Test
    fun migrate1To2() {
        val db = helper.createDatabase(testDb, 1)

        val beastValues = ContentValues().apply {
            put("receiver_id", "test-receiver-1")
            put("received_at", 1700000000000L)
            put("position_rate", 1.5)
            put("positions", 100)
            put("message_rate", 42.5)
            put("bandwidth", 10.0)
            put("connection_time", 3600L)
            put("latency", 100L)
        }
        db.insert("stats_beast", SQLiteDatabase.CONFLICT_REPLACE, beastValues)

        val mlatValues = ContentValues().apply {
            put("receiver_id", "test-receiver-1")
            put("received_at", 1700000000000L)
            put("message_rate", 5.0)
            put("peer_count", 10)
            put("badsync_timeout", 0L)
            put("outlier_percent", 2.5f)
        }
        db.insert("stats_mlat", SQLiteDatabase.CONFLICT_REPLACE, mlatValues)

        db.close()

        val migratedDb = helper.runMigrationsAndValidate(testDb, 2, true, FeederStatsDatabase.MIGRATION_1_2)

        // Verify beast data is intact
        migratedDb.query("SELECT * FROM stats_beast WHERE receiver_id = 'test-receiver-1'").use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.getDouble(cursor.getColumnIndexOrThrow("message_rate")) shouldBe 42.5
            cursor.getInt(cursor.getColumnIndexOrThrow("positions")) shouldBe 100
        }

        // Verify mlat data is intact
        migratedDb.query("SELECT * FROM stats_mlat WHERE receiver_id = 'test-receiver-1'").use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.getDouble(cursor.getColumnIndexOrThrow("message_rate")) shouldBe 5.0
            cursor.getInt(cursor.getColumnIndexOrThrow("peer_count")) shouldBe 10
        }

        // Verify indices exist
        migratedDb.query("SELECT name FROM sqlite_master WHERE type='index' AND name LIKE 'index_stats_%'").use { cursor ->
            val indexNames = mutableListOf<String>()
            while (cursor.moveToNext()) {
                indexNames.add(cursor.getString(0))
            }
            indexNames.contains("index_stats_beast_receiver_id_received_at") shouldBe true
            indexNames.contains("index_stats_mlat_receiver_id_received_at") shouldBe true
        }

        // Verify getSince-style query works
        migratedDb.query("SELECT * FROM stats_beast WHERE receiver_id = 'test-receiver-1' AND received_at >= 1699999999000 ORDER BY received_at ASC").use { cursor ->
            cursor.count shouldBe 1
        }

        migratedDb.close()
    }
}
