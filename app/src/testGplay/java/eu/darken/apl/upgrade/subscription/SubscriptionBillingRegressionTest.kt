package eu.darken.apl.upgrade.subscription

import android.app.Activity
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.server.session.SessionManager
import eu.darken.apl.server.session.SessionState
import eu.darken.apl.upgrade.subscription.SubscriptionState.Submission
import eu.darken.apl.upgrade.subscription.play.LaunchOutcome
import eu.darken.apl.upgrade.subscription.play.OfferQuery
import eu.darken.apl.upgrade.subscription.play.PlayBilling
import eu.darken.apl.upgrade.subscription.play.PlayConnection
import eu.darken.apl.upgrade.subscription.play.PurchaseQuery
import eu.darken.apl.upgrade.subscription.play.PurchaseUpdate
import eu.darken.apl.upgrade.subscription.submit.SubmissionFixture
import eu.darken.apl.upgrade.subscription.submit.SubmissionFixture.Companion.INSTALLATION_A
import eu.darken.apl.upgrade.subscription.submit.SubmissionFixture.Companion.INSTALLATION_B
import eu.darken.apl.upgrade.subscription.submit.SubscriptionSubmissions
import eu.darken.apl.upgrade.subscription.submit.SubscriptionSubmitter.Outcome
import eu.darken.apl.upgrade.subscription.submit.SubscriptionTokenRecord.PlayState
import eu.darken.apl.server.api.SubscriptionRequest
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class SubscriptionBillingRegressionTest : BaseTest() {

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

    private val fixture = SubmissionFixture()
    private val play = FakePlayBilling()
    private val sessionState = MutableStateFlow<SessionState>(SessionState.Active(INSTALLATION_A))
    private val sessionManager = mockk<SessionManager>().apply {
        every { state } returns sessionState
    }
    private var foreground: (() -> Unit)? = null

    /** Purchase tokens in the order they reached the endpoint. */
    private val posted = mutableListOf<String>()

    @AfterEach
    fun teardown() = fixture.close()

    private fun TestScope.coordinator(submissions: SubscriptionSubmissions = fixture.submissions) =
        SubscriptionBillingGplay(
            appScope = backgroundScope,
            playBilling = play,
            submissions = submissions,
            accessRepo = fixture.accessRepo,
            sessionManager = sessionManager,
            privacyAcceptance = fixture.privacyAcceptance,
            observeForeground = { foreground = it },
            packageName = "eu.darken.apl",
        ).apply {
            setup()
            runCurrent()
        }

    /**
     * Returns once every reconcile queued so far has finished: the drain holds the operation lock for
     * the whole reconcile, the purchase sheet queues behind it on the same lock.
     */
    private suspend fun TestScope.settle(billing: SubscriptionBillingGplay) {
        runCurrent()
        billing.subscribe(mockk()) shouldBe SubscribeResult.Launched
    }

    @Test
    fun `F1 an ordinary reconcile during an in-flight submission causes no second submission`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        coEvery { fixture.endpoint.submitSubscription(any(), any()) } coAnswers {
            synchronized(posted) { posted += secondArg<SubscriptionRequest>().purchaseToken }
            if (posted.size == 1) {
                entered.complete(Unit)
                release.await()
            }
            SubmissionFixture.response()
        }
        play.purchases = PurchaseQuery.Success(listOf(SubmissionFixture.purchase(TOKEN)))
        val billing = coordinator()

        // The foreground reconcile hands the new purchase over
        foreground!!.invoke()
        settle(billing)
        fixture.enqueued.size shouldBe 1
        fixture.record(HASH)!!.requestedGen shouldBe 1

        // Its job is running, the POST has not been answered yet
        val job = async { fixture.submitter.submit(INSTALLATION_A, HASH) }
        entered.await()

        // Ordinary reconcile, e.g. the upgrade screen opening
        billing.reconcile("upgrade-screen")
        settle(billing)

        release.complete(Unit)
        job.await() shouldBe Outcome.DONE
        val afterJob = fixture.record(HASH)!!

        // WorkManager runs whatever else was queued for the token
        repeat(fixture.enqueued.size - 1) { fixture.submitter.submit(INSTALLATION_A, HASH) }

        withClue("record after the in-flight job: $afterJob, queued work: ${fixture.enqueued.size}") {
            assertSoftly {
                withClue("open request left by the in-flight job") { afterJob.hasOpenRequest shouldBe false }
                withClue("POSTs for the token") { posted shouldContainExactly listOf(TOKEN) }
            }
        }
    }

    @Test
    fun `F2 a suspended token with an unanswered request is not updating`() = runTest {
        fixture.submissions.request(INSTALLATION_A, SubmissionFixture.purchase(TOKEN), "purchase-update")
        // Suspended before its job ran: the job skips it, nothing ever completes the request
        fixture.submissions.request(INSTALLATION_A, SubmissionFixture.purchase(TOKEN, suspended = true), "changed")
        fixture.submitter.submit(INSTALLATION_A, HASH) shouldBe Outcome.DONE

        val record = fixture.record(HASH)!!
        record.playState shouldBe PlayState.SUSPENDED

        withClue("record: $record") {
            SubscriptionBillingGplay.submissionOf(
                updating = false,
                records = listOf(record),
                reported = setOf(HASH),
            ) shouldNotBe Submission.Updating
        }
    }

    @Test
    fun `F3 a policy wait for a previous installation does not leave the new one updating`() = runTest {
        fixture.store.update { it.copy(awaitingPolicyFor = INSTALLATION_A) }
        sessionState.value = SessionState.Active(INSTALLATION_B)
        fixture.accessState.value = SubmissionFixture.access(INSTALLATION_B, fetchStartedElapsed = fixture.elapsed + 1)
            .copy(tier = AccessState.Tier.FREE)

        val firstAwaiting = CompletableDeferred<String?>()
        val submissions = mockk<SubscriptionSubmissions>().apply {
            every { records(any()) } answers { fixture.submissions.records(firstArg()) }
            every { awaitingFor } returns fixture.submissions.awaitingFor.onEach { firstAwaiting.complete(it) }
            coEvery { request(any(), any(), any()) } coAnswers {
                fixture.submissions.request(firstArg(), secondArg(), thirdArg())
            }
        }
        val billing = coordinator(submissions)

        // The coordinator has received the policy wait's first value
        val awaiting = firstAwaiting.await()
        settle(billing)

        withClue("policy wait emitted awaitingFor=$awaiting with B active, stored ${fixture.store.current()}") {
            billing.state.first().submission shouldNotBe Submission.Updating
        }
    }

    companion object {
        private const val TOKEN = "PURCHASE-TOKEN-SENTINEL"
        private val HASH = SubmissionFixture.purchase(TOKEN).tokenHash
    }
}
