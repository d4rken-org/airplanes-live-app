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
        lastSeenAt: Instant? = null,
        lastSeenAircraftId: String? = null,
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
        lastSeenAt = lastSeenAt,
        lastSeenAircraftId = lastSeenAircraftId,
    )

    private suspend fun heldAircraftWatch(): String {
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
        return watch.id
    }

    @Test
    fun `a rollup hour that counted aircraft without a last seen time is seen at its start`() = runBlocking<Unit> {
        val watchId = heldAircraftWatch()
        db.server.upsertRollups(
            listOf(
                rollup(watchId, "08:00:00", 2, "full", source = "history"),
                rollup(watchId, "09:00:00", 0, "full", source = "history"),
            )
        )

        repo.historyLastSeen.first()[watchId] shouldBe at("08:00:00")
    }

    @Test
    fun `an exact last seen time in a later hour wins over an hour without one`() = runBlocking<Unit> {
        val watchId = heldAircraftWatch()
        db.server.upsertRollups(
            listOf(
                rollup(watchId, "08:00:00", 2, "full", source = "history"),
                rollup(watchId, "09:00:00", 1, "full", lastSeenAt = at("09:42:00")),
            )
        )

        repo.historyLastSeen.first()[watchId] shouldBe at("09:42:00")
    }

    @Test
    fun `an hour without a last seen time of another revision is not seen`() = runBlocking<Unit> {
        val watchId = heldAircraftWatch()
        db.server.upsertRollups(
            listOf(
                rollup(watchId, "08:00:00", 2, "full", source = "history"),
                rollup(watchId, "10:00:00", 3, "full", revision = 1, source = "history"),
            )
        )

        repo.historyLastSeen.first()[watchId] shouldBe at("08:00:00")
    }

    @Test
    fun `the server state's last seen time counts next to the rollups`() = runBlocking<Unit> {
        val watchId = heldAircraftWatch()
        db.server.upsertState(db.server.getState(watchId)!!.copy(lastSeenAt = at("10:15:00")))
        db.server.upsertRollups(listOf(rollup(watchId, "08:00:00", 2, "full", source = "history")))

        repo.historyLastSeen.first()[watchId] shouldBe at("10:15:00")
    }

    @Test
    fun `the newest rollup that names its aircraft is the last sighting, a newer one without does not hide it`() =
        runBlocking<Unit> {
            val watchId = heldAircraftWatch()
            db.server.upsertRollups(
                listOf(
                    rollup(watchId, "08:00:00", 1, "full", lastSeenAt = at("08:10:00"), lastSeenAircraftId = "aaaaaa"),
                    rollup(watchId, "09:00:00", 1, "full", lastSeenAt = at("09:20:00"), lastSeenAircraftId = "bbbbbb"),
                    rollup(watchId, "10:00:00", 2, "full", lastSeenAt = at("10:30:00")),
                    rollup(watchId, "11:00:00", 1, "full", source = "history"),
                )
            )

            repo.historyLastSighting.first()[watchId] shouldBe LastSighting(at("09:20:00"), "BBBBBB")
        }

    @Test
    fun `rollups seen at the same time pick the later hour`() = runBlocking<Unit> {
        val watchId = heldAircraftWatch()
        db.server.upsertRollups(
            listOf(
                rollup(watchId, "09:00:00", 1, "full", lastSeenAt = at("10:00:00"), lastSeenAircraftId = "aaaaaa"),
                rollup(watchId, "10:00:00", 1, "full", lastSeenAt = at("10:00:00"), lastSeenAircraftId = "bbbbbb"),
            )
        )

        repo.historyLastSighting.first()[watchId] shouldBe LastSighting(at("10:00:00"), "BBBBBB")
    }

    @Test
    fun `the newer of the server state's and the rollups' sighting is the last sighting`() = runBlocking<Unit> {
        val watchId = heldAircraftWatch()
        db.server.upsertRollups(
            listOf(rollup(watchId, "09:00:00", 1, "full", lastSeenAt = at("09:20:00"), lastSeenAircraftId = "bbbbbb"))
        )
        val state = db.server.getState(watchId)!!

        db.server.upsertState(state.copy(lastSeenAt = at("10:15:00"), lastSeenAircraftId = "cccccc"))
        repo.historyLastSighting.first()[watchId] shouldBe LastSighting(at("10:15:00"), "CCCCCC")

        db.server.upsertState(state.copy(lastSeenAt = at("08:15:00"), lastSeenAircraftId = "cccccc"))
        repo.historyLastSighting.first()[watchId] shouldBe LastSighting(at("09:20:00"), "BBBBBB")
    }

    @Test
    fun `a server state seen without an aircraft id is no sighting`() = runBlocking<Unit> {
        val watchId = heldAircraftWatch()
        db.server.upsertState(db.server.getState(watchId)!!.copy(lastSeenAt = at("10:15:00")))

        repo.historyLastSighting.first()[watchId] shouldBe null
    }

    @Test
    fun `sightings of another server revision are ignored`() = runBlocking<Unit> {
        val watchId = heldAircraftWatch()
        db.server.upsertRollups(
            listOf(
                rollup(watchId, "08:00:00", 1, "full", lastSeenAt = at("08:10:00"), lastSeenAircraftId = "aaaaaa"),
                rollup(
                    watchId, "10:00:00", 1, "full",
                    revision = 1, lastSeenAt = at("10:10:00"), lastSeenAircraftId = "bbbbbb",
                ),
            )
        )

        repo.historyLastSighting.first()[watchId] shouldBe LastSighting(at("08:10:00"), "AAAAAA")
    }

    @Test
    fun `nothing is sighted while the server state is of another definition`() = runBlocking<Unit> {
        val watch = db.createLocation(50.0, 8.5, 5_000f, null, null, "Home", "")
        db.server.upsertState(
            WatchServerStateEntity(
                watchId = watch.id,
                installationId = "installation-a",
                serverWatchId = "server-1",
                serverRevision = 2,
                uploadState = WatchServerStateEntity.UPLOAD_SYNCED,
                ackedDefinitionRevision = 0,
                lastSeenAt = at("09:30:00"),
                lastSeenAircraftId = "cccccc",
                updatedAt = at("12:00:00"),
            )
        )
        db.server.upsertRollups(
            listOf(rollup(watch.id, "08:00:00", 1, "full", lastSeenAt = at("08:10:00"), lastSeenAircraftId = "aaaaaa"))
        )
        repo.historyLastSighting.first()[watch.id] shouldBe LastSighting(at("09:30:00"), "CCCCCC")

        db.updateLocation(watch.id, 51.0, 9.0, 5_000f, null, null, "Home")

        repo.historyLastSighting.first()[watch.id] shouldBe null
    }

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
        val watch = db.createLocation(50.0, 8.5, 5_000f, null, null, "Home", "")
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

        db.updateLocation(watch.id, 51.0, 9.0, 5_000f, null, null, "Home")

        val timeline = repo.activity(watch.id, since = at("08:00:00"), asOf = at("09:30:00")).first()
        timeline.cells shouldBe listOf(Cell.UNKNOWN, Cell.UNKNOWN)
        timeline.hasData shouldBe false
        repo.historyLastSeen.first()[watch.id] shouldBe null
    }

    @Test
    fun `the server identity is known only while the server holds the watch's current definition`() =
        runBlocking<Unit> {
            val watch = db.createLocation(50.0, 8.5, 5_000f, null, null, "Home", "")
            repo.serverIdentity(watch.id).first() shouldBe null

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
            repo.serverIdentity(watch.id).first() shouldBe WatchServerIdentity(
                localId = watch.id,
                serverWatchId = "server-1",
                revision = 2,
                installationId = "installation-a",
            )

            db.updateLocation(watch.id, 51.0, 9.0, 5_000f, null, null, "Home")

            repo.serverIdentity(watch.id).first() shouldBe null
        }
}
