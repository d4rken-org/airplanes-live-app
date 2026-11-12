package eu.darken.apl.feeder.ui.actions

import eu.darken.apl.feeder.core.Feeder
import eu.darken.apl.feeder.core.FeederRepo
import eu.darken.apl.feeder.core.config.FeederConfig
import eu.darken.apl.feeder.core.server.FeederHistoryRepo
import eu.darken.apl.feeder.core.server.FeederServerMode
import eu.darken.apl.feeder.core.server.FeederServerView
import eu.darken.apl.server.api.FeederHistoryHour
import eu.darken.apl.server.api.FeederMonitorEntry
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.coroutine.TestDispatcherProvider

class FeederActionViewModelTest : BaseTest() {

    private val feeders = MutableStateFlow<Collection<Feeder>>(emptyList())
    private val feederRepo = mockk<FeederRepo>(relaxed = true).apply {
        every { feeders } returns this@FeederActionViewModelTest.feeders
    }
    private val historyRepo = mockk<FeederHistoryRepo>()
    private val serverMode = mockk<FeederServerMode>().apply {
        every { state } returns MutableStateFlow(FeederServerMode.State(access = null, isActive = false))
    }

    @BeforeEach
    fun setup() {
        // viewModelScope is built on the main dispatcher
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { historyRepo.get(any(), any()) } returns HOURS
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = FeederActionViewModel(
        dispatcherProvider = TestDispatcherProvider(),
        feederRepo = feederRepo,
        webpageTool = mockk(relaxed = true),
        feederHistoryRepo = historyRepo,
        feederServerMode = serverMode,
    ).apply { init(FEEDER_ID) }

    private val FeederActionViewModel.loaded
        get() = state.filterNotNull()

    private fun unavailable() = Feeder(config = FeederConfig.newFeeder(FEEDER_ID))

    private fun monitored(entry: FeederMonitorEntry? = ENTRY) =
        unavailable().copy(server = FeederServerView(feederId = FEEDER_ID, entry = entry))

    @Test
    fun `a monitored feeder loads the server's charts`() = runTest(UnconfinedTestDispatcher()) {
        feeders.value = listOf(monitored())
        val vm = createViewModel()

        val state = vm.loaded.first { it.serverCharts != FeederServerCharts.LOADING }

        state.serverCharts shouldBe FeederServerCharts.from(HOURS)
        coVerify(exactly = 1) { historyRepo.get(FEEDER_ID, any()) }
    }

    @Test
    fun `a feeder without a monitor yet gets the server's empty charts`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { historyRepo.get(any(), any()) } returns emptyList()
        feeders.value = listOf(monitored(entry = null))
        val vm = createViewModel()

        vm.loaded.first { it.serverCharts != FeederServerCharts.LOADING }.serverCharts shouldBe FeederServerCharts.NO_DATA
    }

    @Test
    fun `a feeder without server data has no charts and asks for no history`() = runTest(UnconfinedTestDispatcher()) {
        feeders.value = listOf(unavailable())
        val vm = createViewModel()

        vm.loaded.first().serverCharts shouldBe null
        coVerify(exactly = 0) { historyRepo.get(any(), any()) }
    }

    @Test
    fun `charts follow the feeder gaining and losing server data`() = runTest(UnconfinedTestDispatcher()) {
        feeders.value = listOf(unavailable())
        val vm = createViewModel()
        val states = MutableStateFlow<List<FeederActionViewModel.State>>(emptyList())
        backgroundScope.launch { vm.loaded.collect { state -> states.value = states.value + state } }
        states.first { it.isNotEmpty() }.last().serverCharts shouldBe null

        val history = CompletableDeferred<List<FeederHistoryHour>>()
        coEvery { historyRepo.get(any(), any()) } coAnswers { history.await() }
        feeders.value = listOf(monitored())
        states.first { it.last().serverCharts == FeederServerCharts.LOADING }
        history.complete(HOURS)
        states.first { it.last().serverCharts == FeederServerCharts.from(HOURS) }
        coVerify(exactly = 1) { historyRepo.get(FEEDER_ID, any()) }

        feeders.value = listOf(unavailable())
        states.first { it.last().serverCharts == null }
        coVerify(exactly = 1) { historyRepo.get(FEEDER_ID, any()) }
    }

    companion object {
        private const val FEEDER_ID = "0a1b2c3d-0000-4000-8000-000000000001"

        private val ENTRY = FeederMonitorEntry(
            feederId = FEEDER_ID,
            monitorId = "m",
            offlineAfterMinutes = 60,
            status = "active",
            createdAt = 0,
            updatedAt = 0,
            health = "online",
            eventSeq = 0,
        )

        private val HOURS = listOf(0L, 3_600_000L).map { hour ->
            FeederHistoryHour(
                hour = hour,
                msgsPerSecondAvg = 100.0 + hour / 3_600_000L,
                mlatPeersAvg = 5.0,
                mlatOutlierPercentAvg = 1.0,
                isFinal = true,
            )
        }
    }
}
