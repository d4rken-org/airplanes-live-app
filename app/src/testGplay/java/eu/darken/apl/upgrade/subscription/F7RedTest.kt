package eu.darken.apl.upgrade.subscription

import android.app.Activity
import eu.darken.apl.feeder.core.link.FeederLinkRepo
import eu.darken.apl.feeder.core.link.FeederLinkRepo.FeederLinkState
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.server.session.SessionManager
import eu.darken.apl.server.session.SessionState
import eu.darken.apl.upgrade.UpgradeRepo
import eu.darken.apl.upgrade.subscription.play.LaunchOutcome
import eu.darken.apl.upgrade.subscription.play.OfferQuery
import eu.darken.apl.upgrade.subscription.play.PlayBilling
import eu.darken.apl.upgrade.subscription.play.PlayConnection
import eu.darken.apl.upgrade.subscription.play.PurchaseQuery
import eu.darken.apl.upgrade.subscription.play.PurchaseUpdate
import eu.darken.apl.upgrade.subscription.submit.SubmissionFixture
import eu.darken.apl.upgrade.subscription.submit.SubmissionFixture.Companion.INSTALLATION_A
import eu.darken.apl.upgrade.ui.UpgradeEvents
import eu.darken.apl.upgrade.ui.UpgradeViewModel
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.coroutine.TestDispatcherProvider

class F7RedTest : BaseTest() {

    private class FakePlayBilling : PlayBilling {
        override val connection = MutableStateFlow<PlayConnection>(PlayConnection.Ready)
        override val purchaseUpdates: Flow<PurchaseUpdate> = emptyFlow()
        var purchases: PurchaseQuery = PurchaseQuery.Success(emptyList())

        override suspend fun connect(): PlayConnection = PlayConnection.Ready
        override suspend fun queryPurchases(): PurchaseQuery = purchases
        override suspend fun queryOffer(): OfferQuery = OfferQuery.Missing
        override suspend fun launch(activity: Activity): LaunchOutcome = LaunchOutcome.Launched
        override suspend fun showInAppMessages(activity: Activity, onSubscriptionStatusUpdated: () -> Unit) = Unit
    }

    private object FreeInfo : UpgradeRepo.Info {
        override val type = UpgradeRepo.Type.GPLAY
        override val isPro = false
        override val isSettled = true
        override val source: UpgradeRepo.Source? = null
        override val error: Throwable? = null
    }

    private val dispatcher = UnconfinedTestDispatcher()

    private val fixture = SubmissionFixture()
    private val play = FakePlayBilling()
    private val sessionState = MutableStateFlow<SessionState>(SessionState.Active(INSTALLATION_A))
    private val coordinatorSessionManager = mockk<SessionManager>().apply {
        every { state } returns sessionState
    }
    private var foreground: (() -> Unit)? = null

    private val upgradeRepo = mockk<UpgradeRepo> {
        every { upgradeInfo } returns MutableStateFlow<UpgradeRepo.Info>(FreeInfo)
    }
    private val feederLinkRepo = mockk<FeederLinkRepo> {
        every { state } returns MutableStateFlow<FeederLinkState>(FeederLinkState.Unlinked(tier = "free"))
        coEvery { refresh() } returns Unit
    }
    private val vmAccessRepo = mockk<AccessRepo> {
        every { state } returns MutableStateFlow<AccessState?>(null)
        coEvery { refresh(any()) } returns Unit
    }
    private val vmSessionManager = mockk<SessionManager> {
        every { state } returns MutableStateFlow<SessionState>(SessionState.NoSession)
    }

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
        fixture.close()
    }

    private fun TestScope.coordinator() = SubscriptionBillingGplay(
        appScope = backgroundScope,
        playBilling = play,
        submissions = fixture.submissions,
        accessRepo = fixture.accessRepo,
        sessionManager = coordinatorSessionManager,
        privacyAcceptance = fixture.privacyAcceptance,
        observeForeground = { foreground = it },
        packageName = "eu.darken.apl",
    ).apply {
        setup()
        runCurrent()
    }

    private suspend fun TestScope.settle(billing: SubscriptionBillingGplay) {
        runCurrent()
        billing.subscribe(mockk()) shouldBe SubscribeResult.Launched
    }

    private fun createViewModel(billing: SubscriptionBilling) = UpgradeViewModel(
        dispatcherProvider = TestDispatcherProvider(dispatcher),
        upgradeRepo = upgradeRepo,
        feederLinkRepo = feederLinkRepo,
        accessRepo = vmAccessRepo,
        sessionManager = vmSessionManager,
        subscriptionBilling = billing,
        webpageTool = mockk(relaxed = true),
        context = mockk(relaxed = true),
        emailTool = mockk(relaxed = true),
    )

    /** Runs a failing Restore through the VM, returns what it emitted. */
    private suspend fun TestScope.failingRestore(billing: SubscriptionBillingGplay): UpgradeEvents {
        play.purchases = PurchaseQuery.Failed(-1)
        val vm = createViewModel(billing)
        settle(billing) // the VM's init reconcile ("upgrade-screen") has finished, also against the failing query

        vm.restore()
        // DataStore reads run on real IO threads: wait in real time
        return withContext(Dispatchers.Default) { withTimeout(10_000) { vm.events.first() } }
    }

    @Test
    fun `F7 a failing Restore after Play reported no purchase says unreachable, not found nothing`() = runTest(dispatcher) {
        play.purchases = PurchaseQuery.Success(emptyList())
        val billing = coordinator()
        foreground!!.invoke()
        settle(billing)
        withClue("precondition: cached ownership after the first successful query") {
            billing.state.first().ownership shouldBe SubscriptionState.Ownership.None
        }

        val event = failingRestore(billing)

        withClue("Restore's purchase query failed") {
            event shouldBe UpgradeEvents.RestoreUnreachable
        }
    }

    @Test
    fun `F7 a failing Restore after Play reported an owned purchase says unreachable`() = runTest(dispatcher) {
        play.purchases = PurchaseQuery.Success(listOf(SubmissionFixture.purchase(TOKEN)))
        val billing = coordinator()
        foreground!!.invoke()
        settle(billing)
        withClue("precondition: cached ownership after the first successful query") {
            billing.state.first().ownership shouldBe SubscriptionState.Ownership.Owned(suspended = false)
        }

        val event = failingRestore(billing)

        withClue("Restore's purchase query failed") {
            event shouldBe UpgradeEvents.RestoreUnreachable
        }
    }

    companion object {
        private const val TOKEN = "PURCHASE-TOKEN-SENTINEL"
    }
}
