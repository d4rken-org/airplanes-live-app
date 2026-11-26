package eu.darken.apl.server.status

import android.net.Network
import android.net.NetworkCapabilities
import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.network.NetworkStateProvider
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.ServerCodes
import eu.darken.apl.server.status.ServerStatusTracker.Route
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Duration
import java.time.Instant

class ServerStatusTrackerTest : BaseTest() {

    private val network = MutableStateFlow(validatedNetwork())

    private fun TestScope.tracker(): ServerStatusTracker {
        val provider = mockk<NetworkStateProvider>().apply { every { networkState } returns network }
        val clock = object : MonotonicClock {
            override fun elapsed(): Long = testScheduler.currentTime
        }
        return ServerStatusTracker(provider, clock, backgroundScope)
    }

    private fun problem(code: String, status: Int = 503, retryAfterSeconds: Long? = null, until: Instant? = null) =
        ServerApiException(code = code, status = status, retryAfterSeconds = retryAfterSeconds, until = until)

    private val maintenance = problem(ServerCodes.SERVER_MAINTENANCE, retryAfterSeconds = 60, until = UNTIL)
    private val incident = problem(ServerCodes.SERVER_INCIDENT, retryAfterSeconds = 30, until = LATER)
    private val unavailable = problem(ServerCodes.SERVER_UNAVAILABLE)
    private val badRequest = problem(ServerCodes.INVALID_REQUEST, status = 400)

    @Test
    fun `nothing is wrong before any outcome`() = runTest {
        val tracker = tracker()

        tracker.status.value shouldBe ServerStatus.Ok
        tracker.isTroubled.value shouldBe false
        tracker.retryHint.value.shouldBeNull()
    }

    @Test
    fun `maintenance and incident show at once with their expected end`() = runTest {
        val tracker = tracker()

        tracker.onErrorResponse(Route.DATA, maintenance)
        tracker.status.value shouldBe ServerStatus.Maintenance(UNTIL)
        tracker.isTroubled.value shouldBe true
        tracker.retryHint.value shouldBe Duration.ofSeconds(60)

        tracker.onErrorResponse(Route.DATA, incident)
        tracker.status.value shouldBe ServerStatus.Incident(LATER)
        tracker.retryHint.value shouldBe Duration.ofSeconds(30)

        tracker.onErrorResponse(Route.DATA, problem(ServerCodes.SERVER_MAINTENANCE))
        tracker.status.value shouldBe ServerStatus.Maintenance(null)
        tracker.retryHint.value.shouldBeNull()
    }

    @Test
    fun `an outage answer records when its retry hint ends`() = runTest {
        val tracker = tracker()
        advanceTimeBy(5_000)

        tracker.onErrorResponse(Route.DATA, maintenance)
        tracker.retryNotBefore.value shouldBe 65_000L

        tracker.onErrorResponse(Route.DATA, problem(ServerCodes.SERVER_INCIDENT))
        tracker.retryNotBefore.value.shouldBeNull()

        tracker.onErrorResponse(Route.DATA, maintenance)
        tracker.retryNotBefore.value shouldBe 65_000L
        tracker.onSuccess(Route.META)
        tracker.status.value shouldBe ServerStatus.Ok
        tracker.retryNotBefore.value.shouldBeNull()
    }

    @Test
    fun `maintenance replaces a pending failure`() = runTest {
        val tracker = tracker()
        tracker.onErrorResponse(Route.DATA, unavailable)

        tracker.onErrorResponse(Route.DATA, maintenance)
        advanceTimeBy(20_000)
        runCurrent()

        tracker.status.value shouldBe ServerStatus.Maintenance(UNTIL)
    }

    @Test
    fun `connection failures and other server errors keep maintenance and incident`() = runTest {
        val tracker = tracker()

        tracker.onErrorResponse(Route.DATA, maintenance)
        repeat(2) { tracker.onConnectionFailure() }
        repeat(2) { tracker.onErrorResponse(Route.DATA, problem("http_502", status = 502)) }
        advanceTimeBy(20_000)
        runCurrent()
        tracker.status.value shouldBe ServerStatus.Maintenance(UNTIL)

        tracker.onErrorResponse(Route.DATA, incident)
        repeat(2) { tracker.onConnectionFailure() }
        repeat(2) { tracker.onErrorResponse(Route.DATA, unavailable) }
        advanceTimeBy(20_000)
        runCurrent()
        tracker.status.value shouldBe ServerStatus.Incident(LATER)
    }

    @Test
    fun `maintenance, incident and unreachable clear on a meta answer`() = runTest {
        val tracker = tracker()

        listOf(
            { tracker.onErrorResponse(Route.DATA, maintenance) },
            { tracker.onErrorResponse(Route.DATA, incident) },
        ).forEach { enter ->
            enter()
            tracker.onSuccess(Route.META)
            tracker.status.value shouldBe ServerStatus.Ok
            tracker.isTroubled.value shouldBe false
            tracker.retryHint.value.shouldBeNull()
        }

        repeat(2) { tracker.onConnectionFailure() }
        tracker.status.value shouldBe ServerStatus.Unreachable
        tracker.onSuccess(Route.META)
        tracker.status.value shouldBe ServerStatus.Ok
        tracker.isTroubled.value shouldBe false
    }

    @Test
    fun `maintenance, incident and unreachable clear on a refused data request`() = runTest {
        val tracker = tracker()

        tracker.onErrorResponse(Route.DATA, maintenance)
        tracker.onErrorResponse(Route.DATA, badRequest)
        tracker.status.value shouldBe ServerStatus.Ok

        tracker.onErrorResponse(Route.DATA, incident)
        tracker.onErrorResponse(Route.DATA, problem(ServerCodes.APP_UPDATE_REQUIRED, status = 403))
        tracker.status.value shouldBe ServerStatus.Ok

        repeat(2) { tracker.onConnectionFailure() }
        tracker.onErrorResponse(Route.META, badRequest)
        tracker.status.value shouldBe ServerStatus.Ok
        tracker.isTroubled.value shouldBe false
    }

    @Test
    fun `a single server error stays hidden, a second one shows`() = runTest {
        val tracker = tracker()

        tracker.onErrorResponse(Route.DATA, problem(ServerCodes.DATABASE_UNAVAILABLE, retryAfterSeconds = 5))
        tracker.status.value shouldBe ServerStatus.Ok
        tracker.isTroubled.value shouldBe true
        tracker.retryHint.value shouldBe Duration.ofSeconds(5)

        tracker.onErrorResponse(Route.DATA, problem("http_504", status = 504))
        tracker.status.value shouldBe ServerStatus.ServerProblem
        tracker.isTroubled.value shouldBe true
    }

    @Test
    fun `a single server error shows once the deadline passed, not before`() = runTest {
        val tracker = tracker()

        tracker.onErrorResponse(Route.DATA, unavailable)
        advanceTimeBy(ServerStatusTracker.PROMOTE_AFTER_MS - 1)
        runCurrent()
        tracker.status.value shouldBe ServerStatus.Ok
        tracker.isTroubled.value shouldBe true

        advanceTimeBy(1)
        runCurrent()
        tracker.status.value shouldBe ServerStatus.ServerProblem
    }

    @Test
    fun `a single connection failure shows once the deadline passed, not before`() = runTest {
        val tracker = tracker()

        tracker.onConnectionFailure()
        advanceTimeBy(ServerStatusTracker.PROMOTE_AFTER_MS - 1)
        runCurrent()
        tracker.status.value shouldBe ServerStatus.Ok
        tracker.isTroubled.value shouldBe true

        advanceTimeBy(1)
        runCurrent()
        tracker.status.value shouldBe ServerStatus.Unreachable
    }

    @Test
    fun `a cleared failure starts a new deadline`() = runTest {
        val tracker = tracker()

        tracker.onConnectionFailure()
        advanceTimeBy(6_000)
        tracker.onErrorResponse(Route.DATA, badRequest)
        tracker.onConnectionFailure()
        advanceTimeBy(6_000)
        runCurrent()
        tracker.status.value shouldBe ServerStatus.Ok

        advanceTimeBy(4_000)
        runCurrent()
        tracker.status.value shouldBe ServerStatus.Unreachable
    }

    @Test
    fun `an answer before the deadline cancels a pending failure`() = runTest {
        val tracker = tracker()

        tracker.onConnectionFailure()
        tracker.onSuccess(Route.META)
        tracker.isTroubled.value shouldBe false

        tracker.onErrorResponse(Route.DATA, unavailable)
        tracker.onSuccess(Route.DATA)
        tracker.isTroubled.value shouldBe false

        advanceTimeBy(20_000)
        runCurrent()
        tracker.status.value shouldBe ServerStatus.Ok
        tracker.isTroubled.value shouldBe false
    }

    @Test
    fun `a pending connection failure turns into a server problem`() = runTest {
        val tracker = tracker()

        tracker.onConnectionFailure()
        tracker.onErrorResponse(Route.DATA, unavailable)

        tracker.status.value shouldBe ServerStatus.ServerProblem
    }

    @Test
    fun `a pending server problem stays one after a connection failure and a healthy meta`() = runTest {
        val tracker = tracker()

        tracker.onErrorResponse(Route.DATA, unavailable)
        tracker.onSuccess(Route.META)
        tracker.isTroubled.value shouldBe true
        tracker.onConnectionFailure()

        tracker.status.value shouldBe ServerStatus.ServerProblem
    }

    @Test
    fun `a healthy meta between failing data calls keeps the server problem until data works`() = runTest {
        val tracker = tracker()

        tracker.onErrorResponse(Route.DATA, unavailable)
        tracker.onSuccess(Route.META)
        tracker.onErrorResponse(Route.DATA, unavailable)
        tracker.status.value shouldBe ServerStatus.ServerProblem

        tracker.onSuccess(Route.META)
        tracker.onErrorResponse(Route.DATA, badRequest)
        tracker.status.value shouldBe ServerStatus.ServerProblem
        tracker.isTroubled.value shouldBe true

        tracker.onSuccess(Route.DATA)
        tracker.status.value shouldBe ServerStatus.Ok
        tracker.isTroubled.value shouldBe false
        tracker.retryHint.value.shouldBeNull()
    }

    @Test
    fun `an answer without data routes clears a shown and a pending server problem`() = runTest {
        val tracker = tracker()

        repeat(2) { tracker.onErrorResponse(Route.DATA, unavailable) }
        tracker.status.value shouldBe ServerStatus.ServerProblem
        tracker.onServerAnsweredWithoutDataRoute()
        tracker.status.value shouldBe ServerStatus.Ok
        tracker.isTroubled.value shouldBe false
        tracker.retryHint.value.shouldBeNull()

        tracker.onErrorResponse(Route.DATA, unavailable)
        tracker.isTroubled.value shouldBe true
        tracker.onServerAnsweredWithoutDataRoute()
        tracker.isTroubled.value shouldBe false
        advanceTimeBy(20_000)
        runCurrent()
        tracker.status.value shouldBe ServerStatus.Ok
    }

    @Test
    fun `an answer without data routes leaves everything but a server problem alone`() = runTest {
        val tracker = tracker()

        tracker.onErrorResponse(Route.DATA, maintenance)
        tracker.onServerAnsweredWithoutDataRoute()
        tracker.status.value shouldBe ServerStatus.Maintenance(UNTIL)
        tracker.retryHint.value shouldBe Duration.ofSeconds(60)

        tracker.onErrorResponse(Route.DATA, incident)
        tracker.onServerAnsweredWithoutDataRoute()
        tracker.status.value shouldBe ServerStatus.Incident(LATER)

        tracker.onSuccess(Route.META)
        repeat(2) { tracker.onConnectionFailure() }
        tracker.onServerAnsweredWithoutDataRoute()
        tracker.status.value shouldBe ServerStatus.Unreachable
        tracker.isTroubled.value shouldBe true

        tracker.onSuccess(Route.META)
        tracker.onConnectionFailure()
        tracker.onServerAnsweredWithoutDataRoute()
        tracker.isTroubled.value shouldBe true
        advanceTimeBy(20_000)
        runCurrent()
        tracker.status.value shouldBe ServerStatus.Unreachable
    }

    @Test
    fun `a server error replaces unreachable`() = runTest {
        val tracker = tracker()
        repeat(2) { tracker.onConnectionFailure() }

        repeat(2) { tracker.onErrorResponse(Route.DATA, unavailable) }

        tracker.status.value shouldBe ServerStatus.ServerProblem
    }

    @Test
    fun `a connection failure does not replace a server problem`() = runTest {
        val tracker = tracker()
        repeat(2) { tracker.onErrorResponse(Route.DATA, unavailable) }

        repeat(2) { tracker.onConnectionFailure() }
        advanceTimeBy(20_000)
        runCurrent()

        tracker.status.value shouldBe ServerStatus.ServerProblem
    }

    @Test
    fun `connection failures without a validated network record nothing`() = runTest {
        val tracker = tracker()

        listOf(
            NetworkStateProvider.State.Fallback,
            NetworkStateProvider.State.LegacyAPI21(isMeteredConnection = false, isInternetAvailable = true),
            NetworkStateProvider.State.Modern(activeNetwork = mockk<Network>(), capabilities = capabilities(validated = false)),
            NetworkStateProvider.State.Modern(activeNetwork = null, capabilities = null),
        ).forEach { state ->
            network.value = state
            repeat(2) { tracker.onConnectionFailure() }
            advanceTimeBy(20_000)
            runCurrent()

            tracker.status.value shouldBe ServerStatus.Ok
            tracker.isTroubled.value shouldBe false
        }

        network.value = validatedNetwork()
        repeat(2) { tracker.onConnectionFailure() }
        tracker.status.value shouldBe ServerStatus.Unreachable
    }

    companion object {
        private val UNTIL = Instant.parse("2026-11-26T15:30:00Z")
        private val LATER = Instant.parse("2026-11-26T18:00:00Z")

        private fun capabilities(validated: Boolean) = mockk<NetworkCapabilities>().apply {
            every { hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns validated
        }

        private fun validatedNetwork(): NetworkStateProvider.State =
            NetworkStateProvider.State.Modern(activeNetwork = mockk<Network>(), capabilities = capabilities(validated = true))
    }
}
