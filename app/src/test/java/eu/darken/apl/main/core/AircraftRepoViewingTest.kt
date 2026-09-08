package eu.darken.apl.main.core

import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.db.AircraftDatabase
import eu.darken.apl.main.core.request.OperationRunner
import eu.darken.apl.main.core.request.OperationStore
import eu.darken.apl.main.core.request.RequestCoordinator
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.ServerModule
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.server.api.AircraftObservation
import eu.darken.apl.server.api.AircraftPosition
import eu.darken.apl.server.api.Allowance
import eu.darken.apl.server.api.QueryMetadata
import eu.darken.apl.server.api.RequestLimits
import eu.darken.apl.server.api.RequestRate
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.ServerEndpoint
import eu.darken.apl.server.api.TierPolicy
import eu.darken.apl.server.api.Usage
import eu.darken.apl.server.api.UsageUpdate
import eu.darken.apl.server.api.ViewingResponse
import eu.darken.apl.server.session.SessionManager
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import testhelper.coroutine.TestDispatcherProvider
import java.time.Instant

/**
 * The loop is driven on virtual time, so the endpoint and the cache are stubbed: a socket or a Room
 * executor completes outside the test scheduler, which would resume the loop only after the test
 * stopped advancing time.
 */
@RunWith(RobolectricTestRunner::class)
class AircraftRepoViewingTest {

    private val database = mockk<AircraftDatabase>(relaxed = true)
    private val written = mutableListOf<Aircraft>()

    private val endpoint = mockk<ServerEndpoint>()
    private val sessionManager = mockk<SessionManager>()
    private val accessRepo = mockk<AccessRepo>(relaxed = true)
    private val accessState = MutableStateFlow<AccessState?>(null)

    private var virtualTime: () -> Long = { 0L }
    private val monotonicClock = object : MonotonicClock {
        override fun elapsed(): Long = virtualTime()
    }
    private val serverClock = ServerClock(monotonicClock)

    private var requestCount = 0

    @Before
    fun setup() {
        requestCount = 0
        written.clear()
        coEvery { database.update(any()) } coAnswers { written.addAll(firstArg<Collection<Aircraft>>()) }
        every { accessRepo.state } returns accessState
        accessState.value = freePolicy()
        coEvery { sessionManager.authed<Any>(any()) } coAnswers {
            firstArg<suspend (String) -> Any>().invoke("token")
        }
    }

    private fun answerWithSnapshot() {
        coEvery { endpoint.ar(any(), any()) } coAnswers {
            requestCount++
            viewingResponse()
        }
    }

    private fun answerWithExhausted() {
        coEvery { endpoint.ar(any(), any()) } coAnswers {
            requestCount++
            throw ServerApiException(code = "daily_allowance_exhausted", status = 429, retryAfterSeconds = 60)
        }
    }

    private fun TestScope.createRepo(): AircraftRepo {
        virtualTime = { testScheduler.currentTime }
        val json = ServerModule.serverJson()
        val store = OperationStore(database, TestDispatcherProvider(), json)
        return AircraftRepo(
            appScope = CoroutineScope(Dispatchers.Unconfined),
            aircraftDatabase = database,
            endpoint = endpoint,
            sessionManager = sessionManager,
            accessRepo = accessRepo,
            requestCoordinator = RequestCoordinator(accessRepo, monotonicClock),
            operationRunner = OperationRunner(store, serverClock),
            operationStore = store,
            serverClock = serverClock,
            json = json,
        )
    }

    private fun freePolicy(resetsAt: Long = RESETS_AT) = AccessState(
        installationId = "installation",
        fetchedAt = Instant.EPOCH,
        tier = AccessState.Tier.FREE,
        restricted = false,
        allowanceScope = "principal",
        limits = TierPolicy(
            viewingIntervalSeconds = 5,
            viewingBurst = 3,
            viewingPerDay = 25000,
            searchesPerDay = 25,
            watchesPerDay = 1000,
            maxRadiusNm = 25,
            watchTypes = listOf("hex", "callsign"),
        ),
        usage = Usage(
            resetsAt = resetsAt,
            viewing = Allowance(25000, 0, 0, 25000),
            search = Allowance(25, 0, 0, 25),
            watch = Allowance(1000, 0, 0, 1000),
        ),
        installationRequests = RequestLimits(
            viewing = RequestRate(perSecond = 1000.0, burst = 1000),
            search = RequestRate(perSecond = 1000.0, burst = 1000),
            watch = RequestRate(perSecond = 1000.0, burst = 1000),
            concurrency = 3,
        ),
    )

    private fun arQuery() = flowOf(AircraftRepo.ViewingQuery.Ar(50.03, 8.57, 25.0))

    private fun viewingResponse() = ViewingResponse(
        serverTime = SERVER_TIME,
        metadata = QueryMetadata(
            sourceTime = SERVER_TIME,
            fetchedAt = SERVER_TIME,
            expiresAt = SERVER_TIME + 120_000,
            complete = true,
            stale = false,
        ),
        aircraft = listOf(
            AircraftObservation(
                id = "3c65a3",
                messageObservedAt = SERVER_TIME - 500,
                position = AircraftPosition(50.03, 8.57, SERVER_TIME - 500),
                callsign = "DLH453",
            )
        ),
        totalMatching = 1,
        capped = false,
        usage = UsageUpdate(
            scope = "principal",
            bucket = "VIEWING",
            resetsAt = RESETS_AT,
            allowance = Allowance(25000, 1, 0, 24999),
        ),
    )

    @Test
    fun `polling follows the viewing interval of the policy`() {
        runTest {
            val repo = createRepo()
            answerWithSnapshot()

            val emittedAt = mutableListOf<Long>()
            val states = mutableListOf<AircraftRepo.ViewingState>()
            val job = launch {
                repo.viewing(arQuery()).collect {
                    states.add(it)
                    emittedAt.add(testScheduler.currentTime)
                }
            }

            advanceTimeBy(11_000)
            job.cancel()

            emittedAt.take(3) shouldBe listOf(0L, 5_000L, 10_000L)
            states.first().shouldBeInstanceOf<AircraftRepo.ViewingState.Snapshot>()
        }
    }

    @Test
    fun `observations reach the cache stamped with the server time`() {
        runTest {
            val repo = createRepo()
            answerWithSnapshot()

            val job = launch { repo.viewing(arQuery()).collect { } }
            advanceTimeBy(1_000)
            job.cancel()

            written.first().apply {
                hex shouldBe "3C65A3"
                callsign shouldBe "DLH453"
                fetchedAt shouldBe Instant.ofEpochMilli(SERVER_TIME)
                messageSeenAt shouldBe Instant.ofEpochMilli(SERVER_TIME - 500)
            }
        }
    }

    @Test
    fun `a second viewing collector stops the first loop`() {
        runTest {
            val repo = createRepo()
            answerWithSnapshot()

            val firstStates = mutableListOf<AircraftRepo.ViewingState>()
            val first = launch { repo.viewing(arQuery()).collect { firstStates.add(it) } }
            advanceTimeBy(6_000)
            val firstCountBefore = firstStates.size

            val secondStates = mutableListOf<AircraftRepo.ViewingState>()
            val second = launch { repo.viewing(arQuery()).collect { secondStates.add(it) } }
            advanceTimeBy(11_000)

            firstStates.size shouldBe firstCountBefore
            (secondStates.size >= 2) shouldBe true

            first.cancel()
            second.cancel()
        }
    }

    @Test
    fun `an exhausted allowance stops requests until it resets`() {
        runTest {
            val repo = createRepo()
            answerWithExhausted()

            val states = mutableListOf<AircraftRepo.ViewingState>()
            val job = launch { repo.viewing(arQuery()).collect { states.add(it) } }
            advanceTimeBy(60_000)

            requestCount shouldBe 1
            states.last() shouldBe AircraftRepo.ViewingState.Waiting(
                AircraftRepo.ViewingState.Reason.Exhausted(Instant.ofEpochMilli(RESETS_AT))
            )

            answerWithSnapshot()
            accessState.value = freePolicy(resetsAt = RESETS_AT + 86_400_000)
            advanceTimeBy(1_000)
            job.cancel()

            requestCount shouldBe 2
        }
    }

    @Test
    fun `a takeover during an exhaustion wait starts the new loop`() {
        runTest {
            val repo = createRepo()
            answerWithExhausted()

            val first = launch { repo.viewing(arQuery()).collect { } }
            advanceTimeBy(1_000)
            requestCount shouldBe 1

            answerWithSnapshot()
            val secondStates = mutableListOf<AircraftRepo.ViewingState>()
            // The rejection put the viewing bucket on hold for the retry hint it carried
            val second = launch { repo.viewing(arQuery()).collect { secondStates.add(it) } }
            advanceTimeBy(61_000)
            first.cancel()
            second.cancel()

            (requestCount >= 2) shouldBe true
            secondStates.last().shouldBeInstanceOf<AircraftRepo.ViewingState.Snapshot>()
        }
    }

    companion object {
        private const val SERVER_TIME = 1_710_000_000_000L
        private const val RESETS_AT = 1_710_028_800_000L
    }
}
