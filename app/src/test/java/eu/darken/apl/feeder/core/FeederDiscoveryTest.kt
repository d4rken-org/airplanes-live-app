package eu.darken.apl.feeder.core

import eu.darken.apl.feeder.core.api.FeedStatus
import eu.darken.apl.feeder.core.api.FeederEndpoint
import eu.darken.apl.feeder.core.config.FeederConfig
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.io.IOException
import java.util.UUID

class FeederDiscoveryTest : BaseTest() {

    private val endpoint = mockk<FeederEndpoint>()
    private val feederRepo = mockk<FeederRepo>()

    private val uuidA = "0199a1f2-0000-7000-8000-a1b2c3d4e5f6"
    private val uuidB = "0199a1f2-1111-7000-8000-b2c3d4e5f6a7"

    @BeforeEach
    fun setup() {
        every { feederRepo.feeders } returns flowOf(emptyList())
    }

    private fun create() = FeederDiscovery(
        feederEndpoint = endpoint,
        feederRepo = feederRepo,
    )

    private fun monitored(vararg ids: ReceiverId) {
        every { feederRepo.feeders } returns flowOf(
            ids.map { Feeder(config = FeederConfig.newFeeder(it)) },
        )
    }

    private fun feedStatus(
        host: String? = "203.0.113.7",
        beast: List<String> = emptyList(),
        mlat: List<String> = emptyList(),
    ) = FeedStatus(
        host = host,
        beastClients = beast.map { FeedStatus.BeastClient(uuid = UUID.fromString(it), host = "10.0.0.2") },
        mlatClients = mlat.map {
            FeedStatus.MlatClient(user = "someone", uuid = UUID.fromString(it), latitude = 1.0, longitude = 2.0)
        },
    )

    @Test
    fun `no monitored feeders asks the network nothing`() = runTest {
        monitored()

        create().findRegisterable() shouldBe FeederDiscovery.Registerable(host = null, ids = emptySet())

        coVerify(exactly = 0) { endpoint.getFeedStatus() }
    }

    @Test
    fun `a monitored feeder on this network is registerable`() = runTest {
        monitored(uuidA)
        coEvery { endpoint.getFeedStatus() } returns feedStatus(beast = listOf(uuidA))

        create().findRegisterable() shouldBe FeederDiscovery.Registerable(
            host = "203.0.113.7",
            ids = setOf(uuidA),
        )
    }

    @Test
    fun `matching ignores case and reports the id as it is stored`() = runTest {
        val stored = uuidA.uppercase()
        monitored(stored)
        coEvery { endpoint.getFeedStatus() } returns feedStatus(beast = listOf(uuidA))

        create().findRegisterable().ids shouldBe setOf(stored)
    }

    @Test
    fun `feeders on this network that are not monitored are not offered`() = runTest {
        monitored(uuidA)
        coEvery { endpoint.getFeedStatus() } returns feedStatus(beast = listOf(uuidB))

        create().findRegisterable().ids shouldBe emptySet()
    }

    @Test
    fun `an mlat only client is not a registerable feeder`() = runTest {
        monitored(uuidA)
        coEvery { endpoint.getFeedStatus() } returns feedStatus(beast = emptyList(), mlat = listOf(uuidA))

        create().findRegisterable().ids shouldBe emptySet()
    }

    @Test
    fun `only the monitored feeders present on this network are offered`() = runTest {
        val uuidC = "0199a1f2-2222-7000-8000-c3d4e5f6a7b8"
        monitored(uuidA, uuidB, uuidC)
        coEvery { endpoint.getFeedStatus() } returns feedStatus(beast = listOf(uuidA, uuidC))

        create().findRegisterable().ids shouldBe setOf(uuidA, uuidC)
    }

    @Test
    fun `cancellation while the feed status is pending is not an empty answer`() = runTest {
        monitored(uuidA)
        val started = CompletableDeferred<Unit>()
        coEvery { endpoint.getFeedStatus() } coAnswers {
            started.complete(Unit)
            awaitCancellation()
        }

        var outcome: Result<FeederDiscovery.Registerable>? = null
        val discovery = create()
        val job = launch { outcome = runCatching { discovery.findRegisterable() } }
        started.await()
        job.cancelAndJoin()

        // Swallowing the cancellation would complete with an empty answer instead of failing;
        // runCatching records CancellationException as a Failure, so getOrNull() covers both shapes
        outcome?.getOrNull() shouldBe null
    }

    @Test
    fun `a failed feed status propagates instead of reporting nothing registerable`() = runTest {
        monitored(uuidA)
        coEvery { endpoint.getFeedStatus() } throws IOException("offline")

        shouldThrow<IOException> { create().findRegisterable() }
    }
}
