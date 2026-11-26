package eu.darken.apl.upgrade.subscription

import android.app.Activity
import eu.darken.apl.server.PrivacyAcceptance
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.server.session.SessionManager
import eu.darken.apl.server.session.SessionState
import eu.darken.apl.upgrade.UpgradeRepo
import eu.darken.apl.upgrade.subscription.SubscriptionState.Offer
import eu.darken.apl.upgrade.subscription.SubscriptionState.Ownership
import eu.darken.apl.upgrade.subscription.SubscriptionState.Submission
import eu.darken.apl.upgrade.subscription.play.LaunchOutcome
import eu.darken.apl.upgrade.subscription.play.OfferQuery
import eu.darken.apl.upgrade.subscription.play.PlayBilling
import eu.darken.apl.upgrade.subscription.play.PlayConnection
import eu.darken.apl.upgrade.subscription.play.PlayOffer
import eu.darken.apl.upgrade.subscription.play.PlayPurchase
import eu.darken.apl.upgrade.subscription.play.PlaySubscriptionProduct
import eu.darken.apl.upgrade.subscription.play.PurchaseQuery
import eu.darken.apl.upgrade.subscription.play.PurchaseUpdate
import eu.darken.apl.upgrade.subscription.submit.SubmissionFixture
import eu.darken.apl.upgrade.subscription.submit.SubscriptionSubmissions
import eu.darken.apl.upgrade.subscription.submit.SubscriptionTokenRecord
import eu.darken.apl.upgrade.subscription.submit.SubscriptionTokenRecord.PlayState
import eu.darken.apl.upgrade.subscription.submit.SubscriptionTokens
import eu.darken.apl.upgrade.core.UpgradeRepoGplay
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Instant
import java.time.Period

class SubscriptionBillingGplayTest : BaseTest() {

    private class FakePlayBilling : PlayBilling {
        override val connection = MutableStateFlow<PlayConnection>(PlayConnection.Ready)
        val updates = Channel<PurchaseUpdate>(Channel.UNLIMITED)
        override val purchaseUpdates: Flow<PurchaseUpdate> = updates.receiveAsFlow()

        var purchases: PurchaseQuery = PurchaseQuery.Success(emptyList())
        var offer: OfferQuery = OfferQuery.Missing
        var launchOutcome: LaunchOutcome = LaunchOutcome.Launched

        /** Holds the next purchase query until completed. */
        var gate: CompletableDeferred<Unit>? = null
        var purchaseQueries = 0
        var connects = 0
        var statusUpdated: (() -> Unit)? = null
        var connectResult: PlayConnection = PlayConnection.Ready

        override suspend fun connect(): PlayConnection {
            connects++
            return connectResult
        }

        override suspend fun queryPurchases(): PurchaseQuery {
            purchaseQueries++
            gate?.let {
                gate = null
                it.await()
            }
            return purchases
        }

        override suspend fun queryOffer(): OfferQuery = offer

        override suspend fun launch(activity: Activity): LaunchOutcome = launchOutcome

        override suspend fun showInAppMessages(activity: Activity, onSubscriptionStatusUpdated: () -> Unit) {
            statusUpdated = onSubscriptionStatusUpdated
        }
    }

    private data class Request(val installationId: String, val tokenHash: String, val playState: PlayState)

    private val play = FakePlayBilling()

    private val tokens = MutableStateFlow(SubscriptionTokens())
    private val requests = mutableListOf<Request>()
    private val resumed = mutableListOf<String>()

    /** Requests that ask for a submission, the others only record the purchase. */
    private val submitted: List<String>
        get() = requests.filter { it.playState == PlayState.PURCHASED }.map { it.tokenHash }

    private val awaitingFor = MutableStateFlow<String?>(null)
    private val submissions = mockk<SubscriptionSubmissions>().apply {
        every { records(any()) } answers {
            val installationId = firstArg<String>()
            tokens.map { it.recordsOf(installationId) }
        }
        every { awaitingFor } returns this@SubscriptionBillingGplayTest.awaitingFor
        coEvery { request(any(), any(), any()) } coAnswers { fakeRequest(firstArg(), secondArg()) }
        coEvery { resume(any(), any(), any()) } coAnswers { resumed += secondArg<String>() }
    }

    private val accessState = MutableStateFlow<AccessState?>(null)
    private val accessRepo = mockk<AccessRepo>(relaxed = true).apply {
        every { state } returns accessState
    }
    private val sessionState = MutableStateFlow<SessionState>(SessionState.Active(INSTALLATION_A))
    private val sessionManager = mockk<SessionManager>().apply {
        every { state } returns sessionState
    }
    private val privacyAccepted = MutableStateFlow(true)

    private var foreground: (() -> Unit)? = null

    // Mirrors SubscriptionSubmissions.request without the store and the work queue
    private fun fakeRequest(installationId: String, purchase: PlayPurchase): SubscriptionTokenRecord? {
        val playState = when {
            purchase.isSuspended -> PlayState.SUSPENDED
            purchase.state == PlayPurchase.State.PURCHASED -> PlayState.PURCHASED
            purchase.state == PlayPurchase.State.PENDING -> PlayState.PENDING
            else -> return null
        }
        requests += Request(installationId, purchase.tokenHash, playState)
        val record = tokens.value.find(installationId, purchase.tokenHash) ?: SubscriptionTokenRecord(
            installationId = installationId,
            tokenHash = purchase.tokenHash,
            token = purchase.token,
            productId = PlaySubscriptionProduct.PRODUCT_ID,
        )
        val updated = record.copy(playState = playState).let {
            if (playState == PlayState.PURCHASED) {
                it.copy(requestedGen = it.requestedGen + 1, submission = SubscriptionTokenRecord.Submission.REQUESTED)
            } else {
                it
            }
        }
        tokens.value = tokens.value.upsert(updated)
        return updated
    }

    /** What the submission job leaves behind. */
    private fun finishSubmission(
        token: String,
        submission: SubscriptionTokenRecord.Submission = SubscriptionTokenRecord.Submission.ATTACHED,
    ) {
        tokens.value = tokens.value.updateRecord(INSTALLATION_A, hash(token)) {
            it.copy(completedGen = it.requestedGen, submission = submission)
        }
    }

    private fun stored(
        token: String,
        playState: PlayState = PlayState.PURCHASED,
        submission: SubscriptionTokenRecord.Submission = SubscriptionTokenRecord.Submission.ATTACHED,
        open: Boolean = false,
        installationId: String = INSTALLATION_A,
    ) {
        tokens.value = tokens.value.upsert(
            SubscriptionTokenRecord(
                installationId = installationId,
                tokenHash = hash(token),
                token = token,
                productId = PlaySubscriptionProduct.PRODUCT_ID,
                playState = playState,
                submission = submission,
                requestedGen = 2,
                completedGen = if (open) 1 else 2,
            )
        )
    }

    private fun playReports(vararg purchases: PlayPurchase) {
        play.purchases = PurchaseQuery.Success(purchases.toList())
    }

    private fun access(
        installationId: String = INSTALLATION_A,
        tier: AccessState.Tier,
        subscription: AccessState.SubscriptionStatus.State? = null,
    ) {
        accessState.value = SubmissionFixture.access(installationId, fetchStartedElapsed = null).copy(
            tier = tier,
            subscription = subscription?.let { AccessState.SubscriptionStatus(state = it) },
        )
    }

    private fun TestScope.coordinator() = SubscriptionBillingGplay(
        appScope = backgroundScope,
        playBilling = play,
        submissions = submissions,
        accessRepo = accessRepo,
        sessionManager = sessionManager,
        privacyAcceptance = PrivacyAcceptance(privacyAccepted),
        observeForeground = { foreground = it },
        packageName = "eu.darken.apl",
    )

    private fun TestScope.started() = coordinator().apply {
        setup()
        runCurrent()
    }

    private fun TestScope.toForeground() {
        foreground!!.invoke()
        runCurrent()
    }

    @Test
    fun `nothing is requested before the privacy screen is accepted or without a session`() = runTest {
        playReports(purchase(TOKEN_1))
        privacyAccepted.value = false
        val billing = started()

        toForeground()
        billing.state.first().ownership shouldBe Ownership.Owned(suspended = false)
        requests shouldBe emptyList()

        privacyAccepted.value = true
        sessionState.value = SessionState.NoSession
        toForeground()
        requests shouldBe emptyList()
    }

    @Test
    fun `the installation's first access policy hands over a purchase it has no record of`() = runTest {
        playReports(purchase(TOKEN_1))
        started()
        play.purchaseQueries shouldBe 0

        access(tier = AccessState.Tier.FREE)
        runCurrent()
        submitted shouldBe listOf(hash(TOKEN_1))

        // Only the first one
        accessState.value = accessState.value!!.copy(fetchedAt = Instant.EPOCH.plusSeconds(60))
        runCurrent()
        play.purchaseQueries shouldBe 1
    }

    @Test
    fun `a purchase without a server subscription is requested once per foreground session`() = runTest {
        stored(TOKEN_1)
        playReports(purchase(TOKEN_1))
        access(tier = AccessState.Tier.FREE)
        val billing = started()
        // The process started without coming to the foreground
        play.purchaseQueries shouldBe 1
        submitted shouldBe emptyList()

        toForeground()
        submitted shouldBe listOf(hash(TOKEN_1))
        finishSubmission(TOKEN_1, SubscriptionTokenRecord.Submission.NOT_ATTACHED)

        billing.reconcile("again")
        runCurrent()
        submitted.size shouldBe 1

        toForeground()
        submitted shouldBe listOf(hash(TOKEN_1), hash(TOKEN_1))
    }

    @Test
    fun `a server subscription needs no foreground request`() = runTest {
        stored(TOKEN_1)
        playReports(purchase(TOKEN_1))
        access(tier = AccessState.Tier.SUBSCRIBER)
        started()

        toForeground()
        toForeground()
        requests shouldBe emptyList()
    }

    @Test
    fun `no foreground request without this installation's access policy`() = runTest {
        stored(TOKEN_1)
        playReports(purchase(TOKEN_1))
        started()

        toForeground()
        requests shouldBe emptyList()

        access(installationId = INSTALLATION_B, tier = AccessState.Tier.FREE)
        runCurrent()
        toForeground()
        requests shouldBe emptyList()
    }

    @Test
    fun `restore requests every purchased token whatever the server said before`() = runTest {
        stored(TOKEN_1, submission = SubscriptionTokenRecord.Submission.ATTACHED)
        stored(TOKEN_2, submission = SubscriptionTokenRecord.Submission.NOT_ATTACHED)
        stored(TOKEN_3, submission = SubscriptionTokenRecord.Submission.REJECTED)
        stored(TOKEN_4, playState = PlayState.SUSPENDED, submission = SubscriptionTokenRecord.Submission.NONE)
        playReports(purchase(TOKEN_1), purchase(TOKEN_2), purchase(TOKEN_3), purchase(TOKEN_4, suspended = true))
        access(tier = AccessState.Tier.SUBSCRIBER)
        val billing = started()
        requests shouldBe emptyList()

        billing.restore()

        submitted shouldBe listOf(hash(TOKEN_1), hash(TOKEN_2), hash(TOKEN_3))
    }

    @Test
    fun `restore after the records were deleted requests the purchase again`() = runTest {
        playReports(purchase(TOKEN_1))
        val billing = started()

        billing.restore()

        submitted shouldBe listOf(hash(TOKEN_1))
    }

    @Test
    fun `restore finds nothing when Play reports no purchase`() = runTest {
        val billing = started()

        billing.restore() shouldBe RestoreResult.NothingFound
    }

    @Test
    fun `restore finds a purchased subscription, suspended included`() = runTest {
        playReports(purchase(TOKEN_1))
        val billing = started()
        billing.restore() shouldBe RestoreResult.Found

        playReports(purchase(TOKEN_1, suspended = true))
        billing.restore() shouldBe RestoreResult.Found
    }

    @Test
    fun `restore finds nothing when Play only reports a pending purchase`() = runTest {
        playReports(purchase(TOKEN_1, PlayPurchase.State.PENDING))
        val billing = started()

        billing.restore() shouldBe RestoreResult.NothingFound
    }

    @Test
    fun `restore is unreachable when the purchase query fails and keeps the cached ownership`() = runTest {
        playReports(purchase(TOKEN_1))
        val billing = started()
        toForeground()
        billing.state.first().ownership shouldBe Ownership.Owned(suspended = false)

        play.purchases = PurchaseQuery.Failed(2)
        billing.restore() shouldBe RestoreResult.Unreachable
        billing.state.first().ownership shouldBe Ownership.Owned(suspended = false)
    }

    @Test
    fun `restore is unreachable when Play can't connect and keeps the cached ownership`() = runTest {
        val billing = started()
        toForeground()
        billing.state.first().ownership shouldBe Ownership.None

        play.connectResult = PlayConnection.Unavailable(3)
        billing.restore() shouldBe RestoreResult.Unreachable
        billing.state.first().ownership shouldBe Ownership.None
    }

    @Test
    fun `a pending purchase is recorded and requested once Play reports it purchased`() = runTest {
        val billing = started()
        val pending = purchase(TOKEN_1, PlayPurchase.State.PENDING)
        playReports(pending)

        play.updates.send(PurchaseUpdate.Purchases(listOf(pending)))
        runCurrent()
        requests shouldBe listOf(Request(INSTALLATION_A, hash(TOKEN_1), PlayState.PENDING))
        billing.state.first().ownership shouldBe Ownership.None
        billing.state.first().submission shouldBe Submission.PendingInPlay

        playReports(purchase(TOKEN_1))
        play.updates.send(PurchaseUpdate.Purchases(listOf(purchase(TOKEN_1))))
        runCurrent()
        submitted shouldBe listOf(hash(TOKEN_1))
        billing.state.first().ownership shouldBe Ownership.Owned(suspended = false)
        billing.state.first().submission shouldBe Submission.Updating
    }

    @Test
    fun `a purchase Play reports twice is requested once`() = runTest {
        started()
        playReports(purchase(TOKEN_1))

        play.updates.send(PurchaseUpdate.Purchases(listOf(purchase(TOKEN_1))))
        play.updates.send(PurchaseUpdate.Purchases(listOf(purchase(TOKEN_1))))
        runCurrent()

        submitted shouldBe listOf(hash(TOKEN_1))
    }

    @Test
    fun `already owned from the purchase sheet reconciles`() = runTest {
        val billing = started()
        play.launchOutcome = LaunchOutcome.AlreadyOwned

        billing.subscribe(mockk()) shouldBe SubscribeResult.AlreadyOwned
        runCurrent()
        play.purchaseQueries shouldBe 1

        play.updates.send(PurchaseUpdate.AlreadyOwned)
        runCurrent()
        play.purchaseQueries shouldBe 2
    }

    @Test
    fun `a canceled purchase changes nothing`() = runTest {
        val billing = started()
        play.launchOutcome = LaunchOutcome.Canceled

        billing.subscribe(mockk()) shouldBe SubscribeResult.Canceled
        play.updates.send(PurchaseUpdate.Canceled)
        runCurrent()

        play.purchaseQueries shouldBe 0
        requests shouldBe emptyList()
    }

    @Test
    fun `launch outcomes map to subscribe results`() = runTest {
        val billing = started()

        play.launchOutcome = LaunchOutcome.Launched
        billing.subscribe(mockk()) shouldBe SubscribeResult.Launched
        play.launchOutcome = LaunchOutcome.Unavailable
        billing.subscribe(mockk()) shouldBe SubscribeResult.Unavailable
        play.launchOutcome = LaunchOutcome.Failed(6)
        billing.subscribe(mockk()) shouldBe SubscribeResult.Failed(null)
    }

    @Test
    fun `ownership is unknown until Play answered and keeps its last value when a query fails`() = runTest {
        play.purchases = PurchaseQuery.Failed(2)
        val billing = started()
        billing.state.first().ownership shouldBe Ownership.Unknown

        toForeground()
        billing.state.first().ownership shouldBe Ownership.Unknown

        playReports(purchase(TOKEN_1))
        toForeground()
        billing.state.first().ownership shouldBe Ownership.Owned(suspended = false)

        play.purchases = PurchaseQuery.Failed(2)
        toForeground()
        billing.state.first().ownership shouldBe Ownership.Owned(suspended = false)
    }

    @Test
    fun `an offer Play can't sell leaves ownership alone`() = runTest {
        playReports(purchase(TOKEN_1))
        play.offer = OfferQuery.Found(OFFER)
        val billing = started()
        billing.state.first().offer shouldBe Offer.Loading

        toForeground()
        billing.state.first().offer shouldBe Offer.Available("2,99 €", Period.ofMonths(1), Period.ofDays(7))

        play.offer = OfferQuery.Missing
        toForeground()
        billing.state.first().offer shouldBe Offer.Unavailable
        billing.state.first().ownership shouldBe Ownership.Owned(suspended = false)

        play.offer = OfferQuery.Failed(2)
        toForeground()
        billing.state.first().offer shouldBe Offer.Unavailable
        billing.state.first().ownership shouldBe Ownership.Owned(suspended = false)
    }

    @Test
    fun `a suspended subscription is owned and recorded without a submission`() = runTest {
        playReports(purchase(TOKEN_1, suspended = true))
        val billing = started()

        toForeground()

        billing.state.first().ownership shouldBe Ownership.Owned(suspended = true)
        requests shouldBe listOf(Request(INSTALLATION_A, hash(TOKEN_1), PlayState.SUSPENDED))
    }

    @Test
    fun `a status update from Play's messages requests the purchase again`() = runTest {
        stored(TOKEN_1)
        playReports(purchase(TOKEN_1))
        access(tier = AccessState.Tier.SUBSCRIBER)
        val billing = started()
        billing.showInAppMessages(mockk())
        val queries = play.purchaseQueries

        play.statusUpdated!!.invoke()
        runCurrent()

        play.purchaseQueries shouldBe queries + 1
        submitted shouldBe listOf(hash(TOKEN_1))
    }

    @Test
    fun `an open request from before a restart is resumed without asking again`() = runTest {
        stored(TOKEN_1, submission = SubscriptionTokenRecord.Submission.REQUESTED, open = true)
        playReports(purchase(TOKEN_1))
        access(tier = AccessState.Tier.SUBSCRIBER)

        started()

        requests shouldBe emptyList()
        resumed shouldBe listOf(hash(TOKEN_1))
    }

    @Test
    fun `a submission waiting for its policy at startup refreshes access once`() = runTest {
        awaitingFor.value = INSTALLATION_A
        started()
        coVerify(exactly = 1) { accessRepo.refresh("subscription-recovery") }

        awaitingFor.value = null
        runCurrent()
        awaitingFor.value = INSTALLATION_A
        runCurrent()
        coVerify(exactly = 1) { accessRepo.refresh("subscription-recovery") }
    }

    @Test
    fun `reconciles requested while one runs lead to exactly one more`() = runTest {
        val billing = started()
        val gate = CompletableDeferred<Unit>()
        play.gate = gate

        billing.reconcile("first")
        runCurrent()
        billing.reconcile("second")
        billing.reconcile("third")
        billing.reconcile("fourth")
        runCurrent()
        play.purchaseQueries shouldBe 1

        gate.complete(Unit)
        runCurrent()
        play.purchaseQueries shouldBe 2
    }

    @Test
    fun `submission only considers the tokens Play still reports`() = runTest {
        stored(TOKEN_1, submission = SubscriptionTokenRecord.Submission.REJECTED)
        val billing = started()
        billing.state.first().submission shouldBe Submission.Rejected

        playReports(purchase(TOKEN_2))
        stored(TOKEN_2, submission = SubscriptionTokenRecord.Submission.ATTACHED)
        toForeground()

        billing.state.first().submission shouldBe Submission.Idle
    }

    @Test
    fun `an ended subscription asks Play again`() = runTest {
        stored(TOKEN_1)
        playReports(purchase(TOKEN_1))
        access(tier = AccessState.Tier.SUBSCRIBER, subscription = AccessState.SubscriptionStatus.State.ACTIVE)
        val billing = started()
        play.purchaseQueries shouldBe 1
        billing.state.first().ownership shouldBe Ownership.Owned(suspended = false)

        playReports()
        access(tier = AccessState.Tier.FREE)
        runCurrent()

        play.purchaseQueries shouldBe 2
        billing.state.first().ownership shouldBe Ownership.None
    }

    @Test
    fun `an installation's first access policy asks Play only for the access-ready reconcile`() = runTest {
        started()
        play.purchaseQueries shouldBe 0

        access(tier = AccessState.Tier.SUBSCRIBER, subscription = AccessState.SubscriptionStatus.State.ACTIVE)
        runCurrent()
        play.purchaseQueries shouldBe 1

        sessionState.value = SessionState.Active(INSTALLATION_B)
        access(installationId = INSTALLATION_B, tier = AccessState.Tier.FREE)
        runCurrent()
        play.purchaseQueries shouldBe 2
    }

    @Test
    fun `a policy with the same tier and subscription state does not ask Play`() = runTest {
        access(tier = AccessState.Tier.SUBSCRIBER, subscription = AccessState.SubscriptionStatus.State.ACTIVE)
        started()
        play.purchaseQueries shouldBe 1

        accessState.value = accessState.value!!.let {
            it.copy(
                fetchedAt = Instant.EPOCH.plusSeconds(60),
                usage = it.usage.copy(resetsAt = 86_400_000),
            )
        }
        runCurrent()

        play.purchaseQueries shouldBe 1
    }

    @Test
    fun `asking Play after an access change submits and resumes nothing`() = runTest {
        stored(TOKEN_1)
        stored(TOKEN_2, submission = SubscriptionTokenRecord.Submission.REQUESTED, open = true)
        playReports(purchase(TOKEN_1), purchase(TOKEN_2))
        access(tier = AccessState.Tier.SUBSCRIBER, subscription = AccessState.SubscriptionStatus.State.ACTIVE)
        val billing = started()
        toForeground()
        requests shouldBe emptyList()
        resumed.clear()
        val queries = play.purchaseQueries

        access(tier = AccessState.Tier.FREE)
        runCurrent()

        play.purchaseQueries shouldBe queries + 1
        billing.state.first().ownership shouldBe Ownership.Owned(suspended = false)
        requests shouldBe emptyList()
        resumed shouldBe emptyList()
    }

    @Test
    fun `an access change keeps the ownership when Play can't connect`() = runTest {
        playReports(purchase(TOKEN_1))
        access(tier = AccessState.Tier.SUBSCRIBER, subscription = AccessState.SubscriptionStatus.State.ACTIVE)
        val billing = started()
        billing.state.first().ownership shouldBe Ownership.Owned(suspended = false)
        val connects = play.connects
        val queries = play.purchaseQueries
        requests.clear()

        play.connectResult = PlayConnection.Unavailable(3)
        playReports()
        access(tier = AccessState.Tier.FREE)
        runCurrent()

        play.connects shouldBe connects + 1
        play.purchaseQueries shouldBe queries
        billing.state.first().ownership shouldBe Ownership.Owned(suspended = false)
        requests shouldBe emptyList()
    }

    @Test
    fun `an access change keeps the ownership when the purchase query fails`() = runTest {
        playReports(purchase(TOKEN_1))
        access(tier = AccessState.Tier.SUBSCRIBER, subscription = AccessState.SubscriptionStatus.State.ACTIVE)
        val billing = started()
        billing.state.first().ownership shouldBe Ownership.Owned(suspended = false)
        val queries = play.purchaseQueries
        requests.clear()

        play.purchases = PurchaseQuery.Failed(2)
        access(tier = AccessState.Tier.FREE)
        runCurrent()

        play.purchaseQueries shouldBe queries + 1
        billing.state.first().ownership shouldBe Ownership.Owned(suspended = false)
        requests shouldBe emptyList()
    }

    @Test
    fun `the coordinator does not depend on the upgrade state`() {
        val types = SubscriptionBillingGplay::class.java.declaredConstructors.flatMap { it.parameterTypes.toList() } +
            SubscriptionBillingGplay::class.java.declaredFields.map { it.type }
        types.none { UpgradeRepo::class.java.isAssignableFrom(it) || it == UpgradeRepoGplay::class.java } shouldBe true
    }

    @Test
    fun `manage link opens Play's page for the subscription`() {
        SubscriptionBillingGplay.manageUrl("eu.darken.apl") shouldBe
            "https://play.google.com/store/account/subscriptions?sku=upgrade.pro&package=eu.darken.apl"
    }

    @Test
    fun `ownership comes from purchased tokens only`() {
        SubscriptionBillingGplay.ownershipOf(emptyList()) shouldBe Ownership.None
        SubscriptionBillingGplay.ownershipOf(listOf(purchase(TOKEN_1, PlayPurchase.State.PENDING))) shouldBe
            Ownership.None
        SubscriptionBillingGplay.ownershipOf(
            listOf(purchase(TOKEN_1, suspended = true), purchase(TOKEN_2))
        ) shouldBe Ownership.Owned(suspended = false)
        SubscriptionBillingGplay.ownershipOf(listOf(purchase(TOKEN_1, suspended = true))) shouldBe
            Ownership.Owned(suspended = true)
    }

    @Test
    fun `submission state priority`() {
        fun record(
            token: String,
            playState: PlayState = PlayState.PURCHASED,
            submission: SubscriptionTokenRecord.Submission = SubscriptionTokenRecord.Submission.ATTACHED,
            open: Boolean = false,
        ) = SubscriptionTokenRecord(
            installationId = INSTALLATION_A,
            tokenHash = hash(token),
            token = token,
            productId = PlaySubscriptionProduct.PRODUCT_ID,
            playState = playState,
            submission = submission,
            requestedGen = 1,
            completedGen = if (open) 0 else 1,
        )

        val open = record(TOKEN_1, submission = SubscriptionTokenRecord.Submission.REQUESTED, open = true)
        val pending = record(TOKEN_2, playState = PlayState.PENDING, submission = SubscriptionTokenRecord.Submission.NONE)
        val rejected = record(TOKEN_3, submission = SubscriptionTokenRecord.Submission.REJECTED)
        val notAttached = record(TOKEN_4, submission = SubscriptionTokenRecord.Submission.NOT_ATTACHED)
        val attached = record(TOKEN_5)
        val all = listOf(open, pending, rejected, notAttached, attached)

        fun submissionOf(updating: Boolean, records: List<SubscriptionTokenRecord>, reported: Set<String>? = null) =
            SubscriptionBillingGplay.submissionOf(updating, records, reported)

        submissionOf(updating = true, records = emptyList()) shouldBe Submission.Updating
        submissionOf(updating = false, records = all) shouldBe Submission.Updating
        submissionOf(updating = false, records = all - open) shouldBe Submission.PendingInPlay
        submissionOf(updating = false, records = listOf(rejected, notAttached, attached)) shouldBe Submission.Rejected
        submissionOf(updating = false, records = listOf(notAttached, attached)) shouldBe Submission.NotAttached
        submissionOf(updating = false, records = listOf(attached)) shouldBe Submission.Idle
        submissionOf(updating = false, records = emptyList()) shouldBe Submission.Idle
        // Before Play answered every record counts, afterwards only the ones it reported
        submissionOf(updating = false, records = all, reported = setOf(hash(TOKEN_4))) shouldBe Submission.NotAttached
        submissionOf(updating = false, records = all, reported = emptySet()) shouldBe Submission.Idle
    }

    companion object {
        private const val INSTALLATION_A = SubmissionFixture.INSTALLATION_A
        private const val INSTALLATION_B = SubmissionFixture.INSTALLATION_B
        private const val TOKEN_1 = "token-1"
        private const val TOKEN_2 = "token-2"
        private const val TOKEN_3 = "token-3"
        private const val TOKEN_4 = "token-4"
        private const val TOKEN_5 = "token-5"

        private val OFFER = PlayOffer(
            basePlanId = PlaySubscriptionProduct.BASE_PLAN_ID,
            offerId = PlaySubscriptionProduct.TRIAL_OFFER_ID,
            offerToken = "offer-token",
            formattedPrice = "2,99 €",
            priceMicros = 2_990_000,
            billingPeriod = Period.ofMonths(1),
            trialPeriod = Period.ofDays(7),
        )

        private fun purchase(
            token: String,
            state: PlayPurchase.State = PlayPurchase.State.PURCHASED,
            suspended: Boolean = false,
        ) = SubmissionFixture.purchase(token, state, suspended)

        private fun hash(token: String) = purchase(token).tokenHash
    }
}
