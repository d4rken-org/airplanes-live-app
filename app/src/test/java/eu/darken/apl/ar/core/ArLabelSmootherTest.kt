package eu.darken.apl.ar.core

import eu.darken.apl.ar.core.ArLabelSmoother.Companion.LABEL_ALPHA
import eu.darken.apl.ar.ui.ArLabel
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class ArLabelSmootherTest : BaseTest() {

    private fun label(x: Float, y: Float) = ArLabel(
        hex = "AABBCC",
        callsign = null,
        registration = null,
        description = null,
        altitudeFt = null,
        speedKts = null,
        distanceM = 1000.0,
        screenXNorm = x,
        screenYNorm = y,
    )

    @Test
    fun `second pass moves part of the way to the new position`() {
        val smoother = ArLabelSmoother()
        smoother.smooth(listOf(label(0.2f, 0.3f)), 0)

        val moved = smoother.smooth(listOf(label(0.8f, 0.7f)), 0).single()

        moved.screenXNorm shouldBe (0.2f + LABEL_ALPHA * 0.6f).plusOrMinus(1e-5f)
        moved.screenYNorm shouldBe (0.3f + LABEL_ALPHA * 0.4f).plusOrMinus(1e-5f)
    }

    @Test
    fun `after a reset the next pass returns the raw position`() {
        val smoother = ArLabelSmoother()
        smoother.smooth(listOf(label(0.2f, 0.3f)), 0)
        smoother.requestReset(1)

        val moved = smoother.smooth(listOf(label(0.8f, 0.7f)), 1).single()

        moved.screenXNorm shouldBe 0.8f
        moved.screenYNorm shouldBe 0.7f
    }

    @Test
    fun `a reset waits for the first frame with the new rotation`() {
        val smoother = ArLabelSmoother()
        smoother.smooth(listOf(label(0.2f, 0.3f)), 0)
        smoother.requestReset(2)

        val oldRotation = smoother.smooth(listOf(label(0.8f, 0.7f)), 0).single()

        oldRotation.screenXNorm shouldBe (0.2f + LABEL_ALPHA * 0.6f).plusOrMinus(1e-5f)
        oldRotation.screenYNorm shouldBe (0.3f + LABEL_ALPHA * 0.4f).plusOrMinus(1e-5f)

        val newRotation = smoother.smooth(listOf(label(0.5f, 0.5f)), 2).single()

        newRotation.screenXNorm shouldBe 0.5f
        newRotation.screenYNorm shouldBe 0.5f
    }
}
