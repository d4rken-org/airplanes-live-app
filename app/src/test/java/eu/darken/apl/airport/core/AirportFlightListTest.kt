package eu.darken.apl.airport.core

import eu.darken.apl.server.api.AirportFlightEntryDto
import eu.darken.apl.server.api.AirportFlightsGroupDto
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Instant

class AirportFlightListTest : BaseTest() {

    private fun AirportFlightList.keys(direction: String) = direction(direction)!!.entries.map { it.key }

    @Test
    fun `a load keeps every direction with its cursor and entries in server order`() {
        val list = airportFlightListFixture("answered-load.json")

        list.evaluationId shouldBe "ev-9"
        list.evaluatedAt shouldBe Instant.ofEpochMilli(1794571200000)
        list.expiresAt shouldBe Instant.ofEpochMilli(1794571260000)
        list.evaluationExpiresAt shouldBe Instant.ofEpochMilli(1794573000000)
        list.airport shouldBe AirportFlightList.AirportInfo(id = "oa:2212", timeZone = "Europe/Berlin", elevationFt = 364)
        list.sources.map { it.kind } shouldContainExactly listOf(
            "known_routes", "cached_routes", "nearby_traffic", "history", "schedules", "crowd_reports",
        )

        list.directions.map { Triple(it.direction, it.total, it.next) } shouldContainExactly listOf(
            Triple("arrival", 31, null),
            Triple("departure", 64, "c-dep-2"),
            Triple("unknown", 3, "c-unk-2"),
        )
        list.keys("arrival") shouldContainExactly listOf("k-a1", "k-a2", "k-a3", "k-a4", "k-a5", "k-a6")
        list.keys("departure") shouldContainExactly listOf("k-d1", "k-d2", "k-d3", "k-d4", "k-d5")
        list.keys("unknown") shouldContainExactly listOf("k-u1")

        list.aircraft.keys shouldContainExactly setOf("3c6444", "406a91")
        list.aircraft["3c6444"] shouldBe AirportFlightList.Aircraft("3c6444", "X-EXMP")
        list.aircraft["406a91"] shouldBe AirportFlightList.Aircraft("406a91", null)
    }

    @Test
    fun `a page appends to its direction and replaces its cursor`() {
        val list = airportFlightListFixture("answered-load.json")

        val paged = list.appendPage(airportFlightsFixture("answered-page.json")).shouldNotBeNull()

        paged.keys("departure") shouldContainExactly listOf(
            "k-d1", "k-d2", "k-d3", "k-d4", "k-d5", "k-d21", "k-d22", "k-d23",
        )
        paged.direction("departure")!!.next shouldBe "c-dep-3"
        paged.direction("departure")!!.total shouldBe 64
        paged.direction("arrival") shouldBe list.direction("arrival")
        paged.direction("unknown") shouldBe list.direction("unknown")
        paged.aircraft.keys shouldContainExactly setOf("3c6444", "406a91", "4b18f2")
        paged.evaluatedAt shouldBe list.evaluatedAt
        paged.sources shouldBe list.sources
    }

    @Test
    fun `a page repeating listed entries does not list them twice`() {
        val list = airportFlightListFixture("answered-load.json")
        val page = airportFlightsFixture("answered-page.json")

        val once = list.appendPage(page)!!
        val twice = once.appendPage(page)!!

        twice.keys("departure") shouldBe once.keys("departure")
    }

    @Test
    fun `a page of another evaluation is discarded`() {
        val list = airportFlightListFixture("answered-load.json")
        val page = airportFlightsFixture("answered-page.json").copy(evaluationId = "ev-8")

        list.appendPage(page).shouldBeNull()
    }

    @Test
    fun `directions this build does not know keep their own cursors`() {
        val list = airportFlightListFixture("answered-other-directions.json")

        list.directions.map { Triple(it.direction, it.total, it.next) } shouldContainExactly listOf(
            Triple("unknown", 4, "c-unk-2"),
            Triple("overflight", 12, "c-ovf-2"),
            Triple("diverted_in", 2, "c-div-2"),
        )
        list.keys("unknown") shouldContainExactly listOf("k-u1")
        list.keys("overflight") shouldContainExactly listOf("k-o1")
        list.keys("diverted_in") shouldContainExactly listOf("k-v1")
        list.airport!!.timeZone.shouldBeNull()
    }

    @Test
    fun `a page of a direction this build does not know only changes that direction`() {
        val list = airportFlightListFixture("answered-other-directions.json")
        val page = airportFlightsFixture("answered-page.json").copy(
            evaluationId = "ev-10",
            groups = listOf(AirportFlightsGroupDto(direction = "overflight", total = 12, next = null)),
            entries = listOf(AirportFlightEntryDto(key = "k-o2", direction = "overflight")),
        )

        val paged = list.appendPage(page).shouldNotBeNull()

        paged.keys("overflight") shouldContainExactly listOf("k-o1", "k-o2")
        paged.direction("overflight")!!.next.shouldBeNull()
        paged.direction("unknown") shouldBe list.direction("unknown")
        paged.direction("diverted_in") shouldBe list.direction("diverted_in")
    }

    @Test
    fun `entries of a direction without a group follow the grouped ones`() {
        val response = airportFlightsFixture("answered-load.json").let {
            it.copy(entries = it.entries + AirportFlightEntryDto(key = "k-x1", direction = "go_around"))
        }

        val list = AirportFlightList.from(response).shouldNotBeNull()

        list.directions.map { it.direction } shouldContainExactly listOf("arrival", "departure", "unknown", "go_around")
        list.direction("go_around")!!.let {
            it.total.shouldBeNull()
            it.next.shouldBeNull()
            it.entries.map { entry -> entry.key } shouldContainExactly listOf("k-x1")
        }
    }

    @Test
    fun `only an answered load with an evaluation makes a list`() {
        val load = airportFlightsFixture("answered-load.json")

        AirportFlightList.from(load.copy(evaluationId = null)).shouldBeNull()
        AirportFlightList.from(load.copy(evaluatedAt = null)).shouldBeNull()
        AirportFlightList.from(airportFlightsFixture("outcome-result-expired.json")).shouldBeNull()
    }
}
