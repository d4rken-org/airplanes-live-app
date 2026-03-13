package eu.darken.apl.ar.core

import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import kotlin.math.abs
import kotlin.math.PI

class HeadingFusionTest : BaseTest() {

    @Test
    fun `stable offset converges to actual difference`() {
        val fusion = HeadingFusion(tau = 1f)
        val gameAz = 0f
        val magAz = 0.1f
        var result = 0f

        for (i in 0 until 100) {
            val ts = (i * 66_666_667L) // ~15Hz, 66ms intervals
            result = fusion.update(gameAz, magAz, ts)
        }

        abs(result - 0.1f) shouldBeLessThan 0.01f
    }

    @Test
    fun `wraparound near PI boundary produces small offset`() {
        val fusion = HeadingFusion(tau = 1f)
        val gameAz = -PI.toFloat() + 0.05f
        val magAz = PI.toFloat() - 0.05f

        var result = 0f
        for (i in 0 until 100) {
            val ts = (i * 66_666_667L)
            result = fusion.update(gameAz, magAz, ts)
        }

        // The circular delta should be ~-0.1 (mag is 0.1 rad "behind" game going the short way)
        abs(result) shouldBeLessThan 0.15f
    }

    @Test
    fun `single large mag spike is rejected by time constant`() {
        val fusion = HeadingFusion(tau = 5f)

        // Feed steady state first
        for (i in 0 until 50) {
            fusion.update(0f, 0.05f, i * 66_666_667L)
        }
        val beforeSpike = fusion.smoothedOffsetRad

        // Single spike: magnetometer jumps 1 radian
        fusion.update(0f, 1.05f, 50 * 66_666_667L)
        val afterSpike = fusion.smoothedOffsetRad

        // With tau=5, alpha for 66ms ≈ 0.013 — offset should move very little
        val spikeMovement = abs(afterSpike - beforeSpike)
        spikeMovement shouldBeLessThan 0.05f
    }

    @Test
    fun `reset clears state and reinitializes on next update`() {
        val fusion = HeadingFusion(tau = 1f)

        fusion.update(0f, 0.5f, 0L)
        abs(fusion.smoothedOffsetRad) shouldBeGreaterThan 0.1f

        fusion.reset()
        fusion.smoothedOffsetRad shouldBe 0f

        // After reset, first update re-initializes to the new offset
        fusion.update(0f, -0.3f, 1_000_000_000L)
        abs(fusion.smoothedOffsetRad - (-0.3f)) shouldBeLessThan 0.01f
    }

    @Test
    fun `time-based alpha - larger dt produces faster convergence`() {
        val fusionFast = HeadingFusion(tau = 2f)
        val fusionSlow = HeadingFusion(tau = 2f)

        // Fast: 200ms intervals
        fusionFast.update(0f, 1f, 0L)
        fusionFast.update(0f, 1f, 200_000_000L)
        val fastResult = fusionFast.smoothedOffsetRad

        // Slow: 20ms intervals
        fusionSlow.update(0f, 1f, 0L)
        fusionSlow.update(0f, 1f, 20_000_000L)
        val slowResult = fusionSlow.smoothedOffsetRad

        // Both start at offset=1.0. After one step, fast should have moved less (closer to initial)
        // Wait — larger dt means larger alpha means MORE convergence, not less
        // Actually first update initializes to 1.0, second update also feeds 1.0
        // So they converge to 1.0 equally. Let me change: offset shifts on second call.
        // Restart with a shift scenario:
        val f1 = HeadingFusion(tau = 2f)
        val f2 = HeadingFusion(tau = 2f)

        // Initialize both to offset=0
        f1.update(0f, 0f, 0L)
        f2.update(0f, 0f, 0L)

        // Now mag jumps to 1.0 — larger dt should produce more movement
        f1.update(0f, 1f, 200_000_000L) // 200ms
        f2.update(0f, 1f, 20_000_000L)  // 20ms

        abs(f1.smoothedOffsetRad) shouldBeGreaterThan abs(f2.smoothedOffsetRad)
    }

    @Test
    fun `circularDelta wraps correctly`() {
        val delta1 = HeadingFusion.circularDelta(3f, -3f)
        abs(delta1) shouldBeLessThan PI.toFloat()

        val delta2 = HeadingFusion.circularDelta(-3f, 3f)
        abs(delta2) shouldBeLessThan PI.toFloat()

        // Same angle should give zero delta
        val delta3 = HeadingFusion.circularDelta(1f, 1f)
        abs(delta3) shouldBeLessThan 0.001f
    }
}
