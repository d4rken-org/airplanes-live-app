package eu.darken.apl.search.ui

import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.WebpageTool
import eu.darken.apl.common.location.LocationManager2
import eu.darken.apl.search.core.SearchQuery
import eu.darken.apl.search.core.SearchRepo
import eu.darken.apl.search.core.SearchSettings
import eu.darken.apl.search.core.SearchTerm
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.watch.core.WatchRepo
import eu.darken.apl.watch.core.types.Watch
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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

    @BeforeEach
    fun setup() {
        // viewModelScope is built on the main dispatcher
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { watchRepo.watches } returns MutableStateFlow(emptyList<Watch>())
        every { accessRepo.state } returns MutableStateFlow<AccessState?>(null)
        every { locationManager2.state } returns MutableStateFlow(LocationManager2.State.Waiting)
        coEvery { searchRepo.search(any()) } returns SearchRepo.SearchResult(query = SearchQuery(emptyList()))
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
    )

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
}
