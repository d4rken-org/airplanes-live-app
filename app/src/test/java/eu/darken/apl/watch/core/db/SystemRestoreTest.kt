package eu.darken.apl.watch.core.db

import androidx.test.core.app.ApplicationProvider
import eu.darken.apl.backup.core.SystemRestoreMarker
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
    fun `after a system restore the watches stay as they were and the marker is consumed`() = runBlocking<Unit> {
        val before = WatchDatabase(context)
        val watch = before.createAircraft("3C65A3", "note")
        // As the restore agent leaves it, before this process opened the restored database
        SystemRestoreMarker.WATCH_CHECKS.set(context)

        val restored = WatchDatabase(context)

        restored.snapshot().single().apply {
            id shouldBe watch.id
            note shouldBe "note"
        }
        SystemRestoreMarker.WATCH_CHECKS.isSet(context) shouldBe false
    }
}
