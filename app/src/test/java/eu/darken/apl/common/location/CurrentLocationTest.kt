package eu.darken.apl.common.location

import android.location.Location
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.util.concurrent.Executor
import kotlin.time.Duration.Companion.seconds

class CurrentLocationTest : BaseTest() {

    private val executor = Executor { it.run() }

    private fun fix(provider: String): Location = mockk {
        every { this@mockk.provider } returns provider
        every { latitude } returns 52.0
        every { longitude } returns 13.0
    }

    private fun TestScope.request(gateway: FakeLocationProviderGateway): Deferred<Location?> {
        val result = async { requestCurrentLocation(gateway, executor, 10.seconds) }
        runCurrent()
        return result
    }

    @Test
    fun `first fix wins and every request is cancelled`() = runTest {
        val gateway = FakeLocationProviderGateway()

        val result = request(gateway)
        gateway.oneShotRequests shouldBe listOf("network", "fused", "gps")

        val gpsFix = fix("gps")
        gateway.answer("network", null)
        gateway.answer("gps", gpsFix)
        runCurrent()

        result.isCompleted shouldBe true
        result.await() shouldBe gpsFix
        gateway.cancelledOneShots shouldContainExactlyInAnyOrder listOf("network", "fused", "gps")
    }

    @Test
    fun `all providers without a fix return null before the timeout`() = runTest {
        val gateway = FakeLocationProviderGateway()

        val result = request(gateway)
        gateway.answer("network", null)
        gateway.answer("fused", null)
        gateway.answer("gps", null)
        runCurrent()

        result.isCompleted shouldBe true
        result.await() shouldBe null
        currentTime shouldBe 0L
        gateway.cancelledOneShots shouldContainExactlyInAnyOrder listOf("network", "fused", "gps")
    }

    @Test
    fun `no answer in time returns null and cancels every request`() = runTest {
        val gateway = FakeLocationProviderGateway()

        val result = request(gateway)
        gateway.answer("network", null)
        advanceTimeBy(9.seconds)
        runCurrent()
        result.isCompleted shouldBe false

        advanceTimeBy(1.seconds)
        runCurrent()

        result.isCompleted shouldBe true
        currentTime shouldBe 10_000L
        result.await() shouldBe null
        gateway.cancelledOneShots shouldContainExactlyInAnyOrder listOf("network", "fused", "gps")
    }

    @Test
    fun `a provider failing to start is skipped while another answers`() = runTest {
        val gateway = FakeLocationProviderGateway().apply {
            oneShotErrors["network"] = SecurityException("no network")
            oneShotErrors["fused"] = IllegalArgumentException("no fused")
        }

        val result = request(gateway)
        gateway.oneShotRequests shouldBe listOf("gps")

        val gpsFix = fix("gps")
        gateway.answer("gps", gpsFix)
        runCurrent()

        result.await() shouldBe gpsFix
        gateway.cancelledOneShots shouldBe listOf("gps")
    }

    @Test
    fun `no provider started returns null`() = runTest {
        val gateway = FakeLocationProviderGateway().apply {
            oneShotErrors["network"] = SecurityException("no network")
            oneShotErrors["fused"] = SecurityException("no fused")
            oneShotErrors["gps"] = SecurityException("no gps")
        }

        requestCurrentLocation(gateway, executor, 10.seconds) shouldBe null
        currentTime shouldBe 0L
        gateway.oneShotRequests shouldBe emptyList()
    }

    @Test
    fun `missing coarse permission returns null without requests`() = runTest {
        val gateway = FakeLocationProviderGateway(coarse = false)

        requestCurrentLocation(gateway, executor, 10.seconds) shouldBe null
        gateway.oneShotRequests shouldBe emptyList()
    }

    @Test
    fun `caller cancellation cancels every request`() = runTest {
        val gateway = FakeLocationProviderGateway()

        val result = request(gateway)
        result.cancel()
        runCurrent()

        result.isCancelled shouldBe true
        gateway.cancelledOneShots shouldContainExactlyInAnyOrder listOf("network", "fused", "gps")
    }

    @Test
    fun `a failing cancellation does not skip the others`() = runTest {
        val gateway = FakeLocationProviderGateway().apply {
            cancelErrors["network"] = IllegalStateException("gone")
        }

        val result = request(gateway)
        val fusedFix = fix("fused")
        gateway.answer("fused", fusedFix)
        runCurrent()

        result.await() shouldBe fusedFix
        gateway.cancelledOneShots shouldContainExactlyInAnyOrder listOf("network", "fused", "gps")
    }
}
