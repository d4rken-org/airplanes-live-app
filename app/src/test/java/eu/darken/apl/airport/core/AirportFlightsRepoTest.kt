package eu.darken.apl.airport.core

import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.request.OperationStore
import eu.darken.apl.main.core.request.RequestCoordinator
import eu.darken.apl.server.ServerModule
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.server.api.AirportFlightEntryDto
import eu.darken.apl.server.api.AirportFlightsPageDto
import eu.darken.apl.server.api.AirportFlightsRequest
import eu.darken.apl.server.api.AirportFlightsResponse
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.ServerCodes
import eu.darken.apl.server.api.ServerEndpoint
import eu.darken.apl.server.session.SessionManager
import eu.darken.apl.server.session.SessionRevokedException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelper.json.readTestResource
import java.io.IOException
import java.time.Instant

/** Robolectric because caching the answer's aircraft builds `android.location.Location`s. */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class AirportFlightsRepoTest {

    private val json = ServerModule.serverJson()

    private val endpoint = mockk<ServerEndpoint>()
    private val sessionManager = mockk<SessionManager>()
    private val accessRepo = mockk<AccessRepo>(relaxed = true)
    private val aircraftRepo = mockk<AircraftRepo>()

    private var virtualTime: () -> Long = { 0L }
    private val clock = object : MonotonicClock {
        override fun elapsed(): Long = virtualTime()
    }

    private val written = mutableListOf<Aircraft>()

    /** Virtual send time and request of every call that reached the endpoint. */
    private val sent = mutableListOf<Pair<Long, AirportFlightsRequest>>()
    private val answers = ArrayDeque<() -> AirportFlightsResponse>()

    private val replayWindowMs = OperationStore.REPLAY_WINDOW.toMillis()

    @Before
    fun setup() {
        every { accessRepo.state } returns MutableStateFlow<AccessState?>(null)
        coEvery { aircraftRepo.update(any()) } coAnswers { written.addAll(firstArg<Collection<Aircraft>>()) }
        coEvery { sessionManager.authed<Any?>(any()) } coAnswers {
            firstArg<suspend (String) -> Any?>().invoke("token")
        }
        coEvery { endpoint.airportFlights(any(), any()) } coAnswers {
            sent += virtualTime() to secondArg<AirportFlightsRequest>()
            answers.removeFirst().invoke()
        }
    }

    private fun TestScope.createRepo(): AirportFlightsRepo {
        virtualTime = { testScheduler.currentTime }
        return AirportFlightsRepo(
            endpoint = endpoint,
            sessionManager = sessionManager,
            requestCoordinator = RequestCoordinator(accessRepo, clock),
            accessRepo = accessRepo,
            aircraftRepo = aircraftRepo,
            clock = clock,
        )
    }

    private fun fixture(name: String): AirportFlightsResponse =
        json.decodeFromString(AirportFlightsResponse.serializer(), readTestResource("airport-flights/$name"))

    private fun answer(name: String) {
        answers.addLast { fixture(name) }
    }

    private fun fail(error: Exception) {
        answers.addLast { throw error }
    }

    @Test
    fun `an answered load caches its aircraft at the evaluation time and applies usage and pass`() = runTest {
        val repo = createRepo()
        val response = fixture("answered-load.json")
        answer("answered-load.json")
        val operation = repo.newLoad("oa:2212")

        repo.send(operation) shouldBe AirportFlightsResult.Answered(response)

        sent.single().second shouldBe AirportFlightsRequest(operationId = operation.operationId, airportId = "oa:2212")
        written.shouldNotBeEmpty()
        written.map { it.hex } shouldBe response.aircraft.map { it.id.uppercase() }
        written.forEach { it.fetchedAt shouldBe Instant.ofEpochMilli(1_794_571_200_000L) }
        verify(exactly = 1) { accessRepo.applyUsage(response.usage) }
        repo.pass.value shouldBe AirportFlightsPass(
            scope = "s-3",
            revision = 7,
            airportId = "oa:2212",
            validUntil = 1_794_573_000_000L,
        )
    }

    @Test
    fun `a page sends only the page under a new id`() = runTest {
        val repo = createRepo()
        answer("answered-page.json")
        val operation = repo.newPage("ev-9", AirportFlightEntryDto.DIRECTION_DEPARTURE, "c-dep-2")

        repo.send(operation).shouldBeInstanceOf<AirportFlightsResult.Answered>()

        sent.single().second shouldBe AirportFlightsRequest(
            operationId = operation.operationId,
            page = AirportFlightsPageDto(
                evaluationId = "ev-9",
                direction = AirportFlightEntryDto.DIRECTION_DEPARTURE,
                cursor = "c-dep-2",
            ),
        )
        repo.newLoad("oa:2212").operationId shouldNotBe repo.newLoad("oa:2212").operationId
    }

    @Test
    fun `a transport failure resends the identical request under the same id`() = runTest {
        val repo = createRepo()
        fail(IOException("offline"))
        answer("answered-load.json")
        val operation = repo.newLoad("oa:2212")

        repo.send(operation).shouldBeInstanceOf<AirportFlightsResult.Answered>()

        sent shouldHaveSize 2
        sent[0].second shouldBe operation.request
        sent[1].second shouldBe operation.request
        sent[1].first - sent[0].first shouldBe 1_000L
    }

    @Test
    fun `a retryable server error resends the identical request`() = runTest {
        val repo = createRepo()
        fail(ServerApiException(code = ServerCodes.OPERATION_IN_PROGRESS, status = 409, retryAfterSeconds = 1))
        answer("answered-load.json")
        val operation = repo.newLoad("oa:2212")

        repo.send(operation).shouldBeInstanceOf<AirportFlightsResult.Answered>()

        sent.map { it.second } shouldBe listOf(operation.request, operation.request)
    }

    @Test
    fun `transport failures retry until the deadline and then expire without another send`() = runTest {
        val repo = createRepo()
        coEvery { endpoint.airportFlights(any(), any()) } coAnswers {
            sent += virtualTime() to secondArg<AirportFlightsRequest>()
            throw IOException("offline")
        }
        val operation = repo.newLoad("oa:2212")

        repo.send(operation) shouldBe AirportFlightsResult.Expired

        sent.size shouldBeGreaterThan 1
        sent.forEach {
            it.second shouldBe operation.request
            it.first shouldBeLessThan replayWindowMs
        }
    }

    @Test
    fun `an operation first sent more than the replay window ago is not sent again`() = runTest {
        val repo = createRepo()
        fail(ServerApiException(code = ServerCodes.INTERNAL, status = 500))
        val operation = repo.newLoad("oa:2212")
        repo.send(operation).shouldBeInstanceOf<AirportFlightsResult.Failed>()
        sent shouldHaveSize 1

        delay(replayWindowMs + 1)
        answer("answered-load.json")
        repo.send(operation) shouldBe AirportFlightsResult.Expired

        sent shouldHaveSize 1
    }

    @Test
    fun `a block run again by the session after the deadline does not send`() = runTest {
        val repo = createRepo()
        coEvery { sessionManager.authed<Any?>(any()) } coAnswers {
            val block = firstArg<suspend (String) -> Any?>()
            try {
                block("token")
            } catch (e: ServerApiException) {
                if (e.code != ServerCodes.EXPIRED) throw e
                delay(replayWindowMs + 1)
                block("token-2")
            }
        }
        fail(ServerApiException(code = ServerCodes.EXPIRED, status = 401))
        answer("answered-load.json")

        repo.send(repo.newLoad("oa:2212")) shouldBe AirportFlightsResult.Expired

        sent shouldHaveSize 1
    }

    @Test
    fun `flights unavailable waits the full retry delay and resends the same operation`() = runTest {
        val repo = createRepo()
        fail(ServerApiException(code = ServerCodes.FLIGHTS_UNAVAILABLE, status = 503, retryAfterSeconds = 120))
        answer("answered-load.json")
        val operation = repo.newLoad("oa:2212")
        val waits = mutableListOf<Long>()

        repo.send(operation) { waits += it }.shouldBeInstanceOf<AirportFlightsResult.Answered>()

        waits shouldBe listOf(120_000L)
        sent.map { it.first } shouldBe listOf(0L, 120_000L)
        sent.map { it.second } shouldBe listOf(operation.request, operation.request)
    }

    @Test
    fun `a hidden wait for flights data keeps the server's retry time`() = runTest {
        val repo = createRepo()
        fail(ServerApiException(code = ServerCodes.FLIGHTS_UNAVAILABLE, status = 503, retryAfterSeconds = 120))
        answer("answered-load.json")
        val operation = repo.newLoad("oa:2212")

        val hidden = launch { repo.send(operation) }
        advanceTimeBy(30_000L)
        runCurrent()
        hidden.cancel()
        sent shouldHaveSize 1

        val shown = async { repo.send(operation) }
        shown.await()

        sent.map { it.first } shouldBe listOf(0L, 120_000L)
        sent.map { it.second } shouldBe listOf(operation.request, operation.request)
    }

    @Test
    fun `flights unavailable past the deadline returns instead of waiting`() = runTest {
        val repo = createRepo()
        fail(ServerApiException(code = ServerCodes.FLIGHTS_UNAVAILABLE, status = 503, retryAfterSeconds = 400))
        val waits = mutableListOf<Long>()

        repo.send(repo.newLoad("oa:2212")) { waits += it } shouldBe AirportFlightsResult.FlightsUnavailable(400_000L)

        waits.shouldBeEmpty()
        sent shouldHaveSize 1
        testScheduler.currentTime shouldBe 0L
    }

    @Test
    fun `refresh too soon cools down only that airport`() = runTest {
        val repo = createRepo()
        fail(ServerApiException(code = ServerCodes.REFRESH_TOO_SOON, status = 429, retryAfterSeconds = 60))

        repo.send(repo.newLoad("oa:A")) shouldBe AirportFlightsResult.CoolingDown(60_000L)
        repo.cooldownUntil("oa:A") shouldBe 60_000L

        repo.send(repo.newLoad("oa:A")) shouldBe AirportFlightsResult.CoolingDown(60_000L)
        sent shouldHaveSize 1

        answer("answered-load.json")
        repo.send(repo.newLoad("oa:B")).shouldBeInstanceOf<AirportFlightsResult.Answered>()
        sent shouldHaveSize 2
        sent[1].first shouldBe 0L

        delay(60_000L)
        repo.cooldownUntil("oa:A").shouldBeNull()
        answer("answered-load.json")
        repo.send(repo.newLoad("oa:A")).shouldBeInstanceOf<AirportFlightsResult.Answered>()
        sent shouldHaveSize 3
    }

    @Test
    fun `terminal refusals map to their result after one request`() = runTest {
        listOf(
            ServerApiException(code = "http_404", status = 404) to AirportFlightsResult.NotOffered,
            ServerApiException(code = ServerCodes.UNKNOWN_AIRPORT, status = 400) to AirportFlightsResult.UnknownAirport,
            ServerApiException(code = ServerCodes.APP_UPDATE_REQUIRED, status = 403) to
                    AirportFlightsResult.UpdateRequired,
            ServerApiException(code = ServerCodes.INSTALLATION_RESTRICTED, status = 403) to
                    AirportFlightsResult.Restricted,
            ServerApiException(code = ServerCodes.INSTALLATION_REVOKED, status = 403) to AirportFlightsResult.Revoked,
        ).forEach { (error, expected) ->
            withClue(error.code) {
                sent.clear()
                fail(error)
                val repo = createRepo()
                repo.send(repo.newLoad("oa:2212")) shouldBe expected
                sent shouldHaveSize 1
            }
        }
    }

    @Test
    fun `other refusals fail after one request`() = runTest {
        listOf(
            ServerApiException(code = ServerCodes.OPERATION_MISMATCH, status = 409),
            ServerApiException(code = ServerCodes.INVALID_REQUEST, status = 400),
            ServerApiException(code = "http_418", status = 418),
            ServerApiException(code = ServerCodes.INTERNAL, status = 500),
            IllegalStateException("broken"),
        ).forEach { error ->
            withClue(error.toString()) {
                sent.clear()
                fail(error)
                val repo = createRepo()
                val result = repo.send(repo.newLoad("oa:2212"))
                result.shouldBeInstanceOf<AirportFlightsResult.Failed>()
                result.error shouldBeSameInstanceAs error
                sent shouldHaveSize 1
            }
        }
    }

    @Test
    fun `a revoked session is revoked without a request`() = runTest {
        coEvery { sessionManager.authed<Any?>(any()) } throws SessionRevokedException()
        val repo = createRepo()

        repo.send(repo.newLoad("oa:2212")) shouldBe AirportFlightsResult.Revoked

        sent.shouldBeEmpty()
    }

    @Test
    fun `cancellation is rethrown`() = runTest {
        val repo = createRepo()
        fail(CancellationException("sheet hidden"))

        shouldThrow<CancellationException> { repo.send(repo.newLoad("oa:2212")) }
    }

    @Test
    fun `outcomes without a list map to their result and still apply usage`() = runTest {
        mapOf(
            "outcome-allowance-exhausted.json" to AirportFlightsResult.AllowanceExhausted,
            "outcome-pass-required.json" to
                    AirportFlightsResult.ReloadNeeded(AirportFlightsResponse.OUTCOME_PASS_REQUIRED),
            "outcome-evaluation-expired.json" to
                    AirportFlightsResult.ReloadNeeded(AirportFlightsResponse.OUTCOME_EVALUATION_EXPIRED),
            "outcome-result-expired.json" to
                    AirportFlightsResult.ReloadNeeded(AirportFlightsResponse.OUTCOME_RESULT_EXPIRED),
            "outcome-access-changed.json" to
                    AirportFlightsResult.ReloadNeeded(AirportFlightsResponse.OUTCOME_ACCESS_CHANGED),
            "outcome-unknown.json" to AirportFlightsResult.UnknownOutcome("airport_closed"),
        ).forEach { (name, expected) ->
            withClue(name) {
                answer(name)
                val repo = createRepo()
                repo.send(repo.newLoad("oa:2212")) shouldBe expected
                verify { accessRepo.applyUsage(fixture(name).usage) }
            }
        }
        written.shouldBeEmpty()
    }

    @Test
    fun `a lower revision in the same scope is ignored`() = runTest {
        val repo = createRepo()
        answer("answered-load.json")
        answer("answered-replayed.json")

        repo.send(repo.newLoad("oa:2212"))
        repo.send(repo.newLoad("oa:2212"))

        repo.pass.value shouldBe AirportFlightsPass(
            scope = "s-3",
            revision = 7,
            airportId = "oa:2212",
            validUntil = 1_794_573_000_000L,
        )
    }

    @Test
    fun `a new scope is taken even with a lower revision`() = runTest {
        val repo = createRepo()
        answer("answered-load.json")
        answer("outcome-access-changed.json")

        repo.send(repo.newLoad("oa:2212"))
        repo.send(repo.newLoad("oa:2212"))

        repo.pass.value shouldBe AirportFlightsPass(scope = "s-4", revision = 1, airportId = null, validUntil = null)
    }

    @Test
    fun `an answer without a pass airport clears it and keeps scope and revision`() = runTest {
        val repo = createRepo()
        answer("answered-load.json")
        answer("outcome-allowance-exhausted.json")

        repo.send(repo.newLoad("oa:2212"))
        repo.send(repo.newLoad("oa:2212"))

        repo.pass.value shouldBe AirportFlightsPass(scope = "s-3", revision = 11, airportId = null, validUntil = null)
    }
}
