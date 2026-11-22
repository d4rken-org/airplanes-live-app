package eu.darken.apl.upgrade.subscription.submit

import eu.darken.apl.common.debug.logging.Logging
import eu.darken.apl.common.http.HttpModule
import eu.darken.apl.server.ServerModule
import eu.darken.apl.server.api.ServerEndpoint
import eu.darken.apl.server.api.SubscriptionRequest
import eu.darken.apl.server.api.SubscriptionResponse
import eu.darken.apl.upgrade.subscription.submit.SubmissionFixture.Companion.INSTALLATION_A
import eu.darken.apl.upgrade.subscription.submit.SubmissionFixture.Companion.SESSION_TOKEN
import eu.darken.apl.upgrade.subscription.submit.SubmissionFixture.Companion.purchase
import eu.darken.apl.upgrade.subscription.submit.SubscriptionSubmitter.Outcome
import eu.darken.apl.upgrade.subscription.submit.SubscriptionTokenRecord.Submission
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.mockk.coEvery
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.TestCompatibilityStore
import testhelper.coroutine.TestDispatcherProvider

/** The submission over a real [ServerEndpoint]: wire format, decoding, the endpoint's own gate and logging. */
class SubscriptionSubmitEndpointTest : BaseTest() {

    private val server = MockWebServer()
    private val compatStore = TestCompatibilityStore()
    private val json = ServerModule.serverJson()
    private val logged = CapturingLogger()

    private val fixture = SubmissionFixture { fixture ->
        ServerEndpoint(
            baseClient = HttpModule().baseHttpClient(),
            json = json,
            dispatcherProvider = TestDispatcherProvider(),
            serverClock = fixture.serverClock,
            compatibility = compatStore.holder(),
            privacyAcceptance = fixture.privacyAcceptance,
            requestGate = fixture.requestGate,
        ).apply { baseUrl = server.url("/").toString() }
    }

    private val purchase = purchase(TOKEN)
    private val hash = purchase.tokenHash

    @BeforeEach
    fun setup() {
        Logging.install(logged)
    }

    @AfterEach
    fun teardown() {
        Logging.remove(logged)
        fixture.close()
        server.shutdown()
        compatStore.close()
    }

    private suspend fun requestAndSubmit(): Outcome {
        // Past any wait an earlier answer asked for
        fixture.elapsed += 10 * 60 * 1000L
        fixture.submissions.request(INSTALLATION_A, purchase, "test")
        return fixture.submitter.submit(INSTALLATION_A, hash)
    }

    private suspend fun record() = fixture.record(hash)!!

    @Test
    fun `the purchase is sent with the session's credentials`() = runTest {
        server.enqueue(MockResponse().setBody("""{"state":"active","granted":true,"expiresAt":1790000000000,"attached":true}"""))

        requestAndSubmit() shouldBe Outcome.DONE

        val sent = server.takeRequest()
        sent.path shouldBe "/api/v1/billing/subscription"
        sent.getHeader("Authorization") shouldBe "Bearer $SESSION_TOKEN"
        json.decodeFromString(SubscriptionRequest.serializer(), sent.body.readUtf8()) shouldBe
            SubscriptionRequest(productId = "upgrade.pro", purchaseToken = TOKEN)
        record().submission shouldBe Submission.ATTACHED
    }

    @Test
    fun `an unknown state with an explicit null expiry is stored as is`() = runTest {
        server.enqueue(MockResponse().setBody("""{"state":"brand_new_state","granted":false,"expiresAt":null,"attached":false}"""))

        requestAndSubmit() shouldBe Outcome.DONE

        record().submission shouldBe Submission.NOT_ATTACHED
        record().serverState shouldBe "brand_new_state"
    }

    @Test
    fun `a deletion starting after the check holds the request back`() = runTest {
        val deletionRunning = CompletableDeferred<Unit>()
        val finishDeletion = CompletableDeferred<Unit>()
        // The deletion starts between the submitter's own check and the endpoint call
        coEvery { fixture.sessionManager.authedAs<SubscriptionResponse>(any()) } coAnswers {
            launch {
                fixture.requestGate.deletionOperation {
                    deletionRunning.complete(Unit)
                    finishDeletion.await()
                }
            }
            deletionRunning.await()
            firstArg<suspend (String, String) -> SubscriptionResponse>().invoke(INSTALLATION_A, SESSION_TOKEN)
        }
        server.enqueue(MockResponse().setBody("""{"state":"active","granted":true,"attached":true}"""))

        val run = async { requestAndSubmit() }
        deletionRunning.await()
        withContext(Dispatchers.Default) { delay(200) }
        server.requestCount shouldBe 0

        finishDeletion.complete(Unit)
        run.await() shouldBe Outcome.DONE
        server.requestCount shouldBe 1
    }

    @Test
    fun `no outcome logs the purchase token`() = runTest {
        server.enqueue(MockResponse().setBody("""{"state":"active","granted":true,"attached":true}"""))
        requestAndSubmit() shouldBe Outcome.DONE

        server.enqueue(problem(429, "quota_exceeded", retryAfter = 1))
        requestAndSubmit() shouldBe Outcome.DONE
        server.enqueue(problem(503, "billing_unavailable", retryAfter = 1))
        requestAndSubmit() shouldBe Outcome.DONE
        server.enqueue(problem(422, "purchase_invalid"))
        requestAndSubmit() shouldBe Outcome.DONE
        server.enqueue(problem(400, "invalid_request"))
        requestAndSubmit() shouldBe Outcome.DONE
        server.enqueue(problem(403, "installation_revoked"))
        requestAndSubmit() shouldBe Outcome.FAILED
        server.enqueue(MockResponse().setBody("""{"unexpected": true}"""))
        requestAndSubmit() shouldBe Outcome.FAILED
        // Last: once refused, the endpoint refuses every later call itself
        server.enqueue(problem(403, "app_update_required"))
        requestAndSubmit() shouldBe Outcome.DONE

        server.requestCount shouldBe 8
        logged.lines.filter { "#$hash" in it }.shouldNotBeEmpty()
        logged.lines.forEach { it shouldNotContain TOKEN }
    }

    @Test
    fun `a network failure is retried without logging the purchase token`() = runTest {
        // OkHttp may retry a dropped connection on its own
        repeat(4) { server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)) }

        requestAndSubmit() shouldBe Outcome.RETRY

        record().hasOpenRequest shouldBe true
        logged.lines.filter { "#$hash" in it }.shouldNotBeEmpty()
        logged.lines.forEach { it shouldNotContain TOKEN }
    }

    private fun problem(status: Int, code: String, retryAfter: Long? = null) = MockResponse()
        .setResponseCode(status)
        .setHeader("Content-Type", "application/problem+json")
        .setBody(
            buildString {
                append("""{"type":"about:blank","status":$status,"code":"$code"""")
                retryAfter?.let { append(""","retryAfterSeconds":$it""") }
                append("}")
            }
        )

    private class CapturingLogger : Logging.Logger {
        private val captured = mutableListOf<String>()
        val lines: List<String> get() = synchronized(captured) { captured.toList() }

        override fun log(priority: Logging.Priority, tag: String, message: String, metaData: Map<String, Any>?) {
            synchronized(captured) { captured.add("$tag $message") }
        }
    }

    companion object {
        private const val TOKEN = "PURCHASE-TOKEN-SENTINEL"
    }
}
