package eu.darken.apl.backup.core

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import eu.darken.apl.common.datastore.DataStoreValue
import eu.darken.apl.common.serialization.SerializationModule
import eu.darken.apl.feeder.core.config.FeederConfig
import eu.darken.apl.feeder.core.config.FeederGroup
import eu.darken.apl.feeder.core.config.FeederSettings
import eu.darken.apl.watch.core.server.WatchServerMode
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
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
        val boom = IllegalStateException("server mode unreadable")
        val serverMode = mockk<WatchServerMode> { coEvery { current() } throws boom }
        val group = mockk<DataStoreValue<FeederGroup>> {
            every { flow } returns flowOf(FeederGroup())
            coEvery { update(any()) } answers {
                DataStoreValue.Updated(old = FeederGroup(), new = firstArg<(FeederGroup) -> FeederGroup?>()(FeederGroup())!!)
            }
        }
        val feederSettings = mockk<FeederSettings> { every { feederGroup } returns group }
        val repo = BackupRepo(
            context = context,
            watchDatabase = mockk(relaxed = true),
            feederSettings = feederSettings,
            feederStatsDatabase = mockk(relaxed = true),
            json = json,
            dispatcherProvider = TestDispatcherProvider(),
            serverMode = serverMode,
            syncScheduler = mockk(relaxed = true),
        )
        val data = BackupData(
            version = 1,
            createdAt = AT,
            appVersion = "test",
            appVersionCode = 1,
            watches = WatchBackup(
                items = listOf(WatchItemBackup(type = "aircraft", createdAt = AT, hexCode = "3C65A3")),
                checks = emptyList(),
            ),
            feeders = FeederBackup(
                configs = listOf(FeederConfig(receiverId = "receiver", addedAt = AT)),
                beastStats = emptyList(),
                mlatStats = emptyList(),
            ),
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
            BackupRepo.RestoreOptions(includeWatches = true, includeFeeders = true)
        )

        result.errors shouldHaveSize 1
        result.errors.single().apply {
            section shouldBe BackupRepo.RestoreError.Section.WATCHES
            cause shouldBeSameInstanceAs boom
        }
        result.feedersImported shouldBe 1
    }

    companion object {
        private val AT = Instant.parse("2026-10-13T10:00:00Z")
    }
}
