package eu.darken.apl.watch.core.server

import androidx.test.core.app.ApplicationProvider
import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.datastore.DataStoreValue
import eu.darken.apl.common.http.HttpModule
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.ServerModule
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.server.api.Allowance
import eu.darken.apl.server.api.RequestLimits
import eu.darken.apl.server.api.RequestRate
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.ServerEndpoint
import eu.darken.apl.server.api.TierPolicy
import eu.darken.apl.server.api.Usage
import eu.darken.apl.server.api.WatchEvent
import eu.darken.apl.server.api.WatchEventsResponse
import eu.darken.apl.server.api.WatchListLimits
import eu.darken.apl.server.api.WatchListResponse
import eu.darken.apl.server.api.WatchListWatch
import eu.darken.apl.server.api.WatchMutationResponse
import eu.darken.apl.server.api.WatchRollup
import eu.darken.apl.server.api.WatchRollupsResponse
import eu.darken.apl.server.api.WatchUpsertRequest
import eu.darken.apl.server.session.SessionManager
import eu.darken.apl.upgrade.UpgradeRepo
import eu.darken.apl.watch.core.WatchSettings
import eu.darken.apl.watch.core.db.WatchDatabase
import eu.darken.apl.watch.core.db.server.WatchNotificationOutboxEntity
import eu.darken.apl.watch.core.db.server.WatchServerRollupEntity
import eu.darken.apl.watch.core.db.server.WatchServerStateEntity
import eu.darken.apl.watch.core.db.server.WatchServerSyncEntity
import eu.darken.apl.watch.core.db.server.WatchServerTombstoneEntity
import eu.darken.apl.watch.core.types.Watch
import eu.darken.apl.watch.core.types.WatchCheckOutcome
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelper.coroutine.TestDispatcherProvider
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class WatchListSyncTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val json = ServerModule.serverJson()
    private val serverClock = ServerClock(object : MonotonicClock {
        override fun elapsed(): Long = 0L
    }).apply { noteServerTime(NOW.toEpochMilli()) }

    private lateinit var db: WatchDatabase
    private lateinit var mockWebServer: MockWebServer
    private lateinit var fake: FakeWatchListServer
    private lateinit var sync: WatchListSync

    private val sessionManager = mockk<SessionManager>()
    private val accessRepo = mockk<AccessRepo>(relaxed = true)
    private val accessState = MutableStateFlow<AccessState?>(null)

    private val alerts = mockk<WatchServerAlerts>(relaxed = true)
    private val monitorInterval = mockk<DataStoreValue<Duration>>()
    private val watchSettings = mockk<WatchSettings>()
    private val upgradeInfo = mockk<UpgradeRepo.Info>()
    private val upgradeRepo = mockk<UpgradeRepo>()

    private var authCalls = 0

    /** The installation the session yields on the n-th authed call of a run, counting from 1. */
    private var identity: (call: Int) -> String = { INSTALLATION_A }

    @Before
    fun setup() {
        context.deleteDatabase("watch")
        db = WatchDatabase(context)

        fake = FakeWatchListServer(json)
        mockWebServer = MockWebServer().apply {
            dispatcher = fake
            start()
        }
        val endpoint = ServerEndpoint(
            baseClient = HttpModule().baseHttpClient(),
            json = json,
            dispatcherProvider = TestDispatcherProvider(),
            serverClock = serverClock,
        ).apply {
            baseUrl = mockWebServer.url("/").toString()
        }

        every { accessRepo.state } returns accessState
        accessState.value = access()
        coEvery { sessionManager.authedAs<Any?>(any()) } coAnswers {
            authCalls++
            firstArg<suspend (String, String) -> Any?>().invoke(identity(authCalls), "token")
        }
        every { monitorInterval.flow } returns flowOf(Duration.ofMinutes(60))
        every { watchSettings.watchMonitorInterval } returns monitorInterval
        every { upgradeInfo.isPro } returns false
        every { upgradeInfo.isSettled } returns true
        every { upgradeRepo.upgradeInfo } returns flowOf(upgradeInfo)

        sync = WatchListSync(
            sessionManager = sessionManager,
            endpoint = endpoint,
            accessRepo = accessRepo,
            watchDb = db,
            serverClock = serverClock,
            alerts = alerts,
            watchSettings = watchSettings,
            upgradeRepo = upgradeRepo,
        )
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
        context.deleteDatabase("watch")
    }

    private fun access(
        installationId: String = INSTALLATION_A,
        watchList: Boolean = true,
        maxActive: Int? = 10,
        maxStored: Int? = 50,
    ) = AccessState(
        installationId = installationId,
        fetchedAt = NOW,
        tier = AccessState.Tier.FEEDER,
        restricted = false,
        allowanceScope = "principal",
        limits = TierPolicy(
            viewingIntervalSeconds = 5,
            viewingBurst = 3,
            viewingPerDay = 25_000,
            searchesPerDay = 25,
            watchesPerDay = 1000,
            maxRadiusNm = 25,
            maxLocationWatchRadiusKm = 250,
            watchTypes = listOf("hex", "callsign", "squawk", "location"),
            maxActiveWatches = maxActive,
            maxStoredWatches = maxStored,
        ),
        usage = Usage(
            resetsAt = NOW.plus(Duration.ofHours(12)).toEpochMilli(),
            viewing = Allowance(25_000, 0, 0, 25_000),
            search = Allowance(25, 0, 0, 25),
            watch = Allowance(1000, 0, 0, 1000),
        ),
        installationRequests = RequestLimits(RequestRate(1.0, 3), RequestRate(1.0, 3), RequestRate(1.0, 3), 3),
        aircraftOperations = if (watchList) listOf(AccessState.OPERATION_WATCHLIST) else emptyList(),
    )

    private fun upserts(): List<Pair<String, WatchUpsertRequest>> = fake.calls
        .filter { it.method == "PUT" && it.path.startsWith(WATCHES_PATH) }
        .map { it.path.removePrefix(WATCHES_PATH) to json.decodeFromString<WatchUpsertRequest>(it.body) }

    /** Every request except the event and rollup polls, see [eventPolls] and [rollupPolls]. */
    private fun requests(from: Int = 0) = fake.calls.drop(from)
        .filterNot { it.path.startsWith(EVENTS_PATH) || it.path.startsWith(ROLLUPS_PATH) }
        .map { it.method to it.path }

    /** The `after` of each event poll. */
    private fun eventPolls(from: Int = 0) = fake.calls.drop(from)
        .filter { it.path.startsWith(EVENTS_PATH) }
        .map { "http://fake${it.path}".toHttpUrl().queryParameter("after")!!.toLong() }

    /** The `after` of each rollup poll. */
    private fun rollupPolls(from: Int = 0) = fake.calls.drop(from)
        .filter { it.path.startsWith(ROLLUPS_PATH) }
        .map { "http://fake${it.path}".toHttpUrl().queryParameter("after")!!.toLong() }

    private suspend fun syncState() = db.server.getSync(INSTALLATION_A)!!

    private suspend fun storedRollups() = db.server.allRollupsSince(Instant.EPOCH).first()

    /** Waits out the millisecond the watch was created in, watches created within one would tie in creation order. */
    private suspend fun <W : Watch> created(create: suspend () -> W): W {
        val watch = create()
        while (Instant.now().toEpochMilli() <= watch.addedAt.toEpochMilli()) Thread.sleep(1)
        return watch
    }

    private fun hexes(count: Int) = Array(count) { "3C65%02X".format(it) }

    /** Creates aircraft watches with alerts on and brings them onto the server, polled up to the newest event. */
    private suspend fun synced(vararg hexes: String, notify: Boolean = true): List<Watch> {
        val watches = hexes.map { hex ->
            val watch = created { db.createAircraft(hex, "") }
            if (notify) db.updateNotification(watch.id, true)
            watch.id
        }
        sync.run("setup") shouldBe WatchListSync.Result.Done(moreWork = false)
        val local = localWatches()
        return watches.map { local.getValue(it) }
    }

    private fun remoteOf(watch: Watch) = fake.watches.single { it.localId == watch.id }

    private suspend fun localWatches() = db.serverTransaction { watches() }.associateBy { it.id }

    private fun rollup(watchId: String, serverWatchId: String) = WatchServerRollupEntity(
        serverWatchId = serverWatchId,
        revision = 1,
        hour = NOW.minus(Duration.ofHours(2)),
        source = "live",
        watchId = watchId,
        installationId = INSTALLATION_A,
        seq = 1,
        evaluationBasis = "recent_messages",
        aircraftCount = 1,
        coverage = "full",
        isFinal = true,
    )

    @Test
    fun `the first sync uploads every watch in creation order with its baseline and sends no selection`() = runBlocking<Unit> {
        val matched = created { db.createAircraft("3C65A3", "") }
        db.applyCheck(matched.id, matched.definitionRevision, NOW, WatchCheckOutcome.MATCHED, 1)
        val absent = created { db.createFlight("DLH123", "") }
        db.applyCheck(absent.id, absent.definitionRevision, NOW, WatchCheckOutcome.ABSENT, 0)
        val unknown = created { db.createSquawk("7700", "") }
        fake.cursor = 42

        sync.run("test") shouldBe WatchListSync.Result.Done(moreWork = false)

        // A create answers only for itself, the events page has the list read again
        requests() shouldBe listOf(
            "GET" to LIST_PATH,
            "PUT" to WATCHES_PATH + matched.id,
            "PUT" to WATCHES_PATH + absent.id,
            "PUT" to WATCHES_PATH + unknown.id,
            "GET" to LIST_PATH,
        )
        upserts().map { it.second.baseline } shouldBe listOf("present", "absent", null)
        upserts().forEach { (_, body) -> body.expectedWatchRevision shouldBe null }
        fake.calls.filter { it.method == "PUT" }.forEach { call ->
            call.body.contains("enabled") shouldBe false
            call.body.contains("priority") shouldBe false
        }
        upserts()[0].second.apply {
            type shouldBe "hex"
            value shouldBe "3C65A3"
        }

        db.server.getAllStates().apply {
            size shouldBe 3
            forEach {
                it.installationId shouldBe INSTALLATION_A
                it.uploadState shouldBe WatchServerStateEntity.UPLOAD_SYNCED
                it.ackedDefinitionRevision shouldBe 0L
                it.serverWatchId shouldBe fake.watches.single { remote -> remote.localId == it.watchId }.watchId
            }
        }
        db.server.getSync(INSTALLATION_A)!!.apply {
            phase shouldBe WatchServerSyncEntity.PHASE_LIVE
            eventCursor shouldBe 42L
            listRevision shouldBe fake.revision
        }
    }

    @Test
    fun `watches past the active limit are uploaded in creation order and taken as paused from the server`() =
        runBlocking<Unit> {
            val watches = hexes(12).map { hex -> created { db.createAircraft(hex, "") } }

            sync.run("test") shouldBe WatchListSync.Result.Done(moreWork = false)

            requests() shouldBe listOf("GET" to LIST_PATH) +
                    watches.map { "PUT" to WATCHES_PATH + it.id } +
                    listOf("GET" to LIST_PATH)
            syncState().phase shouldBe WatchServerSyncEntity.PHASE_LIVE
            val states = db.server.getAllStates().associateBy { it.watchId }
            // (status, pauseReason) per watch, in creation order
            watches.map { states.getValue(it.id).run { status to pauseReason } } shouldBe
                    List(10) { "active" to null } + List(2) { "paused" to "over_limit" }
        }

    @Test
    fun `a lower active limit is taken from the server without changing the local watches`() = runBlocking<Unit> {
        accessState.value = access(maxActive = 50)
        fake.maxActive = 50
        val watches = synced(*hexes(12))
        db.server.getAllStates().map { it.status }.distinct() shouldBe listOf("active")
        val local = localWatches()

        accessState.value = access(maxActive = 10)
        fake.maxActive = 10
        val before = fake.calls.size

        sync.run("downgrade") shouldBe WatchListSync.Result.Done(moreWork = false)

        // The server's pauses raised the list revision the events page names
        requests(before) shouldBe listOf("GET" to LIST_PATH)
        localWatches() shouldBe local
        val states = db.server.getAllStates().associateBy { it.watchId }
        watches.map { states.getValue(it.id).run { status to pauseReason } } shouldBe
                List(10) { "active" to null } + List(2) { "paused" to "over_limit" }
    }

    @Test
    fun `deleting an active watch takes the one the server promoted`() = runBlocking<Unit> {
        val watches = synced(*hexes(11))
        db.server.getState(watches[10].id)!!.pauseReason shouldBe "over_limit"

        db.deleteWatch(watches[0].id)
        sync.run("delete") shouldBe WatchListSync.Result.Done(moreWork = false)

        fake.watches.none { it.localId == watches[0].id } shouldBe true
        db.server.getState(watches[10].id)!!.apply {
            status shouldBe "active"
            pauseReason shouldBe null
        }
    }

    @Test
    fun `a create after a delete in the same run still takes the watch the delete promoted`() = runBlocking<Unit> {
        val watches = synced(*hexes(11))
        db.deleteWatch(watches[0].id)
        val added = db.createAircraft("3C65FF", "")
        val before = fake.calls.size

        sync.run("delete and create") shouldBe WatchListSync.Result.Done(moreWork = false)

        requests(before) shouldBe listOf(
            "DELETE" to WATCHES_PATH + watches[0].id,
            "PUT" to WATCHES_PATH + added.id,
            "GET" to LIST_PATH,
        )
        db.server.getState(watches[10].id)!!.apply {
            status shouldBe "active"
            pauseReason shouldBe null
        }
        db.server.getState(added.id)!!.apply {
            status shouldBe "paused"
            pauseReason shouldBe "over_limit"
        }
    }

    @Test
    fun `an edit that frees an active slot takes the watch the server promoted`() = runBlocking<Unit> {
        val area = created { db.createLocation(50.0, 8.5, 5_000f, "Home", "") }
        val watches = synced(*hexes(10))
        db.server.getState(watches[9].id)!!.pauseReason shouldBe "over_limit"

        // Wider than the tier's areas take a slot for
        db.updateLocation(area.id, 50.0, 8.5, 300_000f, "Home")
        sync.run("edit") shouldBe WatchListSync.Result.Done(moreWork = false)

        upserts().last().apply {
            first shouldBe area.id
            second.expectedWatchRevision shouldBe 1
        }
        db.server.getState(area.id)!!.apply {
            status shouldBe "paused"
            pauseReason shouldBe "tier"
        }
        db.server.getState(watches[9].id)!!.apply {
            status shouldBe "active"
            pauseReason shouldBe null
        }
    }

    @Test
    fun `an edit the server refuses as invalid has its server copy deleted until the watch changes again`() =
        runBlocking<Unit> {
            val area = created { db.createLocation(50.0, 8.5, 5_000f, "Home", "") }
            val watches = synced(*hexes(10))
            val held = remoteOf(area)
            fake.intercept = { call ->
                val refused = call.method == "PUT" && call.path == WATCHES_PATH + area.id &&
                        (json.decodeFromString<WatchUpsertRequest>(call.body).latitude ?: 0.0) > 90.0
                if (refused) problem(400, "invalid_request") else null
            }

            db.updateLocation(area.id, 95.0, 8.5, 5_000f, "Home")
            sync.run("refused") shouldBe WatchListSync.Result.Done(moreWork = true)

            db.server.getTombstones(INSTALLATION_A).map { it.watchId } shouldBe listOf(area.id)
            db.server.getState(area.id)!!.apply {
                uploadState shouldBe WatchServerStateEntity.UPLOAD_REJECTED_INVALID
                serverWatchId shouldBe null
                serverRevision shouldBe null
            }

            val before = fake.calls.size
            sync.run("next") shouldBe WatchListSync.Result.Done(moreWork = false)

            // Unchanged, the refused definition is not sent again
            requests(before) shouldBe listOf(
                "DELETE" to WATCHES_PATH + area.id,
                "GET" to LIST_PATH,
            )
            fake.watches.none { it.localId == area.id } shouldBe true
            db.server.getTombstones(INSTALLATION_A).shouldBeEmpty()
            db.server.getState(area.id)!!.apply {
                uploadState shouldBe WatchServerStateEntity.UPLOAD_REJECTED_INVALID
                serverWatchId shouldBe null
                serverRevision shouldBe null
            }
            db.server.getState(watches[9].id)!!.apply {
                status shouldBe "active"
                pauseReason shouldBe null
            }

            db.updateLocation(area.id, 51.0, 8.5, 5_000f, "Home")
            sync.run("corrected") shouldBe WatchListSync.Result.Done(moreWork = false)

            upserts().last().apply {
                first shouldBe area.id
                second.latitude shouldBe 51.0
                second.expectedWatchRevision shouldBe null
            }
            val recreated = remoteOf(area)
            recreated.watchId shouldNotBe held.watchId
            db.server.getState(area.id)!!.apply {
                uploadState shouldBe WatchServerStateEntity.UPLOAD_SYNCED
                serverWatchId shouldBe recreated.watchId
                // Created again, it comes after the others
                status shouldBe "paused"
                pauseReason shouldBe "over_limit"
            }
        }

    @Test
    fun `a periodic run that reads the list keeps a refused edit unmapped until its copy is deleted`() =
        runBlocking<Unit> {
            val area = created { db.createLocation(50.0, 8.5, 5_000f, "Home", "") }
            synced(*hexes(10))
            fake.intercept = { call ->
                val refused = call.method == "PUT" && call.path == WATCHES_PATH + area.id &&
                        (json.decodeFromString<WatchUpsertRequest>(call.body).latitude ?: 0.0) > 90.0
                if (refused) problem(400, "invalid_request") else null
            }

            db.updateLocation(area.id, 95.0, 8.5, 5_000f, "Home")
            sync.run("refused", periodic = true)

            val refusedAt = fake.calls.indexOfLast { it.method == "PUT" && it.path == WATCHES_PATH + area.id }
            requests(refusedAt + 1) shouldContain ("GET" to LIST_PATH)
            db.server.getTombstones(INSTALLATION_A).map { it.watchId } shouldBe listOf(area.id)
            db.server.getState(area.id)!!.apply {
                uploadState shouldBe WatchServerStateEntity.UPLOAD_REJECTED_INVALID
                serverWatchId shouldBe null
                serverRevision shouldBe null
            }

            sync.run("next")

            db.server.getTombstones(INSTALLATION_A).shouldBeEmpty()
            fake.watches.none { it.localId == area.id } shouldBe true
            db.server.getState(area.id)!!.apply {
                uploadState shouldBe WatchServerStateEntity.UPLOAD_REJECTED_INVALID
                serverWatchId shouldBe null
                serverRevision shouldBe null
            }
        }

    @Test
    fun `a list reread after another edit in the same run keeps a refused edit unmapped`() = runBlocking<Unit> {
        val area = created { db.createLocation(50.0, 8.5, 5_000f, "Home", "") }
        // Only an area has a definition that can be edited, aircraft watches change only their note
        val other = created { db.createLocation(48.0, 11.5, 5_000f, "Work", "") }
        synced(*hexes(10))
        fake.intercept = { call ->
            val refused = call.method == "PUT" && call.path == WATCHES_PATH + area.id &&
                    (json.decodeFromString<WatchUpsertRequest>(call.body).latitude ?: 0.0) > 90.0
            if (refused) problem(400, "invalid_request") else null
        }

        db.updateLocation(area.id, 95.0, 8.5, 5_000f, "Home")
        db.updateLocation(other.id, 49.0, 11.5, 5_000f, "Work")
        sync.run("refused")

        // The other edit went through
        remoteOf(other).latitude shouldBe 49.0
        val lastPut = fake.calls.indexOfLast { it.method == "PUT" }
        requests(lastPut + 1) shouldContain ("GET" to LIST_PATH)
        db.server.getTombstones(INSTALLATION_A).map { it.watchId } shouldBe listOf(area.id)
        db.server.getState(area.id)!!.apply {
            uploadState shouldBe WatchServerStateEntity.UPLOAD_REJECTED_INVALID
            serverWatchId shouldBe null
            serverRevision shouldBe null
        }
    }

    @Test
    fun `a watch the server refuses as invalid is left out and the others go on`() = runBlocking<Unit> {
        val first = created { db.createAircraft("3C65A3", "") }
        val second = created { db.createAircraft("3C65A4", "") }
        val third = created { db.createAircraft("3C65A5", "") }
        fake.intercept = { call ->
            if (call.method == "PUT" && call.path == WATCHES_PATH + second.id) problem(400, "invalid_request") else null
        }

        sync.run("test") shouldBe WatchListSync.Result.Done(moreWork = false)

        upserts().map { it.first } shouldBe listOf(first.id, second.id, third.id)
        db.server.getState(second.id)!!.uploadState shouldBe WatchServerStateEntity.UPLOAD_REJECTED_INVALID
        db.server.getState(first.id)!!.uploadState shouldBe WatchServerStateEntity.UPLOAD_SYNCED
        db.server.getState(third.id)!!.uploadState shouldBe WatchServerStateEntity.UPLOAD_SYNCED
        fake.watches.map { it.localId } shouldBe listOf(first.id, third.id)

        // Unchanged, it is not sent again
        val before = fake.calls.size
        sync.run("again") shouldBe WatchListSync.Result.Done(moreWork = false)
        requests(before).shouldBeEmpty()
    }

    @Test
    fun `a session that enrolled as a new installation starts over from scratch`() = runBlocking<Unit> {
        val watch = db.createAircraft("3C65A3", "")
        db.server.upsertSync(
            WatchServerSyncEntity(
                installationId = INSTALLATION_A,
                listRevision = 7,
                eventCursor = 9,
                phase = WatchServerSyncEntity.PHASE_LIVE,
                updatedAt = NOW,
            )
        )
        db.server.upsertState(
            WatchServerStateEntity(
                watchId = watch.id,
                installationId = INSTALLATION_A,
                serverWatchId = "server-of-a",
                serverRevision = 1,
                uploadState = WatchServerStateEntity.UPLOAD_SYNCED,
                ackedDefinitionRevision = 0,
                updatedAt = NOW,
            )
        )
        db.server.insertTombstones(listOf(WatchServerTombstoneEntity("deleted-under-a", INSTALLATION_A, NOW)))
        identity = { INSTALLATION_B }
        accessState.value = access(installationId = INSTALLATION_B)

        sync.run("test") shouldBe WatchListSync.Result.Done(moreWork = false)

        // Nothing of A is sent as B, the tombstone of A is not B's to delete
        requests() shouldBe listOf(
            "GET" to LIST_PATH,
            "PUT" to WATCHES_PATH + watch.id,
            "GET" to LIST_PATH,
        )
        upserts().single().second.expectedWatchRevision shouldBe null
        db.server.getSync(INSTALLATION_A) shouldBe null
        db.server.getTombstones(INSTALLATION_A).shouldBeEmpty()
        db.server.getSync(INSTALLATION_B)!!.phase shouldBe WatchServerSyncEntity.PHASE_LIVE
        db.server.getState(watch.id)!!.apply {
            installationId shouldBe INSTALLATION_B
            serverWatchId shouldBe fake.watches.single().watchId
            uploadState shouldBe WatchServerStateEntity.UPLOAD_SYNCED
        }
    }

    @Test
    fun `a session that changes installation mid run aborts it and drops the server state`() = runBlocking<Unit> {
        db.createAircraft("3C65A3", "")
        // The scope and the list are fetched as A, the first upload would go out as B
        identity = { call -> if (call <= 2) INSTALLATION_A else INSTALLATION_B }

        sync.run("test") shouldBe WatchListSync.Result.Aborted

        requests() shouldBe listOf("GET" to LIST_PATH)
        db.server.getSync(INSTALLATION_A) shouldBe null
        db.server.getAllStates().shouldBeEmpty()
    }

    @Test
    fun `an area moved while its upload is in flight is acknowledged as sent and edited on the next run`() = runBlocking<Unit> {
        val watch = db.createLocation(50.0, 8.5, 5_000f, "Home", "")
        var moved = false
        fake.intercept = { call ->
            if (call.method == "PUT" && call.path == WATCHES_PATH + watch.id && !moved) {
                moved = true
                runBlocking { db.updateLocation(watch.id, 51.0, 9.0, 5_000f, "Home") }
            }
            null
        }

        sync.run("first") shouldBe WatchListSync.Result.Done(moreWork = true)

        upserts().single().second.latitude shouldBe 50.0
        fake.watches.single().apply {
            revision shouldBe 1
            latitude shouldBe 50.0
        }
        localWatches().getValue(watch.id).definitionRevision shouldBe 1L
        db.server.getState(watch.id)!!.apply {
            serverWatchId shouldBe fake.watches.single().watchId
            serverRevision shouldBe 1L
            ackedDefinitionRevision shouldNotBe 1L
        }

        sync.run("second") shouldBe WatchListSync.Result.Done(moreWork = false)

        upserts().last().second.apply {
            expectedWatchRevision shouldBe 1
            latitude shouldBe 51.0
            longitude shouldBe 9.0
            radiusKm shouldBe 5.0
            baseline shouldBe null
        }
        db.server.getState(watch.id)!!.apply {
            serverRevision shouldBe 2L
            ackedDefinitionRevision shouldBe 1L
        }
        fake.watches.single().latitude shouldBe 51.0
    }

    @Test
    fun `a watch deleted while its create is in flight is deleted on the server by the next run`() = runBlocking<Unit> {
        val watch = db.createAircraft("3C65A3", "")
        var deleted = false
        fake.intercept = { call ->
            if (call.method == "PUT" && call.path == WATCHES_PATH + watch.id && !deleted) {
                deleted = true
                runBlocking { db.deleteWatch(watch.id) }
            }
            null
        }

        sync.run("first") shouldBe WatchListSync.Result.Done(moreWork = true)

        db.server.getTombstones(INSTALLATION_A).map { it.watchId } shouldBe listOf(watch.id)
        db.server.getState(watch.id) shouldBe null
        fake.watches.single().localId shouldBe watch.id

        val before = fake.calls.size
        sync.run("second") shouldBe WatchListSync.Result.Done(moreWork = false)

        // The delete answers no list revision, the event poll's newer one has the list read again
        requests(before) shouldBe listOf(
            "DELETE" to WATCHES_PATH + watch.id,
            "GET" to LIST_PATH,
        )
        fake.watches.shouldBeEmpty()
        db.server.getTombstones(INSTALLATION_A).shouldBeEmpty()
    }

    @Test
    fun `an expired list registers the watches again with their last known presence`() = runBlocking<Unit> {
        val watch = db.createAircraft("3C65A3", "")
        db.server.upsertSync(
            WatchServerSyncEntity(
                installationId = INSTALLATION_A,
                listRevision = 5,
                eventCursor = 7,
                phase = WatchServerSyncEntity.PHASE_HYDRATE,
                updatedAt = NOW,
            )
        )
        db.server.upsertState(
            WatchServerStateEntity(
                watchId = watch.id,
                installationId = INSTALLATION_A,
                serverWatchId = "expired-1",
                serverRevision = 1,
                uploadState = WatchServerStateEntity.UPLOAD_SYNCED,
                ackedDefinitionRevision = 0,
                presence = "present",
                eventSeq = 3,
                updatedAt = NOW,
            )
        )
        db.server.upsertRollups(listOf(rollup(watch.id, "expired-1")))
        fake.state = "expired"
        fake.cursor = 2

        sync.run("test") shouldBe WatchListSync.Result.Done(moreWork = false)

        upserts().single().second.apply {
            baseline shouldBe "present"
            expectedWatchRevision shouldBe null
        }
        db.server.getState(watch.id)!!.apply {
            serverWatchId shouldBe fake.watches.single().watchId
            uploadState shouldBe WatchServerStateEntity.UPLOAD_SYNCED
            presence shouldBe "present"
            eventSeq shouldBe 0L
        }
        db.server.getSync(INSTALLATION_A)!!.eventCursor shouldBe 2L
        db.server.rollupsSince(watch.id, Instant.EPOCH).first().shouldBeEmpty()
    }

    @Test
    fun `a mapped watch missing from the list is registered again and loses its rollups`() = runBlocking<Unit> {
        val kept = db.createAircraft("3C65A3", "")
        val missing = db.createAircraft("3C65A4", "")
        val keptRemote = fake.seed(kept.id, "3C65A3", presence = "present")
        db.server.upsertSync(
            WatchServerSyncEntity(
                installationId = INSTALLATION_A,
                listRevision = 5,
                eventCursor = 7,
                phase = WatchServerSyncEntity.PHASE_HYDRATE,
                updatedAt = NOW,
            )
        )
        db.server.upsertState(
            WatchServerStateEntity(
                watchId = kept.id,
                installationId = INSTALLATION_A,
                serverWatchId = keptRemote.watchId,
                serverRevision = 1,
                uploadState = WatchServerStateEntity.UPLOAD_SYNCED,
                ackedDefinitionRevision = 0,
                presence = "present",
                updatedAt = NOW,
            )
        )
        db.server.upsertState(
            WatchServerStateEntity(
                watchId = missing.id,
                installationId = INSTALLATION_A,
                serverWatchId = "gone-1",
                serverRevision = 2,
                uploadState = WatchServerStateEntity.UPLOAD_SYNCED,
                ackedDefinitionRevision = 0,
                presence = "absent",
                updatedAt = NOW,
            )
        )
        db.server.upsertRollups(listOf(rollup(kept.id, keptRemote.watchId), rollup(missing.id, "gone-1")))

        sync.run("test") shouldBe WatchListSync.Result.Done(moreWork = false)

        upserts().map { it.first } shouldBe listOf(missing.id)
        upserts().single().second.baseline shouldBe "absent"
        db.server.getState(missing.id)!!.serverWatchId shouldBe fake.watches.single { it.localId == missing.id }.watchId
        db.server.rollupsSince(missing.id, Instant.EPOCH).first().shouldBeEmpty()
        db.server.rollupsSince(kept.id, Instant.EPOCH).first().size shouldBe 1
        // The event cursor from before the hydration is kept
        db.server.getSync(INSTALLATION_A)!!.eventCursor shouldBe 7L
    }

    @Test
    fun `an unavailable watch list holds uploads back until its retry hint`() = runBlocking<Unit> {
        val watch = db.createAircraft("3C65A3", "")
        fake.intercept = { call ->
            if (call.method == "PUT" && call.path == WATCHES_PATH + watch.id) {
                problem(503, "watchlist_unavailable", retryAfterHeader = "3600")
            } else {
                null
            }
        }
        val until = NOW.plus(Duration.ofHours(1))

        sync.run("first") shouldBe WatchListSync.Result.Done(moreWork = false, resumeAt = until)

        db.server.getSync(INSTALLATION_A)!!.uploadsNotBefore shouldBe until

        val before = fake.calls.size
        sync.run("second") shouldBe WatchListSync.Result.Done(moreWork = false, resumeAt = until)
        requests(before).shouldBeEmpty()
    }

    @Test
    fun `nothing is sent when the server offers no watch list`() = runBlocking<Unit> {
        db.createAircraft("3C65A3", "")
        accessState.value = access(watchList = false)

        sync.run("test") shouldBe WatchListSync.Result.Inactive

        fake.calls.shouldBeEmpty()
        coVerify(exactly = 0) { sessionManager.authedAs<Any?>(any()) }
        db.server.getSync(INSTALLATION_A) shouldBe null
    }

    @Test
    fun `nothing is sent when the access is for another installation`() = runBlocking<Unit> {
        db.createAircraft("3C65A3", "")
        accessState.value = access(installationId = INSTALLATION_B)

        sync.run("test") shouldBe WatchListSync.Result.Inactive

        fake.calls.shouldBeEmpty()
        db.server.getSync(INSTALLATION_A) shouldBe null
        db.server.getSync(INSTALLATION_B) shouldBe null
    }

    @Test
    fun `a new match of a synced watch is applied and alerted under the watch`() = runBlocking<Unit> {
        val (watch) = synced("3C65A3")
        val matched = fake.event("matched", remoteOf(watch), observedAt = NOW.minus(Duration.ofMinutes(1)))

        sync.run("poll") shouldBe WatchListSync.Result.Done(moreWork = false)

        db.server.getState(watch.id)!!.apply {
            presence shouldBe "present"
            eventSeq shouldBe matched.seq
            lastMatchSeq shouldBe matched.seq
            lastMatchAt shouldBe NOW.minus(Duration.ofMinutes(1))
            lastMatchAircraftIds shouldBe "3C65A3"
            lastMatchTotal shouldBe 1
        }
        coVerify(exactly = 1) { alerts.postAlert(match { it.id == watch.id }, listOf("3C65A3"), 1) }
        coVerify(exactly = 0) { alerts.postSummary(any(), any()) }
        db.server.getOutbox().shouldBeEmpty()
        db.server.getEvents().map { it.seq } shouldBe listOf(matched.seq)
        syncState().apply {
            eventCursor shouldBe matched.seq
            lastPollAt shouldBe NOW
        }
    }

    @Test
    fun `a page with a new list revision is applied before the list is read again`() = runBlocking<Unit> {
        val (watch) = synced("3C65A3")
        val remote = remoteOf(watch)
        fake.watches[fake.watches.indexOf(remote)] = remote.copy(
            status = "paused",
            pauseReason = "lease_expired",
            presence = "absent",
            lastSeenAt = NOW.minus(Duration.ofHours(1)).toEpochMilli(),
            lastSeenAircraftId = "3C65A3",
        )
        fake.revision++
        val matched = fake.event("matched", remote)
        val before = fake.calls.size

        sync.run("poll") shouldBe WatchListSync.Result.Done(moreWork = false)

        requests(before) shouldBe listOf("GET" to LIST_PATH)
        db.server.getState(watch.id)!!.apply {
            status shouldBe "paused"
            pauseReason shouldBe "lease_expired"
            // The list is older than the applied match
            presence shouldBe "present"
            eventSeq shouldBe matched.seq
            lastSeenAt shouldBe NOW.minus(Duration.ofHours(1))
        }
        syncState().apply {
            listRevision shouldBe fake.revision
            eventCursor shouldBe matched.seq
        }
        coVerify(exactly = 1) { alerts.postAlert(match { it.id == watch.id }, listOf("3C65A3"), 1) }
    }

    @Test
    fun `an event of an older revision is stored but neither applied nor alerted`() = runBlocking<Unit> {
        val (watch) = synced("3C65A3")
        val stale = fake.event("matched", remoteOf(watch), revision = 0)

        sync.run("poll") shouldBe WatchListSync.Result.Done(moreWork = false)

        db.server.getEvents().map { it.seq } shouldBe listOf(stale.seq)
        db.server.getState(watch.id)!!.apply {
            presence shouldBe "unknown"
            eventSeq shouldBe 0L
            lastMatchSeq shouldBe null
        }
        syncState().eventCursor shouldBe stale.seq
        coVerify(exactly = 0) { alerts.postAlert(any(), any(), any()) }
    }

    @Test
    fun `an event of a watch whose edit is not uploaded yet is stored but neither applied nor alerted`() = runBlocking<Unit> {
        val area = db.createLocation(50.0, 8.5, 5_000f, "Home", "")
        db.updateNotification(area.id, true)
        sync.run("setup") shouldBe WatchListSync.Result.Done(moreWork = false)
        val remote = fake.watches.single()
        db.updateLocation(area.id, 51.0, 9.0, 5_000f, "Home")
        fake.intercept = { call ->
            if (call.method == "PUT") problem(503, "watchlist_unavailable", retryAfterHeader = "3600") else null
        }
        val matched = fake.event("matched", remote)

        sync.run("poll") shouldBe WatchListSync.Result.Done(moreWork = false, resumeAt = NOW.plus(Duration.ofHours(1)))

        db.server.getEvents().map { it.seq } shouldBe listOf(matched.seq)
        db.server.getState(area.id)!!.apply {
            serverRevision shouldBe 1L
            presence shouldBe "unknown"
            eventSeq shouldBe 0L
            lastMatchSeq shouldBe null
        }
        coVerify(exactly = 0) { alerts.postAlert(any(), any(), any()) }
    }

    @Test
    fun `a match of a watch without alerts is applied quietly`() = runBlocking<Unit> {
        val (watch) = synced("3C65A3", notify = false)
        val matched = fake.event("matched", remoteOf(watch))

        sync.run("poll") shouldBe WatchListSync.Result.Done(moreWork = false)

        db.server.getState(watch.id)!!.apply {
            presence shouldBe "present"
            eventSeq shouldBe matched.seq
            lastMatchSeq shouldBe matched.seq
        }
        db.server.getOutbox().shouldBeEmpty()
        coVerify(exactly = 0) { alerts.postAlert(any(), any(), any()) }
        coVerify(exactly = 0) { alerts.postSummary(any(), any()) }
    }

    @Test
    fun `an alert that failed to post after its commit is posted by the next run`() = runBlocking<Unit> {
        val (watch) = synced("3C65A3")
        var posts = 0
        coEvery { alerts.postAlert(any(), any(), any()) } coAnswers {
            if (++posts == 1) throw IllegalStateException("killed while posting")
        }
        val matched = fake.event("matched", remoteOf(watch))

        sync.run("poll") shouldBe WatchListSync.Result.Done(moreWork = false)

        db.server.getOutbox().map { it.id } shouldBe listOf(WatchNotificationOutboxEntity.individualId(matched.seq))
        db.server.getState(watch.id)!!.eventSeq shouldBe matched.seq

        sync.run("next") shouldBe WatchListSync.Result.Done(moreWork = false)

        db.server.getOutbox().shouldBeEmpty()
        coVerify(exactly = 2) { alerts.postAlert(match { it.id == watch.id }, listOf("3C65A3"), 1) }
    }

    @Test
    fun `after a long gap old matches go into one summary and recent ones alert`() = runBlocking<Unit> {
        val (watch) = synced("3C65A3")
        val remote = remoteOf(watch)
        db.server.upsertSync(syncState().copy(lastPollAt = NOW.minus(Duration.ofHours(5))))
        // The expected gap is 60 min + 10 min flex + 15 min grace
        fake.event("matched", remote, observedAt = NOW.minus(Duration.ofHours(4)))
        fake.event("cleared", remote, observedAt = NOW.minus(Duration.ofHours(3)))
        val recent = fake.event("matched", remote, observedAt = NOW.minus(Duration.ofMinutes(10)))

        sync.run("poll") shouldBe WatchListSync.Result.Done(moreWork = false)

        coVerify(exactly = 1) { alerts.postSummary(sightings = 1, watches = 1) }
        coVerify(exactly = 1) { alerts.postAlert(any(), any(), any()) }
        db.server.getState(watch.id)!!.apply {
            presence shouldBe "present"
            lastMatchSeq shouldBe recent.seq
        }
        db.server.getOutbox().shouldBeEmpty()
        syncState().lastPollAt shouldBe NOW
    }

    @Test
    fun `at most five matches of a run alert on their own, across its pages`() = runBlocking<Unit> {
        val watches = synced("3C65A1", "3C65A2", "3C65A3", "3C65A4", "3C65A5", "3C65A6", "3C65A7")
        fake.eventPageSize = 4
        val matches = watches.map { fake.event("matched", remoteOf(it)) }
        val before = fake.calls.size

        sync.run("poll") shouldBe WatchListSync.Result.Done(moreWork = false)

        eventPolls(before) shouldBe listOf(0L, matches[3].seq)
        coVerify(exactly = 5) { alerts.postAlert(any(), any(), any()) }
        watches.take(5).forEach { watch ->
            coVerify(exactly = 1) { alerts.postAlert(match { it.id == watch.id }, any(), any()) }
        }
        coVerify(exactly = 1) { alerts.postSummary(sightings = 2, watches = 2) }
        syncState().apply {
            pollRunId shouldBe null
            pollRunIndividual shouldBe 0
        }
        db.server.getOutbox().shouldBeEmpty()
    }

    @Test
    fun `a poll run cut short after a page is resumed with the alerts it already used`() = runBlocking<Unit> {
        val watches = synced("3C65A1", "3C65A2", "3C65A3", "3C65A4", "3C65A5", "3C65A6", "3C65A7")
        fake.eventPageSize = 4
        val matches = watches.map { fake.event("matched", remoteOf(it)) }
        var polls = 0
        fake.intercept = { call ->
            if (call.path.startsWith(EVENTS_PATH) && ++polls == 2) problem(500, "internal_error") else null
        }

        shouldThrow<ServerApiException> { sync.run("cut short") }

        val runId = syncState().run {
            eventCursor shouldBe matches[3].seq
            pollRunIndividual shouldBe 4
            lastPollAt shouldBe NOW
            pollRunId.shouldNotBeNull()
        }
        db.server.getOutbox().map { it.id } shouldBe
                matches.take(4).map { WatchNotificationOutboxEntity.individualId(it.seq) }
        // Keeps the summary in the outbox to show which poll run it belongs to
        coEvery { alerts.postSummary(any(), any()) } throws IllegalStateException("killed while posting")

        sync.run("resume") shouldBe WatchListSync.Result.Done(moreWork = false)

        coVerify(exactly = 5) { alerts.postAlert(any(), any(), any()) }
        watches.take(5).forEach { watch ->
            coVerify(exactly = 1) { alerts.postAlert(match { it.id == watch.id }, any(), any()) }
        }
        coVerify(exactly = 1) { alerts.postSummary(sightings = 2, watches = 2) }
        db.server.getOutbox().single().apply {
            id shouldBe WatchNotificationOutboxEntity.summaryId(runId)
            summaryCount shouldBe 2
        }
        syncState().apply {
            eventCursor shouldBe matches.last().seq
            pollRunId shouldBe null
            pollRunIndividual shouldBe 0
            lastPollAt shouldBe NOW
        }
    }

    @Test
    fun `a poll run stopped by the page cap is resumed with the alerts it already used`() = runBlocking<Unit> {
        val watches = synced("3C65A1", "3C65A2", "3C65A3", "3C65A4", "3C65A5", "3C65A6", "3C65A7")
        fake.eventPageSize = 1
        repeat(6) { fake.gap() }
        val matches = watches.map { fake.event("matched", remoteOf(it)) }

        sync.run("capped") shouldBe WatchListSync.Result.Done(moreWork = true)

        coVerify(exactly = 4) { alerts.postAlert(any(), any(), any()) }
        val runId = syncState().run {
            eventCursor shouldBe matches[3].seq
            pollRunIndividual shouldBe 4
            pollRunId.shouldNotBeNull()
        }
        // Keeps the summary in the outbox to show which poll run it belongs to
        coEvery { alerts.postSummary(any(), any()) } throws IllegalStateException("killed while posting")

        sync.run("resume") shouldBe WatchListSync.Result.Done(moreWork = false)

        coVerify(exactly = 5) { alerts.postAlert(any(), any(), any()) }
        coVerify(exactly = 1) { alerts.postSummary(sightings = 2, watches = 2) }
        db.server.getOutbox().single().apply {
            id shouldBe WatchNotificationOutboxEntity.summaryId(runId)
            summaryCount shouldBe 2
        }
        syncState().apply {
            eventCursor shouldBe matches.last().seq
            pollRunId shouldBe null
            pollRunIndividual shouldBe 0
        }
    }

    @Test
    fun diagF4aMovedAreaDropsTheLastMatchOfTheOldArea() = runBlocking<Unit> {
        val watch = db.createLocation(50.0, 8.5, 5_000f, "Home", "")
        sync.run("setup") shouldBe WatchListSync.Result.Done(moreWork = false)
        val synced = db.server.getState(watch.id)!!
        synced.serverRevision shouldBe 1L
        db.server.upsertState(
            synced.copy(
                lastMatchSeq = 7,
                lastMatchAt = NOW.minus(Duration.ofMinutes(5)),
                lastMatchAircraftIds = "3C65A3",
                lastMatchTotal = 1,
            )
        )

        db.updateLocation(watch.id, 51.0, 9.0, 5_000f, "Home")
        sync.run("edit") shouldBe WatchListSync.Result.Done(moreWork = false)

        upserts().last().second.expectedWatchRevision shouldBe 1
        db.server.getState(watch.id)!!.apply {
            serverRevision shouldBe 2L
            ackedDefinitionRevision shouldBe 1L
            lastMatchSeq shouldBe null
            lastMatchAt shouldBe null
            lastMatchAircraftIds shouldBe null
            lastMatchTotal shouldBe null
        }
    }

    @Test
    fun diagF5InterruptedPollRunPostsOneSummaryOnlyAfterCatchingUp() = runBlocking<Unit> {
        val (watch) = synced("3C65A3")
        val remote = remoteOf(watch)
        db.server.upsertSync(syncState().copy(lastPollAt = NOW.minus(Duration.ofHours(5))))
        fake.eventPageSize = 2
        // All four are older than the expected gap, so each only counts towards the run's summary
        repeat(4) { fake.event("matched", remote, observedAt = NOW.minus(Duration.ofHours(4)).plusSeconds(60L * it)) }
        var polls = 0
        fake.intercept = { call ->
            if (call.path.startsWith(EVENTS_PATH) && ++polls == 2) problem(500, "internal_error") else null
        }

        shouldThrow<ServerApiException> { sync.run("cut short") }

        syncState().pollRunId.shouldNotBeNull()
        coVerify(exactly = 0) { alerts.postSummary(any(), any()) }

        sync.run("resume") shouldBe WatchListSync.Result.Done(moreWork = false)

        syncState().pollRunId shouldBe null
        coVerify(exactly = 0) { alerts.postAlert(any(), any(), any()) }
        coVerify(exactly = 1) { alerts.postSummary(any(), any()) }
        coVerify(exactly = 1) { alerts.postSummary(sightings = 4, watches = 1) }
    }

    @Test
    fun `a poll run after one that caught up starts with a new id and a full alert budget`() = runBlocking<Unit> {
        val watches = synced("3C65A1", "3C65A2", "3C65A3", "3C65A4", "3C65A5", "3C65A6", "3C65A7")
        fake.eventPageSize = 4
        // Keeps the summaries in the outbox to show which poll run each belongs to
        coEvery { alerts.postSummary(any(), any()) } throws IllegalStateException("killed while posting")
        watches.forEach { fake.event("matched", remoteOf(it)) }

        sync.run("first") shouldBe WatchListSync.Result.Done(moreWork = false)

        val first = db.server.getOutbox().single()
        syncState().pollRunId shouldBe null
        watches.forEach { fake.event("matched", remoteOf(it)) }

        sync.run("second") shouldBe WatchListSync.Result.Done(moreWork = false)

        coVerify(exactly = 10) { alerts.postAlert(any(), any(), any()) }
        val summaries = db.server.getOutbox()
        summaries.map { it.summaryCount } shouldBe listOf(2, 2)
        summaries.map { it.id }.toSet().size shouldBe 2
        summaries.map { it.id } shouldContain first.id
        syncState().pollRunId shouldBe null
    }

    @Test
    fun `an expired cursor takes the state from the list without alerting the missed events`() = runBlocking<Unit> {
        val (watch) = synced("3C65A3")
        val remote = remoteOf(watch)
        fake.event("matched", remote)
        fake.event("cleared", remote)
        fake.watches[fake.watches.indexOf(remote)] = remote.copy(
            presence = "absent",
            eventSeq = 2,
            lastSeenAt = NOW.minus(Duration.ofMinutes(30)).toEpochMilli(),
            lastSeenAircraftId = "3C65A3",
        )
        fake.expiredBefore = fake.cursor
        val before = fake.calls.size

        sync.run("poll") shouldBe WatchListSync.Result.Done(moreWork = false)

        eventPolls(before) shouldBe listOf(0L, 2L)
        requests(before) shouldBe listOf("GET" to LIST_PATH)
        db.server.getState(watch.id)!!.apply {
            presence shouldBe "absent"
            eventSeq shouldBe 2L
            lastSeenAt shouldBe NOW.minus(Duration.ofMinutes(30))
            lastSeenAircraftId shouldBe "3C65A3"
        }
        syncState().eventCursor shouldBe 2L
        db.server.getEvents().shouldBeEmpty()
        coVerify(exactly = 0) { alerts.postAlert(any(), any(), any()) }
        coVerify(exactly = 0) { alerts.postSummary(any(), any()) }
    }

    @Test
    fun `a run polls at most ten pages and asks for another run`() = runBlocking<Unit> {
        synced("3C65A3")
        db.server.upsertSync(syncState().copy(lastPollAt = NOW.minus(Duration.ofHours(1))))
        fake.eventPageSize = 1
        repeat(12) { fake.gap() }
        val before = fake.calls.size

        sync.run("first") shouldBe WatchListSync.Result.Done(moreWork = true)

        eventPolls(before) shouldBe (0L until 10L).toList()
        syncState().apply {
            eventCursor shouldBe 10L
            // Not read up to the newest event yet
            lastPollAt shouldBe NOW.minus(Duration.ofHours(1))
        }

        val second = fake.calls.size
        sync.run("second") shouldBe WatchListSync.Result.Done(moreWork = false)

        eventPolls(second) shouldBe listOf(10L, 11L)
        syncState().apply {
            eventCursor shouldBe 12L
            lastPollAt shouldBe NOW
        }
        db.server.getEvents().size shouldBe 12
    }

    @Test
    fun `a throttled poll holds polls back without failing the run or its uploads`() = runBlocking<Unit> {
        val watch = db.createAircraft("3C65A3", "")
        fake.intercept = { call ->
            if (call.path.startsWith(EVENTS_PATH)) {
                problem(429, "installation_rate_exceeded", retryAfterHeader = "30")
            } else {
                null
            }
        }

        sync.run("first") shouldBe WatchListSync.Result.Done(moreWork = false)

        upserts().map { it.first } shouldBe listOf(watch.id)
        db.server.getState(watch.id)!!.uploadState shouldBe WatchServerStateEntity.UPLOAD_SYNCED
        eventPolls() shouldBe listOf(0L)
        syncState().eventsNotBefore shouldBe NOW.plus(Duration.ofSeconds(30))

        val before = fake.calls.size
        sync.run("second") shouldBe WatchListSync.Result.Done(moreWork = false)

        eventPolls(before).shouldBeEmpty()
    }

    @Test
    fun `no events are polled before the list was read`() = runBlocking<Unit> {
        db.createAircraft("3C65A3", "")
        val until = NOW.plus(Duration.ofHours(1))
        db.server.upsertSync(
            WatchServerSyncEntity(
                installationId = INSTALLATION_A,
                phase = WatchServerSyncEntity.PHASE_HYDRATE,
                uploadsNotBefore = until,
                updatedAt = NOW,
            )
        )

        sync.run("test") shouldBe WatchListSync.Result.Done(moreWork = false, resumeAt = until)

        fake.calls.shouldBeEmpty()
        syncState().eventCursor shouldBe null
    }

    @Test
    fun `alerts are posted right after the events phase`() = runBlocking<Unit> {
        val (watch) = synced("3C65A3")
        var callsAtPost = -1
        coEvery { alerts.postAlert(any(), any(), any()) } coAnswers { callsAtPost = fake.calls.size }
        fake.event("matched", remoteOf(watch))

        sync.run("poll") shouldBe WatchListSync.Result.Done(moreWork = false)

        // Nothing a later phase sends goes out before the post
        callsAtPost shouldBe fake.calls.indexOfLast { it.path.startsWith(EVENTS_PATH) } + 1
    }

    @Test
    fun `a periodic run refreshes the last sightings and takes no presence the cursor has not reached`() = runBlocking<Unit> {
        val (watch) = synced("3C65A3")
        val remote = remoteOf(watch)
        fake.watches[fake.watches.indexOf(remote)] = remote.copy(
            presence = "present",
            eventSeq = 5,
            lastSeenAt = NOW.minus(Duration.ofMinutes(10)).toEpochMilli(),
            lastSeenAircraftId = "3C65A3",
        )
        // The list knows events the polls have not delivered yet
        fake.cursor = 5

        val before = fake.calls.size
        sync.run("quiet") shouldBe WatchListSync.Result.Done(moreWork = false)
        requests(before).shouldBeEmpty()

        sync.run("periodic", periodic = true) shouldBe WatchListSync.Result.Done(moreWork = false)

        db.server.getState(watch.id)!!.apply {
            lastSeenAt shouldBe NOW.minus(Duration.ofMinutes(10))
            lastSeenAircraftId shouldBe "3C65A3"
            presence shouldBe "unknown"
            eventSeq shouldBe 0L
        }

        // Once the cursor passed the list's, its presence is taken too
        fake.gap()
        sync.run("periodic", periodic = true) shouldBe WatchListSync.Result.Done(moreWork = false)

        db.server.getState(watch.id)!!.apply {
            presence shouldBe "present"
            eventSeq shouldBe 5L
        }
        syncState().eventCursor shouldBe 6L
    }

    @Test
    fun `rollups are read from the start in pages, each stored with its cursor`() = runBlocking<Unit> {
        val (watch) = synced("3C65A3")
        syncState().rollupCursor shouldBe 0L
        val remote = remoteOf(watch)
        fake.rollupPageSize = 2
        val first = fake.rollup(
            remote,
            hour = NOW.minus(Duration.ofHours(30)),
            count = 2,
            coverage = "full",
            source = "history",
            lastSeenAt = NOW.minus(Duration.ofHours(29)),
        )
        (1..4).forEach { fake.rollup(remote, NOW.minus(Duration.ofHours(it.toLong())), count = 0, coverage = "full") }
        val before = fake.calls.size

        sync.run("rollups") shouldBe WatchListSync.Result.Done(moreWork = false)

        rollupPolls(before) shouldBe listOf(0L, 2L, 4L)
        syncState().rollupCursor shouldBe 5L
        val stored = storedRollups()
        stored.size shouldBe 5
        stored.first().apply {
            serverWatchId shouldBe remote.watchId
            watchId shouldBe watch.id
            installationId shouldBe INSTALLATION_A
            revision shouldBe 1L
            hour shouldBe NOW.minus(Duration.ofHours(30))
            source shouldBe "history"
            seq shouldBe first.seq
            aircraftCount shouldBe 2
            coverage shouldBe "full"
            isFinal shouldBe true
            lastSeenAt shouldBe NOW.minus(Duration.ofHours(29))
            lastSeenAircraftId shouldBe "3C65A3"
        }
    }

    @Test
    fun `a later copy of a rollup hour replaces the stored one`() = runBlocking<Unit> {
        val (watch) = synced("3C65A3")
        val remote = remoteOf(watch)
        val hour = NOW.minus(Duration.ofMinutes(30))
        fake.rollup(remote, hour = hour, count = 1, coverage = "partial", final = false)
        sync.run("first") shouldBe WatchListSync.Result.Done(moreWork = false)
        storedRollups().single().isFinal shouldBe false

        fake.rollup(remote, hour = hour, count = 3, coverage = "full")
        val before = fake.calls.size
        sync.run("second") shouldBe WatchListSync.Result.Done(moreWork = false)

        rollupPolls(before) shouldBe listOf(1L)
        storedRollups().single().apply {
            seq shouldBe 2L
            aircraftCount shouldBe 3
            coverage shouldBe "full"
            isFinal shouldBe true
        }
        syncState().rollupCursor shouldBe 2L
    }

    @Test
    fun `an expired rollup cursor reads every rollup again and replaces the stored ones`() = runBlocking<Unit> {
        val (watch) = synced("3C65A3")
        val remote = remoteOf(watch)
        val dropped = fake.rollup(remote, hour = NOW.minus(Duration.ofHours(3)), count = 1, coverage = "full")
        val kept = NOW.minus(Duration.ofHours(2))
        fake.rollup(remote, hour = kept, count = 0, coverage = "partial", final = false)
        sync.run("first") shouldBe WatchListSync.Result.Done(moreWork = false)
        storedRollups().size shouldBe 2

        fake.rollups.remove(dropped)
        fake.rollup(remote, hour = kept, count = 2, coverage = "full")
        fake.rollupsExpiredBefore = fake.rollupSeq
        val before = fake.calls.size

        sync.run("expired") shouldBe WatchListSync.Result.Done(moreWork = false)

        rollupPolls(before) shouldBe listOf(2L, 0L)
        storedRollups().single().apply {
            hour shouldBe kept
            aircraftCount shouldBe 2
        }
        syncState().rollupCursor shouldBe 3L
    }

    @Test
    fun `old rollup hours are pruned every run and those of unknown server watches once the list was read`() =
        runBlocking<Unit> {
            val (watch) = synced("3C65A3")
            val remote = remoteOf(watch)
            val recent = NOW.minus(Duration.ofHours(1))
            fake.rollup(remote, hour = recent, count = 1, coverage = "full")
            sync.run("bootstrap") shouldBe WatchListSync.Result.Done(moreWork = false)

            fake.rollup(remote, hour = NOW.minus(Duration.ofDays(32)), count = 1, coverage = "full", source = "history")
            fake.rollup(
                remote.copy(watchId = "server-unknown", localId = "unknown-local"),
                hour = recent,
                count = 1,
                coverage = "full",
            )
            sync.run("incremental") shouldBe WatchListSync.Result.Done(moreWork = false)

            // Stored by identity until a list read tells whether the server watch is still there
            storedRollups().map { it.serverWatchId to it.hour }.toSet() shouldBe setOf(
                remote.watchId to recent,
                "server-unknown" to recent,
            )

            sync.run("periodic", periodic = true) shouldBe WatchListSync.Result.Done(moreWork = false)

            storedRollups().map { it.serverWatchId to it.hour } shouldBe listOf(remote.watchId to recent)
        }

    @Test
    fun `a run reads at most five rollup pages and asks for another run`() = runBlocking<Unit> {
        val (watch) = synced("3C65A3")
        val remote = remoteOf(watch)
        fake.rollupPageSize = 1
        (1..6).forEach { fake.rollup(remote, NOW.minus(Duration.ofHours(it.toLong())), count = 0, coverage = "full") }
        val before = fake.calls.size

        sync.run("first") shouldBe WatchListSync.Result.Done(moreWork = true)

        rollupPolls(before) shouldBe (0L until 5L).toList()
        syncState().rollupCursor shouldBe 5L

        val second = fake.calls.size
        sync.run("second") shouldBe WatchListSync.Result.Done(moreWork = false)

        rollupPolls(second) shouldBe listOf(5L)
        storedRollups().size shouldBe 6
    }

    @Test
    fun `a failing rollup read leaves the run's events and alerts in place`() = runBlocking<Unit> {
        val (watch) = synced("3C65A3")
        val matched = fake.event("matched", remoteOf(watch))
        fake.intercept = { call -> if (call.path.startsWith(ROLLUPS_PATH)) problem(500, "internal") else null }
        val before = fake.calls.size

        sync.run("poll") shouldBe WatchListSync.Result.Done(moreWork = false)

        rollupPolls(before) shouldBe listOf(0L)
        db.server.getState(watch.id)!!.apply {
            presence shouldBe "present"
            lastMatchSeq shouldBe matched.seq
        }
        coVerify(exactly = 1) { alerts.postAlert(match { it.id == watch.id }, listOf("3C65A3"), 1) }
        db.server.getOutbox().shouldBeEmpty()
        syncState().apply {
            eventCursor shouldBe matched.seq
            rollupsNotBefore shouldBe null
        }
    }

    @Test
    fun `a throttled rollup read holds rollups back until the retry hint`() = runBlocking<Unit> {
        synced("3C65A3")
        fake.intercept = { call ->
            if (call.path.startsWith(ROLLUPS_PATH)) {
                problem(429, "installation_rate_exceeded", retryAfterHeader = "30")
            } else {
                null
            }
        }
        val before = fake.calls.size

        sync.run("first") shouldBe WatchListSync.Result.Done(moreWork = false)

        rollupPolls(before) shouldBe listOf(0L)
        syncState().apply {
            rollupsNotBefore shouldBe NOW.plus(Duration.ofSeconds(30))
            eventsNotBefore shouldBe null
        }

        val second = fake.calls.size
        sync.run("second") shouldBe WatchListSync.Result.Done(moreWork = false)

        rollupPolls(second).shouldBeEmpty()
    }

    private fun problem(status: Int, code: String, retryAfterHeader: String? = null) = MockResponse()
        .setResponseCode(status)
        .setHeader("Content-Type", "application/problem+json")
        .apply { retryAfterHeader?.let { setHeader("Retry-After", it) } }
        .setBody("""{"type":"about:blank","title":"x","status":$status,"detail":"detail","code":"$code"}""")

    /** A watch list server that keeps its state in memory and answers by the API rules. */
    private inner class FakeWatchListServer(private val json: Json) : Dispatcher() {

        inner class Call(val method: String, val path: String, val body: String)

        val calls = CopyOnWriteArrayList<Call>()
        var revision = 0L
        var state = "active"
        var cursor = 0L
        /** The first this many watches that take a slot are active, in creation order; the others are paused. */
        var maxActive = 10
        var maxStored = 50
        private val allowedTypes = setOf("hex", "callsign", "squawk", "location")

        /** Wider areas take no slot, like the access's maximum location radius. */
        var maxRadiusKm = 250.0

        /** In creation order. */
        val watches = mutableListOf<WatchListWatch>()

        /** Runs before the regular handling, a non-null answer replaces it. */
        var intercept: (Call) -> MockResponse? = { null }
        private var nextWatchId = 1

        val events = mutableListOf<WatchEvent>()
        var eventPageSize = Int.MAX_VALUE

        /** Polls from before this seq are answered as an expired cursor. */
        var expiredBefore: Long? = null

        val rollups = mutableListOf<WatchRollup>()
        var rollupSeq = 0L
        var rollupPageSize = Int.MAX_VALUE

        /** Rollup polls from before this seq, except from the start, are answered as an expired cursor. */
        var rollupsExpiredBefore: Long? = null

        /** Stores the next copy of a rollup hour; it replaces an earlier copy with the same identity. */
        fun rollup(
            remote: WatchListWatch,
            hour: Instant,
            count: Int,
            coverage: String,
            source: String = "live",
            final: Boolean = true,
            lastSeenAt: Instant? = null,
        ): WatchRollup {
            val rollup = WatchRollup(
                seq = ++rollupSeq,
                watchId = remote.watchId,
                localId = remote.localId,
                revision = remote.revision,
                hour = hour.toEpochMilli(),
                source = source,
                evaluationBasis = "recent_messages",
                aircraftCount = count,
                coverage = coverage,
                coveredMinutes = if (coverage == "full") 60 else null,
                isFinal = final,
                lastSeenAt = lastSeenAt?.toEpochMilli(),
                lastSeenAircraftId = lastSeenAt?.let { remote.value },
            )
            rollups.removeAll {
                it.watchId == rollup.watchId && it.revision == rollup.revision &&
                        it.hour == rollup.hour && it.source == rollup.source
            }
            rollups += rollup
            return rollup
        }

        /** Adds the next event; the list's cursor follows it. */
        fun event(
            kind: String,
            remote: WatchListWatch,
            observedAt: Instant = NOW,
            revision: Int = remote.revision,
            aircraftIds: List<String> = listOf(remote.value ?: "3C65A3"),
        ): WatchEvent {
            val event = WatchEvent(
                seq = cursor + 1,
                kind = kind,
                observedAt = observedAt.toEpochMilli(),
                watchId = remote.watchId,
                localId = remote.localId,
                revision = revision,
                aircraftIds = if (kind == "matched") aircraftIds else null,
                totalMatching = if (kind == "matched") aircraftIds.size else null,
            )
            events += event
            cursor = event.seq
            return event
        }

        fun gap(): WatchEvent {
            val event = WatchEvent(
                seq = cursor + 1,
                kind = "coverage_gap",
                observedAt = NOW.toEpochMilli(),
                gapFrom = NOW.minus(Duration.ofMinutes(5)).toEpochMilli(),
                gapUntil = NOW.toEpochMilli(),
            )
            events += event
            cursor = event.seq
            return event
        }

        fun seed(localId: String, hex: String, presence: String): WatchListWatch {
            val watch = WatchListWatch(
                localId = localId,
                watchId = "server-${nextWatchId++}",
                type = "hex",
                value = hex,
                revision = 1,
                status = "active",
                createdAt = NOW.toEpochMilli(),
                updatedAt = NOW.toEpochMilli(),
                presence = presence,
                eventSeq = 0,
                backfill = "done",
            )
            watches += watch
            restatus()
            revision++
            return watches.last()
        }

        @Synchronized
        override fun dispatch(request: RecordedRequest): MockResponse {
            val call = Call(request.method!!, request.path!!, request.body.readUtf8())
            calls += call
            intercept(call)?.let { return it }
            return when {
                call.method == "GET" && call.path == LIST_PATH -> ok(list())
                call.method == "GET" && call.path.startsWith(EVENTS_PATH) -> pollEvents(call.path)
                call.method == "GET" && call.path.startsWith(ROLLUPS_PATH) -> pollRollups(call.path)
                call.method == "PUT" && call.path.startsWith(WATCHES_PATH) -> {
                    upsert(call.path.removePrefix(WATCHES_PATH), json.decodeFromString(call.body))
                }

                call.method == "DELETE" && call.path.startsWith(WATCHES_PATH) -> {
                    if (watches.removeAll { it.localId == call.path.removePrefix(WATCHES_PATH) }) {
                        restatus()
                        revision++
                    }
                    MockResponse().setResponseCode(204)
                }

                else -> MockResponse().setResponseCode(404)
            }
        }

        private fun WatchListWatch.takesSlot(): Boolean =
            type in allowedTypes && (type != "location" || (radiusKm ?: 0.0) <= maxRadiusKm)

        /** Derives the active and limit-paused statuses, a watch paused for another reason is left alone. */
        private fun restatus(): Boolean {
            var active = 0
            var changed = false
            watches.indices.forEach { index ->
                val watch = watches[index]
                if (watch.status != "active" && watch.pauseReason !in DERIVED_PAUSES) return@forEach
                val pauseReason = when {
                    !watch.takesSlot() -> "tier"
                    active < maxActive -> {
                        active++
                        null
                    }

                    else -> "over_limit"
                }
                val status = if (pauseReason == null) "active" else "paused"
                if (watch.status != status || watch.pauseReason != pauseReason) {
                    watches[index] = watch.copy(status = status, pauseReason = pauseReason)
                    changed = true
                }
            }
            return changed
        }

        /** A status change no mutation announced, e.g. after a lower [maxActive], raises the revision too. */
        private fun settle() {
            if (restatus()) revision++
        }

        private fun list(): WatchListResponse {
            settle()
            return WatchListResponse(
                serverTime = NOW.toEpochMilli(),
                revision = revision,
                state = state,
                limits = WatchListLimits(maxActiveWatches = maxActive, maxStoredWatches = maxStored),
                watches = if (state == "expired") emptyList() else watches.toList(),
                cursor = cursor,
            )
        }

        private fun pollEvents(path: String): MockResponse {
            settle()
            val url = "http://fake$path".toHttpUrl()
            val after = url.queryParameter("after")!!.toLong()
            val limit = url.queryParameter("limit")?.toInt() ?: 200
            expiredBefore?.let { if (after < it) return problem(410, "cursor_expired") }
            val pending = events.filter { it.seq > after }.sortedBy { it.seq }
            val page = pending.take(minOf(limit, eventPageSize))
            val body = WatchEventsResponse(
                serverTime = NOW.toEpochMilli(),
                events = page,
                cursor = page.lastOrNull()?.seq ?: after,
                hasMore = pending.size > page.size,
                listRevision = revision,
            )
            return MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(json.encodeToString(WatchEventsResponse.serializer(), body))
        }

        private fun pollRollups(path: String): MockResponse {
            val url = "http://fake$path".toHttpUrl()
            val after = url.queryParameter("after")!!.toLong()
            val limit = url.queryParameter("limit")?.toInt() ?: 500
            rollupsExpiredBefore?.let { if (after != 0L && after < it) return problem(410, "cursor_expired") }
            val pending = rollups.filter { it.seq > after }.sortedBy { it.seq }
            val page = pending.take(minOf(limit, rollupPageSize))
            val body = WatchRollupsResponse(
                serverTime = NOW.toEpochMilli(),
                rollups = page,
                cursor = page.lastOrNull()?.seq ?: after,
                hasMore = pending.size > page.size,
            )
            return MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(json.encodeToString(WatchRollupsResponse.serializer(), body))
        }

        private fun WatchListWatch.sameDefinition(body: WatchUpsertRequest): Boolean {
            if (type != body.type) return false
            return if (type == "location") {
                latitude == body.latitude && longitude == body.longitude && radiusKm == body.radiusKm
            } else {
                value?.trim()?.uppercase() == body.value?.trim()?.uppercase()
            }
        }

        private fun upsert(localId: String, body: WatchUpsertRequest): MockResponse {
            if (state == "expired") {
                watches.clear()
                state = "active"
            }
            val index = watches.indexOfFirst { it.localId == localId }
            if (index >= 0) {
                if (watches[index].sameDefinition(body)) {
                    settle()
                    return ok(WatchMutationResponse(revision, watches[index]))
                }
                val existing = watches[index]
                if (body.expectedWatchRevision != existing.revision) return problem(409, "revision_conflict")
                val updated = existing.copy(
                    type = body.type,
                    value = body.value,
                    latitude = body.latitude,
                    longitude = body.longitude,
                    radiusKm = body.radiusKm,
                    revision = existing.revision + 1,
                    presence = "unknown",
                    eventSeq = 0,
                )
                watches[index] = updated
                restatus()
                revision++
                return ok(WatchMutationResponse(revision, watches[index]))
            }
            if (body.expectedWatchRevision != null) return problem(404, "watch_not_found")
            if (watches.size >= maxStored) return problem(409, "stored_watch_limit")
            val created = WatchListWatch(
                localId = localId,
                watchId = "server-${nextWatchId++}",
                type = body.type,
                value = body.value,
                latitude = body.latitude,
                longitude = body.longitude,
                radiusKm = body.radiusKm,
                revision = 1,
                status = "active",
                createdAt = NOW.toEpochMilli(),
                updatedAt = NOW.toEpochMilli(),
                presence = body.baseline ?: "unknown",
                eventSeq = 0,
                backfill = "pending",
            )
            watches += created
            restatus()
            revision++
            return ok(WatchMutationResponse(revision, watches.last()), status = 201)
        }

        private fun ok(body: WatchListResponse) = MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(json.encodeToString(WatchListResponse.serializer(), body))

        private fun ok(body: WatchMutationResponse, status: Int = 200) = MockResponse()
            .setResponseCode(status)
            .setHeader("Content-Type", "application/json")
            .setBody(json.encodeToString(WatchMutationResponse.serializer(), body))
    }

    companion object {
        private val NOW: Instant = Instant.parse("2026-10-18T12:00:00Z")
        private const val INSTALLATION_A = "installation-a"
        private const val INSTALLATION_B = "installation-b"
        private const val LIST_PATH = "/api/v1/watchlist"
        private const val WATCHES_PATH = "/api/v1/watchlist/watches/"
        private const val EVENTS_PATH = "/api/v1/watchlist/events"
        private const val ROLLUPS_PATH = "/api/v1/watchlist/rollups"

        /** The pause reasons the fake derives from the slots itself. */
        private val DERIVED_PAUSES = setOf("over_limit", "tier")
    }
}
