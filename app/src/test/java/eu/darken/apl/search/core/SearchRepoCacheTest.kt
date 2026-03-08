package eu.darken.apl.search.core

import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.api.AirplanesLiveApi
import eu.darken.apl.main.core.api.AirplanesLiveEndpoint
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.HttpException
import retrofit2.Response
import testhelper.BaseTest
import java.time.Instant

class SearchRepoCacheTest : BaseTest() {

    private lateinit var endpoint: AirplanesLiveEndpoint
    private lateinit var aircraftRepo: AircraftRepo
    private lateinit var repo: SearchRepo

    private val aircraftCache = MutableStateFlow<Map<String, Aircraft>>(emptyMap())

    @BeforeEach
    fun setup() {
        endpoint = mockk()
        aircraftRepo = mockk(relaxUnitFun = true)

        every { aircraftRepo.aircraft } returns aircraftCache

        coEvery { endpoint.getBySquawk(any()) } returns emptyList()
        coEvery { endpoint.getByHex(any()) } returns emptyList()
        coEvery { endpoint.getByAirframe(any()) } returns emptyList()
        coEvery { endpoint.getByCallsign(any()) } returns emptyList()
        coEvery { endpoint.getByRegistration(any()) } returns emptyList()

        repo = SearchRepo(
            endpoint = endpoint,
            aircraftRepo = aircraftRepo,
        )
    }

    private fun mockAircraft(
        hex: String = "ABCDEF",
        callsign: String? = null,
        registration: String? = null,
        squawk: String? = null,
        airframe: String? = null,
    ): Aircraft = mockk(relaxed = true) {
        every { this@mockk.hex } returns hex.uppercase()
        every { this@mockk.callsign } returns callsign
        every { this@mockk.registration } returns registration
        every { this@mockk.squawk } returns squawk
        every { this@mockk.airframe } returns airframe
        every { this@mockk.seenAt } returns Instant.now()
    }

    private fun apiAircraft(hex: String = "ABCDEF"): AirplanesLiveApi.Aircraft = mockk(relaxed = true) {
        every { this@mockk.hex } returns hex.uppercase()
        every { this@mockk.seenAt } returns Instant.now()
    }

    private fun http429(): HttpException =
        HttpException(Response.error<Any>(429, "rate limited".toResponseBody()))

    @Test
    fun `cache emitted before API results`() = runTest {
        val cachedAc = mockAircraft(hex = "AAAAAA")
        aircraftCache.value = mapOf("AAAAAA" to cachedAc)

        val results = repo.liveSearch(SearchQuery.Hex("AAAAAA"), SearchRepo.CachePolicy.CACHE_FIRST_UI).toList()

        results.first().aircraft.map { it.hex } shouldContainExactlyInAnyOrder listOf("AAAAAA")
        results.first().searching shouldBe true
        results.first().cacheOnlyCount shouldBe 1
    }

    @Test
    fun `API results overwrite cache for same hex, cache extras kept`() = runTest {
        val cachedAc1 = mockAircraft(hex = "AAAAAA")
        val cachedAc2 = mockAircraft(hex = "BBBBBB")
        aircraftCache.value = mapOf("AAAAAA" to cachedAc1, "BBBBBB" to cachedAc2)

        val apiAc = apiAircraft(hex = "AAAAAA")
        coEvery { endpoint.getByHex(any()) } returns listOf(apiAc)

        val results = repo.liveSearch(SearchQuery.Hex(setOf("AAAAAA", "BBBBBB")), SearchRepo.CachePolicy.CACHE_FIRST_UI).toList()

        val finalResult = results.last()
        finalResult.searching shouldBe false
        finalResult.aircraft.map { it.hex } shouldContainExactlyInAnyOrder listOf("AAAAAA", "BBBBBB")
        finalResult.cacheOnlyCount shouldBe 1
    }

    @Test
    fun `full API failure with cache fallback`() = runTest {
        val cachedAc = mockAircraft(hex = "AAAAAA")
        aircraftCache.value = mapOf("AAAAAA" to cachedAc)

        coEvery { endpoint.getByHex(any()) } throws http429()

        val results = repo.liveSearch(SearchQuery.Hex("AAAAAA"), SearchRepo.CachePolicy.CACHE_FIRST_UI).toList()

        val finalResult = results.last()
        finalResult.aircraft.map { it.hex } shouldContainExactlyInAnyOrder listOf("AAAAAA")
        finalResult.errors.size shouldBe 1
        finalResult.cacheOnlyCount shouldBe 1
    }

    @Test
    fun `API_ONLY returns no cache entries`() = runTest {
        val cachedAc = mockAircraft(hex = "AAAAAA")
        aircraftCache.value = mapOf("AAAAAA" to cachedAc)

        coEvery { endpoint.getByHex(any()) } returns emptyList()

        val result = repo.search(SearchQuery.Hex("AAAAAA"))

        result.aircraft.shouldBeEmpty()
        result.cacheOnlyCount shouldBe 0
    }

    @Test
    fun `empty query returns empty immediately`() = runTest {
        val cachedAc = mockAircraft(hex = "AAAAAA")
        aircraftCache.value = mapOf("AAAAAA" to cachedAc)

        val results = repo.liveSearch(SearchQuery.Hex(emptySet()), SearchRepo.CachePolicy.CACHE_FIRST_UI).toList()

        val finalResult = results.last()
        finalResult.aircraft.shouldBeEmpty()
        finalResult.cacheOnlyCount shouldBe 0
    }

    @Test
    fun `blank tokens in All query are filtered`() = runTest {
        val cachedAc = mockAircraft(hex = "AAAAAA")
        aircraftCache.value = mapOf("AAAAAA" to cachedAc)

        val results = repo.liveSearch(
            SearchQuery.All(setOf("", " ", "AAAAAA")),
            SearchRepo.CachePolicy.CACHE_FIRST_UI,
        ).toList()

        results.first().aircraft.map { it.hex } shouldContainExactlyInAnyOrder listOf("AAAAAA")
    }

    @Test
    fun `interesting query does not use cache`() = runTest {
        val cachedAc = mockAircraft(hex = "AAAAAA")
        aircraftCache.value = mapOf("AAAAAA" to cachedAc)

        coEvery { endpoint.getMilitary() } returns emptyList()
        coEvery { endpoint.getLADD() } returns emptyList()
        coEvery { endpoint.getPIA() } returns emptyList()

        val results = repo.liveSearch(
            SearchQuery.Interesting(military = true),
            SearchRepo.CachePolicy.CACHE_FIRST_UI,
        ).toList()

        val finalResult = results.last()
        finalResult.aircraft.shouldBeEmpty()
        finalResult.cacheOnlyCount shouldBe 0
    }

    @Test
    fun `partial API failure still merges cache extras`() = runTest {
        val cachedAc1 = mockAircraft(hex = "AAAAAA", callsign = "FLG123")
        val cachedAc2 = mockAircraft(hex = "BBBBBB", callsign = "FLG456")
        aircraftCache.value = mapOf("AAAAAA" to cachedAc1, "BBBBBB" to cachedAc2)

        val apiAc = apiAircraft(hex = "AAAAAA")
        coEvery { endpoint.getByCallsign(any()) } returns listOf(apiAc)
        // Other endpoint calls already return emptyList from setup

        val results = repo.liveSearch(
            SearchQuery.Callsign(setOf("FLG123", "FLG456")),
            SearchRepo.CachePolicy.CACHE_FIRST_UI,
        ).toList()

        val finalResult = results.last()
        finalResult.aircraft.map { it.hex } shouldContainExactlyInAnyOrder listOf("AAAAAA", "BBBBBB")
        finalResult.cacheOnlyCount shouldBe 1
    }
}
