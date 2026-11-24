package eu.darken.apl.common.location

import android.location.Location
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.util.concurrent.Executor
import kotlin.time.Duration.Companion.seconds

class CurrentLocationLateFixTest : BaseTest() {

    private val executor = Executor { it.run() }

    private fun fix(provider: String, nanos: Long): Location = mockk {
        every { this@mockk.provider } returns provider
        every { latitude } returns 52.0
        every { longitude } returns 13.0
        every { elapsedRealtimeNanos } returns nanos
    }

    @Test
    fun `a cache aging out during the short wait keeps waiting for the full timeout`() = runTest {
        val gateway = FakeLocationProviderGateway().apply { nowNanos = 600_000_000_000L }
        gateway.lastKnownFixes["fused"] = fix("fused", 1_000_000_000L)

        val result = async { requestCurrentLocation(gateway, executor, 10.seconds) }
        runCurrent()

        gateway.nowNanos += 3_000_000_000L
        advanceTimeBy(3.seconds)
        runCurrent()
        result.isCompleted shouldBe false

        advanceTimeBy(2.seconds)
        val fresh = fix("gps", gateway.nowNanos)
        gateway.answer("gps", fresh)
        runCurrent()

        result.await() shouldBe fresh
    }

    @Test
    fun `unanswered requests keep running after the timeout`() = runTest {
        val gateway = FakeLocationProviderGateway()

        val result = async { requestCurrentLocation(gateway, executor, 10.seconds) }
        runCurrent()
        advanceTimeBy(10.seconds)
        runCurrent()

        result.await() shouldBe null
        gateway.cancelledOneShots shouldBe emptyList()
    }
}
