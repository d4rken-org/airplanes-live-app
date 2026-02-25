package eu.darken.apl.search.core

import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.main.core.api.AirplanesLiveApi
import eu.darken.apl.main.core.api.AirplanesLiveEndpoint
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.HttpException
import retrofit2.Response
import testhelper.BaseTest

class SearchRepoTest : BaseTest() {

    private lateinit var endpoint: AirplanesLiveEndpoint
    private lateinit var aircraftRepo: AircraftRepo
    private lateinit var repo: SearchRepo

    @BeforeEach
    fun setup() {
        endpoint = mockk()
        aircraftRepo = mockk(relaxUnitFun = true)

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

    private fun mockAircraft(): AirplanesLiveApi.Aircraft = mockk(relaxed = true)

    private fun http429(): HttpException =
        HttpException(Response.error<Any>(429, "rate limited".toResponseBody()))

    @Test
    fun `interesting search returns results when all calls succeed`() = runTest {
        val milAc = mockAircraft()
        val laddAc = mockAircraft()
        val piaAc = mockAircraft()
        coEvery { endpoint.getMilitary() } returns listOf(milAc)
        coEvery { endpoint.getLADD() } returns listOf(laddAc)
        coEvery { endpoint.getPIA() } returns listOf(piaAc)

        val result = repo.search(SearchQuery.Interesting(military = true, ladd = true, pia = true))

        result.aircraft.size shouldBe 3
        result.errors.shouldBeEmpty()
        result.searching shouldBe false
    }

    @Test
    fun `interesting search returns partial results when one call fails`() = runTest {
        val milAc = mockAircraft()
        val piaAc = mockAircraft()
        coEvery { endpoint.getMilitary() } returns listOf(milAc)
        coEvery { endpoint.getLADD() } throws http429()
        coEvery { endpoint.getPIA() } returns listOf(piaAc)

        val result = repo.search(SearchQuery.Interesting(military = true, ladd = true, pia = true))

        result.aircraft.size shouldBe 2
        result.errors.size shouldBe 1
        result.errors.first().shouldBeInstanceOf<HttpException>().code() shouldBe 429
        result.searching shouldBe false
    }

    @Test
    fun `interesting search returns empty results when all calls fail`() = runTest {
        coEvery { endpoint.getMilitary() } throws http429()
        coEvery { endpoint.getLADD() } throws http429()
        coEvery { endpoint.getPIA() } throws http429()

        val result = repo.search(SearchQuery.Interesting(military = true, ladd = true, pia = true))

        result.aircraft.shouldBeEmpty()
        result.errors.size shouldBe 3
        result.searching shouldBe false
    }
}
