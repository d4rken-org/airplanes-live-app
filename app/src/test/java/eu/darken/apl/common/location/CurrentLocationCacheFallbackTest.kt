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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class CurrentLocationCacheFallbackTest : BaseTest() {

    private val executor = Executor { it.run() }

    private fun fix(provider: String, nanos: Long): Location = mockk {
        every { this@mockk.provider } returns provider
        every { latitude } returns 52.0
        every { longitude } returns 13.0
        every { elapsedRealtimeNanos } returns nanos
    }

    private fun TestScope.request(gateway: FakeLocationProviderGateway): Deferred<Location?> {
        val result = async { requestCurrentLocation(gateway, executor, 10.seconds) }
        runCurrent()
        return result
    }

    @Test
    fun `a recent last-known fix answers when no provider does`() = runTest {
        val gateway = FakeLocationProviderGateway()
        gateway.nowNanos = 600_000_000_000L
        val cached = mockk<Location> {
            every { provider } returns "fused"
            every { latitude } returns 32.77
            every { longitude } returns -96.79
            every { elapsedRealtimeNanos } returns 500_000_000_000L
        }
        gateway.lastKnownFixes["fused"] = cached

        val result = async { requestCurrentLocation(gateway, Executor { it.run() }, 10.seconds) }
        runCurrent()
        advanceTimeBy(10.seconds)
        runCurrent()

        result.await() shouldBe cached
    }

    @Test
    fun `a recent cache answers after the short wait and every request is cancelled`() = runTest {
        val gateway = FakeLocationProviderGateway().apply { nowNanos = 600_000_000_000L }
        val cached = fix("fused", 500_000_000_000L)
        gateway.lastKnownFixes["fused"] = cached

        val result = request(gateway)
        gateway.oneShotRequests shouldBe listOf("network", "fused", "gps")

        advanceTimeBy(2999.milliseconds)
        runCurrent()
        result.isCompleted shouldBe false

        advanceTimeBy(1.milliseconds)
        runCurrent()

        result.isCompleted shouldBe true
        currentTime shouldBe 3000L
        result.await() shouldBe cached
        gateway.cancelledOneShots shouldContainExactlyInAnyOrder listOf("network", "fused", "gps")
    }

    @Test
    fun `a fresh answer beats the cache`() = runTest {
        val gateway = FakeLocationProviderGateway().apply { nowNanos = 600_000_000_000L }
        gateway.lastKnownFixes["fused"] = fix("fused", 500_000_000_000L)

        val result = request(gateway)
        advanceTimeBy(1.seconds)
        val fresh = fix("gps", 601_000_000_000L)
        gateway.answer("gps", fresh)
        runCurrent()

        result.isCompleted shouldBe true
        currentTime shouldBe 1000L
        result.await() shouldBe fresh
        gateway.cancelledOneShots shouldContainExactlyInAnyOrder listOf("network", "fused", "gps")
    }

    @Test
    fun `a cache older than 10 minutes is ignored`() = runTest {
        val gateway = FakeLocationProviderGateway().apply { nowNanos = 700_000_000_000L }
        gateway.lastKnownFixes["fused"] = fix("fused", 99_999_999_999L)

        val result = request(gateway)
        advanceTimeBy(9999.milliseconds)
        runCurrent()
        result.isCompleted shouldBe false

        advanceTimeBy(1.milliseconds)
        runCurrent()

        result.isCompleted shouldBe true
        currentTime shouldBe 10_000L
        result.await() shouldBe null
    }

    @Test
    fun `a cache exactly 10 minutes old is used`() = runTest {
        val gateway = FakeLocationProviderGateway().apply { nowNanos = 700_000_000_000L }
        val cached = fix("fused", 100_000_000_000L)
        gateway.lastKnownFixes["fused"] = cached

        val result = request(gateway)
        advanceTimeBy(3.seconds)
        runCurrent()

        result.isCompleted shouldBe true
        currentTime shouldBe 3000L
        result.await() shouldBe cached
    }

    @Test
    fun `a cache is returned immediately when no request could be started`() = runTest {
        val gateway = FakeLocationProviderGateway().apply {
            nowNanos = 600_000_000_000L
            oneShotErrors["network"] = SecurityException("no network")
            oneShotErrors["fused"] = SecurityException("no fused")
            oneShotErrors["gps"] = SecurityException("no gps")
        }
        val cached = fix("network", 550_000_000_000L)
        gateway.lastKnownFixes["network"] = cached

        requestCurrentLocation(gateway, executor, 10.seconds) shouldBe cached
        currentTime shouldBe 0L
        gateway.oneShotRequests shouldBe emptyList()
    }

    @Test
    fun `a failing last-known lookup does not hide another provider's cache`() = runTest {
        val gateway = FakeLocationProviderGateway().apply {
            nowNanos = 600_000_000_000L
            lastKnownErrors["network"] = SecurityException("no network")
        }
        val cached = fix("fused", 500_000_000_000L)
        gateway.lastKnownFixes["fused"] = cached

        val result = request(gateway)
        advanceTimeBy(3.seconds)
        runCurrent()

        result.isCompleted shouldBe true
        currentTime shouldBe 3000L
        result.await() shouldBe cached
    }

    @Test
    fun `a newer last-known fix from during the wait is returned`() = runTest {
        val gateway = FakeLocationProviderGateway().apply { nowNanos = 600_000_000_000L }
        gateway.lastKnownFixes["fused"] = fix("fused", 500_000_000_000L)

        val result = request(gateway)
        advanceTimeBy(1.seconds)
        runCurrent()
        val newer = fix("gps", 601_000_000_000L)
        gateway.lastKnownFixes["gps"] = newer
        gateway.nowNanos = 602_000_000_000L

        advanceTimeBy(2.seconds)
        runCurrent()

        result.isCompleted shouldBe true
        currentTime shouldBe 3000L
        result.await() shouldBe newer
    }

    @Test
    fun `caller cancellation with a recent cache stays a cancellation`() = runTest {
        val gateway = FakeLocationProviderGateway().apply { nowNanos = 600_000_000_000L }
        gateway.lastKnownFixes["fused"] = fix("fused", 500_000_000_000L)

        val result = request(gateway)
        advanceTimeBy(1.seconds)
        result.cancel()
        runCurrent()

        result.isCancelled shouldBe true
        gateway.cancelledOneShots shouldContainExactlyInAnyOrder listOf("network", "fused", "gps")
    }
}
