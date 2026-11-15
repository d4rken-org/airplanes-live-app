package eu.darken.apl.common.flight.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelper.coroutine.TestDispatcherProvider
import java.io.File

/** Builds an older database from its exported schema; Room validates the migrated one when it opens it. */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class FlightMigrationTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setup() {
        context.deleteDatabase("flights")
    }

    @After
    fun teardown() {
        context.deleteDatabase("flights")
    }

    private fun createVersion(version: Int, fill: SQLiteDatabase.() -> Unit) {
        val schema = Json.parseToJsonElement(
            File("schemas/eu.darken.apl.common.flight.db.FlightRoomDb/$version.json").readText()
        ).jsonObject.getValue("database").jsonObject

        val file = context.getDatabasePath("flights").apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            schema.getValue("entities").jsonArray.forEach { entity ->
                val table = entity.jsonObject.getValue("tableName").jsonPrimitive.content
                val statements = listOf(entity.jsonObject.getValue("createSql")) +
                    (entity.jsonObject["indices"]?.jsonArray?.map { it.jsonObject.getValue("createSql") } ?: emptyList())
                statements.forEach { db.execSQL(it.jsonPrimitive.content.replace("\${TABLE_NAME}", table)) }
            }
            schema.getValue("setupQueries").jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
            db.version = version
            db.fill()
        }
    }

    private fun SQLiteDatabase.insertRoute() {
        insert("route_cache", null, ContentValues().apply {
            put("aircraft_hex", "406A93")
            put("callsign", "BAW256")
            put("origin_icao", "VIDP")
            put("destination_icao", "EGLL")
            put("seen_at", 1_700_000_000_000L)
            put("fetched_at", 1_700_000_000_000L)
        })
    }

    private fun createVersion1() = createVersion(1) {
        insert("airports", null, ContentValues().apply {
            put("icao_code", "EGLL")
            put("iata_code", "LHR")
            put("name", "London Heathrow Airport")
            put("country", "United Kingdom")
        })
        insertRoute()
    }

    private fun createVersion2() = createVersion(2) {
        insert("airports", null, ContentValues().apply {
            put("icao_code", "EGLL")
            put("iata_code", "LHR")
            put("name", "London Heathrow Airport")
            put("country", "GB")
            put("latitude", 51.4706)
            put("longitude", -0.461941)
        })
        insertRoute()
    }

    @Test
    fun `version 1 data survives and gains empty coordinates`(): Unit = runBlocking {
        createVersion1()
        val db = FlightDatabase(context, TestDispatcherProvider())

        val airport = db.airportByIcao("EGLL").first().shouldNotBeNull()
        airport.name shouldBe "London Heathrow Airport"
        airport.latitude shouldBe null
        airport.longitude shouldBe null

        db.getLatestByHex("406A93").shouldNotBeNull().callsign shouldBe "BAW256"

        db.upsertAirport("EGLL", null, null, null, 51.4706, -0.461941, null, null, null)
        db.airportByIcao("EGLL").first()!!.latitude shouldBe 51.4706
    }

    @Test
    fun `version 2 data survives and gains an empty catalogue id, elevation and time zone`(): Unit = runBlocking {
        createVersion2()
        val db = FlightDatabase(context, TestDispatcherProvider())

        val airport = db.airportByIcao("EGLL").first().shouldNotBeNull()
        airport.iataCode shouldBe "LHR"
        airport.name shouldBe "London Heathrow Airport"
        airport.country shouldBe "GB"
        airport.latitude shouldBe 51.4706
        airport.longitude shouldBe -0.461941
        airport.catalogueId shouldBe null
        airport.elevationFt shouldBe null
        airport.timeZone shouldBe null

        db.getLatestByHex("406A93").shouldNotBeNull().callsign shouldBe "BAW256"

        db.upsertAirport("EGLL", null, null, null, null, null, "oa:2434", 83, "Europe/London")
        db.airportByIcao("EGLL").first()!!.run {
            catalogueId shouldBe "oa:2434"
            elevationFt shouldBe 83
            timeZone shouldBe "Europe/London"
            latitude shouldBe 51.4706
        }
    }
}
