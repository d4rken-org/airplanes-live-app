package eu.darken.apl.feeder.ui.detail

import eu.darken.apl.common.ClipboardHelper
import eu.darken.apl.common.navigation.NavEvent
import eu.darken.apl.feeder.core.Feeder
import eu.darken.apl.feeder.core.FeederRepo
import eu.darken.apl.feeder.core.monitor.FeederMuteController
import eu.darken.apl.feeder.core.stats.FeederDetail
import eu.darken.apl.feeder.core.stats.FeederStatsRepo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.HttpException
import retrofit2.Response
import testhelper.BaseTest
import testhelper.coroutine.TestDispatcherProvider
import java.io.IOException
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class FeederDetailViewModelTest : BaseTest() {

    private val feederId = "4d3d2c9f-c0bd-4243-90d0-d913a0ac526a"
    private val feeder = Feeder(id = feederId, name = "Testfeeder")
    private val detail = FeederDetail(
        live = emptyList(),
        history = emptyMap(),
        stats = null,
        fetchedAt = Instant.EPOCH,
    )

    private val feedersFlow = MutableStateFlow(listOf(feeder))
    private val loggedInFlow = MutableStateFlow(true)
    private val mutedFlow = MutableStateFlow(emptySet<String>())

    private lateinit var feederRepo: FeederRepo
    private lateinit var statsRepo: FeederStatsRepo
    private lateinit var muteController: FeederMuteController
    private lateinit var clipboardHelper: ClipboardHelper

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        feedersFlow.value = listOf(feeder)
        loggedInFlow.value = true
        mutedFlow.value = emptySet()
        feederRepo = mockk(relaxed = true) {
            every { feeders } returns feedersFlow
            every { isLoggedIn } returns loggedInFlow
        }
        statsRepo = mockk {
            coEvery { getDetail(any(), any()) } returns detail
        }
        muteController = mockk(relaxed = true) {
            every { mutedFeeders } returns mutedFlow
        }
        clipboardHelper = mockk {
            justRun { copyToClipboard(any()) }
        }
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun createVm() = FeederDetailViewModel(
        dispatcherProvider = TestDispatcherProvider(),
        feederRepo = feederRepo,
        feederStatsRepo = statsRepo,
        muteController = muteController,
        clipboardHelper = clipboardHelper,
    )

    private suspend fun FeederDetailViewModel.awaitDetail(): FeederDetailViewModel.DetailState =
        state.first().detail

    @Test
    fun `initial load succeeds into Ready`() = runTest {
        val vm = createVm()
        vm.init(feederId)

        val state = vm.awaitDetail().shouldBeInstanceOf<FeederDetailViewModel.DetailState.Ready>()
        state.detail shouldBe detail
        state.isRefreshing shouldBe false
        // The feeder is already cached; init must not force a redundant list refresh.
        coVerify(exactly = 0) { feederRepo.refresh() }
    }

    @Test
    fun `cold start without cached feeder refreshes the list`() = runTest {
        feedersFlow.value = emptyList()
        val vm = createVm()
        vm.init(feederId)

        coVerify(exactly = 1) { feederRepo.refresh() }
    }

    @Test
    fun `initial load failure becomes Error`() = runTest {
        coEvery { statsRepo.getDetail(any(), any()) } throws IOException("offline")
        val vm = createVm()
        vm.init(feederId)

        vm.awaitDetail().shouldBeInstanceOf<FeederDetailViewModel.DetailState.Error>()
    }

    @Test
    fun `manual refresh failure keeps last good data and stops the spinner`() = runTest {
        val vm = createVm()
        vm.init(feederId)
        vm.awaitDetail().shouldBeInstanceOf<FeederDetailViewModel.DetailState.Ready>()

        coEvery { statsRepo.getDetail(any(), any()) } throws IOException("offline")
        vm.refresh()

        val state = vm.awaitDetail().shouldBeInstanceOf<FeederDetailViewModel.DetailState.Ready>()
        state.detail shouldBe detail
        state.isRefreshing shouldBe false
    }

    @Test
    fun `silent auto refresh failure keeps last good data`() = runTest {
        val vm = createVm()
        vm.init(feederId)
        vm.awaitDetail().shouldBeInstanceOf<FeederDetailViewModel.DetailState.Ready>()

        coEvery { statsRepo.getDetail(any(), any()) } throws IOException("offline")
        vm.autoRefresh()

        val state = vm.awaitDetail().shouldBeInstanceOf<FeederDetailViewModel.DetailState.Ready>()
        state.detail shouldBe detail
        state.isRefreshing shouldBe false
    }

    @Test
    fun `silent auto refresh failure after initial error keeps the state untouched`() = runTest {
        coEvery { statsRepo.getDetail(any(), any()) } throws IOException("offline")
        val vm = createVm()
        vm.init(feederId)
        val initial = vm.awaitDetail().shouldBeInstanceOf<FeederDetailViewModel.DetailState.Error>()

        vm.autoRefresh()

        // Same instance: a silent cycle never replaces or re-triggers the error UI.
        vm.awaitDetail() shouldBe initial
    }

    @Test
    fun `auto refresh skips while a manual refresh is in flight`() = runTest {
        val vm = createVm()
        vm.init(feederId)
        vm.awaitDetail().shouldBeInstanceOf<FeederDetailViewModel.DetailState.Ready>()

        val gate = CompletableDeferred<FeederDetail>()
        coEvery { statsRepo.getDetail(any(), any()) } coAnswers { gate.await() }

        vm.refresh()
        vm.autoRefresh()

        gate.complete(detail)

        // Initial load + the manual refresh; the auto refresh must have bailed on tryLock.
        coVerify(exactly = 2) { statsRepo.getDetail(any(), any()) }
    }

    @Test
    fun `manual refresh queued behind auto refresh still runs and clears the spinner`() = runTest {
        val vm = createVm()
        vm.init(feederId)
        vm.awaitDetail().shouldBeInstanceOf<FeederDetailViewModel.DetailState.Ready>()

        val gate = CompletableDeferred<FeederDetail>()
        var gated = true
        coEvery { statsRepo.getDetail(any(), any()) } coAnswers {
            if (gated) gate.await() else detail
        }

        vm.autoRefresh()
        vm.refresh()

        gated = false
        gate.complete(detail)

        // Initial load + auto refresh + the queued manual refresh — never dropped.
        coVerify(exactly = 3) { statsRepo.getDetail(any(), any()) }
        val state = vm.awaitDetail().shouldBeInstanceOf<FeederDetailViewModel.DetailState.Ready>()
        state.isRefreshing shouldBe false
    }

    @Test
    fun `list refresh failure does not abort the detail load`() = runTest {
        coEvery { feederRepo.refresh() } throws IOException("list down")
        val vm = createVm()
        vm.init(feederId)

        vm.refresh()

        val state = vm.awaitDetail().shouldBeInstanceOf<FeederDetailViewModel.DetailState.Ready>()
        state.detail shouldBe detail
    }

    @Test
    fun `http 404 navigates up`() = runTest {
        coEvery { statsRepo.getDetail(any(), any()) } throws HttpException(
            Response.error<Any>(404, "gone".toResponseBody())
        )
        val vm = createVm()
        vm.init(feederId)

        vm.navEvents.first() shouldBe NavEvent.Up
    }

    @Test
    fun `enabling notifications unmutes and disabling mutes`() = runTest {
        val vm = createVm()
        vm.init(feederId)

        vm.setNotificationsEnabled(true)
        coVerify { muteController.setMuted(feederId, false) }

        vm.setNotificationsEnabled(false)
        coVerify { muteController.setMuted(feederId, true) }
    }

    @Test
    fun `copy copies the full feeder id`() = runTest {
        val vm = createVm()
        vm.init(feederId)

        vm.copyFeederId()

        coVerify { clipboardHelper.copyToClipboard(feederId) }
    }

    @Test
    fun `logout navigates up`() = runTest {
        val vm = createVm()
        vm.init(feederId)

        loggedInFlow.value = false

        vm.navEvents.first() shouldBe NavEvent.Up
    }
}
