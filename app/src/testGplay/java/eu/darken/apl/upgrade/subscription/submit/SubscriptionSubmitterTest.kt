package eu.darken.apl.upgrade.subscription.submit

import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.ServerCodes
import eu.darken.apl.server.api.SubscriptionRequest
import eu.darken.apl.server.api.SubscriptionResponse
import eu.darken.apl.server.deletion.DeletionState
import eu.darken.apl.server.session.SessionRevokedException
import eu.darken.apl.upgrade.subscription.play.PlayPurchase
import eu.darken.apl.upgrade.subscription.submit.SubmissionFixture.Companion.INSTALLATION_A
import eu.darken.apl.upgrade.subscription.submit.SubmissionFixture.Companion.INSTALLATION_B
import eu.darken.apl.upgrade.subscription.submit.SubmissionFixture.Companion.SESSION_TOKEN
import eu.darken.apl.upgrade.subscription.submit.SubmissionFixture.Companion.access
import eu.darken.apl.upgrade.subscription.submit.SubmissionFixture.Companion.purchase
import eu.darken.apl.upgrade.subscription.submit.SubmissionFixture.Companion.response
import eu.darken.apl.upgrade.subscription.submit.SubscriptionSubmitter.Outcome
import eu.darken.apl.upgrade.subscription.submit.SubscriptionTokenRecord.PlayState
import eu.darken.apl.upgrade.subscription.submit.SubscriptionTokenRecord.Submission
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import testhelper.BaseTest
import java.io.IOException
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

class SubscriptionSubmitterTest : BaseTest() {

    private val fixture = SubmissionFixture()
    private val purchase = purchase(TOKEN)
    private val hash = purchase.tokenHash

    /** Purchase tokens in the order they reached the endpoint. */
    private val submitted = mutableListOf<String>()

    init {
        answerWith { response() }
    }

    @AfterEach
    fun teardown() = fixture.close()

    private fun answerWith(answer: suspend (SubscriptionRequest) -> SubscriptionResponse) {
        coEvery { fixture.endpoint.submitSubscription(any(), any()) } coAnswers {
            val request = secondArg<SubscriptionRequest>()
            synchronized(submitted) { submitted += request.purchaseToken }
            answer(request)
        }
    }

    private suspend fun request(p: PlayPurchase = purchase, installationId: String = INSTALLATION_A) =
        fixture.submissions.request(installationId, p, "test")

    private suspend fun submit(tokenHash: String = hash, installationId: String = INSTALLATION_A) =
        fixture.submitter.submit(installationId, tokenHash)

    private suspend fun record(tokenHash: String = hash) = fixture.record(tokenHash)!!

    @Test
    fun `a request is stored and queues work that carries no token`() = runTest {
        request()

        val queued = fixture.enqueued.single()
        queued.name shouldBe "subscription.submit.$hash"
        queued.policy shouldBe ExistingWorkPolicy.APPEND_OR_REPLACE
        queued.delay shouldBe Duration.ZERO
        queued.request.workSpec.constraints.requiredNetworkType shouldBe NetworkType.CONNECTED
        queued.request.workSpec.input.keyValueMap shouldBe mapOf(
            SubscriptionSubmitWorker.KEY_INSTALLATION_ID to INSTALLATION_A,
            SubscriptionSubmitWorker.KEY_TOKEN_HASH to hash,
        )
        queued.request.workSpec.workerClassName shouldBe SubscriptionSubmitWorker::class.java.name
        record().submission shouldBe Submission.REQUESTED
        record().playState shouldBe PlayState.PURCHASED
        record().token shouldBe TOKEN
    }

    @Test
    fun `two requests before a run share one submission`() = runTest {
        request()
        request()
        fixture.enqueued.size shouldBe 2

        submit() shouldBe Outcome.DONE
        submit() shouldBe Outcome.DONE

        submitted shouldContainExactly listOf(TOKEN)
        record().requestedGen shouldBe 2
        record().completedGen shouldBe 2
    }

    @Test
    fun `a request during a run gets exactly one more submission`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        answerWith {
            if (submitted.size == 1) {
                entered.complete(Unit)
                release.await()
            }
            response()
        }
        request()

        val run = async { submit() }
        entered.await()
        request()
        release.complete(Unit)
        run.await() shouldBe Outcome.DONE

        record().hasOpenRequest shouldBe true
        submit()
        submit()

        submitted shouldContainExactly listOf(TOKEN, TOKEN)
        record().hasOpenRequest shouldBe false
    }

    @Test
    fun `pending and suspended purchases are recorded but never submitted`() = runTest {
        val pending = purchase("PENDING-TOKEN", state = PlayPurchase.State.PENDING)
        val suspended = purchase("SUSPENDED-TOKEN", suspended = true)

        request(pending)
        request(suspended)
        submit(pending.tokenHash)
        submit(suspended.tokenHash)

        fixture.enqueued.shouldBeEmpty()
        submitted.shouldBeEmpty()
        record(pending.tokenHash).playState shouldBe PlayState.PENDING
        record(pending.tokenHash).submission shouldBe Submission.NONE
        record(suspended.tokenHash).playState shouldBe PlayState.SUSPENDED
        record(suspended.tokenHash).submission shouldBe Submission.NONE
    }

    @Test
    fun `a pending purchase is submitted once it is purchased`() = runTest {
        request(purchase(TOKEN, state = PlayPurchase.State.PENDING))
        request()

        submit() shouldBe Outcome.DONE

        submitted shouldContainExactly listOf(TOKEN)
        record().submission shouldBe Submission.ATTACHED
    }

    @Test
    fun `an attached answer is stored and the access policy refreshed`() = runTest {
        request()

        submit() shouldBe Outcome.DONE

        record().submission shouldBe Submission.ATTACHED
        record().serverState shouldBe "active"
        record().retryNotBefore shouldBe null
        fixture.store.current().awaitingPolicyFor shouldBe INSTALLATION_A
        coVerify(exactly = 1) { fixture.accessRepo.refresh("subscription-submitted") }
    }

    @Test
    fun `a not attached answer is stored and not retried`() = runTest {
        answerWith { response(state = "on_hold", attached = false) }
        request()
        val queuedBefore = fixture.enqueued.size

        submit() shouldBe Outcome.DONE
        submit() shouldBe Outcome.DONE

        submitted shouldContainExactly listOf(TOKEN)
        fixture.enqueued.size shouldBe queuedBefore
        record().submission shouldBe Submission.NOT_ATTACHED
        record().serverState shouldBe "on_hold"
        record().hasOpenRequest shouldBe false
        fixture.store.current().awaitingPolicyFor shouldBe INSTALLATION_A
    }

    @Test
    fun `a state this build does not know is stored as is`() = runTest {
        answerWith { SubscriptionResponse(state = "brand_new_state", granted = false, expiresAt = null, attached = true) }
        request()

        submit() shouldBe Outcome.DONE

        record().serverState shouldBe "brand_new_state"
        record().submission shouldBe Submission.ATTACHED
    }

    @Test
    fun `a failed access refresh does not fail the submission`() = runTest {
        coEvery { fixture.accessRepo.refresh(any()) } throws IllegalStateException("refresh broke")
        request()

        submit() shouldBe Outcome.DONE

        record().submission shouldBe Submission.ATTACHED
    }

    @ParameterizedTest
    @CsvSource(
        "installation_rate_exceeded, 429",
        "quota_exceeded, 429",
        "billing_unavailable, 503",
        "database_unavailable, 503",
        "internal, 500",
    )
    fun `a server asking to wait gets a follow-up after its Retry-After`(code: String, status: Int) = runTest {
        answerWith { throw ServerApiException(code = code, status = status, retryAfterSeconds = 120) }
        request()
        val retryAt = fixture.nowMillis + 120_000

        submit() shouldBe Outcome.DONE

        record().retryNotBefore shouldBe retryAt
        record().submission shouldBe Submission.REQUESTED
        record().hasOpenRequest shouldBe true
        val followUp = fixture.enqueued.last()
        followUp.name shouldBe "subscription.submit.$hash"
        followUp.policy shouldBe ExistingWorkPolicy.APPEND_OR_REPLACE
        followUp.delay shouldBe Duration.ofSeconds(120)
        followUp.tokenHash shouldBe hash
        followUp.installationId shouldBe INSTALLATION_A
    }

    @Test
    fun `requests and runs during a wait do not shorten it`() = runTest {
        answerWith { throw ServerApiException(code = ServerCodes.QUOTA_EXCEEDED, status = 429, retryAfterSeconds = 120) }
        request()
        submit()
        val retryAt = record().retryNotBefore!!

        fixture.elapsed += 30_000
        request()
        fixture.enqueued.last().delay shouldBe Duration.ofSeconds(90)

        submit() shouldBe Outcome.DONE
        fixture.enqueued.last().delay shouldBe Duration.ofSeconds(90)
        submitted.size shouldBe 1
        record().retryNotBefore shouldBe retryAt

        answerWith { response() }
        fixture.elapsed += 90_000
        submit() shouldBe Outcome.DONE
        submitted.size shouldBe 2
        record().submission shouldBe Submission.ATTACHED
        record().retryNotBefore shouldBe null
        record().hasOpenRequest shouldBe false
    }

    @Test
    fun `an invalid purchase is rejected`() = runTest {
        answerWith { throw ServerApiException(code = ServerCodes.PURCHASE_INVALID, status = 422) }
        request()
        val queuedBefore = fixture.enqueued.size

        submit() shouldBe Outcome.DONE

        record().submission shouldBe Submission.REJECTED
        record().hasOpenRequest shouldBe false
        fixture.enqueued.size shouldBe queuedBefore
        coVerify(exactly = 0) { fixture.accessRepo.refresh(any()) }
    }

    @ParameterizedTest
    @CsvSource(
        "invalid_request, 400",
        "http_404, 404",
        "some_future_code, 409",
    )
    fun `a refused request is dropped`(code: String, status: Int) = runTest {
        answerWith { throw ServerApiException(code = code, status = status) }
        request()
        val queuedBefore = fixture.enqueued.size

        submit() shouldBe Outcome.DONE

        record().submission shouldBe Submission.NONE
        record().hasOpenRequest shouldBe false
        fixture.enqueued.size shouldBe queuedBefore
    }

    @Test
    fun `an outdated app leaves the request open without a follow-up`() = runTest {
        answerWith { throw ServerApiException(code = ServerCodes.APP_UPDATE_REQUIRED, status = 403) }
        request()
        val queuedBefore = fixture.enqueued.size

        submit() shouldBe Outcome.DONE

        record().submission shouldBe Submission.REQUESTED
        record().hasOpenRequest shouldBe true
        fixture.enqueued.size shouldBe queuedBefore
    }

    @Test
    fun `a revoked installation stops without a retry`() = runTest {
        coEvery { fixture.sessionManager.authedAs<SubscriptionResponse>(any()) } throws SessionRevokedException()
        request()

        submit() shouldBe Outcome.FAILED

        record().submission shouldBe Submission.REQUESTED
        fixture.enqueued.size shouldBe 1
    }

    @Test
    fun `a revocation answered by the endpoint stops without a retry`() = runTest {
        answerWith { throw ServerApiException(code = ServerCodes.INSTALLATION_REVOKED, status = 403) }
        request()

        submit() shouldBe Outcome.FAILED

        record().submission shouldBe Submission.REQUESTED
        fixture.enqueued.size shouldBe 1
    }

    @Test
    fun `a network failure is retried`() = runTest {
        answerWith { throw IOException("offline") }
        request()

        submit() shouldBe Outcome.RETRY

        record().submission shouldBe Submission.REQUESTED
        record().hasOpenRequest shouldBe true
    }

    @Test
    fun `an unexpected failure ends the run`() = runTest {
        answerWith { throw IllegalStateException("unexpected") }
        request()

        submit() shouldBe Outcome.FAILED

        record().hasOpenRequest shouldBe true
    }

    @Test
    fun `a session replaced mid call never sends the token under the new installation`() = runTest {
        val sessionTokens = mutableListOf<String>()
        coEvery { fixture.endpoint.submitSubscription(any(), any()) } coAnswers {
            sessionTokens += firstArg<String>()
            throw ServerApiException(code = ServerCodes.INVALID_TOKEN, status = 401)
        }
        // Like SessionManager: an invalid token is refreshed once and the block runs again, here the
        // refresh had to enroll a new installation
        coEvery { fixture.sessionManager.authedAs<SubscriptionResponse>(any()) } coAnswers {
            val block = firstArg<suspend (String, String) -> SubscriptionResponse>()
            try {
                block(INSTALLATION_A, SESSION_TOKEN)
            } catch (e: ServerApiException) {
                if (e.code != ServerCodes.INVALID_TOKEN) throw e
                block(INSTALLATION_B, "session-token-of-b")
            }
        }
        request()

        submit() shouldBe Outcome.DONE

        sessionTokens shouldContainExactly listOf(SESSION_TOKEN)
        record().submission shouldBe Submission.REQUESTED
        record().hasOpenRequest shouldBe true
        fixture.store.current().recordsOf(INSTALLATION_B).shouldBeEmpty()
    }

    @Test
    fun `a session of another installation gets no request`() = runTest {
        fixture.sessionInstallationId = INSTALLATION_B
        request()

        submit() shouldBe Outcome.DONE

        submitted.shouldBeEmpty()
        record().hasOpenRequest shouldBe true
    }

    @Test
    fun `without the privacy screen accepted nothing is sent`() = runTest {
        fixture.privacyAccepted.value = false
        request()

        submit() shouldBe Outcome.DONE

        submitted.shouldBeEmpty()
        record().submission shouldBe Submission.REQUESTED
    }

    @Test
    fun `nothing is sent while a confirmed deletion waits for its wipe`() = runTest {
        fixture.deletionState.value = DeletionState.WIPE_PENDING
        request()

        submit() shouldBe Outcome.DONE

        submitted.shouldBeEmpty()
        record().submission shouldBe Submission.REQUESTED
    }

    @Test
    fun `nothing is sent while a deletion runs`() = runTest {
        request()
        val deletionRunning = CompletableDeferred<Unit>()
        val finishDeletion = CompletableDeferred<Unit>()
        val deletion = launch {
            fixture.requestGate.deletionOperation {
                deletionRunning.complete(Unit)
                finishDeletion.await()
            }
        }
        deletionRunning.await()

        submit() shouldBe Outcome.DONE

        submitted.shouldBeEmpty()
        record().submission shouldBe Submission.REQUESTED
        finishDeletion.complete(Unit)
        deletion.join()
    }

    @Test
    fun `two tokens are both submitted, one at a time`() = runTest {
        val second = purchase("SECOND-TOKEN")
        val inFlight = AtomicInteger(0)
        var mostInFlight = 0
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        answerWith {
            mostInFlight = maxOf(mostInFlight, inFlight.incrementAndGet())
            if (submitted.size == 1) {
                firstEntered.complete(Unit)
                releaseFirst.await()
            }
            inFlight.decrementAndGet()
            response()
        }
        request()
        request(second)

        val first = async { submit() }
        firstEntered.await()
        val other = async { submit(second.tokenHash) }
        // Real time: the second run must get the chance to overtake if nothing holds it back
        withContext(Dispatchers.Default) { delay(200) }
        submitted.size shouldBe 1
        releaseFirst.complete(Unit)

        first.await() shouldBe Outcome.DONE
        other.await() shouldBe Outcome.DONE
        submitted shouldContainExactly listOf(TOKEN, "SECOND-TOKEN")
        mostInFlight shouldBe 1
        record().submission shouldBe Submission.ATTACHED
        record(second.tokenHash).submission shouldBe Submission.ATTACHED
    }

    @Test
    fun `a request whose work never ran is still open after a restart`() = runTest {
        request()

        fixture.restart()

        record().submission shouldBe Submission.REQUESTED
        record().hasOpenRequest shouldBe true
    }

    @Test
    fun `queued work submits after a restart from its input alone`() = runTest {
        request()
        val queued = fixture.enqueued.single()

        fixture.restart()
        fixture.submitter.submit(queued.installationId!!, queued.tokenHash!!) shouldBe Outcome.DONE

        submitted shouldContainExactly listOf(TOKEN)
        record().submission shouldBe Submission.ATTACHED
    }

    @Test
    fun `an answer lost to process death is submitted again on the next request`() = runTest {
        answerWith { throw CancellationException("process died after the server answered") }
        request()
        runCatching { submit() }

        fixture.restart()
        answerWith { response() }
        record().hasOpenRequest shouldBe true
        request()
        submit() shouldBe Outcome.DONE

        submitted shouldContainExactly listOf(TOKEN, TOKEN)
        record().submission shouldBe Submission.ATTACHED
    }

    @Test
    fun `after a restart an answered submission waits for a policy fetched in the new process`() = runTest {
        request()
        submit()
        fixture.restart()

        fixture.submissions.awaitingFor.first() shouldBe INSTALLATION_A

        // Restored from disk, it may predate the submission
        fixture.accessState.value = access(INSTALLATION_A, fetchStartedElapsed = null)
        fixture.submissions.awaitingFor.first() shouldBe INSTALLATION_A

        fixture.accessState.value = access(INSTALLATION_B, fetchStartedElapsed = 5)
        fixture.submissions.awaitingFor.first() shouldBe INSTALLATION_A

        fixture.accessState.value = access(INSTALLATION_A, fetchStartedElapsed = 5)
        fixture.submissions.awaitingFor.first() shouldBe null
        fixture.store.current().awaitingPolicyFor shouldBe null
    }

    @Test
    fun `only a policy fetched after the answer ends the update`() = runTest {
        val olderFetchStarted = fixture.elapsed
        request()
        fixture.elapsed += 1_000
        submit()

        fixture.submissions.awaitingFor.first() shouldBe INSTALLATION_A

        fixture.accessState.value = access(INSTALLATION_A, fetchStartedElapsed = olderFetchStarted)
        fixture.submissions.awaitingFor.first() shouldBe INSTALLATION_A

        fixture.accessState.value = access(INSTALLATION_B, fetchStartedElapsed = fixture.elapsed + 1_000)
        fixture.submissions.awaitingFor.first() shouldBe INSTALLATION_A
        fixture.store.current().awaitingPolicyFor shouldBe INSTALLATION_A

        fixture.accessState.value = access(INSTALLATION_A, fetchStartedElapsed = fixture.elapsed + 1)
        fixture.submissions.awaitingFor.first() shouldBe null
        fixture.store.current().awaitingPolicyFor shouldBe null
    }

    @Test
    fun `the refresh started by the submission ends the update`() = runTest {
        // Like AccessRepo: the fetch is stamped when the refresh is asked for
        coEvery { fixture.accessRepo.refresh(any()) } coAnswers {
            fixture.accessState.value = access(INSTALLATION_A, fetchStartedElapsed = fixture.elapsed)
            fixture.elapsed += 1
        }
        request()

        submit()

        fixture.submissions.awaitingFor.first() shouldBe null
        coVerifyOrder {
            fixture.endpoint.submitSubscription(any(), any())
            fixture.accessRepo.refresh("subscription-submitted")
        }
    }

    @Test
    fun `nothing is updating before any submission was answered`() = runTest {
        fixture.submissions.awaitingFor.first() shouldBe null
        request()
        fixture.submissions.awaitingFor.first() shouldBe null
    }

    @Test
    fun `records are read per installation`() = runTest {
        request()
        request(installationId = INSTALLATION_B)

        val records = fixture.submissions.records(INSTALLATION_A).first()

        records.map { it.installationId } shouldContainExactly listOf(INSTALLATION_A)
        records.single().tokenHash shouldBe hash
    }

    companion object {
        private const val TOKEN = "PURCHASE-TOKEN-SENTINEL"
    }
}
