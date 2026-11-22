package eu.darken.apl.backup.ui

import androidx.lifecycle.SavedStateHandle
import eu.darken.apl.backup.core.BackupRepo
import eu.darken.apl.feeder.core.link.FeederLinkRepo
import eu.darken.apl.feeder.core.link.FeederLinkRepo.FeederLinkState
import eu.darken.apl.upgrade.UpgradeRepo
import eu.darken.apl.upgrade.subscription.SubscriptionBilling
import eu.darken.apl.upgrade.subscription.SubscriptionState
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
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

class BackupRestoreViewModelTest : BaseTest() {

    private val dispatcher = UnconfinedTestDispatcher()
    private val backupRepo = mockk<BackupRepo>(relaxed = true)
    private val feederLinkRepo = mockk<FeederLinkRepo>()
    private val upgradeRepo = mockk<UpgradeRepo>()
    private val subscriptionState = MutableStateFlow(subscription(supported = false))
    private val subscriptionBilling = mockk<SubscriptionBilling> {
        every { state } returns subscriptionState
    }

    private fun subscription(supported: Boolean) = SubscriptionState(
        supported = supported,
        offer = SubscriptionState.Offer.Unavailable,
        ownership = SubscriptionState.Ownership.None,
        submission = SubscriptionState.Submission.Idle,
    )

    private object FreeInfo : UpgradeRepo.Info {
        override val type = UpgradeRepo.Type.FOSS
        override val isPro = false
        override val isSettled = true
        override val source: UpgradeRepo.Source? = null
        override val error: Throwable? = null
    }

    @BeforeEach
    fun setup() {
        // viewModelScope is built on the main dispatcher
        Dispatchers.setMain(dispatcher)
        every { feederLinkRepo.state } returns MutableStateFlow<FeederLinkState>(FeederLinkState.Unknown)
        every { upgradeRepo.upgradeInfo } returns MutableStateFlow<UpgradeRepo.Info>(FreeInfo)
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = BackupRestoreViewModel(
        handle = SavedStateHandle(),
        dispatcherProvider = TestDispatcherProvider(dispatcher),
        backupRepo = backupRepo,
        feederLinkRepo = feederLinkRepo,
        upgradeRepo = upgradeRepo,
        subscriptionBilling = subscriptionBilling,
    )

    private fun TestScope.eventsOf(vm: BackupRestoreViewModel): List<BackupRestoreEvents> {
        val events = mutableListOf<BackupRestoreEvents>()
        backgroundScope.launch { vm.events.toList(events) }
        return events
    }

    private fun restoreReturns(result: BackupRepo.RestoreResult) {
        coEvery { backupRepo.restoreBackup(any(), any()) } returns result
    }

    @Test
    fun `a restore that imported alerts asks for the notification permission once`() = runTest(dispatcher) {
        restoreReturns(BackupRepo.RestoreResult(watchesImported = 3, watchAlertsImported = 2))
        val vm = createViewModel()
        val events = eventsOf(vm)

        vm.onConfirmRestore(BackupRepo.RestoreOptions())
        advanceUntilIdle()

        events shouldBe listOf(BackupRestoreEvents.RequestNotificationPermission)
    }

    @Test
    fun `a restore without imported alerts does not ask`() = runTest(dispatcher) {
        restoreReturns(BackupRepo.RestoreResult(watchesImported = 3, watchAlertsImported = 0))
        val vm = createViewModel()
        val events = eventsOf(vm)

        vm.onConfirmRestore(BackupRepo.RestoreOptions())
        advanceUntilIdle()

        events shouldBe emptyList()
    }

    @Test
    fun `the state says whether subscriptions are supported`() = runTest(dispatcher) {
        createViewModel().state.value.subscriptionSupported shouldBe false

        subscriptionState.value = subscription(supported = true)
        val vm = createViewModel()
        advanceUntilIdle()

        vm.state.value.subscriptionSupported shouldBe true
    }
}
