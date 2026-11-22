package eu.darken.apl.upgrade.subscription

import android.app.Activity
import eu.darken.apl.server.api.SubscriptionRequest
import eu.darken.apl.server.session.SessionManager
import eu.darken.apl.server.session.SessionState
import eu.darken.apl.upgrade.subscription.play.LaunchOutcome
import eu.darken.apl.upgrade.subscription.play.OfferQuery
import eu.darken.apl.upgrade.subscription.play.PlayBilling
import eu.darken.apl.upgrade.subscription.play.PlayConnection
import eu.darken.apl.upgrade.subscription.play.PurchaseQuery
import eu.darken.apl.upgrade.subscription.play.PurchaseUpdate
import eu.darken.apl.upgrade.subscription.submit.SubmissionFixture
import eu.darken.apl.upgrade.subscription.submit.SubmissionFixture.Companion.INSTALLATION_A
import eu.darken.apl.upgrade.subscription.submit.SubscriptionSubmitter.Outcome
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class F8RedTest : BaseTest() {

    private class FakePlayBilling : PlayBilling {
        override val connection = MutableStateFlow<PlayConnection>(PlayConnection.Ready)
        override val purchaseUpdates: Flow<PurchaseUpdate> = emptyFlow()
        var purchases: PurchaseQuery = PurchaseQuery.Success(emptyList())
        var statusUpdated: (() -> Unit)? = null

        override suspend fun connect(): PlayConnection = PlayConnection.Ready
        override suspend fun queryPurchases(): PurchaseQuery = purchases
        override suspend fun queryOffer(): OfferQuery = OfferQuery.Missing
        override suspend fun launch(activity: Activity): LaunchOutcome = LaunchOutcome.Launched
        override suspend fun showInAppMessages(activity: Activity, onSubscriptionStatusUpdated: () -> Unit) {
            statusUpdated = onSubscriptionStatusUpdated
        }
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

    private fun TestScope.coordinator() =
        SubscriptionBillingGplay(
            appScope = backgroundScope,
            playBilling = play,
            submissions = fixture.submissions,
            accessRepo = fixture.accessRepo,
            sessionManager = sessionManager,
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

    private suspend fun TestScope.runScenario(trigger: suspend TestScope.(SubscriptionBillingGplay) -> Unit) {
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

        // The foreground reconcile hands the purchase over
        foreground!!.invoke()
        settle(billing)
        fixture.enqueued.size shouldBe 1
        fixture.record(HASH)!!.requestedGen shouldBe 1

        // Its job is running, the POST has not been answered yet
        val job = async { fixture.submitter.submit(INSTALLATION_A, HASH) }
        entered.await()

        // The explicit trigger arrives while the POST is in flight
        trigger(billing)
        val duringPost = fixture.record(HASH)!!

        release.complete(Unit)
        job.await() shouldBe Outcome.DONE
        val afterJob = fixture.record(HASH)!!

        // WorkManager runs whatever else was queued for the token
        repeat(fixture.enqueued.size - 1) { fixture.submitter.submit(INSTALLATION_A, HASH) }

        withClue("record during POST: $duringPost, after the in-flight job: $afterJob, queued work: ${fixture.enqueued.size}") {
            assertSoftly {
                withClue("open request left after the in-flight job") { afterJob.hasOpenRequest shouldBe true }
                withClue("POSTs for the token") { posted shouldContainExactly listOf(TOKEN, TOKEN) }
            }
        }
    }

    @Test
    fun `F8 a Restore during an in-flight submission submits again afterwards`() = runTest {
        runScenario { billing ->
            val restored = async { billing.restore() }
            settle(billing)
            restored.await()
        }
    }

    @Test
    fun `F8 a status update during an in-flight submission submits again afterwards`() = runTest {
        runScenario { billing ->
            billing.showInAppMessages(mockk())
            val callback = play.statusUpdated!!
            callback.invoke()
            settle(billing)
        }
    }

    companion object {
        private const val TOKEN = "PURCHASE-TOKEN-SENTINEL"
        private val HASH = SubmissionFixture.purchase(TOKEN).tokenHash
    }
}
