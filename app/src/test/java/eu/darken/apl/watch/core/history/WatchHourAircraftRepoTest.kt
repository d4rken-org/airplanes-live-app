package eu.darken.apl.watch.core.history

import eu.darken.apl.server.api.AircraftAirline
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.ServerCodes
import eu.darken.apl.server.api.ServerEndpoint
import eu.darken.apl.server.api.WatchHourAircraft
import eu.darken.apl.server.api.WatchHourAircraftResponse
import eu.darken.apl.server.session.SessionManager
import eu.darken.apl.watch.core.history.HourAircraftState.NotAvailable.Reason
import eu.darken.apl.watch.core.server.WatchListSync
import eu.darken.apl.watch.core.server.WatchListSyncScheduler
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.io.IOException
import java.time.Instant

class WatchHourAircraftRepoTest : BaseTest() {

    private val endpoint = mockk<ServerEndpoint>()
    private val sessionManager = mockk<SessionManager>()
    private val watchListSync = mockk<WatchListSync>(relaxed = true)
    private val syncScheduler = mockk<WatchListSyncScheduler>(relaxed = true)

    private var sessionInstallation = INSTALLATION

    /** The `after` of every request that reached the endpoint. */
    private val sent = mutableListOf<String?>()
    private val answers = ArrayDeque<() -> WatchHourAircraftResponse>()

    private lateinit var repo: WatchHourAircraftRepo

    @BeforeEach
    fun setup() {
        coEvery { sessionManager.authedAs<Any?>(any()) } coAnswers {
            firstArg<suspend (String, String) -> Any?>().invoke(sessionInstallation, "token")
        }
        coEvery {
            endpoint.watchHourAircraft(
                token = "token",
                localId = IDENTITY.localId,
                watchId = IDENTITY.serverWatchId,
                revision = IDENTITY.revision,
                hour = HOUR.toEpochMilli(),
                after = any(),
            )
        } coAnswers {
            sent += arg<String?>(5)
            answers.removeFirst().invoke()
        }
        repo = WatchHourAircraftRepo(
            endpoint = endpoint,
            sessionManager = sessionManager,
            watchListSync = watchListSync,
            syncScheduler = syncScheduler,
        )
    }

    private fun answer(page: WatchHourAircraftResponse) {
        answers += { page }
    }

    private fun fail(error: Exception) {
        answers += { throw error }
    }

    private fun page(
        vararg aircraftIds: String,
        next: String? = null,
        final: Boolean = true,
        coverage: String = "full",
    ) = WatchHourAircraftResponse(
        serverTime = HOUR.plusSeconds(3_800).toEpochMilli(),
        watchId = IDENTITY.serverWatchId,
        localId = IDENTITY.localId,
        revision = IDENTITY.revision.toInt(),
        hour = HOUR.toEpochMilli(),
        evaluationBasis = "estimated_path",
        coverage = coverage,
        isFinal = final,
        aircraft = aircraftIds.map { WatchHourAircraft(aircraftId = it, lastSeenAt = SEEN.toEpochMilli()) },
        next = next,
    )

    private fun seen(vararg hexes: String) = hexes.map { HourAircraft(hex = it, lastSeenAt = SEEN) }

    private suspend fun load() = repo.load(IDENTITY, HOUR).toList()

    private fun problem(status: Int, code: String, retryAfterSeconds: Long? = null) =
        ServerApiException(code = code, status = status, retryAfterSeconds = retryAfterSeconds)

    @Test
    fun `pages are read until one has no next, an empty page with next included`() = runTest {
        answer(page("3c6444", "3c6a1f", next = "3c6a1f"))
        answer(page(next = "4b1805"))
        answer(page("4b1806"))

        load() shouldBe listOf(
            HourAircraftState.Loading(seen("3C6444", "3C6A1F"), provisional = false, coverage = HourCoverage.FULL),
            HourAircraftState.Loading(seen("3C6444", "3C6A1F"), provisional = false, coverage = HourCoverage.FULL),
            HourAircraftState.Complete(
                seen("3C6444", "3C6A1F", "4B1806"),
                provisional = false,
                coverage = HourCoverage.FULL,
            ),
        )
        sent shouldBe listOf(null, "3c6a1f", "4b1805")
    }

    @Test
    fun `a non-final earlier page keeps the hour provisional even when the last page is final`() = runTest {
        answer(page("3c6444", next = "3c6444", final = false))
        answer(page("3c6a1f", final = true))

        load() shouldBe listOf(
            HourAircraftState.Loading(seen("3C6444"), provisional = true, coverage = HourCoverage.FULL),
            HourAircraftState.Complete(seen("3C6444", "3C6A1F"), provisional = true, coverage = HourCoverage.FULL),
        )
    }

    @Test
    fun `the worst coverage of all pages is kept`() = runTest {
        answer(page("3c6444", next = "3c6444", coverage = "partial"))
        answer(page("3c6a1f", coverage = "full"))

        load().last() shouldBe HourAircraftState.Complete(
            seen("3C6444", "3C6A1F"),
            provisional = false,
            coverage = HourCoverage.PARTIAL,
        )
    }

    @Test
    fun `an all-final hour is answered from the cache the second time`() = runTest {
        answer(page("3c6444", coverage = "partial"))

        val first = load()
        val second = load()

        second shouldBe listOf(
            HourAircraftState.Complete(seen("3C6444"), provisional = false, coverage = HourCoverage.PARTIAL),
        )
        second shouldBe first
        sent shouldBe listOf(null)
    }

    @Test
    fun `a provisional hour is not cached`() = runTest {
        answer(page("3c6444", final = false))
        answer(page("3c6444", "3c6a1f", final = false))

        load().last() shouldBe
                HourAircraftState.Complete(seen("3C6444"), provisional = true, coverage = HourCoverage.FULL)
        load().last() shouldBe
                HourAircraftState.Complete(seen("3C6444", "3C6A1F"), provisional = true, coverage = HourCoverage.FULL)
        sent shouldBe listOf(null, null)
    }

    @Test
    fun `a revision conflict part-way drops the pages and asks for a watch list reread`() = runTest {
        answer(page("3c6444", next = "3c6444"))
        fail(problem(409, ServerCodes.REVISION_CONFLICT))

        load() shouldBe listOf(
            HourAircraftState.Loading(seen("3C6444"), provisional = false, coverage = HourCoverage.FULL),
            HourAircraftState.Changed,
        )
        verify(exactly = 1) { watchListSync.requestReread() }
        verify(exactly = 1) { syncScheduler.requestSync(any()) }
    }

    @Test
    fun `a deleted server watch asks for a watch list reread`() = runTest {
        fail(problem(404, ServerCodes.WATCH_NOT_FOUND))

        load() shouldBe listOf(HourAircraftState.Changed)
        verify(exactly = 1) { watchListSync.requestReread() }
        verify(exactly = 1) { syncScheduler.requestSync(any()) }
    }

    @Test
    fun `throttling retries the same page after the server's wait`() = runTest {
        answer(page("3c6444", next = "3c6444"))
        fail(problem(429, ServerCodes.INSTALLATION_RATE_EXCEEDED, retryAfterSeconds = 2))
        answer(page("3c6a1f"))

        load().last() shouldBe
                HourAircraftState.Complete(seen("3C6444", "3C6A1F"), provisional = false, coverage = HourCoverage.FULL)
        sent shouldBe listOf(null, "3c6444", "3c6444")
        testScheduler.currentTime shouldBe 2_000L
    }

    @Test
    fun `a wait above 30 seconds fails without waiting`() = runTest {
        fail(problem(429, ServerCodes.QUOTA_EXCEEDED, retryAfterSeconds = 40))

        load().single().shouldBeInstanceOf<HourAircraftState.Failed>().error
            .shouldBeInstanceOf<ServerApiException>().code shouldBe ServerCodes.QUOTA_EXCEEDED
        sent shouldBe listOf(null)
        testScheduler.currentTime shouldBe 0L
    }

    @Test
    fun `a page fails after three tries`() = runTest {
        fail(problem(503, ServerCodes.DATABASE_UNAVAILABLE, retryAfterSeconds = 1))
        fail(IOException("offline"))
        fail(problem(503, ServerCodes.REQUEST_TIMEOUT, retryAfterSeconds = 1))

        load().single().shouldBeInstanceOf<HourAircraftState.Failed>().error
            .shouldBeInstanceOf<ServerApiException>().code shouldBe ServerCodes.REQUEST_TIMEOUT
        sent shouldBe listOf(null, null, null)
    }

    @Test
    fun `an hour outside the server's range is not available`() = runTest {
        fail(problem(400, ServerCodes.INVALID_REQUEST))

        load() shouldBe listOf(HourAircraftState.NotAvailable(Reason.OUT_OF_RANGE))
    }

    @Test
    fun `refusals for the tier, the installation and a deleted list are not available`() = runTest {
        fail(problem(403, ServerCodes.TIER_RESTRICTED))
        fail(problem(403, ServerCodes.INSTALLATION_RESTRICTED))
        fail(problem(410, ServerCodes.WATCH_LIST_DELETED))

        load() shouldBe listOf(HourAircraftState.NotAvailable(Reason.TIER_RESTRICTED))
        load() shouldBe listOf(HourAircraftState.NotAvailable(Reason.INSTALLATION_RESTRICTED))
        load() shouldBe listOf(HourAircraftState.NotAvailable(Reason.LIST_DELETED))
    }

    @Test
    fun `aircraft ids come out uppercase with their optional details`() = runTest {
        answer(
            page().copy(
                aircraft = listOf(
                    WatchHourAircraft(
                        aircraftId = "3c6444",
                        callsign = "DLH400",
                        registration = "D-AIQA",
                        aircraftType = "A320",
                        airline = AircraftAirline(designator = "DLH", name = "Lufthansa"),
                        lastSeenAt = SEEN.toEpochMilli(),
                    ),
                    WatchHourAircraft(aircraftId = "~1a2b3c", lastSeenAt = SEEN.toEpochMilli()),
                ),
            )
        )

        load().single().shouldBeInstanceOf<HourAircraftState.Complete>().aircraft shouldBe listOf(
            HourAircraft(
                hex = "3C6444",
                lastSeenAt = SEEN,
                callsign = "DLH400",
                registration = "D-AIQA",
                aircraftType = "A320",
                airlineName = "Lufthansa",
            ),
            HourAircraft(hex = "~1A2B3C", lastSeenAt = SEEN),
        )
    }

    @Test
    fun `an identity of another installation ends as changed without a request`() = runTest {
        sessionInstallation = "installation-b"

        load() shouldBe listOf(HourAircraftState.Changed)
        sent.shouldBeEmpty()
        verify(exactly = 0) { watchListSync.requestReread() }
    }

    companion object {
        private const val INSTALLATION = "installation-a"
        private val HOUR: Instant = Instant.parse("2026-11-12T10:00:00Z")
        private val SEEN: Instant = Instant.parse("2026-11-12T10:29:10Z")
        private val IDENTITY = WatchServerIdentity(
            localId = "home",
            serverWatchId = "6f1c2d3e-4a5b-4c6d-8e7f-90a1b2c3d4e5",
            revision = 2L,
            installationId = INSTALLATION,
        )
    }
}
