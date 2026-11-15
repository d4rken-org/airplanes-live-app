package eu.darken.apl.airport.ui.flights

import eu.darken.apl.airport.core.AirportFlightsOperation
import eu.darken.apl.airport.core.AirportFlightsPass
import eu.darken.apl.airport.core.AirportFlightsRepo
import eu.darken.apl.airport.core.AirportFlightsResult
import eu.darken.apl.airport.core.airportFlightsFixture
import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.api.AirportFlightsPageDto
import eu.darken.apl.server.api.AirportFlightsRequest
import eu.darken.apl.server.api.AirportFlightsResponse
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.io.IOException
import java.time.ZoneId

class AirportFlightsControllerTest : BaseTest() {

    private val airportId = "oa:2212"

    /** answered-load.json's evaluatedAt, server time at virtual time 0. */
    private val serverStart = 1794571200000L

    private val isPro = MutableStateFlow<Boolean?>(null)
    private val pass = MutableStateFlow<AirportFlightsPass?>(null)
    private val sent = mutableListOf<AirportFlightsOperation>()
    private var opCounter = 0
    private lateinit var serverClock: ServerClock

    private var onLoad: suspend (AirportFlightsOperation, (Long) -> Unit) -> AirportFlightsResult = { op, _ -> answer(op) }
    private var onPage: suspend (AirportFlightsOperation) -> AirportFlightsResult = { op -> page(op) }

    private fun TestScope.controller(): AirportFlightsController {
        val monotonic = object : MonotonicClock {
            override fun elapsed(): Long = testScheduler.currentTime
        }
        serverClock = ServerClock(monotonic).apply { noteServerTime(serverStart + testScheduler.currentTime) }
        val repo = mockk<AirportFlightsRepo>()
        every { repo.pass } returns pass
        every { repo.newLoad(any()) } answers {
            AirportFlightsOperation(
                kind = AirportFlightsOperation.Kind.Load(firstArg()),
                request = AirportFlightsRequest(operationId = "load-${++opCounter}", airportId = firstArg()),
            )
        }
        every { repo.newPage(any(), any(), any()) } answers {
            AirportFlightsOperation(
                kind = AirportFlightsOperation.Kind.Page(arg(0), arg(1), arg(2)),
                request = AirportFlightsRequest(
                    operationId = "page-${++opCounter}",
                    page = AirportFlightsPageDto(arg(0), arg(1), arg(2)),
                ),
            )
        }
        coEvery { repo.send(any(), any()) } coAnswers {
            val op = firstArg<AirportFlightsOperation>()
            if (!op.claimSend(testScheduler.currentTime)) return@coAnswers AirportFlightsResult.Expired
            sent += op
            when (op.kind) {
                is AirportFlightsOperation.Kind.Load -> onLoad(op, secondArg())
                is AirportFlightsOperation.Kind.Page -> onPage(op)
            }
        }
        return AirportFlightsController(
            scope = backgroundScope,
            airportId = airportId,
            airportTimeZone = "Europe/Berlin",
            repo = repo,
            isPro = isPro,
            serverClock = serverClock,
            monotonicClock = monotonic,
            deviceZone = { ZoneId.of("UTC") },
        )
    }

    private fun answer(
        op: AirportFlightsOperation,
        evaluationId: String = "ev-9",
        refreshAfterMs: Long? = 60_000,
        expiresInMs: Long = 60_000,
    ): AirportFlightsResult {
        val now = serverClock.now().toEpochMilli()
        return AirportFlightsResult.Answered(
            airportFlightsFixture("answered-load.json").copy(
                operationId = op.operationId,
                evaluationId = evaluationId,
                evaluatedAt = now,
                expiresAt = now + expiresInMs,
                refreshAfter = refreshAfterMs?.let { now + it },
            )
        )
    }

    private fun page(
        op: AirportFlightsOperation,
        modify: (AirportFlightsResponse) -> AirportFlightsResponse = { it },
    ): AirportFlightsResult {
        val kind = op.kind as AirportFlightsOperation.Kind.Page
        return AirportFlightsResult.Answered(
            modify(
                airportFlightsFixture("answered-page.json").copy(
                    operationId = op.operationId,
                    evaluationId = kind.evaluationId,
                )
            )
        )
    }

    private fun TestScope.settle(millis: Long = 0) {
        if (millis > 0) advanceTimeBy(millis)
        runCurrent()
    }

    private val AirportFlightsController.loaded: AirportFlightsUiState.Loaded
        get() = state.value.shouldBeInstanceOf<AirportFlightsUiState.Loaded>()

    private fun AirportFlightsUiState.Loaded.departures() =
        display.sections.single { it.kind == AirportFlightsDisplay.SectionKind.DEPARTURES }

    private val loads get() = sent.filter { it.kind is AirportFlightsOperation.Kind.Load }

    @Test
    fun `free loads nothing until asked and every refresh is a new operation`() = runTest {
        isPro.value = false
        val controller = controller()
        controller.setVisible(true)
        settle(10 * MINUTE)

        sent.shouldBeEmpty()
        controller.state.value shouldBe AirportFlightsUiState.Ready

        val first = CompletableDeferred<Unit>()
        onLoad = { op, _ -> first.await(); answer(op) }
        controller.showFlights()
        controller.state.value shouldBe AirportFlightsUiState.Loading
        settle()
        sent shouldHaveSize 1
        first.complete(Unit)
        settle()
        controller.loaded.refreshing shouldBe false

        settle(10 * MINUTE)
        sent shouldHaveSize 1

        val second = CompletableDeferred<Unit>()
        onLoad = { op, _ -> second.await(); answer(op) }
        controller.refresh()
        settle()
        sent shouldHaveSize 2
        controller.loaded.refreshing shouldBe true

        controller.refresh()
        controller.showFlights()
        controller.reload()
        settle()
        sent shouldHaveSize 2

        second.complete(Unit)
        settle()
        controller.loaded.refreshing shouldBe false
        sent.map { it.operationId }.distinct() shouldHaveSize 2
    }

    @Test
    fun `pro loads when visible and refreshes at refreshAfter`() = runTest {
        isPro.value = true
        onLoad = { op, _ -> answer(op, refreshAfterMs = 90_000) }
        val controller = controller()
        settle(MINUTE)
        sent.shouldBeEmpty()

        controller.setVisible(true)
        settle()
        sent shouldHaveSize 1
        controller.loaded

        settle(89_999)
        sent shouldHaveSize 1
        settle(1)
        sent shouldHaveSize 2
        sent[0].operationId shouldNotBe sent[1].operationId
    }

    @Test
    fun `pro refreshes after a minute without refreshAfter`() = runTest {
        isPro.value = true
        onLoad = { op, _ -> answer(op, refreshAfterMs = null) }
        val controller = controller()
        controller.setVisible(true)
        settle()
        sent shouldHaveSize 1

        settle(59_999)
        sent shouldHaveSize 1
        settle(1)
        sent shouldHaveSize 2
    }

    @Test
    fun `pro sends nothing while hidden and one load when visible again`() = runTest {
        isPro.value = true
        val controller = controller()
        controller.setVisible(true)
        settle()
        sent shouldHaveSize 1

        controller.setVisible(false)
        settle(10 * MINUTE)
        sent shouldHaveSize 1

        controller.setVisible(true)
        settle()
        sent shouldHaveSize 2
        settle(59_999)
        sent shouldHaveSize 2
        settle(1)
        sent shouldHaveSize 3
    }

    @Test
    fun `nothing is sent while the upgrade state is unsettled`() = runTest {
        val controller = controller()
        controller.setVisible(true)
        controller.showFlights()
        controller.refresh()
        controller.reload()
        controller.retry()
        controller.showMore("departure")
        settle(10 * MINUTE)

        sent.shouldBeEmpty()
        controller.state.value shouldBe AirportFlightsUiState.Resolving
    }

    @Test
    fun `showing flights before the upgrade state is known is dropped`() = runTest {
        isPro.value = false
        val controller = controller()
        controller.setVisible(true)
        controller.showFlights()

        controller.state.value shouldBe AirportFlightsUiState.Resolving
        settle(10 * MINUTE)
        sent.shouldBeEmpty()
        controller.state.value shouldBe AirportFlightsUiState.Ready
    }

    @Test
    fun `a lost load is resent under its id within the replay window`() = runTest {
        isPro.value = true
        var calls = 0
        onLoad = { op, _ -> if (++calls == 1) awaitCancellation() else answer(op) }
        val controller = controller()
        controller.setVisible(true)
        settle(1_000)
        sent shouldHaveSize 1

        controller.setVisible(false)
        settle(2 * MINUTE)
        controller.setVisible(true)
        settle()

        sent shouldHaveSize 2
        sent[1].operationId shouldBe sent[0].operationId
        controller.loaded
    }

    @Test
    fun `pro makes a new load for a lost one past the replay window`() = runTest {
        isPro.value = true
        var calls = 0
        onLoad = { op, _ -> if (++calls == 1) awaitCancellation() else answer(op) }
        val controller = controller()
        controller.setVisible(true)
        settle(1_000)

        controller.setVisible(false)
        settle(5 * MINUTE)
        controller.setVisible(true)
        settle()

        sent shouldHaveSize 2
        sent[1].operationId shouldNotBe sent[0].operationId
        controller.loaded
    }

    @Test
    fun `free offers loading again for a lost one past the replay window`() = runTest {
        isPro.value = false
        onLoad = { _, _ -> awaitCancellation() }
        val controller = controller()
        controller.setVisible(true)
        settle()
        controller.showFlights()
        settle(1_000)

        controller.setVisible(false)
        settle(5 * MINUTE)
        controller.setVisible(true)
        settle(MINUTE)

        sent shouldHaveSize 1
        controller.state.value shouldBe AirportFlightsUiState.Ready
    }

    @Test
    fun `free offers a reload for a lost refresh past the replay window`() = runTest {
        isPro.value = false
        val controller = controller()
        controller.setVisible(true)
        settle()
        controller.showFlights()
        settle()

        onLoad = { _, _ -> awaitCancellation() }
        controller.refresh()
        settle(1_000)
        controller.setVisible(false)
        settle(6 * MINUTE)
        controller.setVisible(true)
        settle(MINUTE)

        sent shouldHaveSize 2
        controller.loaded.notice shouldBe AirportFlightsUiState.Notice.ReloadOffered
        controller.loaded.refreshing shouldBe false
    }

    @Test
    fun `pro sends nothing more after a final answer`() = runTest {
        isPro.value = true
        val finals = listOf(
            AirportFlightsResult.NotOffered to AirportFlightsUiState.NotOffered,
            AirportFlightsResult.UnknownAirport to AirportFlightsUiState.UnknownAirport,
            AirportFlightsResult.UpdateRequired to AirportFlightsUiState.UpdateRequired,
            AirportFlightsResult.Restricted to AirportFlightsUiState.Restricted,
            AirportFlightsResult.Revoked to AirportFlightsUiState.Revoked,
            AirportFlightsResult.AllowanceExhausted to AirportFlightsUiState.AllowanceExhausted,
        )
        finals.forEach { (result, expected) ->
            sent.clear()
            onLoad = { _, _ -> result }
            val controller = controller()
            controller.setVisible(true)
            settle(30 * MINUTE)

            sent shouldHaveSize 1
            controller.state.value shouldBe expected
            controller.close()
        }
    }

    @Test
    fun `pro reloads once on its own, then offers it`() = runTest {
        isPro.value = true
        onLoad = { _, _ -> AirportFlightsResult.ReloadNeeded(AirportFlightsResponse.OUTCOME_RESULT_EXPIRED) }
        val controller = controller()
        controller.setVisible(true)
        settle(30 * MINUTE)

        sent shouldHaveSize 2
        sent[0].operationId shouldNotBe sent[1].operationId
        controller.state.value shouldBe AirportFlightsUiState.ReloadOffered
    }

    @Test
    fun `free is offered a reload instead of one on its own`() = runTest {
        isPro.value = false
        onLoad = { _, _ -> AirportFlightsResult.ReloadNeeded(AirportFlightsResponse.OUTCOME_PASS_REQUIRED) }
        val controller = controller()
        controller.setVisible(true)
        settle()
        controller.showFlights()
        settle(30 * MINUTE)

        sent shouldHaveSize 1
        controller.state.value shouldBe AirportFlightsUiState.ReloadOffered

        controller.reload()
        settle()
        sent shouldHaveSize 2
    }

    @Test
    fun `free refresh is disabled during a cooldown`() = runTest {
        isPro.value = false
        val controller = controller()
        controller.setVisible(true)
        settle()
        controller.showFlights()
        settle()

        onLoad = { _, _ -> AirportFlightsResult.CoolingDown(testScheduler.currentTime + 30_000) }
        controller.refresh()
        settle()
        sent shouldHaveSize 2
        controller.loaded.refreshEnabled shouldBe false
        controller.loaded.display.sections.flatMap { it.rows }.shouldHaveSize(12)

        controller.refresh()
        settle(29_999)
        sent shouldHaveSize 2
        controller.loaded.refreshEnabled shouldBe false

        settle(1)
        controller.loaded.refreshEnabled shouldBe true
        onLoad = { op, _ -> answer(op) }
        controller.refresh()
        settle()
        sent shouldHaveSize 3
    }

    @Test
    fun `pro refreshes no sooner than a cooldown ends`() = runTest {
        isPro.value = true
        var calls = 0
        onLoad = { op, _ ->
            if (++calls == 2) AirportFlightsResult.CoolingDown(testScheduler.currentTime + 45_000) else answer(op)
        }
        val controller = controller()
        controller.setVisible(true)
        settle(60_000)
        sent shouldHaveSize 2

        settle(44_999)
        sent shouldHaveSize 2
        settle(1)
        sent shouldHaveSize 3
    }

    @Test
    fun `pro backs off after failures and resends while the operation can be replayed`() = runTest {
        isPro.value = true
        onLoad = { _, _ -> AirportFlightsResult.Failed(IOException("offline")) }
        val controller = controller()
        controller.setVisible(true)
        settle()

        val sentAt = mutableListOf<Long>()
        var seen = 0
        repeat(30) {
            if (sent.size > seen) {
                sentAt += testScheduler.currentTime
                seen = sent.size
            }
            settle(MINUTE)
        }

        sentAt shouldContainExactly listOf(0L, 1 * MINUTE, 3 * MINUTE, 7 * MINUTE, 15 * MINUTE, 25 * MINUTE)
        sent.map { it.operationId }.let { ids ->
            ids[1] shouldBe ids[0]
            ids[2] shouldBe ids[0]
            ids[3] shouldNotBe ids[0]
            ids[4] shouldNotBe ids[3]
        }
        controller.state.value.shouldBeInstanceOf<AirportFlightsUiState.Failed>().canRetry shouldBe true
    }

    @Test
    fun `free retry after flights became unavailable waits for the server's retry time`() = runTest {
        isPro.value = false
        val controller = controller()
        controller.setVisible(true)
        settle()
        val unavailableUntil = testScheduler.currentTime + 10 * MINUTE
        onLoad = { _, _ -> AirportFlightsResult.FlightsUnavailable(unavailableUntil) }
        controller.showFlights()
        settle()
        sent shouldHaveSize 1

        val retrySentAt = mutableListOf<Long>()
        onLoad = { op, _ -> retrySentAt += testScheduler.currentTime; answer(op) }
        controller.retry()
        settle(unavailableUntil - testScheduler.currentTime - 1)

        retrySentAt.filter { it < unavailableUntil } shouldBe emptyList()
    }

    @Test
    fun `free coming back after flights were unavailable can retry at once`() = runTest {
        isPro.value = false
        val controller = controller()
        controller.setVisible(true)
        settle()
        val unavailableUntil = testScheduler.currentTime + 10 * MINUTE
        onLoad = { _, _ -> AirportFlightsResult.FlightsUnavailable(unavailableUntil) }
        controller.showFlights()
        settle()
        controller.state.value.shouldBeInstanceOf<AirportFlightsUiState.Waiting>()

        controller.setVisible(false)
        settle(11 * MINUTE)
        controller.setVisible(true)
        controller.state.value.shouldBeInstanceOf<AirportFlightsUiState.Failed>().canRetry shouldBe true

        onLoad = { op, _ -> answer(op) }
        controller.retry()
        settle()
        sent shouldHaveSize 2
        sent[1].operationId shouldNotBe sent[0].operationId
        controller.loaded
    }

    @Test
    fun `pro keeps loading with backoff while loads expire unanswered`() = runTest {
        isPro.value = true
        val sentAt = mutableListOf<Long>()
        onLoad = { _, _ ->
            sentAt += testScheduler.currentTime
            delay(5 * MINUTE)
            AirportFlightsResult.Expired
        }
        val controller = controller()
        controller.setVisible(true)
        settle(30 * MINUTE)

        withClue("loads sent at $sentAt, state ${controller.state.value}") {
            sentAt.size shouldBeGreaterThan 2
            sentAt.last() shouldBeGreaterThan 10 * MINUTE
        }
    }

    @Test
    fun `a refresh answered with an unknown outcome drops the list`() = runTest {
        isPro.value = false
        val controller = controller()
        controller.setVisible(true)
        settle()
        controller.showFlights()
        settle()
        controller.loaded

        onLoad = { _, _ -> AirportFlightsResult.UnknownOutcome("airport_closed") }
        controller.refresh()
        settle()

        controller.state.value.shouldBeInstanceOf<AirportFlightsUiState.Failed>()
    }

    @Test
    fun `a page answered with an unknown outcome drops the list`() = runTest {
        isPro.value = false
        onPage = { _ -> AirportFlightsResult.UnknownOutcome("airport_closed") }
        val controller = controller()
        controller.setVisible(true)
        settle()
        controller.showFlights()
        settle()
        controller.loaded

        controller.showMore("departure")
        settle()

        controller.state.value.shouldBeInstanceOf<AirportFlightsUiState.Failed>()
    }

    @Test
    fun `a page answered with an unknown outcome during a refresh stops everything`() = runTest {
        isPro.value = true
        val controller = controller()
        controller.setVisible(true)
        settle()
        controller.loaded

        val pageGate = CompletableDeferred<Unit>()
        onPage = { _ -> pageGate.await(); AirportFlightsResult.UnknownOutcome("airport_closed") }
        controller.showMore("departure")
        settle()
        onLoad = { _, _ -> awaitCancellation() }
        controller.refresh()
        settle()
        sent shouldHaveSize 3

        pageGate.complete(Unit)
        settle()
        controller.state.value.shouldBeInstanceOf<AirportFlightsUiState.Failed>().canRetry shouldBe false

        settle(30 * MINUTE)
        controller.setVisible(false)
        controller.setVisible(true)
        settle(30 * MINUTE)
        sent shouldHaveSize 3
        controller.state.value.shouldBeInstanceOf<AirportFlightsUiState.Failed>()
    }

    @Test
    fun `show more pages a direction once at a time`() = runTest {
        isPro.value = false
        val controller = controller()
        controller.setVisible(true)
        settle()
        controller.showFlights()
        settle()

        val gate = CompletableDeferred<Unit>()
        onPage = { op -> gate.await(); page(op) }
        controller.showMore("departure")
        settle()
        sent.last().kind shouldBe AirportFlightsOperation.Kind.Page("ev-9", "departure", "c-dep-2")
        controller.loaded.pagingDirections shouldBe setOf("departure")

        controller.showMore("departure")
        settle()
        sent shouldHaveSize 2

        gate.complete(Unit)
        settle()
        controller.loaded.pagingDirections.shouldBeEmpty()
        controller.loaded.departures().rows shouldHaveSize 8
        controller.loaded.departures().showMoreDirection shouldBe "departure"

        onPage = { op -> page(op) }
        controller.showMore("departure")
        settle()
        sent.last().kind shouldBe AirportFlightsOperation.Kind.Page("ev-9", "departure", "c-dep-3")
    }

    @Test
    fun `a page of a replaced evaluation is discarded`() = runTest {
        isPro.value = false
        val controller = controller()
        controller.setVisible(true)
        settle()
        controller.showFlights()
        settle()

        val gate = CompletableDeferred<Unit>()
        onPage = { op -> gate.await(); page(op) }
        controller.showMore("departure")
        settle()

        onLoad = { op, _ -> answer(op, evaluationId = "ev-10") }
        controller.refresh()
        settle()
        gate.complete(Unit)
        settle()

        controller.loaded.departures().rows shouldHaveSize 5
        controller.loaded.pagingDirections.shouldBeEmpty()
        controller.showMore("departure")
        settle()
        sent.last().kind shouldBe AirportFlightsOperation.Kind.Page("ev-10", "departure", "c-dep-2")
    }

    @Test
    fun `a refresh replaces a paged list`() = runTest {
        isPro.value = false
        val controller = controller()
        controller.setVisible(true)
        settle()
        controller.showFlights()
        settle()
        controller.showMore("departure")
        settle()
        controller.loaded.departures().rows shouldHaveSize 8

        controller.refresh()
        settle()

        sent shouldHaveSize 3
        controller.loaded.departures().rows shouldHaveSize 5
    }

    @Test
    fun `a page without its direction's group ends paging that direction`() = runTest {
        isPro.value = false
        onPage = { op -> page(op) { it.copy(groups = emptyList()) } }
        val controller = controller()
        controller.setVisible(true)
        settle()
        controller.showFlights()
        settle()

        controller.showMore("departure")
        settle()
        controller.loaded.departures().rows shouldHaveSize 8
        controller.loaded.departures().showMoreDirection.shouldBeNull()

        controller.showMore("departure")
        settle()
        sent shouldHaveSize 2
    }

    @Test
    fun `an idle list goes stale and loses its pass line on time`() = runTest {
        isPro.value = false
        pass.value = AirportFlightsPass(scope = "s-3", revision = 7, airportId = airportId, validUntil = serverStart + 150_250)
        onLoad = { op, _ -> answer(op, expiresInMs = 90_500) }
        val controller = controller()
        controller.setVisible(true)
        settle()
        controller.showFlights()
        settle()
        controller.loaded.display.stale shouldBe false
        controller.loaded.display.passUntil.shouldNotBeNull()

        settle(90_499)
        controller.loaded.display.stale shouldBe false
        settle(1)
        controller.loaded.display.stale shouldBe true

        settle(150_249 - 90_500)
        controller.loaded.display.passUntil.shouldNotBeNull()
        settle(1)
        controller.loaded.display.passUntil.shouldBeNull()

        sent shouldHaveSize 1
    }

    @Test
    fun `a refreshed list goes stale at its own expiry`() = runTest {
        isPro.value = false
        onLoad = { op, _ -> answer(op, expiresInMs = 60 * MINUTE) }
        val controller = controller()
        controller.setVisible(true)
        settle()
        controller.showFlights()
        settle()

        settle(20_000)
        onLoad = { op, _ -> answer(op, expiresInMs = 10_000) }
        controller.refresh()
        settle()
        controller.loaded.display.stale shouldBe false

        settle(11_000)
        controller.loaded.display.stale shouldBe true
    }

    @Test
    fun `a pass that arrives later loses its line on time`() = runTest {
        isPro.value = false
        onLoad = { op, _ -> answer(op, expiresInMs = 60 * MINUTE) }
        val controller = controller()
        controller.setVisible(true)
        settle()
        controller.showFlights()
        settle(20_000)

        pass.value = AirportFlightsPass(
            scope = "s-3",
            revision = 7,
            airportId = airportId,
            validUntil = serverStart + testScheduler.currentTime + 5_000,
        )
        settle()
        controller.loaded.display.passUntil.shouldNotBeNull()

        settle(6_000)
        controller.loaded.display.passUntil.shouldBeNull()
    }

    @Test
    fun `close cancels everything`() = runTest {
        isPro.value = true
        val controller = controller()
        controller.setVisible(true)
        settle()
        sent shouldHaveSize 1

        controller.close()
        controller.refresh()
        controller.reload()
        controller.showMore("departure")
        controller.setVisible(false)
        controller.setVisible(true)
        settle(30 * MINUTE)

        sent shouldHaveSize 1
    }

    companion object {
        private const val MINUTE = 60_000L
    }
}
