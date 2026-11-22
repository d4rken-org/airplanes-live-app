package eu.darken.apl.upgrade.ui

import android.app.Activity
import eu.darken.apl.feeder.core.link.FeederLinkRepo
import eu.darken.apl.feeder.core.link.FeederLinkRepo.FeederLinkState
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.server.session.SessionManager
import eu.darken.apl.server.session.SessionState
import eu.darken.apl.upgrade.UpgradeRepo
import eu.darken.apl.upgrade.subscription.SubscribeResult
import eu.darken.apl.upgrade.subscription.SubscriptionBilling
import eu.darken.apl.upgrade.subscription.SubscriptionState
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.coroutine.TestDispatcherProvider

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
    private val accessRepo = mockk<AccessRepo> {
        every { state } returns MutableStateFlow<AccessState?>(null)
        coEvery { refresh(any()) } returns Unit
    }
    private val sessionManager = mockk<SessionManager> {
        every { state } returns MutableStateFlow<SessionState>(SessionState.NoSession)
    }
    private val subscriptionBilling = mockk<SubscriptionBilling>(relaxed = true) {
        every { state } returns MutableStateFlow(
            SubscriptionState(
                supported = true,
                offer = SubscriptionState.Offer.Loading,
                ownership = SubscriptionState.Ownership.Unknown,
                submission = SubscriptionState.Submission.Idle,
            )
        )
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

    private fun createViewModel() = UpgradeViewModel(
        dispatcherProvider = TestDispatcherProvider(dispatcher),
        upgradeRepo = upgradeRepo,
        feederLinkRepo = feederLinkRepo,
        accessRepo = accessRepo,
        sessionManager = sessionManager,
        subscriptionBilling = subscriptionBilling,
        webpageTool = mockk(relaxed = true),
    )

    private fun TestScope.errorsOf(vm: UpgradeViewModel): List<Throwable> {
        val errors = mutableListOf<Throwable>()
        backgroundScope.launch { vm.errorEvents.toList(errors) }
        return errors
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
}
