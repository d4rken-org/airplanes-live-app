package eu.darken.apl.search.ui

import android.location.Location
import eu.darken.apl.airport.core.AirportAggregation
import eu.darken.apl.airport.core.AirportRepo
import eu.darken.apl.airport.core.AirportSearch
import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.WebpageTool
import eu.darken.apl.common.compose.preview.FakeAircraft
import eu.darken.apl.common.location.LocationManager2
import eu.darken.apl.search.core.SearchQuery
import eu.darken.apl.search.core.SearchRepo
import eu.darken.apl.search.core.SearchSettings
import eu.darken.apl.search.core.SearchTerm
import eu.darken.apl.search.ui.SearchViewModel.AirportStatus
import eu.darken.apl.search.ui.SearchViewModel.SearchItem
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.server.api.AirportSearchResultDto
import eu.darken.apl.server.api.AirportSearchResultDto.Companion.STATUS_ANSWERED
import eu.darken.apl.server.api.AirportSearchResultDto.Companion.STATUS_INVALID_QUERY
import eu.darken.apl.server.api.AirportSearchResultDto.Companion.STATUS_SKIPPED
import eu.darken.apl.server.api.AirportSummaryDto
import eu.darken.apl.watch.core.WatchRepo
import eu.darken.apl.watch.core.types.Watch
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.coroutine.TestDispatcherProvider
import java.io.IOException

class SearchViewModelAirportTest : BaseTest() {

    private val searchRepo = mockk<SearchRepo>()
    private val airportRepo = mockk<AirportRepo>()
    private val webpageTool = mockk<WebpageTool>(relaxed = true)
    private val locationManager2 = mockk<LocationManager2>(relaxed = true)
    private val settings = mockk<SearchSettings>(relaxed = true)
    private val watchRepo = mockk<WatchRepo>(relaxed = true)
    private val accessRepo = mockk<AccessRepo>(relaxed = true)
    private val serverClock = ServerClock(object : MonotonicClock {
        override fun elapsed(): Long = 0L
    })
    private val locationState = MutableStateFlow<LocationManager2.State>(LocationManager2.State.Waiting)
    private val locationDismissed = MutableStateFlow(true)
    private val searchRequests = SearchRequests()

    private val heathrow = airport("lhr", municipality = "London")

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { watchRepo.watches } returns MutableStateFlow(emptyList<Watch>())
        every { accessRepo.state } returns MutableStateFlow<AccessState?>(null)
        every { locationManager2.state } returns locationState
        coEvery { searchRepo.search(any()) } answers { resultFor(firstArg(), "AAAAAA") }
        coEvery { searchRepo.nearby(any(), any(), any(), any()) } answers {
            SearchRepo.SearchResult(query = arg(3), aircraft = listOf(FakeAircraft(hex = "CCCCCC")))
        }
        coEvery { airportRepo.search(any()) } answers { noAirports(firstArg()) }
        every { settings.searchLocationDismissed } returns mockk {
            every { flow } returns locationDismissed
        }
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = SearchViewModel(
        dispatcherProvider = TestDispatcherProvider(),
        searchRepo = searchRepo,
        webpageTool = webpageTool,
        locationManager2 = locationManager2,
        settings = settings,
        watchRepo = watchRepo,
        accessRepo = accessRepo,
        serverClock = serverClock,
        searchRequests = searchRequests,
        airportRepo = airportRepo,
    ).apply { onScreenShown() }

    private fun resultFor(query: SearchQuery, vararg hexes: String) = SearchRepo.SearchResult(
        query = query,
        aircraft = hexes.map { FakeAircraft(hex = it) },
    )

    private fun airport(id: String, municipality: String? = null) = AirportSummaryDto(
        id = id,
        name = "Airport $id",
        type = AirportSummaryDto.TYPE_LARGE,
        ident = id.uppercase(),
        latitude = 1.0,
        longitude = 2.0,
        municipality = municipality,
    )

    private fun answered(index: Int, vararg airports: AirportSummaryDto) =
        AirportSearchResultDto(index, STATUS_ANSWERED, airports.toList())

    private fun noAirports(terms: List<String>) = AirportSearch.Answered(terms.indices.map { answered(it) })

    private fun found(vararg results: AirportSearchResultDto) = AirportSearch.Answered(results.toList())

    private suspend fun SearchViewModel.items() = state.first().items

    private suspend fun SearchViewModel.airportSection() =
        items().filterIsInstance<SearchItem.Airports>().singleOrNull()?.section

    private suspend fun SearchViewModel.airportResults(): AirportAggregation.Shown =
        airportSection().shouldNotBeNull().status.shouldBeInstanceOf<AirportStatus.Results>().airports

    private suspend fun SearchViewModel.shownHexes() = items()
        .filterIsInstance<SearchItem.AircraftResult>()
        .map { it.aircraft.hex }

    private fun AirportAggregation.Shown.ids() = rows.map { it.airport.id }

    @Test
    fun `a text search shows airports loading beside the running aircraft search, and both land`() = runTest {
        val aircraft = CompletableDeferred<SearchRepo.SearchResult>()
        val airports = CompletableDeferred<AirportSearch>()
        coEvery { searchRepo.search(any()) } coAnswers { aircraft.await() }
        coEvery { airportRepo.search(any()) } coAnswers { airports.await() }
        val viewModel = createViewModel()

        viewModel.submitCurrent("LHR")

        viewModel.airportSection() shouldBe SearchViewModel.AirportSection(
            terms = listOf("LHR"),
            cutoff = false,
            status = AirportStatus.Loading,
        )
        viewModel.state.first().isSearching shouldBe true

        aircraft.complete(resultFor(SearchQuery(listOf(SearchTerm("LHR"))), "AAAAAA"))
        airports.complete(found(answered(0, heathrow)))

        viewModel.shownHexes() shouldBe listOf("AAAAAA")
        viewModel.airportResults().ids() shouldBe listOf("lhr")
        coVerify(exactly = 1) { airportRepo.search(listOf("LHR")) }
        coVerify(exactly = 1) { searchRepo.search(SearchQuery(listOf(SearchTerm("LHR")))) }
    }

    @Test
    fun `the airports section sits between the location prompt and the aircraft status`() = runTest {
        locationDismissed.value = false
        locationState.value = LocationManager2.State.Unavailable(SecurityException())
        val aircraft = CompletableDeferred<SearchRepo.SearchResult>()
        coEvery { searchRepo.search(any()) } coAnswers { aircraft.await() }
        coEvery { airportRepo.search(any()) } returns found(answered(0, heathrow))
        val viewModel = createViewModel()

        viewModel.submitCurrent("LHR")

        viewModel.items().map { it::class } shouldBe listOf(
            SearchItem.LocationPrompt::class,
            SearchItem.Airports::class,
            SearchItem.Searching::class,
        )
    }

    @Test
    fun `the aircraft search stops spinning while airports are still pending`() = runTest {
        coEvery { airportRepo.search(any()) } coAnswers { CompletableDeferred<AirportSearch>().await() }
        val viewModel = createViewModel()

        viewModel.submitCurrent("LHR")

        viewModel.state.first().isSearching shouldBe false
        viewModel.shownHexes() shouldBe listOf("AAAAAA")
        viewModel.airportSection()!!.status shouldBe AirportStatus.Loading
    }

    @Test
    fun `airports first or aircraft first ends in the same state`() = runTest {
        val aircraftResult = resultFor(SearchQuery(listOf(SearchTerm("LHR"))), "AAAAAA")

        suspend fun finalState(airportsFirst: Boolean): SearchViewModel.State {
            val aircraft = CompletableDeferred<SearchRepo.SearchResult>()
            val airports = CompletableDeferred<AirportSearch>()
            coEvery { searchRepo.search(any()) } coAnswers { aircraft.await() }
            coEvery { airportRepo.search(any()) } coAnswers { airports.await() }
            val viewModel = createViewModel()
            viewModel.submitCurrent("LHR")
            val completeAircraft = { aircraft.complete(aircraftResult) }
            val completeAirports = { airports.complete(found(answered(0, heathrow))) }
            if (airportsFirst) {
                completeAirports()
                completeAircraft()
            } else {
                completeAircraft()
                completeAirports()
            }
            return viewModel.state.first()
        }

        val airportsFirst = finalState(airportsFirst = true)
        val aircraftFirst = finalState(airportsFirst = false)

        airportsFirst.items shouldBe aircraftFirst.items
        airportsFirst.isSearching shouldBe aircraftFirst.isSearching
        airportsFirst.resultRevision shouldBe aircraftFirst.resultRevision
        airportsFirst.items.filterIsInstance<SearchItem.Airports>().single().section.status
            .shouldBeInstanceOf<AirportStatus.Results>()
    }

    @Test
    fun `a failed airport search leaves the aircraft results alone`() = runTest {
        coEvery { airportRepo.search(any()) } returns AirportSearch.Failed(IOException("down"))
        val viewModel = createViewModel()

        viewModel.submitCurrent("LHR")

        viewModel.airportSection()!!.status shouldBe AirportStatus.Failed
        viewModel.shownHexes() shouldBe listOf("AAAAAA")
        viewModel.items().filterIsInstance<SearchItem.Summary>().single().aircraftCount shouldBe 1
        viewModel.state.first().error shouldBe null
    }

    @Test
    fun `a failed aircraft search leaves the airport results alone`() = runTest {
        coEvery { searchRepo.search(any()) } throws IllegalStateException("boom")
        coEvery { airportRepo.search(any()) } returns found(answered(0, heathrow))
        val viewModel = createViewModel()

        viewModel.submitCurrent("LHR")

        viewModel.state.first().error shouldNotBe null
        viewModel.airportResults().ids() shouldBe listOf("lhr")
    }

    @Test
    fun `a selected category asks for no airports`() = runTest {
        val viewModel = createViewModel()
        viewModel.toggleCategoryChip(SearchViewModel.CategoryChip.MILITARY)

        viewModel.submitCurrent("DLH")

        coVerify(exactly = 1) { searchRepo.search(any()) }
        coVerify(exactly = 0) { airportRepo.search(any()) }
        viewModel.airportSection().shouldBeNull()
    }

    @Test
    fun `a category without text asks for no airports`() = runTest {
        val viewModel = createViewModel()
        viewModel.toggleCategoryChip(SearchViewModel.CategoryChip.PRIVACY)

        viewModel.submitCurrent("")

        coVerify(exactly = 1) { searchRepo.search(any()) }
        coVerify(exactly = 0) { airportRepo.search(any()) }
        viewModel.airportSection().shouldBeNull()
    }

    @Test
    fun `a nearby search asks for no airports`() = runTest {
        locationState.value = LocationManager2.State.Available(mockk<Location>(relaxed = true))
        val viewModel = createViewModel()
        viewModel.toggleNearby()

        viewModel.submitCurrent("DLH")

        coVerify(exactly = 1) { searchRepo.nearby(any(), any(), any(), any()) }
        coVerify(exactly = 0) { airportRepo.search(any()) }
        viewModel.airportSection().shouldBeNull()
    }

    @Test
    fun `a nearby search resumed after the permission asks for no airports`() = runTest {
        val location = mockk<Location>(relaxed = true)
        locationState.value = LocationManager2.State.Unavailable(SecurityException())
        val viewModel = createViewModel()
        viewModel.toggleNearby()
        viewModel.submitCurrent("")
        viewModel.events.first() shouldBe SearchEvents.RequestLocationPermission

        locationState.value = LocationManager2.State.Available(location)
        viewModel.onLocationPermissionResult(granted = true)

        coVerify(exactly = 1) { searchRepo.nearby(any(), any(), any(), any()) }
        coVerify(exactly = 0) { airportRepo.search(any()) }
        viewModel.airportSection().shouldBeNull()
    }

    @Test
    fun `searches opened from other screens ask for no airports`() = runTest {
        searchRequests.publish(SearchTarget(hexes = listOf("3c6589")))
        val viewModel = createViewModel().apply { init() }

        searchRequests.publish(SearchTarget(squawks = listOf("7700")))
        searchRequests.publish(SearchTarget(callsigns = listOf("DLH453")))

        coVerify(exactly = 3) { searchRepo.search(any()) }
        coVerify(exactly = 0) { airportRepo.search(any()) }
        viewModel.airportSection().shouldBeNull()
    }

    @Test
    fun `a search opened from another screen hides the airports of the typed one`() = runTest {
        coEvery { airportRepo.search(any()) } returns found(answered(0, heathrow))
        val viewModel = createViewModel().apply { submitCurrent("LHR") }
        viewModel.init()
        viewModel.airportSection().shouldNotBeNull()

        searchRequests.publish(SearchTarget(hexes = listOf("3c6589")))

        viewModel.airportSection().shouldBeNull()
        coVerify(exactly = 1) { airportRepo.search(any()) }
    }

    @Test
    fun `a nearby submit hides the airports of the previous search`() = runTest {
        locationState.value = LocationManager2.State.Available(mockk<Location>(relaxed = true))
        coEvery { airportRepo.search(any()) } returns found(answered(0, heathrow))
        val viewModel = createViewModel()
        viewModel.submitCurrent("LHR")
        viewModel.airportSection().shouldNotBeNull()

        viewModel.toggleNearby()
        viewModel.submitCurrent("LHR")

        viewModel.airportSection().shouldBeNull()
        coVerify(exactly = 1) { airportRepo.search(any()) }
    }

    @Test
    fun `a category submit hides the airports of the previous search`() = runTest {
        coEvery { airportRepo.search(any()) } returns found(answered(0, heathrow))
        val viewModel = createViewModel()
        viewModel.submitCurrent("LHR")
        viewModel.airportSection().shouldNotBeNull()

        viewModel.toggleCategoryChip(SearchViewModel.CategoryChip.MILITARY)
        viewModel.submitCurrent("LHR")

        viewModel.airportSection().shouldBeNull()
        coVerify(exactly = 1) { airportRepo.search(any()) }
    }

    @Test
    fun `a submit of separators only keeps both sections and asks for nothing`() = runTest {
        coEvery { airportRepo.search(any()) } returns found(answered(0, heathrow))
        val viewModel = createViewModel()
        viewModel.submitCurrent("LHR")
        val before = viewModel.state.first()

        viewModel.submitCurrent(", ,")

        val after = viewModel.state.first()
        after.items shouldBe before.items
        after.resultRevision shouldBe before.resultRevision
        viewModel.airportResults().ids() shouldBe listOf("lhr")
        coVerify(exactly = 1) { airportRepo.search(any()) }
        coVerify(exactly = 1) { searchRepo.search(any()) }
    }

    @Test
    fun `airports for an older submit arriving late don't replace the newer ones`() = runTest {
        val late = CompletableDeferred<AirportSearch>()
        coEvery { airportRepo.search(listOf("LHR")) } coAnswers { late.await() }
        coEvery { searchRepo.search(any()) } answers { resultFor(firstArg()) }
        val viewModel = createViewModel()
        viewModel.submitCurrent("LHR")

        viewModel.submitCurrent("BAW12")
        late.complete(found(answered(0, heathrow)))

        val section = viewModel.airportSection().shouldNotBeNull()
        section.terms shouldBe listOf("BAW12")
        viewModel.airportResults().rows shouldBe emptyList()
    }

    @Test
    fun `airports arriving after the field was cleared don't come back`() = runTest {
        val late = CompletableDeferred<AirportSearch>()
        coEvery { airportRepo.search(any()) } coAnswers { late.await() }
        val viewModel = createViewModel()
        viewModel.submitCurrent("LHR")

        viewModel.clearSearch()
        late.complete(found(answered(0, heathrow)))

        viewModel.airportSection().shouldBeNull()
    }

    @Test
    fun `retrying airports leaves the running aircraft search alone`() = runTest {
        val aircraft = CompletableDeferred<SearchRepo.SearchResult>()
        val retried = CompletableDeferred<AirportSearch>()
        coEvery { searchRepo.search(any()) } coAnswers { aircraft.await() }
        var calls = 0
        coEvery { airportRepo.search(any()) } coAnswers {
            if (++calls == 1) AirportSearch.RateLimited else retried.await()
        }
        val viewModel = createViewModel()
        viewModel.submitCurrent("LHR")
        viewModel.airportSection()!!.status shouldBe AirportStatus.RateLimited

        viewModel.retryAirports()

        viewModel.airportSection()!!.status shouldBe AirportStatus.Loading
        retried.complete(found(answered(0, heathrow)))
        viewModel.airportResults().ids() shouldBe listOf("lhr")

        aircraft.complete(resultFor(SearchQuery(listOf(SearchTerm("LHR"))), "AAAAAA"))
        viewModel.shownHexes() shouldBe listOf("AAAAAA")
        viewModel.state.first().isSearching shouldBe false
        coVerify(exactly = 2) { airportRepo.search(listOf("LHR")) }
        coVerify(exactly = 1) { searchRepo.search(any()) }
    }

    @Test
    fun `a failed airport search can be retried`() = runTest {
        var calls = 0
        coEvery { airportRepo.search(any()) } coAnswers {
            if (++calls == 1) AirportSearch.Failed(IOException("down")) else found(answered(0, heathrow))
        }
        val viewModel = createViewModel()
        viewModel.submitCurrent("LHR")
        viewModel.airportSection()!!.status shouldBe AirportStatus.Failed

        viewModel.retryAirports()

        viewModel.airportResults().ids() shouldBe listOf("lhr")
        coVerify(exactly = 1) { searchRepo.search(any()) }
    }

    @Test
    fun `a revoked installation is told so and retrying asks nothing`() = runTest {
        coEvery { airportRepo.search(any()) } returns AirportSearch.Revoked
        val viewModel = createViewModel()
        viewModel.submitCurrent("LHR")

        viewModel.retryAirports()

        viewModel.airportSection()!!.status shouldBe AirportStatus.Revoked
        coVerify(exactly = 1) { airportRepo.search(any()) }
    }

    @Test
    fun `answers that can't be retried map to their own status`() = runTest {
        val cases = mapOf(
            AirportSearch.Unavailable to AirportStatus.Unavailable,
            AirportSearch.UpdateRequired to AirportStatus.UpdateRequired,
            AirportSearch.Restricted to AirportStatus.Restricted,
        )
        cases.forEach { (answer, expected) ->
            coEvery { airportRepo.search(any()) } returns answer
            val viewModel = createViewModel()
            viewModel.submitCurrent("LHR")

            viewModel.retryAirports()

            viewModel.airportSection()!!.status shouldBe expected
            expected.retryable shouldBe false
        }
        coVerify(exactly = 3) { airportRepo.search(any()) }
    }

    @Test
    fun `airports finishing later don't change the aircraft result revision`() = runTest {
        val airports = CompletableDeferred<AirportSearch>()
        coEvery { airportRepo.search(any()) } coAnswers { airports.await() }
        val viewModel = createViewModel()
        viewModel.submitCurrent("LHR")
        val revision = viewModel.state.first().resultRevision
        revision shouldNotBe 0

        airports.complete(found(answered(0, heathrow)))

        viewModel.airportResults().ids() shouldBe listOf("lhr")
        viewModel.state.first().resultRevision shouldBe revision
    }

    @Test
    fun `an expanded section stays expanded through a retry and the aircraft result, new terms collapse it`() =
        runTest {
            val aircraft = CompletableDeferred<SearchRepo.SearchResult>()
            coEvery { searchRepo.search(SearchQuery(listOf(SearchTerm("LHR")))) } coAnswers { aircraft.await() }
            var calls = 0
            coEvery { airportRepo.search(any()) } coAnswers {
                if (++calls == 1) AirportSearch.Failed(IOException("down")) else found(answered(0, heathrow))
            }
            val viewModel = createViewModel()
            viewModel.submitCurrent("LHR")
            viewModel.airportSection()!!.expanded shouldBe false

            viewModel.setAirportsExpanded(true)
            viewModel.airportSection()!!.expanded shouldBe true

            viewModel.retryAirports()
            viewModel.airportResults().ids() shouldBe listOf("lhr")
            viewModel.airportSection()!!.expanded shouldBe true

            aircraft.complete(resultFor(SearchQuery(listOf(SearchTerm("LHR"))), "AAAAAA"))
            viewModel.airportSection()!!.expanded shouldBe true

            viewModel.submitCurrent("EGLL")
            viewModel.airportSection()!!.expanded shouldBe false

            viewModel.submitCurrent("LHR")
            viewModel.airportSection()!!.expanded shouldBe false
        }

    @Test
    fun `collapsing the section collapses it`() = runTest {
        coEvery { airportRepo.search(any()) } returns found(answered(0, heathrow))
        val viewModel = createViewModel()
        viewModel.submitCurrent("LHR")
        viewModel.setAirportsExpanded(true)

        viewModel.setAirportsExpanded(false)

        viewModel.airportSection()!!.expanded shouldBe false
    }

    @Test
    fun `only the first ten terms are searched for airports`() = runTest {
        val terms = slot<List<String>>()
        coEvery { airportRepo.search(capture(terms)) } answers { noAirports(firstArg()) }
        val viewModel = createViewModel()

        viewModel.submitCurrent((1..11).joinToString(", ") { "T$it" })

        terms.captured shouldBe (1..10).map { "T$it" }
        val section = viewModel.airportSection().shouldNotBeNull()
        section.cutoff shouldBe true
        section.terms shouldBe (1..10).map { "T$it" }
    }

    @Test
    fun `a duplicate term doesn't count towards the ten`() = runTest {
        val terms = slot<List<String>>()
        coEvery { airportRepo.search(capture(terms)) } answers { noAirports(firstArg()) }
        coEvery { searchRepo.search(any()) } answers { resultFor(firstArg()) }
        val viewModel = createViewModel()

        // Eleven entries, "t3" repeats "T3"
        viewModel.submitCurrent((1..10).joinToString(", ") { "T$it" } + ", t3")

        terms.captured shouldBe (1..10).map { "T$it" }
        viewModel.airportSection()!!.cutoff shouldBe false
    }

    @Test
    fun `aircraft codes and invalid terms beside a found airport still show it`() = runTest {
        coEvery { airportRepo.search(any()) } returns found(
            answered(0, heathrow),
            AirportSearchResultDto(1, STATUS_SKIPPED),
            AirportSearchResultDto(2, STATUS_INVALID_QUERY),
        )
        val viewModel = createViewModel()

        viewModel.submitCurrent("LHR, 3C6589, X")

        val results = viewModel.airportResults()
        results.ids() shouldBe listOf("lhr")
        results.invalidTerms shouldBe listOf("X")
    }

    @Test
    fun `only invalid terms still show the section`() = runTest {
        coEvery { airportRepo.search(any()) } returns found(AirportSearchResultDto(0, STATUS_INVALID_QUERY))
        val viewModel = createViewModel()

        viewModel.submitCurrent("X")

        val results = viewModel.airportResults()
        results.rows shouldBe emptyList()
        results.invalidTerms shouldBe listOf("X")
        results.noAirports shouldBe false
    }

    @Test
    fun `no airports found reads as no airports`() = runTest {
        coEvery { searchRepo.search(any()) } answers { resultFor(firstArg()) }
        val viewModel = createViewModel()

        viewModel.submitCurrent("ZZZZ")

        viewModel.shownHexes() shouldBe emptyList()
        val results = viewModel.airportResults()
        results.noAirports shouldBe true
        results.invalidTerms shouldBe emptyList()
        viewModel.airportSection()!!.cutoff shouldBe false
    }

    @Test
    fun `found aircraft hide an airports answer that found none`() = runTest {
        val viewModel = createViewModel()

        viewModel.submitCurrent("DLH A320")

        coVerify(exactly = 1) { airportRepo.search(listOf("DLH A320")) }
        viewModel.shownHexes() shouldBe listOf("AAAAAA")
        viewModel.airportSection().shouldBeNull()
    }

    @Test
    fun `an airports answer that found none waits for the aircraft and shows once they found none either`() =
        runTest {
            val aircraft = CompletableDeferred<SearchRepo.SearchResult>()
            coEvery { searchRepo.search(any()) } coAnswers { aircraft.await() }
            val viewModel = createViewModel()

            viewModel.submitCurrent("ZZZZ")

            coVerify(exactly = 1) { airportRepo.search(listOf("ZZZZ")) }
            viewModel.state.first().isSearching shouldBe true
            viewModel.airportSection().shouldBeNull()

            aircraft.complete(resultFor(SearchQuery(listOf(SearchTerm("ZZZZ")))))

            viewModel.state.first().isSearching shouldBe false
            viewModel.shownHexes() shouldBe emptyList()
            viewModel.airportResults().noAirports shouldBe true
        }

    @Test
    fun `an airports answer that found none still shows its invalid terms beside found aircraft`() = runTest {
        coEvery { airportRepo.search(any()) } returns found(
            answered(0),
            AirportSearchResultDto(1, STATUS_INVALID_QUERY),
        )
        val viewModel = createViewModel()

        viewModel.submitCurrent("ZZZZ, X")

        viewModel.shownHexes() shouldBe listOf("AAAAAA")
        val results = viewModel.airportResults()
        results.noAirports shouldBe true
        results.invalidTerms shouldBe listOf("X")
    }

    @Test
    fun `an airports answer that found none still shows its cutoff beside found aircraft`() = runTest {
        val viewModel = createViewModel()

        viewModel.submitCurrent((1..11).joinToString(", ") { "T$it" })

        viewModel.shownHexes() shouldBe listOf("AAAAAA")
        viewModel.airportResults().noAirports shouldBe true
        viewModel.airportSection()!!.cutoff shouldBe true
    }

    @Test
    fun `only aircraft codes hide the section`() = runTest {
        coEvery { airportRepo.search(any()) } returns found(AirportSearchResultDto(0, STATUS_SKIPPED))
        val viewModel = createViewModel()

        viewModel.submitCurrent("3C6589")

        viewModel.airportSection().shouldBeNull()
        viewModel.shownHexes() shouldBe listOf("AAAAAA")
    }

    @Test
    fun `only aircraft codes hide the section with its cutoff`() = runTest {
        coEvery { airportRepo.search(any()) } answers {
            AirportSearch.Answered(firstArg<List<String>>().indices.map { AirportSearchResultDto(it, STATUS_SKIPPED) })
        }
        val viewModel = createViewModel()

        viewModel.submitCurrent((1..11).joinToString(", ") { "7%03d".format(it) })

        viewModel.airportSection().shouldBeNull()
    }

    @Test
    fun `a status this build doesn't know hides the section`() = runTest {
        coEvery { airportRepo.search(any()) } returns found(AirportSearchResultDto(0, "something_new"))
        val viewModel = createViewModel()

        viewModel.submitCurrent("LHR")

        viewModel.airportSection().shouldBeNull()
    }

    @Test
    fun `results that don't answer each term once fail the section`() = runTest {
        coEvery { airportRepo.search(any()) } returns found(answered(1, heathrow))
        val viewModel = createViewModel()

        viewModel.submitCurrent("LHR")

        val status = viewModel.airportSection()!!.status
        status shouldBe AirportStatus.Failed
        status.retryable shouldBe true
    }

    @Test
    fun `an airport found by several terms is listed once`() = runTest {
        val city = airport("lcy", municipality = "London")
        coEvery { airportRepo.search(any()) } returns found(
            answered(0, heathrow.copy(matchedCode = "LHR")),
            answered(1, heathrow.copy(matchedCode = "EGLL")),
            answered(2, heathrow, city),
        )
        val viewModel = createViewModel()

        viewModel.submitCurrent("LHR, EGLL, London")

        coVerify(exactly = 1) { airportRepo.search(listOf("LHR", "EGLL", "London")) }
        val results = viewModel.airportResults()
        results.ids() shouldBe listOf("lhr", "lcy")
        results.rows.first().airport.matchedCode shouldBe "LHR"
    }
}
