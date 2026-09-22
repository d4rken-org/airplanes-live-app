package eu.darken.apl.search.core

import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.compose.preview.FakeAircraft
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.aircraft.AircraftHex
import eu.darken.apl.main.core.query.BatchResult
import eu.darken.apl.main.core.query.TermOutcome
import eu.darken.apl.main.core.request.OperationStore
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.ServerModule
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.api.Allowance
import eu.darken.apl.server.api.UsageUpdate
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.io.IOException

class SearchRepoCacheTest : BaseTest() {

    private val aircraftRepo = mockk<AircraftRepo>()
    private val accessRepo = mockk<AccessRepo>(relaxed = true)
    private val operationStore = mockk<OperationStore>(relaxed = true)
    private val serverClock = ServerClock(object : MonotonicClock {
        override fun elapsed(): Long = 0L
    })
    private val cache = MutableStateFlow<Map<AircraftHex, Aircraft>>(emptyMap())

    private lateinit var repo: SearchRepo

    @BeforeEach
    fun setup() {
        every { aircraftRepo.cache } returns cache
        coEvery { operationStore.pending(any(), any()) } returns emptyList()
        repo = SearchRepo(aircraftRepo, accessRepo, operationStore, serverClock, ServerModule.serverJson())
    }

    private fun cached(vararg aircraft: Aircraft) {
        cache.value = aircraft.associateBy { it.hex }
    }

    private fun answerWith(vararg outcomes: TermOutcome) {
        coEvery { aircraftRepo.search(any()) } returns listOf(
            BatchResult(
                operationId = "5a5c6b2e-3b0a-4a2e-9a0f-000000000001",
                replayed = false,
                snapshot = null,
                outcomes = outcomes.toList(),
                usage = USAGE,
            )
        )
    }

    @Test
    fun `a complete answer suppresses cached extras`() = runTest {
        cached(FakeAircraft(hex = "AAAAAA", callsign = "OLD"))
        answerWith(
            TermOutcome.Answered(
                aircraft = emptyList(),
                complete = true,
                capped = false,
                totalMatching = 0,
                expiresAt = null,
                charged = true,
            )
        )

        val result = repo.search(SearchQuery(listOf(SearchTerm("AAAAAA"))))

        result.aircraft shouldBe emptyList()
        result.cacheOnly shouldBe emptyList()
    }

    @Test
    fun `a rejected term keeps cached extras`() = runTest {
        cached(FakeAircraft(hex = "AAAAAA", callsign = "OLD"))
        answerWith(TermOutcome.Rejected("daily_allowance_exhausted"))

        val result = repo.search(SearchQuery(listOf(SearchTerm("AAAAAA"))))

        result.aircraft.map { it.hex } shouldContainExactlyInAnyOrder listOf("AAAAAA")
        result.cacheOnly.map { it.hex } shouldContainExactlyInAnyOrder listOf("AAAAAA")
    }

    @Test
    fun `an incomplete answer keeps cached extras`() = runTest {
        cached(FakeAircraft(hex = "AAAAAA"), FakeAircraft(hex = "BBBBBB"))
        answerWith(
            TermOutcome.Answered(
                aircraft = listOf(FakeAircraft(hex = "BBBBBB")),
                complete = false,
                capped = false,
                totalMatching = null,
                expiresAt = null,
                charged = false,
            ),
            TermOutcome.Rejected("invalid_request"),
        )

        val result = repo.search(SearchQuery(listOf(SearchTerm("BBBBBB"), SearchTerm("AAAAAA"))))

        result.aircraft.map { it.hex } shouldContainExactlyInAnyOrder listOf("AAAAAA", "BBBBBB")
        result.cacheOnly.map { it.hex } shouldContainExactlyInAnyOrder listOf("AAAAAA")
    }

    @Test
    fun `cached matches compare identifiers case insensitively`() = runTest {
        cached(
            FakeAircraft(
                hex = "AAAAAA",
                callsign = "DLH453",
                registration = "D-AIUE",
                airframe = "A320",
                squawk = "1000",
            ),
            FakeAircraft(hex = "BBBBBB", callsign = "RYR12", registration = null, airframe = null, squawk = null),
        )

        repo.cachedMatches(SearchQuery(listOf(SearchTerm("dlh453")))).map { it.hex } shouldBe listOf("AAAAAA")
        repo.cachedMatches(SearchQuery(listOf(SearchTerm("d-aiue")))).map { it.hex } shouldBe listOf("AAAAAA")
        repo.cachedMatches(SearchQuery(listOf(SearchTerm("a320")))).map { it.hex } shouldBe listOf("AAAAAA")
        repo.cachedMatches(SearchQuery(listOf(SearchTerm("1000")))).map { it.hex } shouldBe listOf("AAAAAA")
        repo.cachedMatches(SearchQuery(listOf(SearchTerm("bbbbbb")))).map { it.hex } shouldBe listOf("BBBBBB")
    }

    @Test
    fun `category terms match the observation flags`() = runTest {
        cached(
            FakeAircraft(hex = "AAAAAA", military = true),
            FakeAircraft(hex = "BBBBBB", ladd = true),
            FakeAircraft(hex = "CCCCCC"),
        )

        val matches = repo.cachedMatches(
            SearchQuery(listOf(SearchTerm(categories = setOf(SearchCategory.MILITARY, SearchCategory.LADD))))
        )

        matches.map { it.hex } shouldContainExactlyInAnyOrder listOf("AAAAAA", "BBBBBB")
    }

    @Test
    fun `a failed search falls back to the cache`() = runTest {
        cached(FakeAircraft(hex = "AAAAAA"))
        coEvery { aircraftRepo.search(any()) } throws IOException("offline")

        val result = repo.search(SearchQuery(listOf(SearchTerm("AAAAAA"))))

        result.error.shouldBeInstanceOf<IOException>()
        result.aircraft.map { it.hex } shouldBe listOf("AAAAAA")
        result.cacheOnly.map { it.hex } shouldBe listOf("AAAAAA")
    }

    companion object {
        private val USAGE = UsageUpdate(
            scope = "principal",
            bucket = "SEARCH",
            resetsAt = 1_710_028_800_000L,
            allowance = Allowance(limit = 25, used = 1, reserved = 0, remaining = 24),
        )
    }
}
