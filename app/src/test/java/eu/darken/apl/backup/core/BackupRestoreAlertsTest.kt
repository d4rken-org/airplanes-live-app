package eu.darken.apl.backup.core

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import eu.darken.apl.common.serialization.SerializationModule
import eu.darken.apl.feeder.core.config.FeederConfig
import eu.darken.apl.feeder.core.config.FeederSettings
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.watch.core.db.WatchDatabase
import eu.darken.apl.watch.core.server.WatchServerMode
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
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
class BackupRestoreAlertsTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val json = SerializationModule().json()
    private val accessRepo = mockk<AccessRepo>(relaxed = true).apply {
        every { state } returns MutableStateFlow<AccessState?>(null)
    }

    private lateinit var db: WatchDatabase

    @Before
    fun setup() {
        context.deleteDatabase("watch")
        db = WatchDatabase(context)
    }

    @After
    fun teardown() {
        context.deleteDatabase("watch")
    }

    private fun repo(feederSettings: FeederSettings = mockk(relaxed = true)) = BackupRepo(
        context = context,
        watchDatabase = db,
        feederSettings = feederSettings,
        json = json,
        dispatcherProvider = TestDispatcherProvider(),
        serverMode = WatchServerMode(accessRepo, db),
        syncScheduler = mockk(relaxed = true),
    )

    private fun aircraft(hex: String?, alert: Boolean) = WatchItemBackup(
        type = "aircraft",
        createdAt = AT,
        notificationEnabled = alert,
        hexCode = hex,
    )

    private fun backup(
        vararg items: WatchItemBackup,
    ) = BackupData(
        version = 1,
        createdAt = AT,
        appVersion = "test",
        appVersionCode = 1,
        watches = WatchBackup(items = items.toList(), checks = emptyList()),
    )

    private suspend fun BackupRepo.restore(
        data: BackupData,
        options: BackupRepo.RestoreOptions = BackupRepo.RestoreOptions(
            includeWatches = true,
            includeFeeders = false,
        ),
    ): BackupRepo.RestoreResult {
        val zip = ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { out ->
                out.putNextEntry(ZipEntry("backup.json"))
                out.write(json.encodeToString(BackupData.serializer(), data).toByteArray())
                out.closeEntry()
            }
        }.toByteArray()
        val uri = Uri.parse("content://eu.darken.apl.test/backup.zip")
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(zip))
        readBackup(uri)
        return restoreBackup(options)
    }

    @Test
    fun `only imported watches with alerts on are counted`() = runBlocking<Unit> {
        val data = backup(
            aircraft("3C6501", alert = true),
            aircraft("3C6502", alert = false),
            aircraft("3C6503", alert = true),
        )

        val result = repo().restore(data)

        result.watchesImported shouldBe 3
        result.watchAlertsImported shouldBe 2
    }

    @Test
    fun `a watch with alerts on that exists already is not counted`() = runBlocking<Unit> {
        db.createAircraft("3C6501", "")

        val result = repo().restore(backup(aircraft("3C6501", alert = true)))

        result.watchesExisted shouldBe 1
        result.watchAlertsImported shouldBe 0
    }

    @Test
    fun `watches left out of the restore are not counted`() = runBlocking<Unit> {
        val result = repo().restore(
            backup(aircraft("3C6501", alert = true)),
            BackupRepo.RestoreOptions(includeWatches = false, includeFeeders = false),
        )

        result.watchesImported shouldBe 0
        result.watchAlertsImported shouldBe 0
    }

    @Test
    fun `a backup without watches counts no alerts`() = runBlocking<Unit> {
        val result = repo().restore(backup().copy(watches = null))

        result.watchAlertsImported shouldBe 0
    }

    @Test
    fun `a watch with alerts on that fails to import is not counted`() = runBlocking<Unit> {
        val data = backup(
            aircraft(null, alert = true),
            aircraft("3C6502", alert = true),
        )

        val result = repo().restore(data)

        result.watchesFailed shouldBe 1
        result.watchAlertsImported shouldBe 1
    }

    @Test
    fun `a later section failing keeps the imported alerts counted`() = runBlocking<Unit> {
        val boom = IllegalStateException("feeder settings unreadable")
        val feederSettings = mockk<FeederSettings> { every { feederGroup } throws boom }
        val data = backup(aircraft("3C6501", alert = true)).copy(
            feeders = FeederBackup(
                configs = listOf(FeederConfig(receiverId = "receiver", addedAt = AT)),
                beastStats = emptyList(),
                mlatStats = emptyList(),
            ),
        )

        val result = repo(feederSettings).restore(
            data,
            BackupRepo.RestoreOptions(includeWatches = true, includeFeeders = true),
        )

        result.watchAlertsImported shouldBe 1
        result.errors.map { it.section } shouldBe listOf(BackupRepo.RestoreError.Section.FEEDERS)
    }

    companion object {
        private val AT = Instant.parse("2026-10-13T10:00:00Z")
    }
}
