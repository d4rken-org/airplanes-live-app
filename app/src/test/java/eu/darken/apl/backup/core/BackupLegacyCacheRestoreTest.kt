package eu.darken.apl.backup.core

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import eu.darken.apl.common.serialization.SerializationModule
import eu.darken.apl.watch.core.db.WatchDatabase
import eu.darken.apl.watch.core.db.types.AircraftWatchEntity
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import testhelper.coroutine.TestDispatcherProvider
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class BackupLegacyCacheRestoreTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val uri = Uri.parse("content://eu.darken.apl.test/backup.zip")

    private val imported = slot<AircraftWatchEntity>()
    private val watchDatabase = mockk<WatchDatabase>(relaxed = true) {
        coEvery { hasAircraftWatch(any()) } returns false
        coEvery { importAircraft(any(), capture(imported), any()) } returns mockk()
    }

    private val repo = BackupRepo(
        context = context,
        watchDatabase = watchDatabase,
        feederSettings = mockk(relaxed = true),
        feederStatsDatabase = mockk(relaxed = true),
        json = SerializationModule().json(),
        dispatcherProvider = TestDispatcherProvider(),
        serverMode = mockk(relaxed = true),
        syncScheduler = mockk(relaxed = true),
    )

    private suspend fun read(content: String): BackupRepo.RestorePreview {
        val zip = ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { out ->
                out.putNextEntry(ZipEntry("backup.json"))
                out.write(content.toByteArray())
                out.closeEntry()
            }
        }.toByteArray()
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(zip))
        return repo.readBackup(uri)
    }

    private suspend fun restore(content: String): BackupRepo.RestoreResult {
        read(content)
        return repo.restoreBackup(BackupRepo.RestoreOptions(includeWatches = true, includeFeeders = true))
    }

    @Test
    fun `an aircraft watch saved without a registration takes it from the archive's aircraft cache`() = runBlocking<Unit> {
        val result = restore(archive(watch = WATCH_WITHOUT_REGISTRATION, cachedHex = "3c65a3", cachedRegistration = "D-AIBL"))

        result.errors.shouldBeEmpty()
        result.watchesImported shouldBe 1
        imported.captured.hexCode shouldBe "3C65A3"
        imported.captured.lastRegistration shouldBe "D-AIBL"
    }

    @Test
    fun `an aircraft watch keeps its own registration over the archive's aircraft cache`() = runBlocking<Unit> {
        val watch = """
            {
                "type": "aircraft",
                "createdAt": "2026-03-06T12:00:00Z",
                "hexCode": "3C65A3",
                "lastRegistration": "D-AIBA"
            }
        """
        val result = restore(archive(watch = watch, cachedHex = "3C65A3", cachedRegistration = "D-AIBL"))

        result.errors.shouldBeEmpty()
        imported.captured.lastRegistration shouldBe "D-AIBA"
    }

    @Test
    fun `an archive holding only an aircraft cache reads as empty`() = runBlocking<Unit> {
        val preview = read(
            """
            {
                "version": 1,
                "createdAt": "2026-03-06T12:00:00Z",
                "appVersion": "0.8.0",
                "appVersionCode": 80000,
                "aircraftCache": {
                    "items": [${cacheItem("3C65A3", "D-AIBL")}]
                }
            }
            """.trimIndent()
        )

        preview.watchCount shouldBe 0
        preview.checkCount shouldBe 0
        preview.feederCount shouldBe 0
        preview.statsCount shouldBe 0
    }

    private fun archive(watch: String, cachedHex: String, cachedRegistration: String) = """
        {
            "version": 1,
            "createdAt": "2026-03-06T12:00:00Z",
            "appVersion": "0.8.0",
            "appVersionCode": 80000,
            "watches": {
                "items": [$watch],
                "checks": []
            },
            "aircraftCache": {
                "items": [${cacheItem(cachedHex, cachedRegistration)}]
            }
        }
    """.trimIndent()

    private fun cacheItem(hex: String, registration: String) = """
        {
            "hex": "$hex",
            "source": "adsb_icao",
            "registration": "$registration",
            "callsign": "DLH453",
            "operator": "Lufthansa",
            "airframe": "A343",
            "military": false,
            "ladd": false,
            "pia": false,
            "altitudeFt": 35000,
            "onGround": false,
            "groundSpeed": 450.5,
            "latitude": 50.1,
            "longitude": 8.6,
            "messageSeenAt": "2026-03-06T11:59:00Z",
            "positionSeenAt": "2026-03-06T11:59:00Z",
            "fetchedAt": "2026-03-06T12:00:00Z"
        }
    """

    companion object {
        private val WATCH_WITHOUT_REGISTRATION = """
            {
                "type": "aircraft",
                "createdAt": "2026-03-06T12:00:00Z",
                "notificationEnabled": true,
                "userNote": "",
                "hexCode": "3C65A3"
            }
        """
    }
}
