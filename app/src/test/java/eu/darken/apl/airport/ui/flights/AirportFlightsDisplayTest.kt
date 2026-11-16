package eu.darken.apl.airport.ui.flights

import eu.darken.apl.airport.core.AirportFlightList
import eu.darken.apl.airport.core.airportFlightListFixture
import eu.darken.apl.airport.core.airportFlightsFixture
import eu.darken.apl.airport.ui.flights.AirportFlightsDisplay.Basis
import eu.darken.apl.airport.ui.flights.AirportFlightsDisplay.Evidence
import eu.darken.apl.airport.ui.flights.AirportFlightsDisplay.FlightStatus
import eu.darken.apl.airport.ui.flights.AirportFlightsDisplay.Identity
import eu.darken.apl.airport.ui.flights.AirportFlightsDisplay.OtherAirport
import eu.darken.apl.airport.ui.flights.AirportFlightsDisplay.Section
import eu.darken.apl.airport.ui.flights.AirportFlightsDisplay.SectionKind
import eu.darken.apl.airport.ui.flights.AirportFlightsDisplay.Source
import eu.darken.apl.airport.ui.flights.AirportFlightsDisplay.SourceKind
import eu.darken.apl.airport.ui.flights.AirportFlightsDisplay.SourceState
import eu.darken.apl.airport.ui.flights.AirportFlightsDisplay.TimeKind
import eu.darken.apl.server.api.AirportFlightDto
import eu.darken.apl.server.api.AirportFlightEntryDto
import eu.darken.apl.server.api.AirportFlightTimeDto
import eu.darken.apl.server.api.AirportFlightTimesDto
import eu.darken.apl.server.api.AirportFlightsAirportDto
import eu.darken.apl.server.api.AirportFlightsResponse
import eu.darken.apl.server.api.AirportFlightsSourceDto
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class AirportFlightsDisplayTest : BaseTest() {

    /** answered-load.json's evaluatedAt, 2026-11-13 13:00 in Berlin. */
    private val evaluatedAt = Instant.ofEpochMilli(1794571200000)
    private val now = evaluatedAt.plusSeconds(30)
    private val deviceZone = ZoneId.of("America/New_York")

    private fun display(
        list: AirportFlightList = airportFlightListFixture("answered-load.json"),
        airportTimeZone: String? = null,
        now: Instant = this.now,
    ) = AirportFlightsDisplay.from(list, airportTimeZone, now, deviceZone)

    private fun AirportFlightsDisplay.section(kind: SectionKind) = sections.single { it.kind == kind }

    private fun AirportFlightsDisplay.row(key: String) = sections.flatMap { it.rows }.single { it.id.endsWith("/$key") }

    private fun listWith(
        entries: List<AirportFlightEntryDto>,
        timeZone: String? = "Europe/Berlin",
        base: AirportFlightsResponse = airportFlightsFixture("answered-load.json"),
    ): AirportFlightList = AirportFlightList.from(
        base.copy(
            airport = AirportFlightsAirportDto(id = "oa:2212", timeZone = timeZone),
            entries = entries,
        )
    )!!

    private fun arrivalAt(key: String, at: Instant) = AirportFlightEntryDto(
        key = key,
        direction = "arrival",
        flight = AirportFlightDto(callsign = key.uppercase()),
        times = AirportFlightTimesDto(estimated = AirportFlightTimeDto(at = at.toEpochMilli(), basis = "runway")),
    )

    @Test
    fun `every known value of a load maps to its own wording`() {
        val display = display()

        display.sections.flatMap { it.rows }.mapNotNull { it.status }.toSet() shouldBe FlightStatus.entries.toSet()
        display.sections.flatMap { it.rows }.flatMap { it.evidence }.toSet() shouldBe Evidence.entries.toSet()
        display.sections.flatMap { it.rows }.mapNotNull { it.time?.basis }.toSet() shouldBe Basis.entries.toSet()
        display.sources shouldContainExactly listOf(
            Source(SourceKind.KNOWN_ROUTES, SourceState.OK),
            Source(SourceKind.CACHED_ROUTES, SourceState.PARTIAL),
            Source(SourceKind.NEARBY_TRAFFIC, SourceState.STALE),
            Source(SourceKind.HISTORY, SourceState.UNAVAILABLE),
            // "warming_up" is a state this build does not know
            Source(SourceKind.SCHEDULES, SourceState.STALE),
            // "crowd_reports" is a kind this build does not know
            Source(SourceKind.OTHER, SourceState.OK),
        )
    }

    @Test
    fun `values this build does not know fall back`() {
        val display = display()

        // "holding_pattern"
        display.row("k-a6").status.shouldBeNull()
        display.row("k-a6").evidence.shouldBeEmpty()
        // "radar_contact" is left out of the explanation
        display.row("k-a3").evidence shouldContainExactly listOf(Evidence.NEARBY_LOW)
        // "block" and no basis at all
        display.row("k-a4").time!!.basis.shouldBeNull()
        display.row("k-d4").time!!.basis.shouldBeNull()
    }

    @Test
    fun `unknown source kinds fold into one with the worst state`() {
        val response = airportFlightsFixture("answered-load.json").copy(
            sources = listOf(
                AirportFlightsSourceDto(kind = "crowd_reports", state = "ok"),
                AirportFlightsSourceDto(kind = "history", state = "ok"),
                AirportFlightsSourceDto(kind = "satellite", state = "unavailable"),
                AirportFlightsSourceDto(kind = "radar", state = "partial"),
            )
        )

        display(list = AirportFlightList.from(response)!!).sources shouldContainExactly listOf(
            Source(SourceKind.HISTORY, SourceState.OK),
            Source(SourceKind.OTHER, SourceState.UNAVAILABLE),
        )
    }

    @Test
    fun `rows carry status, estimate, time kind and other airport`() {
        val display = display()

        display.row("k-a1").let {
            it.status shouldBe FlightStatus.APPROACHING
            it.estimated shouldBe true
            it.time!!.kind shouldBe TimeKind.EXPECTED
            it.time.basis shouldBe Basis.RUNWAY
            it.otherAirport shouldBe OtherAirport(code = "XYZ", name = "Example County International Airport", flag = null)
            it.evidence shouldContainExactly listOf(Evidence.KNOWN_ROUTE, Evidence.FITTED_LEG, Evidence.DESCENDING)
        }
        // Actual wins over estimated and scheduled
        display.row("k-a2").time!!.let {
            it.kind shouldBe TimeKind.ACTUAL
            it.basis shouldBe Basis.RUNWAY
            it.clock.local shouldBe LocalDateTime.of(2026, 11, 13, 12, 52)
        }
        display.row("k-a2").otherAirport shouldBe OtherAirport(code = "XMP", name = null, flag = null)
        display.row("k-a4").otherAirport shouldBe OtherAirport(code = null, name = "Sample Regional Airport", flag = null)
        display.row("k-a5").time!!.kind shouldBe TimeKind.SCHEDULED
        // Without any time it is happening now
        display.row("k-a3").time.shouldBeNull()
        display.row("k-u1").let {
            it.status.shouldBeNull()
            it.estimated shouldBe false
        }
    }

    @Test
    fun `the other airport's country becomes its flag`() {
        val display = display(list = airportFlightListFixture("answered-compact.json"))

        display.row("k-c1").otherAirport shouldBe OtherAirport(
            code = "XYZ",
            name = "Example County International Airport",
            flag = "🇩🇪",
        )
        display.row("k-c2").otherAirport!!.flag shouldBe "🇺🇸"
        // No country
        display.row("k-c3").otherAirport shouldBe OtherAirport(code = "XMP", name = null, flag = null)
        // "D", "DEU" and "1A"
        display.row("k-c5").otherAirport.shouldNotBeNull().flag.shouldBeNull()
        display.row("k-c6").otherAirport.shouldNotBeNull().flag.shouldBeNull()
        display.row("k-c7").otherAirport.shouldNotBeNull().flag.shouldBeNull()
        // A country alone names no airport
        display.row("k-c4").otherAirport.shouldBeNull()
    }

    @Test
    fun `sections keep the server's order and show more continues each direction`() {
        val display = display()

        display.sections.map { it.kind } shouldContainExactly listOf(
            SectionKind.ARRIVALS, SectionKind.DEPARTURES, SectionKind.OTHER,
        )
        display.section(SectionKind.ARRIVALS).let {
            it.rows.map { row -> row.id } shouldContainExactly listOf(
                "arrival/k-a1", "arrival/k-a2", "arrival/k-a3", "arrival/k-a4", "arrival/k-a5", "arrival/k-a6",
            )
            it.count shouldBe 31
            it.showMoreDirection.shouldBeNull()
        }
        display.section(SectionKind.DEPARTURES).let {
            it.rows shouldHaveSize 5
            it.count shouldBe 64
            it.showMoreDirection shouldBe "departure"
        }
        display.section(SectionKind.OTHER).let {
            it.rows.map { row -> row.id } shouldContainExactly listOf("unknown/k-u1")
            it.count shouldBe 3
            it.showMoreDirection shouldBe "unknown"
        }
    }

    @Test
    fun `unknown and future directions fold into other in server order`() {
        val list = airportFlightListFixture("answered-other-directions.json")

        val display = display(list = list)

        display.section(SectionKind.ARRIVALS).rows.shouldBeEmpty()
        display.section(SectionKind.DEPARTURES).rows.shouldBeEmpty()
        display.section(SectionKind.OTHER).let {
            it.rows.map { row -> row.id } shouldContainExactly listOf(
                "unknown/k-u1", "overflight/k-o1", "diverted_in/k-v1",
            )
            it.count shouldBe 18
            it.showMoreDirection shouldBe "unknown"
        }

        val unknownDone = list.copy(
            directions = list.directions.map { if (it.direction == "unknown") it.copy(next = null) else it },
        )
        display(list = unknownDone).section(SectionKind.OTHER).showMoreDirection shouldBe "overflight"
    }

    /** Sections of [display] with one row in [rows] and a next page in [next], none else. */
    private fun withSections(
        rows: Set<SectionKind> = emptySet(),
        next: Set<SectionKind> = emptySet(),
    ): AirportFlightsDisplay {
        val base = display()
        val row = base.row("k-a1")
        return base.copy(
            sections = SectionKind.entries.map { kind ->
                Section(
                    kind = kind,
                    count = 0,
                    rows = if (kind in rows) listOf(row) else emptyList(),
                    showMoreDirection = if (kind in next) "c-$kind" else null,
                )
            }
        )
    }

    @Test
    fun `other is a segment only while it has rows or a next page`() {
        display().visibleSegments shouldContainExactly listOf(
            SectionKind.ARRIVALS, SectionKind.DEPARTURES, SectionKind.OTHER,
        )
        val historyOnly = display(list = airportFlightListFixture("history-only.json"))
        historyOnly.visibleSegments shouldContainExactly listOf(SectionKind.ARRIVALS, SectionKind.DEPARTURES)

        withSections().visibleSegments shouldContainExactly listOf(SectionKind.ARRIVALS, SectionKind.DEPARTURES)
        withSections(rows = setOf(SectionKind.OTHER)).visibleSegments shouldContainExactly listOf(
            SectionKind.ARRIVALS, SectionKind.DEPARTURES, SectionKind.OTHER,
        )
        withSections(next = setOf(SectionKind.OTHER)).visibleSegments shouldContainExactly listOf(
            SectionKind.ARRIVALS, SectionKind.DEPARTURES, SectionKind.OTHER,
        )
    }

    @Test
    fun `without a previous selection arrivals come first, else the first section with something to show`() {
        withSections(rows = setOf(SectionKind.ARRIVALS, SectionKind.DEPARTURES)).selection(null) shouldBe
            SectionKind.ARRIVALS
        withSections(next = setOf(SectionKind.DEPARTURES), rows = setOf(SectionKind.OTHER)).selection(null) shouldBe
            SectionKind.DEPARTURES
        withSections(rows = setOf(SectionKind.OTHER)).selection(null) shouldBe SectionKind.OTHER
        withSections(next = setOf(SectionKind.OTHER)).selection(null) shouldBe SectionKind.OTHER
        withSections().selection(null) shouldBe SectionKind.ARRIVALS
    }

    @Test
    fun `a previous selection stays while it is a segment`() {
        // Departures emptied
        withSections(rows = setOf(SectionKind.ARRIVALS)).selection(SectionKind.DEPARTURES) shouldBe
            SectionKind.DEPARTURES
        withSections().selection(SectionKind.DEPARTURES) shouldBe SectionKind.DEPARTURES
        withSections(rows = setOf(SectionKind.DEPARTURES)).selection(SectionKind.ARRIVALS) shouldBe
            SectionKind.ARRIVALS
        withSections(rows = setOf(SectionKind.ARRIVALS), next = setOf(SectionKind.OTHER))
            .selection(SectionKind.OTHER) shouldBe SectionKind.OTHER
    }

    @Test
    fun `a previous other that is gone falls back`() {
        withSections(rows = setOf(SectionKind.ARRIVALS, SectionKind.DEPARTURES)).selection(SectionKind.OTHER) shouldBe
            SectionKind.ARRIVALS
        withSections(rows = setOf(SectionKind.DEPARTURES)).selection(SectionKind.OTHER) shouldBe
            SectionKind.DEPARTURES
        withSections().selection(SectionKind.OTHER) shouldBe SectionKind.ARRIVALS
    }

    @Test
    fun `history only and schedule only lists have rows`() {
        val history = display(list = airportFlightListFixture("history-only.json"))
        history.section(SectionKind.ARRIVALS).rows shouldHaveSize 2
        history.section(SectionKind.DEPARTURES).rows shouldHaveSize 1
        history.sections.flatMap { it.rows }.forEach {
            it.time!!.kind shouldBe TimeKind.ACTUAL
            (it.identity is Identity.Flight) shouldBe true
        }
        history.sources shouldContainExactly listOf(Source(SourceKind.HISTORY, SourceState.OK))

        val schedule = display(list = airportFlightListFixture("schedule-only.json"))
        schedule.section(SectionKind.ARRIVALS).rows shouldHaveSize 2
        schedule.section(SectionKind.DEPARTURES).rows shouldHaveSize 3
        schedule.sections.flatMap { it.rows }.forEach {
            it.time!!.kind shouldBe TimeKind.EXPECTED
            (it.identity is Identity.Flight) shouldBe true
        }
        schedule.row("k-s5").status shouldBe FlightStatus.CANCELLED
    }

    @Test
    fun `each time gets the offset in force at its moment`() {
        // Berlin leaves summer time on 2026-10-25 at 01:00 UTC, 02:30 local happens twice
        val summer = Instant.parse("2026-10-25T00:30:00Z")
        val winter = Instant.parse("2026-10-25T01:30:00Z")
        val list = listWith(listOf(arrivalAt("k-1", summer), arrivalAt("k-2", winter)))

        val display = display(list = list, now = Instant.parse("2026-10-25T10:00:00Z"))

        display.row("k-1").time!!.clock.let {
            it.local shouldBe LocalDateTime.of(2026, 10, 25, 2, 30)
            it.offset shouldBe ZoneOffset.ofHours(2)
            it.date.shouldBeNull()
        }
        display.row("k-2").time!!.clock.let {
            it.local shouldBe LocalDateTime.of(2026, 10, 25, 2, 30)
            it.offset shouldBe ZoneOffset.ofHours(1)
            it.date.shouldBeNull()
        }
    }

    @Test
    fun `a time on another airport day shows its date`() {
        // 23:30 in Berlin, both times are still the 13th in UTC
        val now = Instant.parse("2026-11-13T22:30:00Z")
        val list = listWith(
            listOf(
                arrivalAt("k-today", Instant.parse("2026-11-13T22:50:00Z")),
                arrivalAt("k-tomorrow", Instant.parse("2026-11-13T23:10:00Z")),
            )
        )

        val display = display(list = list, now = now)

        display.row("k-today").time!!.clock.date.shouldBeNull()
        display.row("k-tomorrow").time!!.clock.let {
            it.date shouldBe LocalDate.of(2026, 11, 14)
            it.local shouldBe LocalDateTime.of(2026, 11, 14, 0, 10)
        }
    }

    @Test
    fun `a time zone that is not usable falls back to the device`() {
        val list = listWith(listOf(arrivalAt("k-1", Instant.parse("2026-11-13T12:10:00Z"))), timeZone = "Mars/Olympus")

        display(list = list).row("k-1").time!!.clock.let {
            it.offset shouldBe ZoneOffset.ofHours(-5)
            it.local shouldBe LocalDateTime.of(2026, 11, 13, 7, 10)
        }
    }

    @Test
    fun `the list's time zone wins over the airport's, which fills in for a missing one`() {
        val at = Instant.parse("2026-11-13T12:10:00Z")

        display(list = listWith(listOf(arrivalAt("k-1", at))), airportTimeZone = "Asia/Tokyo")
            .row("k-1").time!!.clock.offset shouldBe ZoneOffset.ofHours(1)

        display(list = listWith(listOf(arrivalAt("k-1", at)), timeZone = null), airportTimeZone = "Asia/Tokyo")
            .row("k-1").time!!.clock.offset shouldBe ZoneOffset.ofHours(9)

        // The device's New York
        display(list = listWith(listOf(arrivalAt("k-1", at)), timeZone = null), airportTimeZone = null)
            .row("k-1").time!!.clock.offset shouldBe ZoneOffset.ofHours(-5)
    }

    @Test
    fun `a flight without number or callsign is named by its aircraft`() {
        val entries = listOf(
            AirportFlightEntryDto(
                key = "k-both", direction = "arrival",
                flight = AirportFlightDto(callsign = "XLD77", number = "XD77"),
            ),
            AirportFlightEntryDto(
                key = "k-registration", direction = "arrival",
                flight = AirportFlightDto(callsign = " "), aircraftId = "3c6444",
            ),
            AirportFlightEntryDto(key = "k-hex", direction = "arrival", aircraftId = "406a91"),
            AirportFlightEntryDto(key = "k-gone", direction = "arrival", aircraftId = "ffffff"),
            AirportFlightEntryDto(key = "k-nothing", direction = "arrival"),
        )

        val display = display(list = listWith(entries))

        display.row("k-both").identity shouldBe Identity.Flight(callsign = "XLD77", number = "XD77")
        display.row("k-registration").let {
            it.identity shouldBe Identity.Registration("X-EXMP")
            it.aircraftHex shouldBe "3c6444"
        }
        display.row("k-hex").let {
            it.identity shouldBe Identity.Hex("406A91")
            it.aircraftHex shouldBe "406a91"
        }
        display.row("k-gone").let {
            it.identity shouldBe Identity.Unidentified
            it.aircraftHex.shouldBeNull()
        }
        display.row("k-nothing").let {
            it.identity shouldBe Identity.Unidentified
            it.aircraftHex.shouldBeNull()
        }
    }

    @Test
    fun `only an aircraft in the answer can be opened`() {
        val display = display()

        display.row("k-a1").aircraftHex shouldBe "3c6444"
        // 39c4f2 is not among the answer's aircraft
        display.row("k-a3").let {
            it.aircraftHex.shouldBeNull()
            it.identity shouldBe Identity.Flight(callsign = "XLC1418", number = null)
        }
        display.row("k-a4").aircraftHex.shouldBeNull()
    }
}
