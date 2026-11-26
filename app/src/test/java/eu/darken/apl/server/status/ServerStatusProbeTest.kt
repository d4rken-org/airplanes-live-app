package eu.darken.apl.server.status

import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.network.NetworkStateProvider
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.ServerCodes
import eu.darken.apl.server.compat.AppCompatibility
import eu.darken.apl.server.compat.AppCompatibilityCheck
import eu.darken.apl.server.compat.CompatState
import eu.darken.apl.server.session.SessionManager
import eu.darken.apl.server.session.SessionState
import eu.darken.apl.server.status.ServerStatusTracker.Route
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class ServerStatusProbeTest : BaseTest() {

    private val foreground = MutableStateFlow(true)
    private val network = MutableStateFlow(online(true))
    private val compatState = MutableStateFlow<CompatState>(CompatState.Ok)
    private val sessionState = MutableStateFlow<SessionState>(SessionState.Active("installation-1"))
    private val accessState = MutableStateFlow<AccessState?>(null)

    /** Virtual times at which a probe asked `/meta`. */
    private val checks = mutableListOf<Long>()
    private val refreshes = mutableListOf<String>()
    private val polls = mutableListOf<String>()

    /** What the `/meta` request of a probe does, its result says whether the server answered. */
    private var meta: suspend () -> Boolean = { false }
    private var onRefresh: suspend () -> Unit = {}

    private lateinit var tracker: ServerStatusTracker

    private fun TestScope.probe(): ServerStatusProbe {
        val provider = mockk<NetworkStateProvider>().apply { every { networkState } returns network }
        val clock = object : MonotonicClock {
            override fun elapsed(): Long = testScheduler.currentTime
        }
        tracker = ServerStatusTracker(provider, clock, backgroundScope)
        val compatibilityCheck = mockk<AppCompatibilityCheck> {
            coEvery { check(force = true) } coAnswers {
                checks += testScheduler.currentTime
                meta()
            }
        }
        val compatibility = mockk<AppCompatibility> { every { state } returns compatState }
        val sessionManager = mockk<SessionManager> { every { state } returns sessionState }
        val accessRepo = mockk<AccessRepo> {
            every { state } returns accessState
            coEvery { refresh(any()) } coAnswers {
                refreshes += firstArg<String>()
                onRefresh()
            }
            coEvery { refreshUnlessRetrying(any()) } coAnswers { polls += firstArg<String>() }
        }
        return ServerStatusProbe(
            appScope = backgroundScope,
            foreground = foreground,
            networkStateProvider = provider,
            tracker = tracker,
            compatibilityCheck = compatibilityCheck,
            compatibility = compatibility,
            sessionManager = sessionManager,
            accessRepo = accessRepo,
        )
    }

    private fun TestScope.advance(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    private fun TestScope.start() {
        probe().setup()
    }

    private fun TestScope.startTroubled(retryAfterSeconds: Long? = null) {
        start()
        tracker.onErrorResponse(Route.DATA, maintenance(retryAfterSeconds))
        runCurrent()
    }

    private fun answerAndClear(): suspend () -> Boolean = {
        tracker.onSuccess(Route.META)
        // The request returns only after the tracker heard of it, as it does through the endpoint
        delay(100)
        true
    }

    @Test
    fun `without a retry hint probes back off from 15s to 60s`() = runTest {
        startTroubled()

        advance(15_000 - 1)
        checks shouldBe emptyList()
        advance(1)
        checks shouldBe listOf(15_000L)

        advance(150_000)
        checks shouldBe listOf(15_000L, 45_000L, 105_000L, 165_000L)
    }

    @Test
    fun `a retry hint longer than the backoff sets the wait, the backoff still grows`() = runTest {
        startTroubled(retryAfterSeconds = 45)

        advance(210_000)

        checks shouldBe listOf(45_000L, 90_000L, 150_000L, 210_000L)
    }

    @Test
    fun `a long retry hint is waited out every time`() = runTest {
        startTroubled(retryAfterSeconds = 120)

        advance(360_000)

        checks shouldBe listOf(120_000L, 240_000L, 360_000L)
    }

    @Test
    fun `nothing is probed while all is well`() = runTest {
        start()

        advance(600_000)

        checks shouldBe emptyList()
    }

    @Test
    fun `nothing is probed in the background`() = runTest {
        foreground.value = false
        startTroubled()

        advance(600_000)
        checks shouldBe emptyList()

        foreground.value = true
        advance(15_000)
        checks shouldBe listOf(615_000L)
    }

    @Test
    fun `nothing is probed while offline`() = runTest {
        network.value = online(false)
        startTroubled()

        advance(600_000)
        checks shouldBe emptyList()

        network.value = online(true)
        advance(15_000)
        checks shouldBe listOf(615_000L)
    }

    @Test
    fun `going to the background while waiting stops probing and resets the backoff`() = runTest {
        startTroubled()
        advance(45_000)
        checks shouldBe listOf(15_000L, 45_000L)

        advance(5_000)
        foreground.value = false
        advance(600_000)
        checks shouldBe listOf(15_000L, 45_000L)

        foreground.value = true
        advance(15_000)
        checks shouldBe listOf(15_000L, 45_000L, 665_000L)
    }

    @Test
    fun `going to the background during a probe lets it finish but starts no further one`() = runTest {
        val answer = CompletableDeferred<Boolean>()
        var finished = false
        meta = {
            answer.await().also { finished = true }
        }
        startTroubled()
        advance(15_000)
        checks shouldBe listOf(15_000L)

        advance(1_000)
        foreground.value = false
        advance(1_000)
        answer.complete(true)
        advance(600_000)

        finished shouldBe true
        refreshes shouldBe listOf(ServerStatusProbe.REASON)
        checks shouldBe listOf(15_000L)
    }

    @Test
    fun `an answer that ends a maintenance still refreshes the access policy`() = runTest {
        meta = answerAndClear()
        startTroubled(retryAfterSeconds = 300)

        advance(300_000)
        checks shouldBe listOf(300_000L)
        tracker.isTroubled.value shouldBe false
        refreshes shouldBe emptyList()
        advance(1_000)

        refreshes shouldBe listOf(ServerStatusProbe.REASON)
        // A poll would sit out a waiting access retry, which can take minutes
        polls shouldBe emptyList()
        advance(600_000)
        checks shouldBe listOf(300_000L)
    }

    @Test
    fun `an answer during a server problem refreshes access and keeps the problem until data works`() = runTest {
        meta = answerAndClear()
        start()
        repeat(2) { tracker.onErrorResponse(Route.DATA, unavailable) }
        tracker.status.value shouldBe ServerStatus.ServerProblem

        advance(15_000)
        advance(1_000)

        refreshes shouldBe listOf(ServerStatusProbe.REASON)
        tracker.status.value shouldBe ServerStatus.ServerProblem
        tracker.isTroubled.value shouldBe true
    }

    @Test
    fun `a probe without an answer refreshes nothing`() = runTest {
        start()
        repeat(2) { tracker.onErrorResponse(Route.DATA, unavailable) }

        advance(600_000)

        (checks.size > 1) shouldBe true
        refreshes shouldBe emptyList()
        tracker.status.value shouldBe ServerStatus.ServerProblem
    }

    @Test
    fun `an answer while the app must be updated clears a server problem`() = runTest {
        meta = answerAndClear()
        compatState.value = CompatState.UpdateRequired
        start()
        repeat(2) { tracker.onErrorResponse(Route.DATA, unavailable) }

        advance(15_000)
        advance(1_000)

        tracker.status.value shouldBe ServerStatus.Ok
        tracker.isTroubled.value shouldBe false
    }

    @Test
    fun `an answer while the installation is revoked clears a server problem`() = runTest {
        meta = answerAndClear()
        sessionState.value = SessionState.Revoked
        start()
        repeat(2) { tracker.onErrorResponse(Route.DATA, unavailable) }

        advance(15_000)
        advance(1_000)

        tracker.status.value shouldBe ServerStatus.Ok
        tracker.isTroubled.value shouldBe false
    }

    @Test
    fun `on a cold start an answer loads the access policy`() = runTest {
        meta = answerAndClear()
        onRefresh = {
            accessState.value = mockk<AccessState>()
            tracker.onSuccess(Route.DATA)
        }
        start()
        tracker.onErrorResponse(Route.DATA, unavailable)
        accessState.value shouldBe null

        advance(15_000)
        advance(1_000)

        refreshes shouldBe listOf(ServerStatusProbe.REASON)
        (accessState.value != null) shouldBe true
        tracker.status.value shouldBe ServerStatus.Ok
        advance(600_000)
        checks shouldBe listOf(15_000L)
    }

    companion object {
        private val unavailable = ServerApiException(code = ServerCodes.SERVER_UNAVAILABLE, status = 503)

        private fun maintenance(retryAfterSeconds: Long?) = ServerApiException(
            code = ServerCodes.SERVER_MAINTENANCE,
            status = 503,
            retryAfterSeconds = retryAfterSeconds,
        )

        private fun online(available: Boolean): NetworkStateProvider.State =
            NetworkStateProvider.State.LegacyAPI21(isMeteredConnection = false, isInternetAvailable = available)
    }
}
