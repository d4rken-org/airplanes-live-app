package eu.darken.apl.backup.core

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import eu.darken.apl.common.serialization.SerializationModule
import eu.darken.apl.feeder.core.config.FeederConfig
import eu.darken.apl.feeder.core.config.FeederSettings
import eu.darken.apl.main.core.db.AircraftDatabase
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
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
class BackupRestoreErrorTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val json = SerializationModule().json()

    @Test
    fun `a failing section is reported with its cause and later sections still restore`() = runBlocking<Unit> {
        val boom = IllegalStateException("feeder settings unreadable")
        val feederSettings = mockk<FeederSettings> { every { feederGroup } throws boom }
        val aircraftDatabase = mockk<AircraftDatabase>(relaxed = true) {
            coEvery { count() } returnsMany listOf(0, 1)
        }
        val repo = BackupRepo(
            context = context,
            watchDatabase = mockk(relaxed = true),
            feederSettings = feederSettings,
            feederStatsDatabase = mockk(relaxed = true),
            generalSettings = mockk(relaxed = true),
            aircraftDatabase = aircraftDatabase,
            json = json,
            dispatcherProvider = TestDispatcherProvider(),
            serverMode = mockk(relaxed = true),
            syncScheduler = mockk(relaxed = true),
        )
        val data = BackupData(
            version = 1,
            createdAt = AT,
            appVersion = "test",
            appVersionCode = 1,
            feeders = FeederBackup(
                configs = listOf(FeederConfig(receiverId = "receiver", addedAt = AT)),
                beastStats = emptyList(),
                mlatStats = emptyList(),
            ),
            aircraftCache = AircraftCacheBackup(items = listOf(AircraftCacheItemBackup(hex = "3C65A3", fetchedAt = AT))),
        )
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

        val result = repo.restoreBackup(
            BackupRepo.RestoreOptions(includeWatches = true, includeFeeders = true, includeAircraftCache = true)
        )

        result.errors shouldHaveSize 1
        result.errors.single().apply {
            section shouldBe BackupRepo.RestoreError.Section.FEEDERS
            cause shouldBeSameInstanceAs boom
        }
        result.feedersImported shouldBe 0
        result.aircraftCacheImported shouldBe 1
        coVerify { aircraftDatabase.update(match { aircraft -> aircraft.single().hex == "3C65A3" }) }
    }

    companion object {
        private val AT = Instant.parse("2026-10-13T10:00:00Z")
    }
}
