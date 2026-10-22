package eu.darken.apl.backup.core

import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import eu.darken.apl.main.core.db.AircraftDatabase
import eu.darken.apl.main.core.db.PendingOperationEntity
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelper.coroutine.TestDispatcherProvider

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class SystemRestoreAgentTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setup() {
        context.deleteDatabase("aircraft")
        SystemRestoreMarker.entries.forEach { it.clear(context) }
    }

    @After
    fun teardown() {
        context.deleteDatabase("aircraft")
        SystemRestoreMarker.entries.forEach { it.clear(context) }
    }

    @Test
    fun `backups stay Android's file backups with the restore agent declared`() {
        // Without full backup only, a declared agent means key/value backups, and this agent backs up nothing
        context.applicationInfo.backupAgentName shouldBe SystemRestoreAgent::class.java.name
        (context.applicationInfo.flags and ApplicationInfo.FLAG_FULL_BACKUP_ONLY != 0) shouldBe true
    }

    @Test
    fun `a finished restore leaves a marker for every database`() {
        Robolectric.buildBackupAgent(SystemRestoreAgent::class.java).create().get().onRestoreFinished()

        SystemRestoreMarker.entries.forEach { it.isSet(context) shouldBe true }
    }

    @Test
    fun `pending operations from before a restore are dropped, later ones kept`() = runBlocking<Unit> {
        AircraftDatabase(context, TestDispatcherProvider()).pendingOperations.insert(operation("before"))
        SystemRestoreMarker.PENDING_OPERATIONS.set(context)

        val restored = AircraftDatabase(context, TestDispatcherProvider())
        restored.pendingOperations.getAll() shouldBe emptyList()
        SystemRestoreMarker.PENDING_OPERATIONS.isSet(context) shouldBe false
        restored.pendingOperations.insert(operation("after"))

        AircraftDatabase(context, TestDispatcherProvider()).pendingOperations.getAll()
            .map { it.operationId } shouldBe listOf("after")
    }

    private fun operation(id: String) = PendingOperationEntity(
        operationId = id,
        kind = "WATCH",
        requestJson = "{}",
        ownerIds = "[]",
        createdAt = 0,
    )
}
