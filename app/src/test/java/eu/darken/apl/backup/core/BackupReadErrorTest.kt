package eu.darken.apl.backup.core

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import eu.darken.apl.R
import eu.darken.apl.common.serialization.SerializationModule
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
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
import java.io.EOFException
import java.io.FileNotFoundException
import java.time.format.DateTimeParseException
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class BackupReadErrorTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val uri = Uri.parse("content://eu.darken.apl.test/backup.zip")

    private fun repo(context: Context = this.context) = BackupRepo(
        context = context,
        watchDatabase = mockk(relaxed = true),
        feederSettings = mockk(relaxed = true),
        feederStatsDatabase = mockk(relaxed = true),
        generalSettings = mockk(relaxed = true),
        aircraftDatabase = mockk(relaxed = true),
        json = SerializationModule().json(),
        dispatcherProvider = TestDispatcherProvider(),
        serverMode = mockk(relaxed = true),
        syncScheduler = mockk(relaxed = true),
    )

    private fun repoWith(resolver: ContentResolver) = repo(mockk<Context> { every { contentResolver } returns resolver })

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { out ->
            entries.forEach { (name, content) ->
                out.putNextEntry(ZipEntry(name))
                out.write(content)
                out.closeEntry()
            }
        }
    }.toByteArray()

    private fun readArchive(archive: ByteArray) = runBlocking {
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(archive))
        repo().readBackup(uri)
    }

    private fun read(entryName: String, content: String) = readArchive(zipOf(entryName to content.toByteArray()))

    /** Where the first entry's compressed data starts, after its local header, name and extra field. */
    private fun ByteArray.firstEntryDataOffset(): Int {
        fun u16(at: Int) = (this[at].toInt() and 0xFF) or ((this[at + 1].toInt() and 0xFF) shl 8)
        return 30 + u16(26) + u16(28)
    }

    private fun BackupException.localizedDescription() = getLocalizedError(context).description.get(context)

    @Test
    fun `a backup entry that does not decode is not a valid backup`() {
        shouldThrow<BackupException.Invalid> { read("backup.json", "{ not json") }
    }

    @Test
    fun `an archive without the backup entry is not a valid backup`() {
        shouldThrow<BackupException.Invalid> { read("photo.jpg", "{}") }
    }

    @Test
    fun `a backup from a newer format version asks for an app update`() {
        val content = """{"version":999,"createdAt":"2024-01-01T00:00:00Z","appVersion":"9.9","appVersionCode":999}"""
        shouldThrow<BackupException.NewerVersion> { read("backup.json", content) }.version shouldBe 999
    }

    @Test
    fun `a file that cannot be opened for reading`() {
        val missing = mockk<ContentResolver> { every { openInputStream(uri) } returns null }
        shouldThrow<BackupException.CannotOpen> { runBlocking { repoWith(missing).readBackup(uri) } }.apply {
            cause.shouldBeNull()
            localizedDescription() shouldBe context.getString(R.string.backup_error_cannot_open)
        }

        val gone = FileNotFoundException("gone")
        val failing = mockk<ContentResolver> { every { openInputStream(uri) } throws gone }
        shouldThrow<BackupException.CannotOpen> { runBlocking { repoWith(failing).readBackup(uri) } }.apply {
            cause shouldBeSameInstanceAs gone
            localizedDescription() shouldBe context.getString(R.string.backup_error_cannot_open)
        }
    }

    @Test
    fun `a file that cannot be opened for writing`() {
        val options = BackupRepo.BackupOptions(includeWatches = false, includeFeeders = false, includeAircraftCache = false)

        val missing = mockk<ContentResolver> { every { openOutputStream(uri) } returns null }
        shouldThrow<BackupException.CannotOpen> { runBlocking { repoWith(missing).createBackup(uri, options) } }.apply {
            cause.shouldBeNull()
            localizedDescription() shouldBe context.getString(R.string.backup_error_cannot_open)
        }

        val gone = FileNotFoundException("gone")
        val failing = mockk<ContentResolver> { every { openOutputStream(uri) } throws gone }
        shouldThrow<BackupException.CannotOpen> { runBlocking { repoWith(failing).createBackup(uri, options) } }.apply {
            cause shouldBeSameInstanceAs gone
            localizedDescription() shouldBe context.getString(R.string.backup_error_cannot_open)
        }
    }

    @Test
    fun `a backup entry above the size limit is refused`() {
        val archive = ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { out ->
                out.putNextEntry(ZipEntry("backup.json"))
                val chunk = ByteArray(1024 * 1024)
                repeat(50) { out.write(chunk) }
                out.write(0)
                out.closeEntry()
            }
        }.toByteArray()

        shouldThrow<BackupException.TooLarge> { readArchive(archive) }.apply {
            maxSizeMb shouldBe 50L
            localizedDescription() shouldBe context.getString(R.string.backup_error_file_too_large_x, 50L)
        }
    }

    @Test
    fun `a corrupt archive is not a valid backup`() {
        val archive = zipOf("backup.json" to VALID_JSON.toByteArray())
        // A deflate block header of all ones names the reserved block type
        archive[archive.firstEntryDataOffset()] = 0xFF.toByte()

        shouldThrow<BackupException.Invalid> { readArchive(archive) }.apply {
            cause.shouldBeInstanceOf<ZipException>()
            localizedDescription() shouldBe context.getString(R.string.backup_error_invalid)
        }
    }

    @Test
    fun `an archive cut off inside the entry is not a valid backup`() {
        val archive = zipOf("backup.json" to VALID_JSON.toByteArray())
        val truncated = archive.copyOf(archive.firstEntryDataOffset() + 2)

        shouldThrow<BackupException.Invalid> { readArchive(truncated) }.apply {
            cause.shouldBeInstanceOf<EOFException>()
            localizedDescription() shouldBe context.getString(R.string.backup_error_invalid)
        }
    }

    @Test
    fun `an archive with more than the backup entry is not a valid backup`() {
        val archive = zipOf("backup.json" to VALID_JSON.toByteArray(), "extra.txt" to "x".toByteArray())

        shouldThrow<BackupException.Invalid> { readArchive(archive) }
            .localizedDescription() shouldBe context.getString(R.string.backup_error_invalid)
    }

    @Test
    fun `an empty archive is not a valid backup`() {
        shouldThrow<BackupException.Invalid> { readArchive(zipOf()) }
            .localizedDescription() shouldBe context.getString(R.string.backup_error_invalid)
    }

    @Test
    fun `a backup with a broken timestamp is not a valid backup`() {
        val content = VALID_JSON.replace("2024-01-01T00:00:00Z", "yesterday")

        shouldThrow<BackupException.Invalid> { read("backup.json", content) }.apply {
            cause.shouldBeInstanceOf<DateTimeParseException>()
            localizedDescription() shouldBe context.getString(R.string.backup_error_invalid)
        }
    }

    @Test
    fun `the valid archive used above reads`() {
        readArchive(zipOf("backup.json" to VALID_JSON.toByteArray())).appVersion shouldBe "1.0"
    }

    companion object {
        private const val VALID_JSON = """{"version":1,"createdAt":"2024-01-01T00:00:00Z","appVersion":"1.0","appVersionCode":1}"""
    }
}
