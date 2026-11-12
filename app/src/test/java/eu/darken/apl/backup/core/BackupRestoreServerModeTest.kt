package eu.darken.apl.backup.core

import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import eu.darken.apl.common.serialization.SerializationModule
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.server.api.Allowance
import eu.darken.apl.server.api.RequestLimits
import eu.darken.apl.server.api.RequestRate
import eu.darken.apl.server.api.TierPolicy
import eu.darken.apl.server.api.Usage
import eu.darken.apl.watch.core.db.WatchDatabase
import eu.darken.apl.watch.core.server.WatchListSyncScheduler
import eu.darken.apl.watch.core.server.WatchServerMode
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.LocationWatch
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import testhelper.coroutine.TestDispatcherProvider
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class BackupRestoreServerModeTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val json = SerializationModule().json()
    private val accessState = MutableStateFlow<AccessState?>(null)
    private val accessRepo = mockk<AccessRepo>(relaxed = true).apply {
        every { state } returns accessState
    }
    private val syncScheduler = mockk<WatchListSyncScheduler>(relaxed = true)

    private lateinit var db: WatchDatabase
    private lateinit var repo: BackupRepo

    @Before
    fun setup() {
        context.deleteDatabase("watch")
        db = WatchDatabase(context)
        repo = BackupRepo(
            context = context,
            watchDatabase = db,
            feederSettings = mockk(relaxed = true),
            json = json,
            dispatcherProvider = TestDispatcherProvider(),
            serverMode = WatchServerMode(accessRepo, db),
            syncScheduler = syncScheduler,
        )
    }

    @After
    fun teardown() {
        context.deleteDatabase("watch")
    }

    private fun backup(count: Int) = BackupData(
        version = 1,
        createdAt = AT,
        appVersion = "test",
        appVersionCode = 1,
        watches = WatchBackup(
            items = (0 until count).map { index ->
                WatchItemBackup(type = "aircraft", createdAt = AT.plusSeconds(index.toLong()), hexCode = hexOf(index))
            },
            // As older versions exported them, a restore ignores them
            checks = (0 until count).map { index ->
                WatchCheckBackup(watchIndex = index, checkedAt = AT, aircraftCount = index, seenHexes = hexOf(index))
            },
        ),
    )

    private fun hexOf(index: Int) = "3C65%02X".format(index)

    private suspend fun restore(data: BackupData): BackupRepo.RestoreResult {
        val zip = ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { out ->
                out.putNextEntry(ZipEntry("backup.json"))
                out.write(json.encodeToString(BackupData.serializer(), data).toByteArray())
                out.closeEntry()
            }
        }.toByteArray()
        return restoreArchive(zip)
    }

    private suspend fun restoreArchive(zip: ByteArray): BackupRepo.RestoreResult {
        val uri = Uri.parse("content://eu.darken.apl.test/backup.zip")
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(zip))
        repo.readBackup(uri)
        return repo.restoreBackup(
            BackupRepo.RestoreOptions(includeWatches = true, includeFeeders = false)
        )
    }

    @Test
    fun `in server mode a restore beyond the active limit imports every watch`() = runBlocking<Unit> {
        accessState.value = access(offersWatchList = true, maxActive = 10)

        val result = restore(backup(12))

        result.watchesImported shouldBe 12
        db.snapshot().map { (it as AircraftWatch).hex }.toSet() shouldBe (0 until 12).map { hexOf(it) }.toSet()
        verify(exactly = 1) { syncScheduler.requestSync("restore") }
    }

    @Test
    fun `a restore imports in creation order`() = runBlocking<Unit> {
        accessState.value = access(offersWatchList = true, maxActive = 10)
        val createdAt = listOf(AT.plusSeconds(2), AT, AT.plusSeconds(1), AT.plusSeconds(1))
        val data = BackupData(
            version = 1,
            createdAt = AT,
            appVersion = "test",
            appVersionCode = 1,
            watches = WatchBackup(
                items = createdAt.mapIndexed { index, at ->
                    WatchItemBackup(type = "aircraft", createdAt = at, hexCode = hexOf(index))
                },
                checks = createdAt.indices.reversed().map { index ->
                    WatchCheckBackup(watchIndex = index, checkedAt = AT, aircraftCount = index, seenHexes = hexOf(index))
                },
            ),
        )

        restore(data).watchesImported shouldBe 4

        val watches = db.snapshot()
        val hexById = watches.associate { it.id to (it as AircraftWatch).hex }
        // Ties keep the archive's order
        insertionOrder().map { hexById[it] } shouldBe listOf(hexOf(1), hexOf(2), hexOf(3), hexOf(0))
        watches.forEach { watch ->
            val hex = hexById.getValue(watch.id)
            watch.addedAt shouldBe createdAt[hex.takeLast(2).toInt(16)]
        }
    }

    @Test
    fun `without server mode a restore is not held to the active limit`() = runBlocking<Unit> {
        accessState.value = access(offersWatchList = false, maxActive = 10)

        val result = restore(backup(12))

        result.watchesImported shouldBe 12
        db.snapshot().size shouldBe 12
        verify(exactly = 0) { syncScheduler.requestSync(any()) }
    }

    @Test
    fun `a watch that exists already is skipped`() = runBlocking<Unit> {
        accessState.value = access(offersWatchList = true, maxActive = 10)
        db.createAircraft(hexOf(0), "")

        val result = restore(backup(2))

        result.watchesImported shouldBe 1
        result.watchesExisted shouldBe 1
        db.snapshot().size shouldBe 2
    }

    @Test
    fun `location watches are the same watch only when their area matches`() = runBlocking<Unit> {
        accessState.value = access(offersWatchList = false, maxActive = 10)
        db.createLocation(52.52, 13.405, 5000f, null, null, "Berlin", "")
        fun location(lat: Double, radius: Float) = WatchItemBackup(
            type = "location", createdAt = AT, locationLabel = "Berlin", latitude = lat, longitude = 13.405, radiusInMeters = radius,
        )
        val data = BackupData(
            version = 1, createdAt = AT, appVersion = "test", appVersionCode = 1,
            watches = WatchBackup(
                items = listOf(location(52.52, 5000f), location(52.52, 50000f), location(52.4, 5000f)),
                checks = emptyList(),
            ),
        )

        val result = restore(data)

        result.watchesImported shouldBe 2
        result.watchesExisted shouldBe 1
        db.snapshot().map { (it as LocationWatch).radiusInMeters to it.latitude }.toSet() shouldBe setOf(
            5000f to 52.52, 50000f to 52.52, 5000f to 52.4,
        )
    }

    @Test
    fun `a watch that cannot be imported counts as failed, not as existing`() = runBlocking<Unit> {
        accessState.value = access(offersWatchList = false, maxActive = 10)
        val data = backup(2).let { data ->
            data.copy(watches = data.watches!!.copy(items = data.watches.items.mapIndexed { index, item ->
                if (index == 0) item.copy(hexCode = null) else item
            }))
        }

        val result = restore(data)

        result.watchesImported shouldBe 1
        result.watchesExisted shouldBe 0
        result.watchesFailed shouldBe 1
    }

    @Test
    fun `an exported backup comes back, its location watch found whatever its label`() = runBlocking<Unit> {
        accessState.value = access(offersWatchList = false, maxActive = 10)
        val location = db.createLocation(52.520008, 13.404954, 2500.5f, null, null, "Home", "")
        val aircraft = db.createAircraft("3C65A3", "")
        val archive = ByteArrayOutputStream()
        val exportUri = Uri.parse("content://eu.darken.apl.test/export.zip")
        shadowOf(context.contentResolver).registerOutputStream(exportUri, archive)
        repo.createBackup(
            exportUri,
            BackupRepo.BackupOptions(includeWatches = true, includeFeeders = false),
        )
        ZipInputStream(ByteArrayInputStream(archive.toByteArray())).use { zip ->
            zip.nextEntry!!.name shouldBe "backup.json"
            val exported = json.parseToJsonElement(zip.readBytes().decodeToString()).jsonObject
            exported.keys shouldNotContain "aircraftCache"
            // An older version needs the key to read the archive
            exported.getValue("watches").jsonObject.getValue("checks").jsonArray shouldBe JsonArray(emptyList())
        }
        db.deleteWatch(location.id)
        db.deleteWatch(aircraft.id)
        db.createLocation(52.520008, 13.404954, 2500.5f, null, null, "Office", "")

        val result = restoreArchive(archive.toByteArray())

        result.watchesImported shouldBe 1
        result.watchesExisted shouldBe 1
        db.snapshot().filterIsInstance<AircraftWatch>().single().hex shouldBe "3C65A3"
    }

    @Test
    fun `an exported altitude band comes back, in a backup older versions refuse`() = runBlocking<Unit> {
        accessState.value = access(offersWatchList = false, maxActive = 10)
        val banded = db.createLocation(52.52, 13.405, 5000f, 1_000, 10_000, "Berlin", "")
        val plain = db.createLocation(48.13, 11.58, 5000f, null, null, "Munich", "")

        val archive = export()

        exportedJson(archive).apply {
            getValue("version").jsonPrimitive.int shouldBe 2
            val items = getValue("watches").jsonObject.getValue("items").jsonArray.map { it.jsonObject }
            val berlin = items.single { it.getValue("locationLabel").jsonPrimitive.content == "Berlin" }
            berlin.getValue("minAltitudeFt").jsonPrimitive.int shouldBe 1_000
            berlin.getValue("maxAltitudeFt").jsonPrimitive.int shouldBe 10_000
            val munich = items.single { it.getValue("locationLabel").jsonPrimitive.content == "Munich" }
            munich.keys shouldNotContain "minAltitudeFt"
            munich.keys shouldNotContain "maxAltitudeFt"
        }
        db.deleteWatch(banded.id)
        db.deleteWatch(plain.id)

        val result = restoreArchive(archive)

        result.watchesImported shouldBe 2
        db.snapshot().map { it as LocationWatch }.associate { it.label to (it.minAltitudeFt to it.maxAltitudeFt) } shouldBe
                mapOf("Berlin" to (1_000 to 10_000), "Munich" to (null to null))
    }

    @Test
    fun `a backup without altitude bands stays readable by older versions`() = runBlocking<Unit> {
        accessState.value = access(offersWatchList = false, maxActive = 10)
        db.createLocation(52.52, 13.405, 5000f, null, null, "Berlin", "")
        db.createAircraft("3C65A3", "")

        exportedJson(export()).getValue("version").jsonPrimitive.int shouldBe 1
    }

    @Test
    fun `an invalid altitude band fails its watch, it is never clamped`() = runBlocking<Unit> {
        accessState.value = access(offersWatchList = false, maxActive = 10)
        val data = locationBackup(
            banded(10_000, 5_000),
            banded(-1, null),
            banded(null, 60_001),
            banded(60_001, null),
        )

        val result = restore(data)

        result.watchesImported shouldBe 0
        result.watchesFailed shouldBe 4
        db.snapshot() shouldBe emptyList()
    }

    @Test
    fun `altitude bands open at one end or at zero are restored`() = runBlocking<Unit> {
        accessState.value = access(offersWatchList = false, maxActive = 10)
        val data = locationBackup(
            banded(0, null, latitude = 52.1),
            banded(null, 60_000, latitude = 52.2),
            banded(0, 0, latitude = 52.3),
        )

        val result = restore(data)

        result.watchesImported shouldBe 3
        db.snapshot().map { it as LocationWatch }.associate { it.latitude to (it.minAltitudeFt to it.maxAltitudeFt) } shouldBe
                mapOf(52.1 to (0 to null), 52.2 to (null to 60_000), 52.3 to (0 to 0))
    }

    @Test
    fun `the same area is the same watch only with the same altitude band`() = runBlocking<Unit> {
        accessState.value = access(offersWatchList = false, maxActive = 10)
        db.createLocation(52.52, 13.405, 5000f, null, null, "Berlin", "")
        db.createLocation(52.52, 13.405, 5000f, 1_000, null, "Berlin", "")
        val data = locationBackup(
            banded(null, null),
            banded(1_000, null),
            banded(1_000, 10_000),
            banded(null, 10_000),
        )

        val result = restore(data)

        result.watchesImported shouldBe 2
        result.watchesExisted shouldBe 2
        db.snapshot().map { it as LocationWatch }.map { it.minAltitudeFt to it.maxAltitudeFt }.toSet() shouldBe setOf(
            null to null, 1_000 to null, 1_000 to 10_000, null to 10_000,
        )
    }

    private fun banded(minAltitudeFt: Int?, maxAltitudeFt: Int?, latitude: Double = 52.52) = WatchItemBackup(
        type = "location",
        createdAt = AT,
        locationLabel = "Berlin",
        latitude = latitude,
        longitude = 13.405,
        radiusInMeters = 5000f,
        minAltitudeFt = minAltitudeFt,
        maxAltitudeFt = maxAltitudeFt,
    )

    private fun locationBackup(vararg items: WatchItemBackup) = BackupData(
        version = 2,
        createdAt = AT,
        appVersion = "test",
        appVersionCode = 1,
        watches = WatchBackup(items = items.toList()),
    )

    private suspend fun export(): ByteArray {
        val archive = ByteArrayOutputStream()
        val exportUri = Uri.parse("content://eu.darken.apl.test/export.zip")
        shadowOf(context.contentResolver).registerOutputStream(exportUri, archive)
        repo.createBackup(exportUri, BackupRepo.BackupOptions(includeWatches = true, includeFeeders = false))
        return archive.toByteArray()
    }

    private fun exportedJson(archive: ByteArray): JsonObject =
        ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
            zip.nextEntry!!.name shouldBe "backup.json"
            json.parseToJsonElement(zip.readBytes().decodeToString()).jsonObject
        }

    private fun insertionOrder(): List<String> = SQLiteDatabase.openDatabase(
        context.getDatabasePath("watch").path,
        null,
        SQLiteDatabase.OPEN_READWRITE,
    ).use { raw ->
        raw.rawQuery("SELECT id FROM watch_base ORDER BY rowid", null).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
    }

    private fun access(offersWatchList: Boolean, maxActive: Int) = AccessState(
        installationId = "installation",
        fetchedAt = AT,
        tier = AccessState.Tier.FREE,
        restricted = false,
        allowanceScope = "principal",
        limits = TierPolicy(
            viewingIntervalSeconds = 5, viewingBurst = 3, viewingPerDay = 25000, searchesPerDay = 25,
            watchesPerDay = 1000, maxRadiusNm = 25, watchTypes = listOf("hex", "callsign"),
            maxActiveWatches = maxActive, maxStoredWatches = 50,
        ),
        usage = Usage(
            resetsAt = 3_600_000,
            viewing = Allowance(25000, 0, 0, 25000),
            search = Allowance(25, 0, 0, 25),
            watch = Allowance(1000, 0, 0, 1000),
        ),
        installationRequests = RequestLimits(
            viewing = RequestRate(perSecond = 0.2, burst = 3),
            search = RequestRate(perSecond = 1.0, burst = 3),
            watch = RequestRate(perSecond = 1.0, burst = 3),
            concurrency = 3,
        ),
        aircraftOperations = if (offersWatchList) listOf(AccessState.OPERATION_WATCHLIST) else emptyList(),
    )

    companion object {
        private val AT = Instant.parse("2026-10-13T10:00:00Z")
    }
}
