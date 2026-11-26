package eu.darken.apl.upgrade.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import eu.darken.apl.feeder.core.link.FeederLinkRepo
import eu.darken.apl.feeder.core.link.FeederLinkRepo.FeederLinkState
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.server.access.AccessState.SubscriptionStatus
import eu.darken.apl.server.api.Allowance
import eu.darken.apl.server.api.RequestLimits
import eu.darken.apl.server.api.RequestRate
import eu.darken.apl.server.api.TierPolicy
import eu.darken.apl.server.api.Usage
import eu.darken.apl.server.session.SessionManager
import eu.darken.apl.server.session.SessionState
import eu.darken.apl.upgrade.UpgradeRepo
import eu.darken.apl.upgrade.subscription.RestoreResult
import eu.darken.apl.upgrade.subscription.SubscribeResult
import eu.darken.apl.upgrade.subscription.SubscriptionBilling
import eu.darken.apl.upgrade.subscription.SubscriptionState
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.coroutine.TestDispatcherProvider
import java.time.Instant

class UpgradeViewModelTest : BaseTest() {

    private val dispatcher = UnconfinedTestDispatcher()

    private object FreeInfo : UpgradeRepo.Info {
        override val type = UpgradeRepo.Type.GPLAY
        override val isPro = false
        override val isSettled = true
        override val source: UpgradeRepo.Source? = null
        override val error: Throwable? = null
    }

    private val upgradeRepo = mockk<UpgradeRepo> {
        every { upgradeInfo } returns MutableStateFlow<UpgradeRepo.Info>(FreeInfo)
    }
    private val feederLinkRepo = mockk<FeederLinkRepo> {
        every { state } returns MutableStateFlow<FeederLinkState>(FeederLinkState.Unlinked(tier = "free"))
        coEvery { refresh() } returns Unit
    }
    private val accessState = MutableStateFlow<AccessState?>(null)
    private val accessRepo = mockk<AccessRepo> {
        every { state } returns accessState
        coEvery { refresh(any()) } returns Unit
        coEvery { refreshUnlessRetrying(any()) } returns Unit
    }
    private val sessionManager = mockk<SessionManager> {
        every { state } returns MutableStateFlow<SessionState>(SessionState.NoSession)
    }
    private val subscriptionState = MutableStateFlow(
        SubscriptionState(
            supported = true,
            offer = SubscriptionState.Offer.Loading,
            ownership = SubscriptionState.Ownership.Unknown,
            submission = SubscriptionState.Submission.Idle,
        )
    )
    private val subscriptionBilling = mockk<SubscriptionBilling>(relaxed = true) {
        every { state } returns subscriptionState
    }

    @BeforeEach
    fun setup() {
        // viewModelScope is built on the main dispatcher
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        context: Context = mockk(relaxed = true),
    ) = UpgradeViewModel(
        dispatcherProvider = TestDispatcherProvider(dispatcher),
        upgradeRepo = upgradeRepo,
        feederLinkRepo = feederLinkRepo,
        accessRepo = accessRepo,
        sessionManager = sessionManager,
        subscriptionBilling = subscriptionBilling,
        webpageTool = mockk(relaxed = true),
        context = context,
        emailTool = mockk(relaxed = true),
    )

    private fun TestScope.errorsOf(vm: UpgradeViewModel): List<Throwable> {
        val errors = mutableListOf<Throwable>()
        backgroundScope.launch { vm.errorEvents.toList(errors) }
        return errors
    }

    private fun TestScope.eventsOf(vm: UpgradeViewModel): List<UpgradeEvents> {
        val events = mutableListOf<UpgradeEvents>()
        backgroundScope.launch { vm.events.toList(events) }
        return events
    }

    private fun accessStateOf(
        tier: AccessState.Tier,
        subscription: SubscriptionStatus? = null,
    ) = AccessState(
        installationId = "installation",
        fetchedAt = Instant.EPOCH,
        tier = tier,
        restricted = false,
        allowanceScope = "principal",
        limits = TierPolicy(
            viewingIntervalSeconds = 5, viewingBurst = 3, viewingPerDay = 25000, searchesPerDay = 25,
            watchesPerDay = 1000, maxRadiusNm = 25, watchTypes = listOf("hex", "callsign"),
        ),
        usage = Usage(
            resetsAt = 3_600_000,
            viewing = Allowance(25000, 0, 0, 25000),
            search = Allowance(25, 0, 0, 25),
            watch = Allowance(1000, 0, 0, 1000),
        ),
        installationRequests = RequestLimits(
            viewing = RequestRate(perSecond = 0.2, burst = 3),
            search = RequestRate(perSecond = 1.0, burst = 3),
            watch = RequestRate(perSecond = 1.0, burst = 3),
            concurrency = 3,
        ),
        subscription = subscription,
    )

    private val subscriber = accessStateOf(
        tier = AccessState.Tier.SUBSCRIBER,
        subscription = SubscriptionStatus(state = SubscriptionStatus.State.ACTIVE),
    )
    private val free = accessStateOf(tier = AccessState.Tier.FREE)

    /** The poll never ends on its own, so a test drives it with these instead of advanceUntilIdle. */
    private fun TestScope.advanceTimeTo(millis: Long) {
        advanceTimeBy(millis - testScheduler.currentTime)
        runCurrent()
    }

    private fun pollsShouldBe(count: Int) = coVerify(exactly = count) {
        accessRepo.refreshUnlessRetrying("upgrade-screen-poll")
    }

    private fun restoreReturns(result: RestoreResult) {
        coEvery { subscriptionBilling.restore() } returns result
    }

    private fun subscribeReturns(result: SubscribeResult) {
        coEvery { subscriptionBilling.subscribe(any()) } returns result
    }

    @Test
    fun `opening the screen reconciles the subscription`() = runTest(dispatcher) {
        createViewModel()

        verify(exactly = 1) { subscriptionBilling.reconcile("upgrade-screen") }
    }

    @Test
    fun `refresh reconciles the subscription`() = runTest(dispatcher) {
        val vm = createViewModel()

        vm.refresh()
        advanceUntilIdle()

        verify(exactly = 1) { subscriptionBilling.reconcile("manual") }
    }

    @Test
    fun `resuming the screen refreshes access`() = runTest(dispatcher) {
        val vm = createViewModel()

        vm.onResume()
        advanceUntilIdle()

        coVerify(exactly = 1) { accessRepo.refresh("upgrade-screen") }
    }

    @Test
    fun `a failed access refresh on resume shows nothing`() = runTest(dispatcher) {
        coEvery { accessRepo.refresh(any()) } throws SerializationException("bad policy")
        val vm = createViewModel()
        val errors = errorsOf(vm)

        vm.onResume()
        advanceUntilIdle()

        errors.shouldBeEmpty()
        vm.state.first().isRefreshing shouldBe false
    }

    @Test
    fun `a resumed screen with a subscription polls access`() = runTest(dispatcher) {
        accessState.value = subscriber
        val vm = createViewModel()

        vm.onResume()
        advanceTimeTo(14_999)
        pollsShouldBe(0)

        advanceTimeTo(15_000)
        pollsShouldBe(1)

        advanceTimeTo(30_000)
        pollsShouldBe(2)

        vm.onPause()
    }

    @Test
    fun `a resumed screen without a subscription does not poll`() = runTest(dispatcher) {
        accessState.value = free
        val vm = createViewModel()

        vm.onResume()
        advanceTimeTo(60_000)

        pollsShouldBe(0)
        vm.onPause()
    }

    @Test
    fun `a subscription arriving while resumed starts polling`() = runTest(dispatcher) {
        val vm = createViewModel()
        vm.onResume()

        advanceTimeTo(10_000)
        accessState.value = subscriber
        advanceTimeTo(24_999)
        pollsShouldBe(0)

        advanceTimeTo(25_000)
        pollsShouldBe(1)

        vm.onPause()
    }

    @Test
    fun `a subscription ending while resumed stops polling`() = runTest(dispatcher) {
        accessState.value = subscriber
        val vm = createViewModel()
        vm.onResume()

        advanceTimeTo(15_000)
        pollsShouldBe(1)

        accessState.value = free
        advanceTimeTo(60_000)
        pollsShouldBe(1)

        vm.onPause()
    }

    @Test
    fun `a subscription the server reports without granting the tier polls`() = runTest(dispatcher) {
        accessState.value = accessStateOf(
            tier = AccessState.Tier.FREE,
            subscription = SubscriptionStatus(state = SubscriptionStatus.State.ON_HOLD),
        )
        val vm = createViewModel()

        vm.onResume()
        advanceTimeTo(15_000)

        pollsShouldBe(1)
        vm.onPause()
    }

    @Test
    fun `usage updates do not postpone the poll`() = runTest(dispatcher) {
        accessState.value = subscriber
        val vm = createViewModel()
        vm.onResume()

        advanceTimeTo(5_000)
        accessState.value = subscriber.copy(usage = subscriber.usage.copy(search = Allowance(25, 1, 0, 24)))
        advanceTimeTo(10_000)
        accessState.value = subscriber.copy(usage = subscriber.usage.copy(search = Allowance(25, 2, 0, 23)))

        advanceTimeTo(15_000)
        pollsShouldBe(1)

        vm.onPause()
    }

    @Test
    fun `resuming again while resumed does not add a second poll`() = runTest(dispatcher) {
        accessState.value = subscriber
        val vm = createViewModel()

        vm.onResume()
        advanceTimeTo(5_000)
        vm.onResume()

        advanceTimeTo(15_000)
        pollsShouldBe(1)

        advanceTimeTo(30_000)
        pollsShouldBe(2)

        vm.onPause()
    }

    @Test
    fun `pausing stops the poll and resuming restarts it`() = runTest(dispatcher) {
        accessState.value = subscriber
        val vm = createViewModel()
        vm.onResume()

        advanceTimeTo(15_000)
        pollsShouldBe(1)

        advanceTimeTo(20_000)
        vm.onPause()
        advanceTimeTo(60_000)
        pollsShouldBe(1)

        vm.onResume()
        advanceTimeTo(74_999)
        pollsShouldBe(1)

        advanceTimeTo(75_000)
        pollsShouldBe(2)

        vm.onPause()
    }

    @Test
    fun `a failed poll shows nothing and the next one still runs`() = runTest(dispatcher) {
        coEvery { accessRepo.refreshUnlessRetrying(any()) } throws SerializationException("bad policy")
        accessState.value = subscriber
        val vm = createViewModel()
        val errors = errorsOf(vm)

        vm.onResume()
        advanceTimeTo(15_000)
        pollsShouldBe(1)

        advanceTimeTo(30_000)
        pollsShouldBe(2)

        errors.shouldBeEmpty()
        vm.onPause()
    }

    @Test
    fun `an unavailable subscription surfaces as an error`() = runTest(dispatcher) {
        subscribeReturns(SubscribeResult.Unavailable)
        val vm = createViewModel()
        val errors = errorsOf(vm)

        vm.subscribe(mockk<Activity>())
        advanceUntilIdle()

        errors shouldHaveSize 1
        errors.single().shouldBeInstanceOf<SubscribeException>().result shouldBe SubscribeResult.Unavailable
    }

    @Test
    fun `a failed subscription surfaces as an error`() = runTest(dispatcher) {
        val failure = SubscribeResult.Failed(IllegalStateException("billing"))
        subscribeReturns(failure)
        val vm = createViewModel()
        val errors = errorsOf(vm)

        vm.subscribe(mockk<Activity>())
        advanceUntilIdle()

        errors shouldHaveSize 1
        errors.single().shouldBeInstanceOf<SubscribeException>().result shouldBe failure
    }

    @Test
    fun `a canceled or already owned subscription shows nothing`() = runTest(dispatcher) {
        val vm = createViewModel()
        val errors = errorsOf(vm)

        subscribeReturns(SubscribeResult.Canceled)
        vm.subscribe(mockk<Activity>())
        subscribeReturns(SubscribeResult.AlreadyOwned)
        vm.subscribe(mockk<Activity>())
        subscribeReturns(SubscribeResult.Launched)
        vm.subscribe(mockk<Activity>())
        advanceUntilIdle()

        errors.shouldBeEmpty()
    }

    @Test
    fun `a restore that finds no subscription says so`() = runTest(dispatcher) {
        restoreReturns(RestoreResult.NothingFound)
        val vm = createViewModel()
        val events = eventsOf(vm)

        vm.restore()
        advanceUntilIdle()

        events shouldBe listOf(UpgradeEvents.RestoreFoundNothing)
    }

    @Test
    fun `a restore that can't reach Google Play says so`() = runTest(dispatcher) {
        restoreReturns(RestoreResult.Unreachable)
        val vm = createViewModel()
        val events = eventsOf(vm)

        vm.restore()
        advanceUntilIdle()

        events shouldBe listOf(UpgradeEvents.RestoreUnreachable)
    }

    @Test
    fun `a restore that finds the subscription shows nothing`() = runTest(dispatcher) {
        restoreReturns(RestoreResult.Found)
        val vm = createViewModel()
        val events = eventsOf(vm)

        vm.restore()
        advanceUntilIdle()

        events.shouldBeEmpty()
    }

    @Test
    fun `contacting support without an email app says so`() = runTest(dispatcher) {
        val context = mockk<Context>(relaxed = true) {
            every { startActivity(any()) } throws ActivityNotFoundException()
        }
        val vm = createViewModel(context = context)
        val events = eventsOf(vm)

        vm.contactSupport()
        advanceUntilIdle()

        events shouldBe listOf(UpgradeEvents.SupportEmailUnavailable)
    }

    @Test
    fun `restoring shows while the restore runs`() = runTest(dispatcher) {
        val done = CompletableDeferred<RestoreResult>()
        coEvery { subscriptionBilling.restore() } coAnswers { done.await() }
        val vm = createViewModel()
        vm.state.first().isRestoring shouldBe false

        vm.restore()
        vm.state.first().isRestoring shouldBe true

        done.complete(RestoreResult.Found)
        advanceUntilIdle()
        vm.state.first().isRestoring shouldBe false
    }
}
