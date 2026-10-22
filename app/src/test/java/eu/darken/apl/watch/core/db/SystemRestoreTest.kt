package eu.darken.apl.watch.core.db

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.test.core.app.ApplicationProvider
import eu.darken.apl.backup.core.SystemRestoreMarker
import eu.darken.apl.watch.core.types.WatchCheckOutcome
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class SystemRestoreTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setup() {
        context.deleteDatabase("watch")
        SystemRestoreMarker.WATCH_CHECKS.clear(context)
    }

    @After
    fun teardown() {
        context.deleteDatabase("watch")
        SystemRestoreMarker.WATCH_CHECKS.clear(context)
    }

    @Test
    fun `after a system restore the old checks are history and the next hit counts as the first`() = runBlocking<Unit> {
        val before = WatchDatabase(context)
        val watch = before.createAircraft("3C65A3", "")
        before.applyCheck(watch.id, watch.definitionRevision, AT, WatchCheckOutcome.ABSENT, 0)
        // As the restore agent leaves it, before this process opened the restored database
        SystemRestoreMarker.WATCH_CHECKS.set(context)

        val restored = WatchDatabase(context)
        restored.snapshot().first.single().apply {
            lastCheckAt shouldBe null
            lastCheckOutcome shouldBe null
        }
        restored.checks.getLastCheck(watch.id)!!.apply {
            aircraftcount shouldBe 0
            imported shouldBe true
        }
        SystemRestoreMarker.WATCH_CHECKS.isSet(context) shouldBe false

        restored.applyCheck(watch.id, watch.definitionRevision, AT.plusSeconds(60), WatchCheckOutcome.MATCHED, 1) shouldBe
                WatchDatabase.CheckApplication.Applied(previousCount = null)
    }

    @Test
    fun `checks made after the restore stay this device's own on later starts`() = runBlocking<Unit> {
        val before = WatchDatabase(context)
        val watch = before.createAircraft("3C65A3", "")
        before.applyCheck(watch.id, watch.definitionRevision, AT, WatchCheckOutcome.ABSENT, 0)
        SystemRestoreMarker.WATCH_CHECKS.set(context)
        val restored = WatchDatabase(context)
        restored.applyCheck(watch.id, watch.definitionRevision, AT.plusSeconds(60), WatchCheckOutcome.ABSENT, 0)

        val later = WatchDatabase(context)

        later.checks.getLastLiveCheck(watch.id)!!.checkedAt shouldBe AT.plusSeconds(60)
        later.snapshot().first.single().lastCheckOutcome shouldBe WatchCheckOutcome.ABSENT
    }

    @Test
    fun `a reset that fails leaves everything as it was and runs again on the next open`() = runBlocking<Unit> {
        val before = WatchDatabase(context)
        val watch = before.createAircraft("3C65A3", "")
        before.applyCheck(watch.id, watch.definitionRevision, AT, WatchCheckOutcome.ABSENT, 0)
        before.updateLastCheck(watch.id, watch.definitionRevision, AT, WatchCheckOutcome.INCONCLUSIVE, "partial")
        SystemRestoreMarker.WATCH_CHECKS.set(context)
        rawWatchDb { it.execSQL("CREATE TRIGGER fail_reset BEFORE UPDATE ON watch_base BEGIN SELECT RAISE(ABORT, 'disk full'); END") }

        shouldThrow<SQLiteException> { WatchDatabase(context).snapshot() }

        SystemRestoreMarker.WATCH_CHECKS.isSet(context) shouldBe true
        rawWatchDb { raw ->
            raw.rawQuery("SELECT imported FROM watch_checks", null).use { it.moveToFirst(); it.getInt(0) shouldBe 0 }
            raw.execSQL("DROP TRIGGER fail_reset")
        }

        WatchDatabase(context).snapshot().first.single().apply {
            lastCheckOutcome shouldBe null
            lastCheckReason shouldBe null
        }
        SystemRestoreMarker.WATCH_CHECKS.isSet(context) shouldBe false
    }

    private fun rawWatchDb(block: (SQLiteDatabase) -> Unit) = SQLiteDatabase.openDatabase(
        context.getDatabasePath("watch").path,
        null,
        SQLiteDatabase.OPEN_READWRITE,
    ).use(block)

    companion object {
        private val AT = Instant.parse("2026-10-13T10:00:00Z")
    }
}
