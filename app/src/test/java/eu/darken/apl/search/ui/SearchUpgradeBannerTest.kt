package eu.darken.apl.search.ui

import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.WebpageTool
import eu.darken.apl.common.compose.preview.FakeAircraft
import eu.darken.apl.common.location.LocationManager2
import eu.darken.apl.main.core.query.TermOutcome
import eu.darken.apl.search.core.SearchQuery
import eu.darken.apl.search.core.SearchRepo
import eu.darken.apl.search.core.SearchSettings
import eu.darken.apl.search.core.SearchTerm
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.server.api.Allowance
import eu.darken.apl.server.api.RequestLimits
import eu.darken.apl.server.api.RequestRate
import eu.darken.apl.server.api.ServerCodes
import eu.darken.apl.server.api.TierPolicy
import eu.darken.apl.server.api.Usage
import eu.darken.apl.watch.core.WatchRepo
import eu.darken.apl.watch.core.types.Watch
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
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
import java.time.Instant

/**
 * The free tier's quota story is told by one banner at the end of the list plus the summary count.
 * Both read the search outcome, not the tier alone, so the combinations are worth pinning.
 */
class SearchUpgradeBannerTest : BaseTest() {

    private val searchRepo = mockk<SearchRepo>()
    private val webpageTool = mockk<WebpageTool>(relaxed = true)
    private val locationManager2 = mockk<LocationManager2>(relaxed = true)
    private val settings = mockk<SearchSettings>(relaxed = true)
    private val watchRepo = mockk<WatchRepo>(relaxed = true)
    private val accessRepo = mockk<AccessRepo>(relaxed = true)
    private val serverClock = ServerClock(object : MonotonicClock {
        override fun elapsed(): Long = 0L
    })
    private val access = MutableStateFlow<AccessState?>(null)

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { watchRepo.watches } returns MutableStateFlow(emptyList<Watch>())
        every { accessRepo.state } returns access
        every { locationManager2.state } returns MutableStateFlow(LocationManager2.State.Waiting)
        // Every source of the state combine has to emit, a relaxed mock's flow never does
        every { settings.searchLocationDismissed } returns mockk {
            every { flow } returns MutableStateFlow(true)
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
    )

    @Test
    fun `a capped answer reports the match count and offers the upgrade`() = runTest {
        access.value = accessState(AccessState.Tier.FREE, search = Allowance(25, 1, 0, 24))
        answerWith(capped(shown = 10, totalMatching = 1318))
        val viewModel = createViewModel()

        viewModel.submitCurrent("DLH453")
        val items = viewModel.state.first().items

        items.filterIsInstance<SearchViewModel.SearchItem.Summary>().single().totalMatching shouldBe 1318
        items.last() shouldBe SearchViewModel.SearchItem.UpgradeBanner(
            SearchViewModel.BannerState.Capped(shown = 10, total = 1318)
        )
    }

    @Test
    fun `several terms keep the deduplicated count, their totals may overlap`() = runTest {
        access.value = accessState(AccessState.Tier.FREE, search = Allowance(25, 1, 0, 24))
        val shared = FakeAircraft(hex = "AAAAAA")
        answerWith(
            TermOutcome.Answered(listOf(shared), complete = false, capped = true, 100, null, charged = true),
            TermOutcome.Answered(listOf(shared), complete = false, capped = true, 100, null, charged = true),
        )
        val viewModel = createViewModel()

        viewModel.submitCurrent("DLH453")
        val items = viewModel.state.first().items

        items.filterIsInstance<SearchViewModel.SearchItem.Summary>().single().totalMatching shouldBe null
        items.last() shouldBe SearchViewModel.SearchItem.UpgradeBanner(
            SearchViewModel.BannerState.CappedUnknown
        )
    }

    @Test
    fun `a spent allowance reads as exhausted, not as a counter`() = runTest {
        access.value = accessState(AccessState.Tier.FREE, search = Allowance(25, 25, 0, 0))
        answerWith(TermOutcome.Rejected(ServerCodes.DAILY_ALLOWANCE_EXHAUSTED))
        val viewModel = createViewModel()

        viewModel.submitCurrent("DLH453")
        val items = viewModel.state.first().items

        items.last().shouldBeInstanceOf<SearchViewModel.SearchItem.UpgradeBanner>()
            .state.shouldBeInstanceOf<SearchViewModel.BannerState.Exhausted>()
        // The banner is generic, the per-term line is what says which term went unevaluated
        items.filterIsInstance<SearchViewModel.SearchItem.TermStatus>()
            .single().state.shouldBeInstanceOf<SearchViewModel.TermState.Exhausted>()
    }

    @Test
    fun `an upgraded installation gets no banner and keeps its per-term rejections`() = runTest {
        access.value = accessState(AccessState.Tier.FEEDER, search = Allowance(2000, 2000, 0, 0))
        answerWith(TermOutcome.Rejected(ServerCodes.DAILY_ALLOWANCE_EXHAUSTED))
        val viewModel = createViewModel()

        viewModel.submitCurrent("DLH453")
        val items = viewModel.state.first().items

        items.filterIsInstance<SearchViewModel.SearchItem.UpgradeBanner>() shouldBe emptyList()
        items.filterIsInstance<SearchViewModel.SearchItem.TermStatus>()
            .single().state.shouldBeInstanceOf<SearchViewModel.TermState.Exhausted>()
    }

    @Test
    fun `an unspent allowance shows what is left, reservations included`() = runTest {
        // remaining is the server's own figure; limit - used would say 24 here
        access.value = accessState(AccessState.Tier.FREE, search = Allowance(25, 1, 2, 22))
        answerWith(
            TermOutcome.Answered(listOf(FakeAircraft(hex = "AAAAAA")), true, capped = false, 1, null, charged = true)
        )
        val viewModel = createViewModel()

        viewModel.submitCurrent("DLH453")
        val items = viewModel.state.first().items

        items.last() shouldBe SearchViewModel.SearchItem.UpgradeBanner(
            SearchViewModel.BannerState.Remaining(remaining = 22, limit = 25)
        )
    }

    @Test
    fun `cached extras pad the rows but not the capped answer's share of the total`() = runTest {
        access.value = accessState(AccessState.Tier.FREE, search = Allowance(25, 1, 0, 24))
        // A capped term is incomplete, so the repo tops the list up from cache
        val answer = capped(shown = 10, totalMatching = 1318)
        val extras = (0 until 20).map { FakeAircraft(hex = "BBBB%02X".format(it)) }
        coEvery { searchRepo.search(any()) } returns SearchRepo.SearchResult(
            query = SearchQuery(listOf(SearchTerm("term0"))),
            terms = listOf(SearchRepo.TermResult(SearchTerm("term0"), answer, null, null)),
            aircraft = answer.aircraft + extras,
            cacheOnly = extras,
        )
        val viewModel = createViewModel()

        viewModel.submitCurrent("DLH453")
        val items = viewModel.state.first().items

        items.last() shouldBe SearchViewModel.SearchItem.UpgradeBanner(
            SearchViewModel.BannerState.Capped(shown = 10, total = 1318)
        )
    }

    @Test
    fun `a capped result stays explained once the installation is upgraded`() = runTest {
        access.value = accessState(AccessState.Tier.FREE, search = Allowance(25, 1, 0, 24))
        answerWith(capped(shown = 10, totalMatching = 1318))
        val viewModel = createViewModel()
        viewModel.submitCurrent("DLH453")

        // Registering a feeder mid-session leaves the capped result on screen under a Pro tier
        access.value = accessState(AccessState.Tier.FEEDER, search = Allowance(2000, 1, 0, 1999))
        val items = viewModel.state.first().items

        items.filterIsInstance<SearchViewModel.SearchItem.UpgradeBanner>() shouldBe emptyList()
        items.filterIsInstance<SearchViewModel.SearchItem.TermStatus>()
            .single().state shouldBe SearchViewModel.TermState.Capped(1318)
    }

    private fun capped(shown: Int, totalMatching: Int) = TermOutcome.Answered(
        aircraft = (0 until shown).map { FakeAircraft(hex = "AAAA%02X".format(it)) },
        complete = false,
        capped = true,
        totalMatching = totalMatching,
        expiresAt = null,
        charged = true,
    )

    private fun answerWith(vararg outcomes: TermOutcome) {
        val terms = outcomes.indices.map { SearchTerm("term$it") }
        val aircraft = outcomes
            .filterIsInstance<TermOutcome.Answered>()
            .flatMap { it.aircraft }
            .distinctBy { it.hex }
        coEvery { searchRepo.search(any()) } returns SearchRepo.SearchResult(
            query = SearchQuery(terms),
            terms = outcomes.mapIndexed { index, outcome ->
                SearchRepo.TermResult(term = terms[index], outcome = outcome, snapshot = null, usage = null)
            },
            aircraft = aircraft,
        )
    }

    private fun accessState(tier: AccessState.Tier, search: Allowance) = AccessState(
        installationId = "installation",
        fetchedAt = Instant.EPOCH,
        tier = tier,
        restricted = false,
        allowanceScope = "principal",
        limits = TierPolicy(
            viewingIntervalSeconds = 5,
            viewingBurst = 3,
            viewingPerDay = 25000,
            searchesPerDay = search.limit,
            watchesPerDay = 1000,
            maxRadiusNm = 25,
            watchTypes = listOf("hex", "callsign"),
        ),
        usage = Usage(
            resetsAt = 1_710_028_800_000L,
            viewing = Allowance(25000, 0, 0, 25000),
            search = search,
            watch = Allowance(1000, 0, 0, 1000),
        ),
        installationRequests = RequestLimits(
            viewing = RequestRate(perSecond = 1000.0, burst = 1000),
            search = RequestRate(perSecond = 1000.0, burst = 1000),
            watch = RequestRate(perSecond = 1000.0, burst = 1000),
            concurrency = 3,
        ),
    )

}
