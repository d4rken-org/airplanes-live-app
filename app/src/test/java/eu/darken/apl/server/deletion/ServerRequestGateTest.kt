package eu.darken.apl.server.deletion

import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.http.HttpModule
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.ServerModule
import eu.darken.apl.server.api.ServerEndpoint
import eu.darken.apl.server.compat.AppCompatibility
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.TestCompatibilityStore
import testhelper.coroutine.TestDispatcherProvider

class ServerRequestGateTest : BaseTest() {

    private lateinit var mockWebServer: MockWebServer
    private val compatStore = TestCompatibilityStore()
    private lateinit var compatibility: AppCompatibility
    private val persisted = MutableStateFlow(DeletionState.NONE)
    private val gate = ServerRequestGate(persisted)

    private val fakeMonotonicClock = object : MonotonicClock {
        override fun elapsed(): Long = 1_000L
    }

    @BeforeEach
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        compatibility = compatStore.holder()
    }

    @AfterEach
    fun teardown() {
        mockWebServer.shutdown()
        compatStore.close()
    }

    private fun createEndpoint() = ServerEndpoint(
        baseClient = HttpModule().baseHttpClient(),
        json = ServerModule.serverJson(),
        dispatcherProvider = TestDispatcherProvider(),
        serverClock = ServerClock(fakeMonotonicClock),
        compatibility = compatibility,
        requestGate = gate,
    ).apply {
        baseUrl = mockWebServer.url("/").toString()
    }

    /** Loaded first, so a call is already waiting at the gate after runCurrent(). */
    private suspend fun readyEndpoint(): ServerEndpoint {
        compatibility.awaitLoaded()
        return createEndpoint()
    }

    @Test
    fun `a request outside the deletion waits until the deletion ended`() = runTest {
        val endpoint = readyEndpoint()
        mockWebServer.enqueue(MockResponse().setResponseCode(204))

        val release = CompletableDeferred<Unit>()
        val operation = async { gate.deletionOperation { release.await() } }
        runCurrent()

        val outside = async { endpoint.deleteWatch("token", "local-1") }
        runCurrent()
        mockWebServer.requestCount shouldBe 0

        release.complete(Unit)
        operation.await()
        outside.await()
        mockWebServer.requestCount shouldBe 1
    }

    @Test
    fun `a request inside the deletion reaches the server while the gate is closed`() = runTest {
        val endpoint = readyEndpoint()
        persisted.value = DeletionState.WIPE_PENDING
        mockWebServer.enqueue(MockResponse().setResponseCode(204))

        gate.deletionOperation { endpoint.deleteInstallation("token") }

        mockWebServer.requestCount shouldBe 1
        mockWebServer.takeRequest().path shouldBe "/api/v1/installation"
    }

    @Test
    fun `a pending wipe keeps requests waiting until the state is cleared`() = runTest {
        val endpoint = readyEndpoint()
        persisted.value = DeletionState.WIPE_PENDING
        mockWebServer.enqueue(MockResponse().setResponseCode(204))

        val waiting = async { endpoint.deleteWatch("token", "local-1") }
        runCurrent()
        mockWebServer.requestCount shouldBe 0

        persisted.value = DeletionState.NONE
        waiting.await()
        mockWebServer.requestCount shouldBe 1
    }

    @Test
    fun `a pending request leaves the gate open`() = runTest {
        val endpoint = readyEndpoint()
        persisted.value = DeletionState.REQUEST_PENDING
        mockWebServer.enqueue(MockResponse().setResponseCode(204))

        endpoint.deleteWatch("token", "local-1")

        mockWebServer.requestCount shouldBe 1
    }

    @Test
    fun `a failed deletion opens the gate again`() = runTest {
        val endpoint = readyEndpoint()
        mockWebServer.enqueue(MockResponse().setResponseCode(204))

        shouldThrow<IllegalStateException> { gate.deletionOperation { error("deletion failed") } }

        endpoint.deleteWatch("token", "local-1")
        mockWebServer.requestCount shouldBe 1
    }

    @Test
    fun `a cancelled deletion opens the gate again`() = runTest {
        val endpoint = readyEndpoint()
        mockWebServer.enqueue(MockResponse().setResponseCode(204))

        val operation = async { gate.deletionOperation { awaitCancellation() } }
        runCurrent()
        operation.cancel()
        runCurrent()

        endpoint.deleteWatch("token", "local-1")
        mockWebServer.requestCount shouldBe 1
    }
}
