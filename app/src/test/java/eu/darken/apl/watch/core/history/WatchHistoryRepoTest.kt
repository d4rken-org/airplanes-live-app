package eu.darken.apl.watch.core.history

import androidx.test.core.app.ApplicationProvider
import eu.darken.apl.watch.core.db.WatchDatabase
import eu.darken.apl.watch.core.db.server.WatchServerRollupEntity
import eu.darken.apl.watch.core.db.server.WatchServerStateEntity
import eu.darken.apl.watch.core.history.WatchActivityTimeline.Cell
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
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
class WatchHistoryRepoTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var db: WatchDatabase
    private lateinit var repo: WatchHistoryRepo

    @Before
    fun setup() {
        context.deleteDatabase("watch")
        db = WatchDatabase(context)
        repo = WatchHistoryRepo(db)
    }

    @After
    fun teardown() {
        context.deleteDatabase("watch")
    }

    private fun at(time: String) = Instant.parse("2026-10-13T${time}Z")

    private fun rollup(
        watchId: String,
        time: String,
        count: Int,
        coverage: String,
        revision: Long = 2,
        source: String = "live",
    ) = WatchServerRollupEntity(
        serverWatchId = "server-1",
        revision = revision,
        hour = at(time),
        source = source,
        watchId = watchId,
        installationId = "installation-a",
        seq = 1,
        evaluationBasis = "recent_messages",
        aircraftCount = count,
        coverage = coverage,
        isFinal = true,
    )

    @Test
    fun `a server managed watch shows the rollup hours of its current revision`() = runBlocking<Unit> {
        val watch = db.createAircraft("3C65A3", "")
        db.server.upsertState(
            WatchServerStateEntity(
                watchId = watch.id,
                installationId = "installation-a",
                serverWatchId = "server-1",
                serverRevision = 2,
                uploadState = WatchServerStateEntity.UPLOAD_SYNCED,
                ackedDefinitionRevision = 0,
                updatedAt = at("12:00:00"),
            )
        )
        db.server.upsertRollups(
            listOf(
                rollup(watch.id, "08:00:00", 2, "partial"),
                rollup(watch.id, "09:00:00", 0, "full"),
                rollup(watch.id, "10:00:00", 4, "full", revision = 1),
                rollup(watch.id, "11:00:00", 0, "partial"),
                // The hour of the edit
                rollup(watch.id, "12:00:00", 3, "partial", revision = 1),
                rollup(watch.id, "12:00:00", 0, "partial"),
                rollup(watch.id, "13:00:00", 0, "full", source = "history"),
                rollup(watch.id, "13:00:00", 1, "partial"),
            )
        )

        val timeline = repo.activity(watch.id, since = at("08:00:00"), asOf = at("13:30:00")).first()

        timeline.cells shouldBe listOf(
            Cell.ACTIVE,
            Cell.INACTIVE,
            Cell.UNKNOWN,
            Cell.UNKNOWN,
            Cell.UNKNOWN,
            Cell.ACTIVE,
        )
        timeline.counts shouldBe listOf(2, 0, 0, 0, 0, 1)
    }

    @Test
    fun `rollups of a watch the server state does not map show nothing`() = runBlocking<Unit> {
        val watch = db.createAircraft("3C65A3", "")
        db.server.upsertRollups(listOf(rollup(watch.id, "08:00:00", 2, "full")))

        val timeline = repo.activity(watch.id, since = at("08:00:00"), asOf = at("09:30:00")).first()

        timeline.cells shouldBe listOf(Cell.UNKNOWN, Cell.UNKNOWN)
        timeline.hasData shouldBe false
    }

    @Test
    fun `rollups of the current server revision are left out while the watch's definition is not uploaded`() = runBlocking<Unit> {
        val watch = db.createLocation(50.0, 8.5, 5_000f, "Home", "")
        db.server.upsertState(
            WatchServerStateEntity(
                watchId = watch.id,
                installationId = "installation-a",
                serverWatchId = "server-1",
                serverRevision = 2,
                uploadState = WatchServerStateEntity.UPLOAD_SYNCED,
                ackedDefinitionRevision = 0,
                lastSeenAt = at("08:30:00"),
                updatedAt = at("12:00:00"),
            )
        )
        db.server.upsertRollups(listOf(rollup(watch.id, "08:00:00", 2, "full")))
        repo.activity(watch.id, since = at("08:00:00"), asOf = at("09:30:00")).first().hasData shouldBe true

        db.updateLocation(watch.id, 51.0, 9.0, 5_000f, "Home")

        val timeline = repo.activity(watch.id, since = at("08:00:00"), asOf = at("09:30:00")).first()
        timeline.cells shouldBe listOf(Cell.UNKNOWN, Cell.UNKNOWN)
        timeline.hasData shouldBe false
        repo.historyLastSeen.first()[watch.id] shouldBe null
    }
}
