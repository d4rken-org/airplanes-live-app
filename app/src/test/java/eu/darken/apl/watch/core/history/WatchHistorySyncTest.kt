package eu.darken.apl.watch.core.history

import androidx.test.core.app.ApplicationProvider
import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.datastore.DataStoreValue
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.main.core.request.OperationFailedException
import eu.darken.apl.main.core.request.OperationStore
import eu.darken.apl.main.core.request.OperationYieldedException
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.ServerModule
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.server.api.Allowance
import eu.darken.apl.server.api.HistoryCursor
import eu.darken.apl.server.api.HistoryWatchHour
import eu.darken.apl.server.api.HistoryWatchOutcome
import eu.darken.apl.server.api.HistoryWatchRequest
import eu.darken.apl.server.api.HistoryWatchResponse
import eu.darken.apl.server.api.QueryError
import eu.darken.apl.server.api.RequestLimits
import eu.darken.apl.server.api.RequestRate
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.ServerCodes
import eu.darken.apl.server.api.TierPolicy
import eu.darken.apl.server.api.Usage
import eu.darken.apl.server.api.UsageUpdate
import eu.darken.apl.upgrade.UpgradeRepo
import eu.darken.apl.watch.core.WatchRepo
import eu.darken.apl.watch.core.WatchRequestGate
import eu.darken.apl.watch.core.WatchSettings
import eu.darken.apl.watch.core.db.WatchDatabase
import eu.darken.apl.watch.core.db.server.WatchServerStateEntity
import eu.darken.apl.watch.core.server.WatchServerMode
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class WatchHistorySyncTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val json = ServerModule.serverJson()
    private val serverClock = ServerClock(object : MonotonicClock {
        override fun elapsed(): Long = 0L
    }).apply { noteServerTime(NOW.toEpochMilli()) }

    private lateinit var db: WatchDatabase
    private val watchRepo = mockk<WatchRepo>()
    private val aircraftRepo = mockk<AircraftRepo>()
    private val operationStore = mockk<OperationStore>(relaxed = true)
    private val accessRepo = mockk<AccessRepo>(relaxed = true)
    private val accessState = MutableStateFlow<AccessState?>(null)
    private val settings = mockk<WatchSettings>()
    private val upgradeRepo = mockk<UpgradeRepo>()
    private lateinit var sync: WatchHistorySync

    /** Each page the server is asked for, and what it answers. */
    private val sent = mutableListOf<HistoryWatchRequest>()
    private var answer: (HistoryWatchRequest) -> List<HistoryWatchOutcome> = { emptyList() }

    @Before
    fun setup() {
        context.deleteDatabase("watch")
        db = WatchDatabase(context)
        every { watchRepo.watches } returns db.watches
        every { accessRepo.state } returns accessState
        accessState.value = access(remaining = 1000)
        coEvery { operationStore.pending(any(), any()) } returns emptyList()

        val interval = mockk<DataStoreValue<Duration>>()
        every { interval.flow } returns flowOf(Duration.ofHours(1))
        every { settings.watchMonitorInterval } returns interval
        val info = mockk<UpgradeRepo.Info>()
        every { info.isSettled } returns true
        every { info.isPro } returns false
        every { upgradeRepo.upgradeInfo } returns flowOf(info)

        coEvery { aircraftRepo.historyWatches(any(), any(), any(), any()) } answers {
            val request = firstArg<HistoryWatchRequest>()
            sent.add(request)
            response(request, answer(request))
        }

        sync = WatchHistorySync(
            watchRepo = watchRepo,
            watchDb = db,
            aircraftRepo = aircraftRepo,
            operationStore = operationStore,
            accessRepo = accessRepo,
            serverClock = serverClock,
            gate = WatchRequestGate(),
            watchSettings = settings,
            upgradeRepo = upgradeRepo,
            json = json,
            serverMode = WatchServerMode(accessRepo, db),
        )
    }

    @After
    fun teardown() {
        context.deleteDatabase("watch")
    }

    private fun response(request: HistoryWatchRequest, outcomes: List<HistoryWatchOutcome>) = HistoryWatchResponse(
        operationId = request.operationId,
        serverTime = NOW.toEpochMilli(),
        from = request.from,
        until = request.until,
        outcomes = outcomes,
        usage = UsageUpdate("principal", "WATCH", RESETS_AT.toEpochMilli(), Allowance(1000, 1, 0, 999)),
    )

    private fun access(
        remaining: Int,
        tier: AccessState.Tier = AccessState.Tier.FREE,
        resetsAt: Instant = RESETS_AT,
    ) = AccessState(
        installationId = "installation",
        fetchedAt = NOW,
        tier = tier,
        restricted = false,
        allowanceScope = "principal",
        limits = TierPolicy(
            viewingIntervalSeconds = 5,
            viewingBurst = 3,
            viewingPerDay = 25_000,
            searchesPerDay = 25,
            watchesPerDay = 1000,
            maxRadiusNm = 25,
            watchTypes = if (tier == AccessState.Tier.FREE) listOf("hex", "callsign") else listOf("hex", "callsign", "squawk", "location"),
        ),
        usage = Usage(
            resetsAt = resetsAt.toEpochMilli(),
            viewing = Allowance(25_000, 0, 0, 25_000),
            search = Allowance(25, 0, 0, 25),
            watch = Allowance(1000, 1000 - remaining, 0, remaining),
        ),
        installationRequests = RequestLimits(RequestRate(1.0, 3), RequestRate(1.0, 3), RequestRate(1.0, 3), 3),
        aircraftOperations = listOf(AccessState.OPERATION_HISTORY_WATCHES),
    )

    private fun answered(
        index: Int,
        hours: List<HistoryWatchHour>,
        cursor: HistoryCursor? = null,
        lastSeenAt: Instant? = null,
    ) = HistoryWatchOutcome(
        index = index,
        status = "answered",
        evaluationBasis = "recent_messages",
        hours = hours,
        lastSeenAt = lastSeenAt?.toEpochMilli(),
        lastSeenAircraftId = lastSeenAt?.let { "3c65a3" },
        computedUntil = cursor,
    )

    private fun hour(at: Instant, count: Int, final: Boolean = true) =
        HistoryWatchHour(at.toEpochMilli(), count, "full", final)

    @Test
    fun `a new watch is paged through the whole retained week`() = runBlocking<Unit> {
        val watch = db.createAircraft("3C65A3", "")
        val cursor = HistoryCursor(hour = RETAINED_FROM.plus(Duration.ofHours(2)).toEpochMilli())
        answer = { request ->
            if (request.watches.single().cursor == null) {
                listOf(answered(0, listOf(hour(RETAINED_FROM, 1), hour(RETAINED_FROM.plusSeconds(3600), 0)), cursor))
            } else {
                listOf(answered(0, listOf(hour(CURRENT_HOUR, 0, final = false)), lastSeenAt = RETAINED_FROM.plusSeconds(600)))
            }
        }

        val result = sync.run()

        sent.size shouldBe 2
        sent.forEach {
            it.from shouldBe RETAINED_FROM.toEpochMilli()
            it.until shouldBe CURRENT_HOUR.plus(Duration.ofHours(1)).toEpochMilli()
        }
        sent[1].watches.single().cursor shouldBe cursor
        // A continuation is a new request, so it gets its own id
        (sent[0].operationId == sent[1].operationId) shouldBe false

        db.history.getHours(watch.id, RETAINED_FROM, NOW).sortedBy { it.hour }.map { it.aircraftCount } shouldBe listOf(1, 0, 0)
        val state = db.history.getSync(watch.id)!!
        state.cursorJson shouldBe null
        state.lastPassAt shouldBe NOW
        state.lastSeenAt shouldBe RETAINED_FROM.plusSeconds(600)
        result shouldBe WatchHistorySync.Result.Done(NOW.plus(Duration.ofHours(3)))
        coVerify(exactly = 2) { operationStore.complete(any()) }
    }

    @Test
    fun `a top-up asks only from the first hour that is not final`() = runBlocking<Unit> {
        val watch = db.createAircraft("3C65A3", "")
        db.applyHistory("earlier", NOW) {
            writeHours(
                listOf(
                    WatchHistoryPlan.hours(
                        watch.id,
                        answered(0, listOf(hour(RETAINED_FROM, 0), hour(RETAINED_FROM.plusSeconds(3600), 0, final = false))),
                    )
                ).flatten()
            )
        }
        answer = { listOf(answered(0, emptyList())) }

        sync.run()

        sent.single().from shouldBe RETAINED_FROM.plusSeconds(3600).toEpochMilli()
    }

    @Test
    fun `an answer already applied before a crash is not applied again`() = runBlocking<Unit> {
        val watch = db.createAircraft("3C65A3", "")
        val request = HistoryWatchRequest(OPERATION_ID, RETAINED_FROM.toEpochMilli(), CURRENT_HOUR.toEpochMilli(), emptyList())
        val stored = response(request, listOf(answered(0, listOf(hour(RETAINED_FROM, 1)))))
        db.applyHistory(OPERATION_ID, NOW) {
            writeHours(WatchHistoryPlan.hours(watch.id, stored.outcomes.single()))
        }
        coEvery { operationStore.pending(OperationStore.Kind.WATCH_HISTORY, any()) } returns listOf(
            OperationStore.PendingOperation(
                operationId = OPERATION_ID,
                kind = OperationStore.Kind.WATCH_HISTORY,
                requestJson = json.encodeToString(HistoryWatchRequest.serializer(), request),
                ownerIds = listOf(watch.id),
                createdAt = NOW,
                resultJson = json.encodeToString(HistoryWatchResponse.serializer(), stored),
                ownerRevisions = listOf(0),
            )
        ) andThen emptyList()
        coEvery { aircraftRepo.replayHistoryWatches(any(), any()) } returns stored
        answer = { listOf(answered(0, emptyList())) }

        sync.run()

        // The replay changed nothing: no sync row was written for it, the marker was found
        coVerify { operationStore.complete(OPERATION_ID) }
        db.history.getHours(watch.id, RETAINED_FROM, NOW).single().aircraftCount shouldBe 1
    }

    @Test
    fun `an answer for a watch moved while the page was out is dropped`() = runBlocking<Unit> {
        val watch = db.createLocation(50.0, 8.5, 5000f, "FRA", "")
        accessState.value = access(remaining = 1000, tier = AccessState.Tier.FEEDER)
        answer = {
            if (sent.size == 1) {
                runBlocking { db.updateLocation(watch.id, 51.0, 8.5, 5000f, "FRA") }
                listOf(answered(0, listOf(hour(RETAINED_FROM, 4))))
            } else {
                listOf(answered(0, emptyList()))
            }
        }

        sync.run()

        // The old area's hour is gone, and the new area was asked for on its own
        db.history.getHours(watch.id, RETAINED_FROM, NOW) shouldBe emptyList()
        sent.size shouldBe 2
    }

    @Test
    fun `the allowance for the day's periodic checks is not spent`() = runBlocking<Unit> {
        db.createAircraft("3C65A3", "")
        // 14 hourly checks are still due today for the one watch
        accessState.value = access(remaining = 14)

        val result = sync.run()

        sent shouldBe emptyList()
        // Just after the reset, so the access refresh it triggers has landed
        result shouldBe WatchHistorySync.Result.Done(RESETS_AT.plus(Duration.ofMinutes(1)))
    }

    @Test
    fun `a page is never bigger than what can be spent`() = runBlocking<Unit> {
        repeat(4) { db.createAircraft("3C65A$it", "") }
        // 4 watches x 14 checks reserved, 2 left over
        accessState.value = access(remaining = 58)
        answer = { request -> request.watches.indices.map { answered(it, emptyList()) } }

        sync.run()

        sent.first().watches.size shouldBe 2
    }

    @Test
    fun `watch types the tier does not offer are not asked for`() = runBlocking<Unit> {
        db.createLocation(50.0, 8.5, 5000f, "FRA", "")
        db.createAircraft("3C65A3", "")
        answer = { listOf(answered(0, emptyList())) }

        sync.run()

        sent.single().watches.single().type shouldBe "hex"
    }

    @Test
    fun `a page the server had no budget for waits`() = runBlocking<Unit> {
        val watch = db.createAircraft("3C65A3", "")
        answer = { listOf(HistoryWatchOutcome(0, "error", error = QueryError("budget_exhausted"))) }

        val result = sync.run()

        sent.size shouldBe 1
        db.history.getSync(watch.id)!!.nextEligibleAt shouldBe NOW.plus(Duration.ofMinutes(15))
        result shouldBe WatchHistorySync.Result.Done(NOW.plus(Duration.ofMinutes(15)))
    }

    @Test
    fun `nothing is asked without the server offering watch history`() = runBlocking<Unit> {
        db.createAircraft("3C65A3", "")
        accessState.value = access(remaining = 1000).copy(aircraftOperations = listOf("watches"))

        val result = sync.run()

        sent shouldBe emptyList()
        result shouldBe WatchHistorySync.Result.Done(null)
    }

    @Test
    fun `counters from a period that already ended are refreshed instead of rerun at once`() = runBlocking<Unit> {
        db.createAircraft("3C65A3", "")
        accessState.value = access(remaining = 0, resetsAt = NOW.minusSeconds(60))

        val result = sync.run()

        sent shouldBe emptyList()
        coVerify { accessRepo.refreshThrottled(any()) }
        result shouldBe WatchHistorySync.Result.Done(NOW.plus(Duration.ofMinutes(1)))
    }

    @Test
    fun `a page refused as a whole starts its watches over later and lets the others go on`() = runBlocking<Unit> {
        val stuck = db.createAircraft("3C65A3", "")
        db.applyHistory("earlier", NOW) {
            writeSync(
                eu.darken.apl.watch.core.db.history.WatchHistorySyncEntity(
                    watchId = stuck.id,
                    definitionRevision = 0,
                    windowFrom = RETAINED_FROM.minus(Duration.ofDays(2)),
                    windowUntil = RETAINED_FROM.minus(Duration.ofDays(1)),
                    cursorJson = json.encodeToString(HistoryCursor.serializer(), HistoryCursor(hour = 0)),
                    nextEligibleAt = NOW,
                    updatedAt = Instant.EPOCH,
                )
            )
        }
        val other = db.createAircraft("4B1805", "")
        coEvery { aircraftRepo.historyWatches(any(), any(), any(), any()) } answers {
            val request = firstArg<HistoryWatchRequest>()
            sent.add(request)
            if (request.watches.single().cursor != null) throw ServerApiException(ServerCodes.INVALID_REQUEST, 400)
            response(request, listOf(answered(0, emptyList())))
        }

        sync.run()

        db.history.getSync(stuck.id)!!.apply {
            cursorJson shouldBe null
            windowFrom shouldBe null
            nextEligibleAt shouldBe NOW.plus(Duration.ofHours(3))
        }
        coVerify { operationStore.complete(sent.first().operationId) }
        db.history.getSync(other.id)!!.lastPassAt shouldBe NOW
    }

    @Test
    fun `an error that is not the batch's fault is left to the worker`() = runBlocking<Unit> {
        val watch = db.createAircraft("3C65A3", "")
        coEvery { aircraftRepo.historyWatches(any(), any(), any(), any()) } throws
                ServerApiException(ServerCodes.INSTALLATION_RESTRICTED, 403)

        shouldThrow<ServerApiException> { sync.run() }

        db.history.getSync(watch.id) shouldBe null
    }

    @Test
    fun `an id the server cannot use keeps the cursor and waits`() = runBlocking<Unit> {
        val watch = db.createAircraft("3C65A3", "")
        coEvery { aircraftRepo.historyWatches(any(), any(), any(), any()) } throws
                OperationFailedException(ServerCodes.OPERATION_EXPIRED)

        val result = sync.run()

        db.history.getSync(watch.id)!!.nextEligibleAt shouldBe NOW.plus(Duration.ofMinutes(15))
        result shouldBe WatchHistorySync.Result.Done(NOW.plus(Duration.ofMinutes(15)))
    }

    @Test
    fun `a recovered refusal does not block the watch under the current tier`() = runBlocking<Unit> {
        val watch = db.createAircraft("3C65A3", "")
        val request = HistoryWatchRequest(OPERATION_ID, RETAINED_FROM.toEpochMilli(), CURRENT_HOUR.toEpochMilli(), emptyList())
        val stored = response(request, listOf(HistoryWatchOutcome(0, "error", error = QueryError("tier_restricted"))))
        coEvery { operationStore.pending(OperationStore.Kind.WATCH_HISTORY, any()) } returns listOf(
            OperationStore.PendingOperation(
                operationId = OPERATION_ID,
                kind = OperationStore.Kind.WATCH_HISTORY,
                requestJson = json.encodeToString(HistoryWatchRequest.serializer(), request),
                ownerIds = listOf(watch.id),
                createdAt = NOW,
                resultJson = json.encodeToString(HistoryWatchResponse.serializer(), stored),
                ownerRevisions = listOf(0),
            )
        ) andThen emptyList()
        coEvery { aircraftRepo.replayHistoryWatches(any(), any()) } returns stored

        sync.run()

        db.history.getSync(watch.id)!!.apply {
            restrictedTier shouldBe null
            nextEligibleAt shouldBe NOW.plus(Duration.ofHours(3))
        }
        sent shouldBe emptyList()
    }

    @Test
    fun `a fresh refusal waits for a tier change`() = runBlocking<Unit> {
        val watch = db.createAircraft("3C65A3", "")
        answer = { listOf(HistoryWatchOutcome(0, "error", error = QueryError("tier_restricted"))) }

        val result = sync.run()

        db.history.getSync(watch.id)!!.restrictedTier shouldBe "FREE"
        result shouldBe WatchHistorySync.Result.Done(null)
    }

    @Test
    fun `a finalized page is not sent again`() = runBlocking<Unit> {
        val watch = db.createAircraft("3C65A3", "")
        db.applyHistory(OPERATION_ID, NOW) {}
        val request = HistoryWatchRequest(OPERATION_ID, RETAINED_FROM.toEpochMilli(), CURRENT_HOUR.toEpochMilli(), emptyList())
        coEvery { operationStore.pending(OperationStore.Kind.WATCH_HISTORY, any()) } returns listOf(
            OperationStore.PendingOperation(
                operationId = OPERATION_ID,
                kind = OperationStore.Kind.WATCH_HISTORY,
                requestJson = json.encodeToString(HistoryWatchRequest.serializer(), request),
                ownerIds = listOf(watch.id),
                createdAt = NOW,
                resultJson = null,
                ownerRevisions = listOf(0),
            )
        ) andThen emptyList()
        answer = { listOf(answered(0, emptyList())) }

        sync.run()

        coVerify(exactly = 0) { aircraftRepo.replayHistoryWatches(any(), any()) }
        coVerify { operationStore.complete(OPERATION_ID) }
    }

    @Test
    fun diagF2PendingHistoryOfServerHeldWatchIsNotReplayed() = runBlocking<Unit> {
        accessState.value = access(remaining = 1000).copy(
            aircraftOperations = listOf(AccessState.OPERATION_HISTORY_WATCHES, AccessState.OPERATION_WATCHLIST),
        )
        val held = db.createAircraft("3C65A3", "")
        db.server.upsertState(
            WatchServerStateEntity(
                watchId = held.id,
                installationId = "installation",
                serverWatchId = "server-1",
                uploadState = WatchServerStateEntity.UPLOAD_SYNCED,
                updatedAt = NOW,
            )
        )
        val request = HistoryWatchRequest(OPERATION_ID, RETAINED_FROM.toEpochMilli(), CURRENT_HOUR.toEpochMilli(), emptyList())
        coEvery { operationStore.pending(OperationStore.Kind.WATCH_HISTORY, any()) } returns listOf(
            OperationStore.PendingOperation(
                operationId = OPERATION_ID,
                kind = OperationStore.Kind.WATCH_HISTORY,
                requestJson = json.encodeToString(HistoryWatchRequest.serializer(), request),
                ownerIds = listOf(held.id),
                createdAt = NOW,
                resultJson = null,
                ownerRevisions = listOf(0),
            )
        ) andThen emptyList()
        coEvery { aircraftRepo.replayHistoryWatches(any(), any()) } returns
                response(request, listOf(answered(0, listOf(hour(RETAINED_FROM, 3)))))

        sync.run()

        coVerify(exactly = 0) { aircraftRepo.replayHistoryWatches(any(), any()) }
        db.history.getHours(held.id, RETAINED_FROM, NOW) shouldBe emptyList()
        coVerify { operationStore.complete(OPERATION_ID) }
    }

    @Test
    fun diagF2bOwnerHandedToServerDuringReplayGetsNoHistory() = runBlocking<Unit> {
        accessState.value = access(remaining = 1000).copy(
            aircraftOperations = listOf(AccessState.OPERATION_HISTORY_WATCHES, AccessState.OPERATION_WATCHLIST),
        )
        val watch = db.createAircraft("3C65A3", "")
        val request = HistoryWatchRequest(OPERATION_ID, RETAINED_FROM.toEpochMilli(), CURRENT_HOUR.toEpochMilli(), emptyList())
        coEvery { operationStore.pending(OperationStore.Kind.WATCH_HISTORY, any()) } returns listOf(
            OperationStore.PendingOperation(
                operationId = OPERATION_ID,
                kind = OperationStore.Kind.WATCH_HISTORY,
                requestJson = json.encodeToString(HistoryWatchRequest.serializer(), request),
                ownerIds = listOf(watch.id),
                createdAt = NOW,
                resultJson = null,
                ownerRevisions = listOf(0),
            )
        ) andThen emptyList()
        coEvery { aircraftRepo.replayHistoryWatches(any(), any()) } coAnswers {
            // A sync of another trigger hands the watch to the server while the replay is out
            db.server.upsertState(
                WatchServerStateEntity(
                    watchId = watch.id,
                    installationId = "installation",
                    serverWatchId = "server-1",
                    uploadState = WatchServerStateEntity.UPLOAD_SYNCED,
                    updatedAt = NOW,
                )
            )
            response(request, listOf(answered(0, listOf(hour(RETAINED_FROM, 3)))))
        }

        sync.run()

        coVerify(exactly = 1) { aircraftRepo.replayHistoryWatches(any(), any()) }
        db.history.getHours(watch.id, RETAINED_FROM, NOW) shouldBe emptyList()
        db.history.getSync(watch.id) shouldBe null
        coVerify { operationStore.complete(OPERATION_ID) }
    }

    @Test
    fun `a stored cursor that does not read starts a fresh range`() = runBlocking<Unit> {
        val watch = db.createAircraft("3C65A3", "")
        db.applyHistory("earlier", NOW) {
            writeSync(
                eu.darken.apl.watch.core.db.history.WatchHistorySyncEntity(
                    watchId = watch.id,
                    definitionRevision = 0,
                    windowFrom = CURRENT_HOUR,
                    windowUntil = CURRENT_HOUR.plus(Duration.ofHours(1)),
                    cursorJson = "not a cursor",
                    nextEligibleAt = NOW,
                    updatedAt = NOW,
                )
            )
        }
        answer = { listOf(answered(0, emptyList())) }

        sync.run()

        sent.single().apply {
            from shouldBe RETAINED_FROM.toEpochMilli()
            watches.single().cursor shouldBe null
        }
    }

    @Test
    fun `a page cut short for a live check stays pending`() = runBlocking<Unit> {
        db.createAircraft("3C65A3", "")
        coEvery { aircraftRepo.historyWatches(any(), any(), any(), any()) } throws OperationYieldedException()

        val result = sync.run()

        result shouldBe WatchHistorySync.Result.Yielded
        coVerify(exactly = 0) { operationStore.complete(any()) }
    }

    @Test
    fun `with the server's watch list, watches it holds are not paged`() = runBlocking<Unit> {
        accessState.value = access(remaining = 1000).copy(
            aircraftOperations = listOf(AccessState.OPERATION_HISTORY_WATCHES, AccessState.OPERATION_WATCHLIST),
        )
        val held = db.createAircraft("3C65A3", "")
        db.createAircraft("3C65A5", "")
        db.server.upsertState(
            WatchServerStateEntity(
                watchId = held.id,
                installationId = "installation",
                serverWatchId = "server-1",
                uploadState = WatchServerStateEntity.UPLOAD_SYNCED,
                updatedAt = NOW,
            )
        )
        answer = { request -> request.watches.indices.map { answered(it, emptyList()) } }

        sync.run()

        sent.flatMap { request -> request.watches.map { it.value } }.toSet() shouldBe setOf("3C65A5")
    }

    @Test
    fun `without the server's watch list, held watches are paged as before`() = runBlocking<Unit> {
        val held = db.createAircraft("3C65A3", "")
        db.createAircraft("3C65A4", "")
        db.server.upsertState(
            WatchServerStateEntity(
                watchId = held.id,
                installationId = "installation",
                serverWatchId = "server-1",
                uploadState = WatchServerStateEntity.UPLOAD_SYNCED,
                updatedAt = NOW,
            )
        )
        answer = { request -> request.watches.indices.map { answered(it, emptyList()) } }

        sync.run()

        sent.flatMap { request -> request.watches.map { it.value } }.toSet() shouldBe setOf("3C65A3", "3C65A4")
    }

    companion object {
        private val NOW = Instant.parse("2026-10-13T10:25:00Z")
        private val CURRENT_HOUR = Instant.parse("2026-10-13T10:00:00Z")
        private val RETAINED_FROM = Instant.parse("2026-10-06T10:00:00Z")
        private val RESETS_AT = Instant.parse("2026-10-14T00:00:00Z")
        private const val OPERATION_ID = "5a5c6b2e-3b0a-4a2e-9a0f-000000000009"
    }
}
