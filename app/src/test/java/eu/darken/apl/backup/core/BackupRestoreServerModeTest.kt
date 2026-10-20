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
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
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
            feederStatsDatabase = mockk(relaxed = true),
            generalSettings = mockk(relaxed = true),
            aircraftDatabase = mockk(relaxed = true),
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
        val uri = Uri.parse("content://eu.darken.apl.test/backup.zip")
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(zip))
        repo.readBackup(uri)
        return repo.restoreBackup(
            BackupRepo.RestoreOptions(includeWatches = true, includeFeeders = false, includeAircraftCache = false)
        )
    }

    @Test
    fun `in server mode a restore beyond the active limit imports every watch with its checks`() = runBlocking<Unit> {
        accessState.value = access(offersWatchList = true, maxActive = 10)

        val result = restore(backup(12))

        result.watchesImported shouldBe 12
        result.checksImported shouldBe 12
        val (watches, checks) = db.snapshot()
        watches.map { (it as AircraftWatch).hex }.toSet() shouldBe (0 until 12).map { hexOf(it) }.toSet()
        watches.forEach { watch ->
            val hex = (watch as AircraftWatch).hex
            checks.single { it.watchId == watch.id }.seenHexes shouldBe hex
        }
        verify(exactly = 1) { syncScheduler.requestSync("restore") }
    }

    @Test
    fun `a restore imports in creation order, each watch with its own checks`() = runBlocking<Unit> {
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

        val (watches, checks) = db.snapshot()
        val hexById = watches.associate { it.id to (it as AircraftWatch).hex }
        // Ties keep the archive's order
        insertionOrder().map { hexById[it] } shouldBe listOf(hexOf(1), hexOf(2), hexOf(3), hexOf(0))
        watches.forEach { watch ->
            val hex = hexById.getValue(watch.id)
            checks.single { it.watchId == watch.id }.seenHexes shouldBe hex
            watch.addedAt shouldBe createdAt[hex.takeLast(2).toInt(16)]
        }
    }

    @Test
    fun `without server mode a restore is not held to the active limit`() = runBlocking<Unit> {
        accessState.value = access(offersWatchList = false, maxActive = 10)

        val result = restore(backup(12))

        result.watchesImported shouldBe 12
        val (watches, checks) = db.snapshot()
        watches.size shouldBe 12
        watches.forEach { watch -> checks.count { it.watchId == watch.id } shouldBe 1 }
        verify(exactly = 0) { syncScheduler.requestSync(any()) }
    }

    @Test
    fun `a watch that exists already is skipped with its checks`() = runBlocking<Unit> {
        accessState.value = access(offersWatchList = true, maxActive = 10)
        db.createAircraft(hexOf(0), "")

        val result = restore(backup(2))

        result.watchesImported shouldBe 1
        result.watchesExisted shouldBe 1
        result.checksImported shouldBe 1
        result.checksExisted shouldBe 1
        db.snapshot().second.size shouldBe 1
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
