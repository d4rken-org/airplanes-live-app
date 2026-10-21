package eu.darken.apl.search.ui

import android.location.Location
import androidx.lifecycle.viewModelScope
import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.WebpageTool
import eu.darken.apl.common.compose.preview.FakeAircraft
import eu.darken.apl.common.location.LocationManager2
import eu.darken.apl.search.core.SearchQuery
import eu.darken.apl.search.core.SearchRepo
import eu.darken.apl.search.core.SearchSettings
import eu.darken.apl.search.core.SearchTerm
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.ServerCodes
import eu.darken.apl.watch.core.WatchRepo
import eu.darken.apl.watch.core.types.Watch
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.coroutine.TestDispatcherProvider

class SearchViewModelTest : BaseTest() {

    private val searchRepo = mockk<SearchRepo>()
    private val webpageTool = mockk<WebpageTool>(relaxed = true)
    private val locationManager2 = mockk<LocationManager2>(relaxed = true)
    private val settings = mockk<SearchSettings>(relaxed = true)
    private val watchRepo = mockk<WatchRepo>(relaxed = true)
    private val accessRepo = mockk<AccessRepo>(relaxed = true)
    private val serverClock = ServerClock(object : MonotonicClock {
        override fun elapsed(): Long = 0L
    })
    private val locationState = MutableStateFlow<LocationManager2.State>(LocationManager2.State.Waiting)

    @BeforeEach
    fun setup() {
        // viewModelScope is built on the main dispatcher
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { watchRepo.watches } returns MutableStateFlow(emptyList<Watch>())
        every { accessRepo.state } returns MutableStateFlow<AccessState?>(null)
        every { locationManager2.state } returns locationState
        coEvery { searchRepo.search(any()) } answers { resultFor(firstArg()) }
        // Every source of the state combine has to emit, a relaxed mock's flow never does
        every { settings.searchLocationDismissed } returns mockk {
            every { flow } returns MutableStateFlow(true)
        }
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun resultFor(query: SearchQuery, vararg hexes: String) = SearchRepo.SearchResult(
        query = query,
        aircraft = hexes.map { FakeAircraft(hex = it) },
    )

    private val searchRequests = SearchRequests()

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
    ).apply { onScreenShown() }

    private suspend fun SearchViewModel.shown() = state.first()

    private suspend fun SearchViewModel.shownHexes() = shown().items
        .filterIsInstance<SearchViewModel.SearchItem.AircraftResult>()
        .map { it.aircraft.hex }

    @Test
    fun `submitting the current input runs exactly one search`() = runTest {
        val viewModel = createViewModel()

        viewModel.submitCurrent("DLH453")

        coVerify(exactly = 1) { searchRepo.search(SearchQuery(listOf(SearchTerm("DLH453")))) }
    }

    @Test
    fun `submitting nothing searches nothing`() = runTest {
        val viewModel = createViewModel()

        viewModel.submitCurrent(" ")

        coVerify(exactly = 0) { searchRepo.search(any()) }
        coVerify(exactly = 0) { searchRepo.nearby(any(), any(), any(), any()) }
    }

    @Test
    fun `coming back to the tab shows the kept results without searching again`() = runTest {
        coEvery { searchRepo.search(any()) } answers { resultFor(firstArg(), "AAAAAA") }
        val viewModel = createViewModel().apply { submitCurrent("DLH453") }

        // The screen calls init() every time it is shown
        viewModel.init()
        viewModel.init()

        viewModel.shownHexes() shouldBe listOf("AAAAAA")
        coVerify(exactly = 1) { searchRepo.search(any()) }
    }

    @Test
    fun `a search opened for specific aircraft replaces the kept results`() = runTest {
        val viewModel = createViewModel().apply { submitCurrent("DLH453") }
        viewModel.init()

        searchRequests.publish(SearchTarget(hexes = listOf("3c6589")))

        viewModel.shown().input.text shouldBe "3c6589"
        coVerify(exactly = 1) { searchRepo.search(SearchQuery(listOf(SearchTerm("3c6589")))) }
    }

    @Test
    fun `a search asked for before the tab was opened runs when it opens`() = runTest {
        searchRequests.publish(SearchTarget(squawks = listOf("7700")))

        val viewModel = createViewModel().apply { init() }

        viewModel.shown().input.text shouldBe "7700"
        coVerify(exactly = 1) { searchRepo.search(SearchQuery(listOf(SearchTerm("7700")))) }
        verify(exactly = 0) { settings.lastInput }
    }

    @Test
    fun `opening the same aircraft again searches again`() = runTest {
        val target = SearchTarget(hexes = listOf("3c6589"))
        searchRequests.publish(target)
        createViewModel().init()

        searchRequests.publish(target)

        coVerify(exactly = 2) { searchRepo.search(SearchQuery(listOf(SearchTerm("3c6589")))) }
    }

    @Test
    fun `clearing drops the results and shows the hint again`() = runTest {
        val viewModel = createViewModel().apply { submitCurrent("DLH453") }

        viewModel.clearSearch()

        viewModel.shown().items.any { it is SearchViewModel.SearchItem.Hint } shouldBe true
    }

    @Test
    fun `a search still running when the field is cleared does not come back`() = runTest {
        val answer = CompletableDeferred<SearchRepo.SearchResult>()
        coEvery { searchRepo.search(any()) } coAnswers { answer.await() }
        val viewModel = createViewModel().apply { submitCurrent("DLH453") }

        viewModel.clearSearch()
        answer.complete(resultFor(SearchQuery(emptyList()), "AAAAAA"))

        viewModel.shownHexes() shouldBe emptyList()
        viewModel.shown().isSearching shouldBe false
    }

    @Test
    fun `an older search finishing last does not replace the newer one`() = runTest {
        val first = CompletableDeferred<SearchRepo.SearchResult>()
        coEvery { searchRepo.search(SearchQuery(listOf(SearchTerm("DLH453")))) } coAnswers { first.await() }
        coEvery { searchRepo.search(SearchQuery(listOf(SearchTerm("BAW12")))) } answers { resultFor(firstArg(), "BBBBBB") }
        val viewModel = createViewModel()

        viewModel.submitCurrent("DLH453")
        viewModel.submitCurrent("BAW12")
        first.complete(resultFor(SearchQuery(emptyList()), "AAAAAA"))

        viewModel.shownHexes() shouldBe listOf("BBBBBB")
        viewModel.shown().isSearching shouldBe false
    }

    @Test
    fun `an empty submit leaves a running search alone`() = runTest {
        val answer = CompletableDeferred<SearchRepo.SearchResult>()
        coEvery { searchRepo.search(any()) } coAnswers { answer.await() }
        val viewModel = createViewModel().apply { submitCurrent("DLH453") }

        // Commas only are text to the field but no search term
        viewModel.submitCurrent(" , ")
        answer.complete(resultFor(SearchQuery(emptyList()), "AAAAAA"))

        viewModel.shownHexes() shouldBe listOf("AAAAAA")
        viewModel.shown().isSearching shouldBe false
    }

    @Test
    fun `a search that fails unexpectedly shows the failure instead of spinning`() = runTest {
        coEvery { searchRepo.search(any()) } throws IllegalStateException("boom")
        val viewModel = createViewModel().apply { submitCurrent("DLH453") }

        viewModel.shown().error shouldNotBe null
        viewModel.shown().isSearching shouldBe false
    }

    @Test
    fun `a running search ends with the ViewModel`() = runTest {
        var cancelled = false
        coEvery { searchRepo.search(any()) } coAnswers {
            try {
                CompletableDeferred<SearchRepo.SearchResult>().await()
            } catch (e: CancellationException) {
                cancelled = true
                throw e
            }
        }
        val viewModel = createViewModel().apply { submitCurrent("DLH453") }

        viewModel.viewModelScope.cancel()

        cancelled shouldBe true
    }

    @Test
    fun `leaving during the location lookup ends the search uncharged`() = runTest {
        val viewModel = createViewModel()
        viewModel.toggleNearby()
        // The location stays Waiting, so the lookup is still running
        viewModel.submitCurrent("")
        viewModel.shown().isSearching shouldBe true

        viewModel.viewModelScope.cancel()

        coVerify(exactly = 0) { searchRepo.nearby(any(), any(), any(), any()) }
    }

    @Test
    fun `a nearby search waits for the location permission and then runs`() = runTest {
        val location = mockk<Location> {
            every { latitude } returns 50.0
            every { longitude } returns 8.0
        }
        coEvery { searchRepo.nearby(any(), any(), any(), any()) } answers {
            SearchRepo.SearchResult(query = arg(3), aircraft = listOf(FakeAircraft(hex = "CCCCCC")))
        }
        locationState.value = LocationManager2.State.Unavailable(SecurityException())
        val viewModel = createViewModel()
        viewModel.toggleNearby()
        viewModel.submitCurrent("")

        viewModel.events.first() shouldBe SearchEvents.RequestLocationPermission
        coVerify(exactly = 0) { searchRepo.nearby(any(), any(), any(), any()) }

        locationState.value = LocationManager2.State.Available(location)
        viewModel.onLocationPermissionResult(granted = true)

        coVerify(exactly = 1) { searchRepo.nearby(50.0, 8.0, any(), any()) }
        viewModel.shownHexes() shouldBe listOf("CCCCCC")
    }

    @Test
    fun `a nearby search left before it needs the permission asks for nothing`() = runTest {
        val viewModel = createViewModel()
        viewModel.toggleNearby()
        // The location stays Waiting, so the lookup is still running
        viewModel.submitCurrent("")

        viewModel.onScreenHidden()
        viewModel.onScreenShown()
        locationState.value = LocationManager2.State.Unavailable(SecurityException())

        withTimeoutOrNull(1_000) { viewModel.events.first() } shouldBe null
        // Nobody was asked, so the prompt card's grant is not this search's answer
        locationState.value = LocationManager2.State.Available(mockk(relaxed = true))
        viewModel.onLocationPermissionResult(granted = true)
        coVerify(exactly = 0) { searchRepo.nearby(any(), any(), any(), any()) }
    }

    @Test
    fun `a permission result nobody asked for runs no search`() = runTest {
        locationState.value = LocationManager2.State.Available(mockk(relaxed = true))
        val viewModel = createViewModel()
        viewModel.toggleNearby()

        viewModel.onLocationPermissionResult(granted = true)

        coVerify(exactly = 0) { searchRepo.nearby(any(), any(), any(), any()) }
    }

    @Test
    fun `an error is claimed once, also when the screen comes back to it`() = runTest {
        coEvery { searchRepo.search(any()) } answers {
            SearchRepo.SearchResult(query = firstArg(), error = IllegalStateException("boom"))
        }
        val viewModel = createViewModel().apply { submitCurrent("DLH453") }

        val error = viewModel.shown().error!!.error

        viewModel.claimError(error) shouldBe true
        viewModel.claimError(error) shouldBe false
    }

    @Test
    fun `a search refused for an outdated app reports no error`() = runTest {
        coEvery { searchRepo.search(any()) } answers {
            SearchRepo.SearchResult(
                query = firstArg(),
                error = ServerApiException(code = ServerCodes.APP_UPDATE_REQUIRED, status = 403),
            )
        }
        val viewModel = createViewModel().apply { submitCurrent("DLH453") }

        viewModel.shown().resultRevision shouldNotBe 0
        viewModel.shown().error shouldBe null
    }

    @Test
    fun `text typed right after clearing survives the clear`() = runTest {
        val viewModel = createViewModel().apply { submitCurrent("DLH453") }

        // What the screen does on clear, then a character typed before the clear lands
        viewModel.updateDraft(base = "", text = "")
        viewModel.updateDraft(base = "", text = "D")
        viewModel.clearSearch()

        viewModel.draft shouldBe SearchViewModel.Draft(base = "", text = "D")
    }

    @Test
    fun `a cleared field comes back empty`() = runTest {
        val viewModel = createViewModel()
        viewModel.updateDraft(base = "", text = "DLH")

        viewModel.updateDraft(base = "", text = "")
        viewModel.clearSearch()

        viewModel.draft shouldBe SearchViewModel.Draft(base = "", text = "")
    }

    @Test
    fun `submitting drops the draft`() = runTest {
        val viewModel = createViewModel()
        viewModel.updateDraft(base = "", text = "DLH")

        viewModel.submitCurrent("DLH453")

        viewModel.draft shouldBe null
    }

    @Test
    fun `a search asked for by another screen drops the draft`() = runTest {
        val viewModel = createViewModel().apply { updateDraft(base = "", text = "DLH") }
        searchRequests.publish(SearchTarget(hexes = listOf("3c6589")))

        viewModel.init()

        viewModel.draft shouldBe null
    }

    @Test
    fun `scroll and selection saved for one result don't carry over to the next`() = runTest {
        coEvery { searchRepo.search(any()) } answers { resultFor(firstArg(), "AAAAAA") }
        val viewModel = createViewModel().apply { submitCurrent("DLH453") }
        val shown = viewModel.shown().resultRevision
        viewModel.saveSelection(shown, setOf("AAAAAA"))
        viewModel.saveGridPosition(shown, SearchViewModel.GridPosition(index = 5))

        viewModel.submitCurrent("BAW12")

        val revision = viewModel.shown().resultRevision
        viewModel.screenState(revision) shouldBe SearchViewModel.ScreenState(revision)
    }

    @Test
    fun `a screen disposed after a newer result landed can't save over it`() = runTest {
        val viewModel = createViewModel().apply { submitCurrent("DLH453") }
        val old = viewModel.shown().resultRevision
        viewModel.submitCurrent("BAW12")

        viewModel.saveGridPosition(old, SearchViewModel.GridPosition(index = 30))

        viewModel.screenState(viewModel.shown().resultRevision).gridPosition shouldBe SearchViewModel.GridPosition()
    }
}
