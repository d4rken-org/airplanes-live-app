package eu.darken.apl.backup.core

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import eu.darken.apl.common.datastore.DataStoreValue
import eu.darken.apl.common.serialization.SerializationModule
import eu.darken.apl.feeder.core.config.FeederConfig
import eu.darken.apl.feeder.core.config.FeederGroup
import eu.darken.apl.feeder.core.config.FeederSettings
import eu.darken.apl.watch.core.db.WatchDatabase
import eu.darken.apl.watch.core.server.WatchServerMode
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import testhelper.coroutine.TestDispatcherProvider
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
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

    /** Backs a feeder group in memory, so a restore's update is seen by the next read. */
    private class StoredGroup(initial: FeederGroup = FeederGroup()) {
        var current = initial
        val value = mockk<DataStoreValue<FeederGroup>> {
            every { flow } answers { flowOf(current) }
            coEvery { update(any()) } answers {
                val old = current
                current = firstArg<(FeederGroup) -> FeederGroup?>()(old)!!
                DataStoreValue.Updated(old = old, new = current)
            }
        }
    }

    private fun feederRepo(group: StoredGroup) = BackupRepo(
        context = context,
        // Strict, a feeders-only backup must not touch the watches
        watchDatabase = mockk<WatchDatabase>(),
        feederSettings = mockk<FeederSettings> { every { feederGroup } returns group.value },
        json = json,
        dispatcherProvider = TestDispatcherProvider(),
        serverMode = mockk(relaxed = true),
        syncScheduler = mockk(relaxed = true),
    )

    @Test
    fun `an old backup with feeder stats restores its feeder configs and nothing else`() = runBlocking<Unit> {
        val group = StoredGroup()
        val repo = feederRepo(group)
        val zip = ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { out ->
                out.putNextEntry(ZipEntry("backup.json"))
                out.write(OLD_BACKUP_WITH_STATS.toByteArray())
                out.closeEntry()
            }
        }.toByteArray()
        val uri = Uri.parse("content://eu.darken.apl.test/old-backup.zip")
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(zip))

        val preview = repo.readBackup(uri)
        val result = repo.restoreBackup(BackupRepo.RestoreOptions(includeWatches = true, includeFeeders = true))

        preview.feederCount shouldBe 1
        result.errors.shouldBeEmpty()
        result shouldBe BackupRepo.RestoreResult(feedersImported = 1)
        group.current shouldBe FeederGroup(
            configs = setOf(
                FeederConfig(
                    receiverId = "abc-123",
                    addedAt = Instant.parse("2026-01-15T08:30:00Z"),
                    label = "Garage",
                    offlineCheckTimeout = Duration.ofHours(24),
                )
            )
        )
    }

    @Test
    fun `a new backup writes both feeder stats lists empty`() = runBlocking<Unit> {
        val group = StoredGroup(FeederGroup(configs = setOf(FeederConfig(receiverId = "receiver", addedAt = AT))))
        val repo = feederRepo(group)
        val archive = ByteArrayOutputStream()
        val uri = Uri.parse("content://eu.darken.apl.test/export.zip")
        shadowOf(context.contentResolver).registerOutputStream(uri, archive)

        repo.createBackup(uri, BackupRepo.BackupOptions(includeWatches = false, includeFeeders = true))

        val feeders = ZipInputStream(ByteArrayInputStream(archive.toByteArray())).use { zip ->
            zip.nextEntry!!.name shouldBe "backup.json"
            json.parseToJsonElement(zip.readBytes().decodeToString()).jsonObject.getValue("feeders").jsonObject
        }
        feeders.getValue("configs").jsonArray shouldHaveSize 1
        feeders.getValue("beastStats").jsonArray shouldBe JsonArray(emptyList())
        feeders.getValue("mlatStats").jsonArray shouldBe JsonArray(emptyList())
    }

    companion object {
        private val AT = Instant.parse("2026-10-13T10:00:00Z")

        private val OLD_BACKUP_WITH_STATS = """
            {
                "version": 1,
                "createdAt": "2026-03-06T12:00:00Z",
                "appVersion": "0.6.0",
                "appVersionCode": 60000,
                "feeders": {
                    "configs": [
                        {
                            "receiverId": "abc-123",
                            "addedAt": "2026-01-15T08:30:00Z",
                            "label": "Garage",
                            "offlineCheckTimeout": "PT24H"
                        }
                    ],
                    "beastStats": [
                        {
                            "receiverId": "abc-123",
                            "receivedAt": "2026-03-06T12:00:00Z",
                            "positionRate": 5.5,
                            "positions": 500,
                            "messageRate": 200.0,
                            "bandwidth": 512.0,
                            "connectionTime": 3600,
                            "latency": 25
                        }
                    ],
                    "mlatStats": [
                        {
                            "receiverId": "abc-123",
                            "receivedAt": "2026-03-06T12:00:00Z",
                            "messageRate": 50.0,
                            "peerCount": 10,
                            "badSyncTimeout": 2,
                            "outlierPercent": 0.5
                        }
                    ]
                }
            }
        """.trimIndent()
    }
}
