package eu.darken.apl.upgrade.ui

import eu.darken.apl.feeder.core.FeederDiscovery
import eu.darken.apl.feeder.core.link.FeederLinkRepo
import eu.darken.apl.feeder.core.link.FeederLinkRepo.FeederLinkState
import eu.darken.apl.feeder.ui.add.DetectedFeeder
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
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
import java.util.UUID

class FeederRegisterViewModelTest : BaseTest() {

    private val linkState = MutableStateFlow<FeederLinkState>(FeederLinkState.Unlinked(tier = "free"))
    private val feederLinkRepo = mockk<FeederLinkRepo>()
    private val feederDiscovery = mockk<FeederDiscovery>()

    private val first = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val second = UUID.fromString("00000000-0000-0000-0000-000000000002")

    @BeforeEach
    fun setup() {
        // viewModelScope is built on the main dispatcher
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { feederLinkRepo.state } returns linkState
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun detection(vararg uuids: UUID) = FeederDiscovery.Detection(
        host = "203.0.113.7",
        feeders = uuids.map { DetectedFeeder(uuid = it, host = "203.0.113.7", label = null, latitude = null, longitude = null) },
    )

    private fun scanFinds(vararg uuids: UUID) {
        coEvery { feederDiscovery.scan() } returns detection(*uuids)
    }

    private fun createViewModel() = FeederRegisterViewModel(
        dispatcherProvider = TestDispatcherProvider(),
        feederLinkRepo = feederLinkRepo,
        feederDiscovery = feederDiscovery,
        webpageTool = mockk(relaxed = true),
    )

    @Test
    fun `opening the screen scans and selects the only feeder found`() = runTest {
        scanFinds(first)

        val vm = createViewModel()

        vm.state.value.detection?.found shouldBe listOf(first.toString())
        vm.state.value.selected shouldBe first.toString()
        vm.events.first() shouldBe FeederRegisterEvents.FeedersFound(1, "203.0.113.7")
    }

    @Test
    fun `a feeder listed twice counts once`() = runTest {
        scanFinds(first, first)

        val vm = createViewModel()

        vm.state.value.selected shouldBe first.toString()
        vm.state.value.detection?.found shouldBe listOf(first.toString())
        vm.events.first() shouldBe FeederRegisterEvents.FeedersFound(1, "203.0.113.7")
    }

    @Test
    fun `a scan finding several feeders leaves the choice to the user`() {
        scanFinds(first, second)

        createViewModel().state.value.selected shouldBe null
    }

    @Test
    fun `a link state that is not known yet still scans, once`() {
        linkState.value = FeederLinkState.Unknown
        scanFinds(first)

        createViewModel()

        coVerify(exactly = 1) { feederDiscovery.scan() }
    }

    @Test
    fun `an already linked installation does not scan on open`() {
        linkState.value = FeederLinkState.Linked(feeder = mockk(), tier = "feeder")

        createViewModel()

        coVerify(exactly = 0) { feederDiscovery.scan() }
    }

    @Test
    fun `a rescan keeps an id the user typed rather than picking the only feeder found`() {
        scanFinds()
        val vm = createViewModel()
        vm.addManual("typed-id")

        scanFinds(first)
        vm.detect()

        vm.state.value.selected shouldBe "typed-id"
    }

    @Test
    fun `a rescan keeps a picked feeder that is still found`() {
        scanFinds(first, second)
        val vm = createViewModel()
        vm.select(second.toString())

        vm.detect()

        coVerify(exactly = 2) { feederDiscovery.scan() }
        vm.state.value.selected shouldBe second.toString()
    }

    @Test
    fun `a rescan replaces a picked feeder that is gone with the only one found`() {
        scanFinds(first, second)
        val vm = createViewModel()
        vm.select(first.toString())

        scanFinds(second)
        vm.detect()

        vm.state.value.selected shouldBe second.toString()
    }

    @Test
    fun `a rescan drops a picked feeder that is gone when several are found`() {
        val third = UUID.fromString("00000000-0000-0000-0000-000000000003")
        scanFinds(first)
        val vm = createViewModel()
        vm.state.value.selected shouldBe first.toString()

        scanFinds(second, third)
        vm.detect()

        vm.state.value.selected shouldBe null
    }

    @Test
    fun `feeders found after the link turned out to exist are not announced`() = runTest {
        linkState.value = FeederLinkState.Unknown
        val pending = CompletableDeferred<FeederDiscovery.Detection>()
        coEvery { feederDiscovery.scan() } coAnswers { pending.await() }
        val vm = createViewModel()

        linkState.value = FeederLinkState.Linked(feeder = mockk(), tier = "feeder")
        pending.complete(detection(first))

        vm.state.value.isBusy shouldBe false
        withTimeoutOrNull(1_000) { vm.events.first() } shouldBe null
    }
}
