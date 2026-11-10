package eu.darken.apl.feeder.core.server

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.datastore.DataStoreValue
import eu.darken.apl.common.datastore.createValue
import eu.darken.apl.common.datastore.value
import eu.darken.apl.common.http.HttpModule
import eu.darken.apl.common.serialization.SerializationModule
import eu.darken.apl.feeder.core.config.FeederConfig
import eu.darken.apl.feeder.core.config.FeederGroup
import eu.darken.apl.feeder.core.config.FeederSettings
import eu.darken.apl.feeder.core.server.FakeFeederMonitorServer.Companion.EVENTS_PATH
import eu.darken.apl.feeder.core.server.FakeFeederMonitorServer.Companion.FEEDERS_PATH
import eu.darken.apl.feeder.core.server.FakeFeederMonitorServer.Companion.LIST_PATH
import eu.darken.apl.feeder.core.server.FakeFeederMonitorServer.Companion.dropped
import eu.darken.apl.feeder.core.server.FakeFeederMonitorServer.Companion.problem
import eu.darken.apl.feeder.core.server.FeederServerState.Refusal
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.ServerModule
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.server.api.Allowance
import eu.darken.apl.server.api.FeederMonitorLatest
import eu.darken.apl.server.api.FeederMonitorUpsertRequest
import eu.darken.apl.server.api.RequestLimits
import eu.darken.apl.server.api.RequestRate
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.ServerEndpoint
import eu.darken.apl.server.api.TierPolicy
import eu.darken.apl.server.api.Usage
import eu.darken.apl.server.session.SessionManager
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.TestCompatibilityStore
import testhelper.coroutine.TestDispatcherProvider
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.UUID

class FeederMonitorSyncTest : BaseTest() {

    private val json = ServerModule.serverJson()
    private val appJson = SerializationModule().json()

    // The clock is moved by anchoring a new server time, elapsed time stands still
    private val serverClock = ServerClock(object : MonotonicClock {
        override fun elapsed(): Long = 0L
    }).apply { noteServerTime(NOW_MS) }

    private lateinit var storeScope: CoroutineScope
    private lateinit var storeFile: File
    private lateinit var mockWebServer: MockWebServer
    private lateinit var fake: FakeFeederMonitorServer
    private lateinit var store: FeederServerStore
    private lateinit var feederGroup: DataStoreValue<FeederGroup>
    private lateinit var sync: FeederMonitorSync
    private val compatStore = TestCompatibilityStore()

    private val sessionManager = mockk<SessionManager>()
    private val accessRepo = mockk<AccessRepo>(relaxed = true)
    private val accessState = MutableStateFlow<AccessState?>(null)
    private val feederSettings = mockk<FeederSettings>()

    private var authCalls = 0

    /** The installation the session yields on the n-th authed call of a run, counting from 1. */
    private var identity: (call: Int) -> String = { INSTALLATION_A }

    @BeforeEach
    fun setup() {
        storeFile = File(IO_TEST_BASEDIR, "feeder-monitor-sync-${UUID.randomUUID()}.preferences_pb").apply {
            parentFile?.mkdirs()
            delete()
        }
        storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val dataStore = PreferenceDataStoreFactory.create(scope = storeScope, produceFile = { storeFile })
        store = FeederServerStore(dataStore, appJson)
        feederGroup = dataStore.createValue("feeder.group", FeederGroup(), appJson)
        every { feederSettings.feederGroup } returns feederGroup

        fake = FakeFeederMonitorServer(json) { serverClock.now().toEpochMilli() }
        mockWebServer = MockWebServer().apply {
            dispatcher = fake
            start()
        }
        val endpoint = ServerEndpoint(
            baseClient = HttpModule().baseHttpClient(),
            json = json,
            dispatcherProvider = TestDispatcherProvider(),
            serverClock = serverClock,
            compatibility = compatStore.holder(),
        ).apply {
            baseUrl = mockWebServer.url("/").toString()
        }

        every { accessRepo.state } returns accessState
        accessState.value = access()
        coEvery { sessionManager.authedAs<Any?>(any()) } coAnswers {
            authCalls++
            firstArg<suspend (String, String) -> Any?>().invoke(identity(authCalls), "token")
        }

        sync = FeederMonitorSync(
            sessionManager = sessionManager,
            endpoint = endpoint,
            accessRepo = accessRepo,
            store = store,
            feederSettings = feederSettings,
            serverClock = serverClock,
        )
    }

    @AfterEach
    fun teardown() {
        mockWebServer.shutdown()
        runBlocking { storeScope.coroutineContext.job.cancelAndJoin() }
        storeFile.delete()
        compatStore.close()
    }

    private fun access(
        installationId: String = INSTALLATION_A,
        feederMonitor: Boolean = true,
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
            watchTypes = listOf("hex"),
            maxActiveFeederMonitors = maxActive,
            maxStoredFeederMonitors = maxStored,
        ),
        usage = Usage(
            resetsAt = NOW.plus(Duration.ofHours(12)).toEpochMilli(),
            viewing = Allowance(25_000, 0, 0, 25_000),
            search = Allowance(25, 0, 0, 25),
            watch = Allowance(1000, 0, 0, 1000),
        ),
        installationRequests = RequestLimits(RequestRate(1.0, 3), RequestRate(1.0, 3), RequestRate(1.0, 3), 3),
        aircraftOperations = if (feederMonitor) listOf(AccessState.OPERATION_FEEDER_MONITOR) else emptyList(),
    )

    private fun advance(duration: Duration) {
        serverClock.noteServerTime(serverClock.now().plus(duration).toEpochMilli())
    }

    private fun config(receiverId: String, order: Int, timeout: Duration? = Duration.ofHours(48)) = FeederConfig(
        receiverId = receiverId,
        addedAt = NOW.plusSeconds(order.toLong()),
        offlineCheckTimeout = timeout,
    )

    private suspend fun feeders(vararg configs: FeederConfig) = feederGroup.value(FeederGroup(configs.toSet()))

    /** Feeders added in the given order with the default threshold. */
    private suspend fun feeders(vararg ids: String) = feeders(*ids.mapIndexed { index, id -> config(id, index) }.toTypedArray())

    /** Every request except the event polls, see [eventPolls]. */
    private fun requests(from: Int = 0) = fake.calls.drop(from)
        .filterNot { it.path.startsWith(EVENTS_PATH) }
        .map { it.method to it.path }

    private fun puts(from: Int = 0) = fake.calls.drop(from)
        .filter { it.method == "PUT" }
        .map { it.feederId to json.decodeFromString(FeederMonitorUpsertRequest.serializer(), it.body).offlineAfterMinutes }

    private fun putIds(from: Int = 0) = puts(from).map { it.first }

    private fun deletes(from: Int = 0) = fake.calls.drop(from).filter { it.method == "DELETE" }.map { it.feederId }

    /** The `after` of each event poll. */
    private fun eventPolls(from: Int = 0) = fake.calls.drop(from)
        .filter { it.path.startsWith(EVENTS_PATH) }
        .map { "http://fake${it.path}".toHttpUrl().queryParameter("after")!!.toLong() }

    private suspend fun state() = store.current()

    private suspend fun synced(vararg ids: String) {
        feeders(*ids)
        sync.run("setup") shouldBe FeederMonitorSync.Result.Done(moreWork = false)
    }

    @Test
    fun `the first run reads the list, then registers the feeders one at a time in the order they were added`() =
        runBlocking<Unit> {
            feeders(
                config(ID_3, order = 0, timeout = Duration.ofHours(48)),
                config(ID_1, order = 1, timeout = Duration.ofHours(6)),
                config(ID_2, order = 2, timeout = null),
            )

            sync.run("test") shouldBe FeederMonitorSync.Result.Done(moreWork = false)

            requests().take(4) shouldBe listOf(
                "GET" to LIST_PATH,
                "PUT" to FEEDERS_PATH + ID_3,
                "PUT" to FEEDERS_PATH + ID_1,
                "PUT" to FEEDERS_PATH + ID_2,
            )
            puts() shouldBe listOf(ID_3 to 2880, ID_1 to 360, ID_2 to 2880)
            fake.monitors.map { it.feederId } shouldBe listOf(ID_3, ID_1, ID_2)
            with(state()) {
                installationId shouldBe INSTALLATION_A
                monitors.keys shouldBe setOf(ID_1, ID_2, ID_3)
                monitors.getValue(ID_1).offlineAfterMinutes shouldBe 360
                uploads.getValue(ID_1).syncedOfflineAfterMinutes shouldBe 360
                listRevision shouldBe fake.revision
                cursor shouldBe fake.cursor
                limits?.maxStoredFeeders shouldBe 50
                sampleIntervalSeconds shouldBe 300
                lastListReadAt shouldBe NOW_MS
            }
        }

    @Test
    fun `an uppercase local id is registered once and never deleted`() = runBlocking<Unit> {
        feeders(ID_1.uppercase())

        sync.run("first") shouldBe FeederMonitorSync.Result.Done(moreWork = false)
        sync.run("second") shouldBe FeederMonitorSync.Result.Done(moreWork = false)

        putIds() shouldBe listOf(ID_1)
        deletes().shouldBeEmpty()
        state().monitors.keys shouldBe setOf(ID_1)
    }

    @Test
    fun `case variants of one feeder are registered once, dropping one variant deletes nothing`() = runBlocking<Unit> {
        feeders(ID_1.uppercase(), ID_1)
        sync.run("first") shouldBe FeederMonitorSync.Result.Done(moreWork = false)
        putIds() shouldBe listOf(ID_1)

        val from = fake.calls.size
        feeders(ID_1)
        sync.run("second") shouldBe FeederMonitorSync.Result.Done(moreWork = false)

        puts(from).shouldBeEmpty()
        deletes(from).shouldBeEmpty()
        fake.monitors.map { it.feederId } shouldBe listOf(ID_1)
    }

    @Test
    fun `an id the server cannot take is never sent`() = runBlocking<Unit> {
        feeders("not-a-uuid", "00000000-0000-0000-0000-000000000000", "1-2-3-4-5", ID_1)

        sync.run("test") shouldBe FeederMonitorSync.Result.Done(moreWork = false)

        fake.calls.filter { it.path.startsWith(FEEDERS_PATH) }.map { it.feederId } shouldBe listOf(ID_1)
    }

    @Test
    fun `monitors without a local feeder are deleted, a changed threshold is updated`() = runBlocking<Unit> {
        fake.seed(ID_9)
        fake.seed(ID_1, offlineAfterMinutes = 2880)
        feeders(config(ID_1, order = 0, timeout = Duration.ofHours(6)))

        sync.run("test") shouldBe FeederMonitorSync.Result.Done(moreWork = false)

        requests().take(3) shouldBe listOf(
            "GET" to LIST_PATH,
            "DELETE" to FEEDERS_PATH + ID_9,
            "PUT" to FEEDERS_PATH + ID_1,
        )
        puts() shouldBe listOf(ID_1 to 360)
        fake.monitors.map { it.feederId to it.offlineAfterMinutes } shouldBe listOf(ID_1 to 360)
        with(state()) {
            monitors.keys shouldBe setOf(ID_1)
            monitors.getValue(ID_1).offlineAfterMinutes shouldBe 360
        }
    }

    @Test
    fun `a create without a known outcome holds the uploads back, the run after the backoff resumes with it`() =
        runBlocking<Unit> {
            feeders(ID_1, ID_2, ID_3)
            fake.fail("PUT", FEEDERS_PATH + ID_2, times = Int.MAX_VALUE) { dropped() }
            val until = NOW.plus(Duration.ofMinutes(5))

            sync.run("first") shouldBe FeederMonitorSync.Result.Done(moreWork = false, resumeAt = until)

            putIds().distinct() shouldBe listOf(ID_1, ID_2)
            putIds() shouldNotContain ID_3
            fake.monitors.map { it.feederId } shouldBe listOf(ID_1)
            state().uploadsNotBefore shouldBe until.toEpochMilli()
            eventPolls().shouldNotBeEmpty()

            fake.clearFailures()
            var from = fake.calls.size
            sync.run("still held back") shouldBe FeederMonitorSync.Result.Done(moreWork = false, resumeAt = until)
            puts(from).shouldBeEmpty()

            advance(Duration.ofMinutes(5).plusSeconds(1))
            from = fake.calls.size
            sync.run("later") shouldBe FeederMonitorSync.Result.Done(moreWork = false)

            putIds(from) shouldBe listOf(ID_2, ID_3)
            fake.monitors.map { it.feederId } shouldBe listOf(ID_1, ID_2, ID_3)
        }

    @Test
    fun `a delete without a known outcome holds the uploads back, the run after the backoff retries it`() =
        runBlocking<Unit> {
            fake.seed(ID_9)
            feeders(ID_1)
            fake.fail("DELETE", FEEDERS_PATH + ID_9, times = Int.MAX_VALUE) { dropped() }
            val until = NOW.plus(Duration.ofMinutes(5))

            sync.run("first") shouldBe FeederMonitorSync.Result.Done(moreWork = false, resumeAt = until)

            deletes().distinct() shouldBe listOf(ID_9)
            puts().shouldBeEmpty()
            state().uploadsNotBefore shouldBe until.toEpochMilli()

            fake.clearFailures()
            var from = fake.calls.size
            sync.run("still held back") shouldBe FeederMonitorSync.Result.Done(moreWork = false, resumeAt = until)
            deletes(from).shouldBeEmpty()
            puts(from).shouldBeEmpty()

            advance(Duration.ofMinutes(5).plusSeconds(1))
            from = fake.calls.size
            sync.run("later") shouldBe FeederMonitorSync.Result.Done(moreWork = false)

            deletes(from) shouldBe listOf(ID_9)
            putIds(from) shouldBe listOf(ID_1)
            fake.monitors.map { it.feederId } shouldBe listOf(ID_1)
        }

    @Test
    fun `a database outage outlasting the retry holds uploads back for a longer Retry-After`() = runBlocking<Unit> {
        feeders(ID_1, ID_2)
        fake.fail("PUT", FEEDERS_PATH + ID_1, times = Int.MAX_VALUE) {
            problem(503, "database_unavailable", retryAfter = 600)
        }
        val until = NOW.plusSeconds(600)

        sync.run("test") shouldBe FeederMonitorSync.Result.Done(moreWork = false, resumeAt = until)

        putIds() shouldBe listOf(ID_1, ID_1)
        state().uploadsNotBefore shouldBe until.toEpochMilli()
        fake.monitors.shouldBeEmpty()
    }

    @Test
    fun `a database outage on a create is retried in the same run`() = runBlocking<Unit> {
        feeders(ID_1, ID_2)
        fake.fail("PUT", FEEDERS_PATH + ID_1) { problem(503, "database_unavailable", retryAfter = 1) }

        sync.run("test") shouldBe FeederMonitorSync.Result.Done(moreWork = false)

        putIds() shouldBe listOf(ID_1, ID_1, ID_2)
        fake.monitors.map { it.feederId } shouldBe listOf(ID_1, ID_2)
    }

    @Test
    fun `the storage limit refuses that feeder and every later one, a changed feeder set retries them`() =
        runBlocking<Unit> {
            fake.maxStored = 1
            feeders(ID_1, ID_2, ID_3)

            sync.run("first") shouldBe FeederMonitorSync.Result.Done(moreWork = false)

            putIds() shouldBe listOf(ID_1, ID_2)
            with(state().uploads) {
                get(ID_1)?.refusal shouldBe null
                getValue(ID_2).refusal shouldBe Refusal.STORAGE_LIMIT
                getValue(ID_3).refusal shouldBe Refusal.STORAGE_LIMIT
            }

            var from = fake.calls.size
            sync.run("unchanged") shouldBe FeederMonitorSync.Result.Done(moreWork = false)
            puts(from).shouldBeEmpty()

            from = fake.calls.size
            feeders(ID_1, ID_2)
            sync.run("changed") shouldBe FeederMonitorSync.Result.Done(moreWork = false)
            putIds(from) shouldBe listOf(ID_2)
            state().uploads.getValue(ID_2).refusal shouldBe Refusal.STORAGE_LIMIT
            state().uploads.keys shouldNotContain ID_3
        }

    @Test
    fun `the storage limit is retried once the limits change`() = runBlocking<Unit> {
        accessState.value = access(maxStored = 1)
        fake.maxStored = 1
        feeders(ID_1, ID_2, ID_3)
        sync.run("first") shouldBe FeederMonitorSync.Result.Done(moreWork = false)
        putIds() shouldBe listOf(ID_1, ID_2)

        accessState.value = access(maxStored = 5)
        fake.maxStored = 5
        val from = fake.calls.size
        sync.run("raised") shouldBe FeederMonitorSync.Result.Done(moreWork = false)

        putIds(from) shouldBe listOf(ID_2, ID_3)
        fake.monitors.map { it.feederId } shouldBe listOf(ID_1, ID_2, ID_3)
        state().uploads.values.map { it.refusal }.distinct() shouldBe listOf(null)
    }

    @Test
    fun `a full server holds creates back but lets threshold changes through`() = runBlocking<Unit> {
        fake.seed(ID_1, offlineAfterMinutes = 2880)
        fake.full = true
        feeders(
            config(ID_2, order = 0),
            config(ID_1, order = 1, timeout = Duration.ofHours(6)),
            config(ID_3, order = 2),
        )
        val until = NOW.plusSeconds(3600)

        sync.run("full") shouldBe FeederMonitorSync.Result.Done(moreWork = false, resumeAt = until)

        puts() shouldBe listOf(ID_2 to 2880, ID_1 to 360)
        with(state()) {
            uploads.getValue(ID_2).refusal shouldBe Refusal.SERVER_FULL
            createsNotBefore shouldBe until.toEpochMilli()
            uploadsNotBefore shouldBe null
        }
        eventPolls().shouldNotBeEmpty()

        var from = fake.calls.size
        sync.run("still held back") shouldBe FeederMonitorSync.Result.Done(moreWork = false, resumeAt = until)
        puts(from).shouldBeEmpty()

        advance(Duration.ofSeconds(3601))
        fake.full = false
        from = fake.calls.size
        sync.run("later") shouldBe FeederMonitorSync.Result.Done(moreWork = false)

        putIds(from) shouldBe listOf(ID_2, ID_3)
        state().uploads.getValue(ID_2).refusal shouldBe null
    }

    @Test
    fun `a spent mutation budget holds uploads back while the events are still polled`() = runBlocking<Unit> {
        feeders(ID_1)
        fake.fail("PUT", FEEDERS_PATH) { problem(429, "feeder_monitor_mutation_budget_exceeded", retryAfter = 600) }
        val until = NOW.plusSeconds(600)

        sync.run("first") shouldBe FeederMonitorSync.Result.Done(moreWork = false, resumeAt = until)

        state().uploadsNotBefore shouldBe until.toEpochMilli()
        eventPolls().shouldNotBeEmpty()
        fake.monitors.shouldBeEmpty()

        val from = fake.calls.size
        sync.run("second") shouldBe FeederMonitorSync.Result.Done(moreWork = false, resumeAt = until)
        puts(from).shouldBeEmpty()
        eventPolls(from).shouldNotBeEmpty()
    }

    @Test
    fun `an invalid refusal marks that feeder and the next one is still registered`() = runBlocking<Unit> {
        feeders(ID_1, ID_2)
        fake.fail("PUT", FEEDERS_PATH + ID_1, times = Int.MAX_VALUE) { problem(400, "invalid_request") }

        sync.run("first") shouldBe FeederMonitorSync.Result.Done(moreWork = false)

        putIds() shouldBe listOf(ID_1, ID_2)
        state().uploads.getValue(ID_1).refusal shouldBe Refusal.INVALID
        fake.monitors.map { it.feederId } shouldBe listOf(ID_2)

        val from = fake.calls.size
        sync.run("second") shouldBe FeederMonitorSync.Result.Done(moreWork = false)
        puts(from).shouldBeEmpty()
    }

    @Test
    fun `a run sends at most 20 mutations and leaves the rest to the next one`() = runBlocking<Unit> {
        val ids = (1..22).map { id(it) }
        feeders(*ids.toTypedArray())

        sync.run("first") shouldBe FeederMonitorSync.Result.Done(moreWork = true)
        putIds() shouldBe ids.take(20)

        val from = fake.calls.size
        sync.run("second") shouldBe FeederMonitorSync.Result.Done(moreWork = false)
        putIds(from) shouldBe ids.drop(20)
    }

    @Test
    fun `deleting a monitor reads the list again, the next monitor took its slot`() = runBlocking<Unit> {
        fake.maxActive = 1
        fake.seed(ID_9)
        fake.seed(ID_1)
        fake.monitor(ID_1).pauseReason shouldBe "over_limit"
        feeders(ID_1)

        sync.run("test") shouldBe FeederMonitorSync.Result.Done(moreWork = false)

        deletes() shouldBe listOf(ID_9)
        with(state().monitors.getValue(ID_1)) {
            status shouldBe "active"
            pauseReason shouldBe null
        }
        state().listRevision shouldBe fake.revision
    }

    @Test
    fun `events change only the monitor they belong to and only past its event seq`() = runBlocking<Unit> {
        synced(ID_1, ID_2, ID_3)
        store.update { s ->
            s.copy(monitors = s.monitors + (ID_3 to s.monitors.getValue(ID_3).copy(eventSeq = 1000)))
        }
        val healthOf3 = state().monitors.getValue(ID_3).health
        val since = NOW_MS - 30 * 60_000

        val offline = fake.event("offline", ID_1, since = since, reflect = false)
        fake.event("problem_started", ID_2, problem = "deaf", reflect = false)
        fake.event("problem_started", ID_2, problem = "mlat_missing", reflect = false)
        fake.event("problem_cleared", ID_2, problem = "deaf", reflect = false)
        fake.event("online", ID_1, monitorId = "monitor-not-held", reflect = false)
        val gap = fake.event("coverage_gap", ID_2, reflect = false)
        val stale = fake.event("offline", ID_3, since = since, reflect = false)

        sync.run("events") shouldBe FeederMonitorSync.Result.Done(moreWork = false)

        with(state()) {
            with(monitors.getValue(ID_1)) {
                health shouldBe "offline"
                healthSince shouldBe since
                unknownReason shouldBe null
                eventSeq shouldBe offline.seq
            }
            with(monitors.getValue(ID_2)) {
                problems shouldBe listOf("mlat_missing")
                eventSeq shouldBe gap.seq
            }
            with(monitors.getValue(ID_3)) {
                health shouldBe healthOf3
                eventSeq shouldBe 1000
            }
            cursor shouldBe stale.seq
        }

        val back = fake.event("online", ID_1, observedAt = NOW_MS + 300_000, reflect = false)
        sync.run("back online") shouldBe FeederMonitorSync.Result.Done(moreWork = false)
        with(state().monitors.getValue(ID_1)) {
            health shouldBe "online"
            healthSince shouldBe NOW_MS + 300_000
            eventSeq shouldBe back.seq
        }
    }

    @Test
    fun `each page is stored with its cursor`() = runBlocking<Unit> {
        synced(ID_1)
        val base = state().cursor!!
        fake.eventPageSize = 2
        repeat(5) { fake.event(if (it % 2 == 0) "offline" else "online", ID_1, reflect = false) }
        fake.fail("GET", "$EVENTS_PATH?after=${base + 4}&", times = Int.MAX_VALUE) { problem(500, "internal") }
        val from = fake.calls.size

        shouldThrow<ServerApiException> { sync.run("test") }

        eventPolls(from) shouldBe listOf(base, base + 2, base + 4)
        with(state()) {
            cursor shouldBe base + 4
            monitors.getValue(ID_1).eventSeq shouldBe base + 4
            monitors.getValue(ID_1).health shouldBe "online"
        }
    }

    @Test
    fun `the poll stops after ten pages and asks for another run`() = runBlocking<Unit> {
        synced(ID_1)
        val base = state().cursor!!
        fake.eventPageSize = 1
        repeat(12) { fake.event("problem_started", ID_1, problem = "p$it", reflect = false) }
        var from = fake.calls.size

        sync.run("first") shouldBe FeederMonitorSync.Result.Done(moreWork = true)

        eventPolls(from).size shouldBe 10
        state().cursor shouldBe base + 10

        from = fake.calls.size
        sync.run("second") shouldBe FeederMonitorSync.Result.Done(moreWork = false)
        eventPolls(from) shouldBe listOf(base + 10, base + 11)
        state().cursor shouldBe base + 12
        state().monitors.getValue(ID_1).problems.size shouldBe 12
    }

    @Test
    fun `an expired cursor takes every monitor and the cursor from the list`() = runBlocking<Unit> {
        synced(ID_1, ID_2)
        fake.event("offline", ID_1, since = NOW_MS - 60_000)
        fake.event("problem_started", ID_2, problem = "deaf")
        fake.expiredBefore = fake.cursor
        val from = fake.calls.size

        sync.run("test") shouldBe FeederMonitorSync.Result.Done(moreWork = false)

        requests(from) shouldContain ("GET" to LIST_PATH)
        eventPolls(from).last() shouldBe fake.cursor
        with(state()) {
            cursor shouldBe fake.cursor
            monitors.getValue(ID_1) shouldBe fake.monitor(ID_1)
            monitors.getValue(ID_2) shouldBe fake.monitor(ID_2)
        }
    }

    @Test
    fun `a new revision on one page reads the list, it neither undoes nor repeats later events`() = runBlocking<Unit> {
        synced(ID_1)
        fake.eventPageSize = 2
        fake.event("offline", ID_1, since = NOW_MS - 60_000)
        fake.event("problem_started", ID_1, problem = "deaf")
        // The list reflects the online event on the second page, but not the event after it
        val online = fake.event("online", ID_1, observedAt = NOW_MS + 60_000)
        val cleared = fake.event("problem_cleared", ID_1, problem = "deaf", reflect = false)
        fake.revision++
        val from = fake.calls.size

        sync.run("test") shouldBe FeederMonitorSync.Result.Done(moreWork = false)

        fake.calls.drop(from).map { it.path.substringBefore('?') } shouldBe listOf(EVENTS_PATH, LIST_PATH, EVENTS_PATH)
        fake.monitor(ID_1).eventSeq shouldBe online.seq
        with(state()) {
            listRevision shouldBe fake.revision
            cursor shouldBe cleared.seq
            with(monitors.getValue(ID_1)) {
                health shouldBe "online"
                healthSince shouldBe NOW_MS + 60_000
                problems.shouldBeEmpty()
                eventSeq shouldBe cleared.seq
            }
        }
    }

    @Test
    fun `the run's list read brings states that come without an event`() = runBlocking<Unit> {
        synced(ID_1, ID_2)
        state().monitors.getValue(ID_1).unknownReason shouldBe "pending"
        fake.updateMonitor(ID_1) {
            it.copy(health = "online", unknownReason = null, healthSince = NOW_MS, latest = FeederMonitorLatest(beastClients = 1))
        }
        fake.updateMonitor(ID_2) { it.copy(health = "unknown", unknownReason = "upstream") }
        val from = fake.calls.size

        sync.run("test") shouldBe FeederMonitorSync.Result.Done(moreWork = false)

        requests(from) shouldBe listOf("GET" to LIST_PATH)
        with(state().monitors) {
            getValue(ID_1).health shouldBe "online"
            getValue(ID_1).unknownReason shouldBe null
            getValue(ID_1).latest shouldBe FeederMonitorLatest(beastClients = 1)
            getValue(ID_2).unknownReason shouldBe "upstream"
        }
    }

    @Test
    fun `a foreground run skips a list read it just did`() = runBlocking<Unit> {
        synced(ID_1)

        var from = fake.calls.size
        sync.run("foreground", foreground = true) shouldBe FeederMonitorSync.Result.Done(moreWork = false)
        requests(from).shouldBeEmpty()
        eventPolls(from).shouldNotBeEmpty()

        from = fake.calls.size
        sync.run("background") shouldBe FeederMonitorSync.Result.Done(moreWork = false)
        requests(from) shouldBe listOf("GET" to LIST_PATH)

        advance(Duration.ofSeconds(61))
        from = fake.calls.size
        sync.run("foreground later", foreground = true) shouldBe FeederMonitorSync.Result.Done(moreWork = false)
        requests(from) shouldBe listOf("GET" to LIST_PATH)
    }

    @Test
    fun `a read rate limit holds reads back`() = runBlocking<Unit> {
        synced(ID_1)
        fake.fail("GET", EVENTS_PATH) { problem(429, "installation_rate_exceeded", retryAfter = 30) }
        val until = NOW.plusSeconds(30)
        var from = fake.calls.size

        sync.run("limited") shouldBe FeederMonitorSync.Result.Done(moreWork = false, resumeAt = until)

        state().readsNotBefore shouldBe until.toEpochMilli()
        requests(from).shouldBeEmpty()

        from = fake.calls.size
        sync.run("still limited") shouldBe FeederMonitorSync.Result.Done(moreWork = false, resumeAt = until)
        fake.calls.size shouldBe from
    }

    @Test
    fun `an expired list registers every feeder again`() = runBlocking<Unit> {
        synced(ID_1, ID_2)
        fake.monitors.clear()
        fake.state = "expired"
        fake.revision++
        val from = fake.calls.size

        sync.run("test") shouldBe FeederMonitorSync.Result.Done(moreWork = false)

        putIds(from) shouldBe listOf(ID_1, ID_2)
        fake.state shouldBe "active"
        fake.monitors.map { it.feederId } shouldBe listOf(ID_1, ID_2)
        state().monitors.keys shouldBe setOf(ID_1, ID_2)
    }

    @Test
    fun `the state of another installation is dropped and read again`() = runBlocking<Unit> {
        synced(ID_1)
        store.update { it.copy(notified = mapOf(ID_1 to FeederServerState.Notified(monitorId = "monitor-1", offline = true))) }

        accessState.value = access(installationId = INSTALLATION_B)
        identity = { INSTALLATION_B }
        val from = fake.calls.size
        sync.run("other") shouldBe FeederMonitorSync.Result.Done(moreWork = false)

        requests(from).first() shouldBe ("GET" to LIST_PATH)
        with(state()) {
            installationId shouldBe INSTALLATION_B
            notified shouldBe emptyMap()
            monitors.keys shouldBe setOf(ID_1)
        }
    }

    @Test
    fun `an installation change during the run drops the state`() = runBlocking<Unit> {
        feeders(ID_1)
        identity = { call -> if (call <= 2) INSTALLATION_A else INSTALLATION_B }

        sync.run("test") shouldBe FeederMonitorSync.Result.Aborted

        requests() shouldBe listOf("GET" to LIST_PATH)
        state() shouldBe FeederServerState()
    }

    @Test
    fun `without feeder monitoring in the access nothing is requested`() = runBlocking<Unit> {
        feeders(ID_1)
        accessState.value = access(feederMonitor = false)

        sync.run("test") shouldBe FeederMonitorSync.Result.Inactive

        fake.calls.shouldBeEmpty()
        coVerify(exactly = 0) { sessionManager.authedAs<Any?>(any()) }
        state() shouldBe FeederServerState()
    }

    @Test
    fun `an access of another installation than the session's is inactive`() = runBlocking<Unit> {
        feeders(ID_1)
        identity = { INSTALLATION_B }

        sync.run("test") shouldBe FeederMonitorSync.Result.Inactive

        fake.calls.shouldBeEmpty()
    }

    companion object {
        private val NOW: Instant = Instant.parse("2026-11-09T12:00:00Z")
        private val NOW_MS = NOW.toEpochMilli()
        private const val INSTALLATION_A = "installation-a"
        private const val INSTALLATION_B = "installation-b"

        private fun id(n: Int) = "%08x-0000-4000-8000-%012x".format(n, n)

        private val ID_1 = id(1)
        private val ID_2 = id(2)
        private val ID_3 = id(3)
        private val ID_9 = id(9)
    }
}
