package eu.darken.apl.upgrade.subscription.submit

import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.SubscriptionRequest
import eu.darken.apl.upgrade.subscription.submit.SubmissionFixture.Companion.INSTALLATION_A
import eu.darken.apl.upgrade.subscription.submit.SubmissionFixture.Companion.purchase
import eu.darken.apl.upgrade.subscription.submit.SubscriptionSubmitter.Outcome
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import testhelper.BaseTest

class F10RedTest : BaseTest() {

    private val fixture = SubmissionFixture()
    private val purchase = purchase(TOKEN)
    private val hash = purchase.tokenHash

    @AfterEach
    fun teardown() = fixture.close()

    @ParameterizedTest
    @CsvSource(
        "http_503, 503",
        "billing_unavailable, 503",
        "installation_rate_exceeded, 429",
    )
    fun `F10 a retry-later answer without Retry-After backs off instead of a 1-2 s follow-up`(
        code: String,
        status: Int,
    ) = runTest {
        coEvery { fixture.endpoint.submitSubscription(any(), any<SubscriptionRequest>()) } throws
            ServerApiException(code = code, status = status, retryAfterSeconds = null)
        fixture.submissions.request(INSTALLATION_A, purchase, "test")
        val queuedBefore = fixture.enqueued.toList()

        val outcome = fixture.submitter.submit(INSTALLATION_A, hash)

        val record = fixture.record(hash)!!
        val followUps = fixture.enqueued.drop(queuedBefore.size)
        withClue("$code (HTTP $status), now=${fixture.nowMillis}, record=$record, follow-ups=${followUps.map { it.delay }}") {
            assertSoftly {
                withClue("outcome") { outcome shouldBe Outcome.RETRY }
                withClue("retryNotBefore") { record.retryNotBefore shouldBe null }
                withClue("follow-up work queued") { followUps.size shouldBe 0 }
            }
        }
    }

    companion object {
        private const val TOKEN = "PURCHASE-TOKEN-SENTINEL"
    }
}
