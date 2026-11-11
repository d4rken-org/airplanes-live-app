package eu.darken.apl.airport.ui

import eu.darken.apl.airport.core.AirportRef
import eu.darken.apl.airport.core.AirportRepo
import eu.darken.apl.airport.core.AirportSearch
import eu.darken.apl.airport.core.AirportTarget
import eu.darken.apl.airport.core.toAirport
import eu.darken.apl.airport.core.toMapOptions
import eu.darken.apl.common.WebpageTool
import eu.darken.apl.common.navigation.NavEvent
import eu.darken.apl.map.ui.DestinationMap
import eu.darken.apl.server.api.AirportSearchResultDto
import eu.darken.apl.server.api.AirportSearchResultDto.Companion.STATUS_ANSWERED
import eu.darken.apl.server.api.AirportSummaryDto
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.coroutine.TestDispatcherProvider

class AirportDetailsViewModelTest : BaseTest() {

    private val airportRepo = mockk<AirportRepo>()
    private val webpageTool = mockk<WebpageTool>(relaxed = true)

    private val frankfurtDto = AirportSummaryDto(
        id = "oa:2212",
        name = "Frankfurt am Main Airport",
        type = AirportSummaryDto.TYPE_LARGE,
        ident = "EDDF",
        latitude = 50.033333,
        longitude = 8.570556,
        municipality = "Frankfurt am Main",
        region = "DE-HE",
        icao = "EDDF",
        matchedCode = "EDDF",
    )
    private val frankfurt = frankfurtDto.toAirport()

    private val frankfurtWithFactsDto = frankfurtDto.copy(
        elevationFt = 364,
        scheduledService = true,
        website = "https://www.frankfurt-airport.de/",
        wikipedia = "https://en.wikipedia.org/wiki/Frankfurt_Airport",
    )
    private val frankfurtWithFacts = frankfurtWithFactsDto.toAirport()

    private val routeRef = AirportRef.create(
        icao = "EDDF",
        iata = "FRA",
        name = "Frankfurt Main",
        countryName = "Germany",
        latitude = 50.03,
        longitude = 8.57,
    )!!
    private val partial = AirportTarget.Partial(routeRef)

    private val matchFound = AirportSearch.Answered(
        listOf(AirportSearchResultDto(0, STATUS_ANSWERED, listOf(frankfurtDto)))
    )
    private val nothingFound = AirportSearch.Answered(
        listOf(AirportSearchResultDto(0, STATUS_ANSWERED, emptyList()))
    )

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.createViewModel(): AirportDetailsViewModel {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        return AirportDetailsViewModel(
            dispatcherProvider = TestDispatcherProvider(dispatcher),
            airportRepo = airportRepo,
            webpageTool = webpageTool,
        )
    }

    private fun TestScope.navEventsOf(vm: AirportDetailsViewModel): List<NavEvent> {
        val events = mutableListOf<NavEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.navEvents.toList(events) }
        return events
    }

    @Test
    fun `a full airport is never looked up`() = runTest {
        val vm = createViewModel()

        vm.init(AirportTarget.Full(frankfurt))
        runCurrent()

        coVerify(exactly = 0) { airportRepo.search(any()) }
        val state = vm.state.value.shouldNotBeNull()
        state.lookingUp shouldBe false
        state.display shouldBe AirportDetailsDisplay.from(AirportTarget.Full(frankfurt))
    }

    @Test
    fun `a full airport saved without elevation, airline service and links is never looked up`() = runTest {
        val target = Json.decodeFromString(
            DestinationAirportDetails.serializer(),
            DANVILLE_DETAILS_WITHOUT_FACTS_JSON,
        ).target
        val vm = createViewModel()

        vm.init(target)
        runCurrent()

        coVerify(exactly = 0) { airportRepo.search(any()) }
        val state = vm.state.value.shouldNotBeNull()
        state.lookingUp shouldBe false
        state.display shouldBe AirportDetailsDisplay.from(target)
        state.display.title shouldBe "Danville Airport"
    }

    @Test
    fun `a partial airport is looked up once by its lookup term`() = runTest {
        coEvery { airportRepo.search(any()) } returns matchFound
        val vm = createViewModel()

        vm.init(partial)
        vm.init(partial)
        vm.init(AirportTarget.Full(frankfurt))
        runCurrent()

        coVerify(exactly = 1) { airportRepo.search(listOf("EDDF")) }
        coVerify(exactly = 1) { airportRepo.search(any()) }
    }

    @Test
    fun `while the lookup runs the sheet shows the ref and says it is looking up`() = runTest {
        val answer = CompletableDeferred<AirportSearch>()
        coEvery { airportRepo.search(any()) } coAnswers { answer.await() }
        val vm = createViewModel()

        vm.init(partial)
        runCurrent()

        vm.state.value.shouldNotBeNull().apply {
            lookingUp shouldBe true
            display shouldBe AirportDetailsDisplay.from(partial)
        }

        answer.complete(matchFound)
        runCurrent()

        vm.state.value.shouldNotBeNull().lookingUp shouldBe false
    }

    @Test
    fun `a match merges the server's data with the ref's`() = runTest {
        coEvery { airportRepo.search(any()) } returns matchFound
        val vm = createViewModel()

        vm.init(partial)
        runCurrent()

        val state = vm.state.value.shouldNotBeNull()
        state.lookingUp shouldBe false
        state.display shouldBe AirportDetailsDisplay.from(partial, resolved = frankfurt)
        state.display.title shouldBe "Frankfurt am Main Airport"
        state.display.municipality shouldBe "Frankfurt am Main"
        state.display.country shouldBe "Germany"
        state.display.codes.map { it.value } shouldBe listOf("FRA", "EDDF")
    }

    @Test
    fun `no match leaves the ref's data`() = runTest {
        coEvery { airportRepo.search(any()) } returns nothingFound
        val vm = createViewModel()

        vm.init(partial)
        runCurrent()

        val state = vm.state.value.shouldNotBeNull()
        state.lookingUp shouldBe false
        state.display shouldBe AirportDetailsDisplay.from(partial)
    }

    @Test
    fun `a failed lookup leaves the ref's data without an error`() = runTest {
        coEvery { airportRepo.search(any()) } returns AirportSearch.Failed(RuntimeException("offline"))
        val vm = createViewModel()
        val errors = mutableListOf<Throwable>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.errorEvents.toList(errors) }

        vm.init(partial)
        runCurrent()

        val state = vm.state.value.shouldNotBeNull()
        state.lookingUp shouldBe false
        state.display shouldBe AirportDetailsDisplay.from(partial)
        errors shouldBe emptyList()
    }

    @Test
    fun `a lookup that throws leaves the ref's data without an error`() = runTest {
        coEvery { airportRepo.search(any()) } throws IllegalStateException("boom")
        val vm = createViewModel()
        val errors = mutableListOf<Throwable>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.errorEvents.toList(errors) }

        vm.init(partial)
        runCurrent()

        val state = vm.state.value.shouldNotBeNull()
        state.lookingUp shouldBe false
        state.display shouldBe AirportDetailsDisplay.from(partial)
        errors shouldBe emptyList()
    }

    @Test
    fun `after a match the map shows the full airport`() = runTest {
        coEvery { airportRepo.search(any()) } returns matchFound
        val vm = createViewModel()
        val events = navEventsOf(vm)
        vm.init(partial)
        runCurrent()

        vm.showOnMap()
        runCurrent()

        events shouldBe listOf(NavEvent.GoTo(DestinationMap(mapOptions = AirportTarget.Full(frankfurt).toMapOptions())))
    }

    @Test
    fun `without a match the map shows the partial airport`() = runTest {
        coEvery { airportRepo.search(any()) } returns nothingFound
        val vm = createViewModel()
        val events = navEventsOf(vm)
        vm.init(partial)
        runCurrent()

        vm.showOnMap()
        runCurrent()

        events shouldBe listOf(NavEvent.GoTo(DestinationMap(mapOptions = partial.toMapOptions())))
    }

    @Test
    fun `a full airport is shown on the map as it is`() = runTest {
        val vm = createViewModel()
        val events = navEventsOf(vm)
        vm.init(AirportTarget.Full(frankfurt))

        vm.showOnMap()
        runCurrent()

        events shouldBe listOf(NavEvent.GoTo(DestinationMap(mapOptions = AirportTarget.Full(frankfurt).toMapOptions())))
    }

    @Test
    fun `a partial airport without a position does not open the map`() = runTest {
        coEvery { airportRepo.search(any()) } returns nothingFound
        val vm = createViewModel()
        val events = navEventsOf(vm)
        vm.init(AirportTarget.Partial(AirportRef.create(icao = "EDDF", iata = null)!!))
        runCurrent()

        vm.showOnMap()
        runCurrent()

        events shouldBe emptyList()
    }

    @Test
    fun `the website and wikipedia open the server's links`() = runTest {
        val vm = createViewModel()
        vm.init(AirportTarget.Full(frankfurtWithFacts))
        runCurrent()

        vm.openWebsite()
        verify(exactly = 1) { webpageTool.open("https://www.frankfurt-airport.de/") }

        vm.openWikipedia()
        verify(exactly = 1) { webpageTool.open("https://en.wikipedia.org/wiki/Frankfurt_Airport") }
        verify(exactly = 2) { webpageTool.open(any()) }
    }

    @Test
    fun `an airport without links opens nothing`() = runTest {
        val vm = createViewModel()
        vm.init(AirportTarget.Full(frankfurt))
        runCurrent()

        vm.openWebsite()
        vm.openWikipedia()

        verify(exactly = 0) { webpageTool.open(any()) }
    }

    @Test
    fun `links of a looked up airport open once the match is in`() = runTest {
        coEvery { airportRepo.search(any()) } returns AirportSearch.Answered(
            listOf(AirportSearchResultDto(0, STATUS_ANSWERED, listOf(frankfurtWithFactsDto)))
        )
        val vm = createViewModel()
        vm.init(partial)
        runCurrent()

        vm.openWikipedia()

        verify(exactly = 1) { webpageTool.open("https://en.wikipedia.org/wiki/Frankfurt_Airport") }
    }

    @Test
    fun `the map gets the matched airport with its facts and reopens the sheet without a lookup`() = runTest {
        coEvery { airportRepo.search(any()) } returns AirportSearch.Answered(
            listOf(AirportSearchResultDto(0, STATUS_ANSWERED, listOf(frankfurtWithFactsDto)))
        )
        val vm = createViewModel()
        val events = navEventsOf(vm)
        vm.init(partial)
        runCurrent()

        vm.showOnMap()
        runCurrent()

        val goTo = events.single().shouldBeInstanceOf<NavEvent.GoTo>()
        val mapTarget = goTo.destination.shouldBeInstanceOf<DestinationMap>().mapOptions.shouldNotBeNull().airport
        mapTarget shouldBe AirportTarget.Full(frankfurtWithFacts)

        val reopened = createViewModel()
        reopened.init(mapTarget.shouldNotBeNull())
        runCurrent()

        coVerify(exactly = 1) { airportRepo.search(any()) }
        val state = reopened.state.value.shouldNotBeNull()
        state.lookingUp shouldBe false
        state.display.elevationFt shouldBe 364
        state.display.scheduledService shouldBe true
        state.display.website shouldBe "https://www.frankfurt-airport.de/"
        state.display.wikipedia shouldBe "https://en.wikipedia.org/wiki/Frankfurt_Airport"
    }
}
