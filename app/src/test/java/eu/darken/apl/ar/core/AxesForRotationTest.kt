package eu.darken.apl.ar.core

import android.hardware.SensorManager
import android.view.Surface
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.floats.shouldBeLessThan
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class AxesForRotationTest {

    /**
     * Camera facing North at the horizon, device turned counter-clockwise (seen from the screen)
     * by [rotation] quarter turns, which is the turn that gives that display rotation.
     * Portrait (0): device X = East, device Y = Up, device Z = South.
     */
    private fun turnedDeviceMatrix(rotation: Int): FloatArray {
        val angle = rotation * PI / 2
        val c = cos(angle).toFloat()
        val s = sin(angle).toFloat()
        // Columns: device X = (c, 0, s), device Y = (-s, 0, c), device Z = (0, -1, 0) in East/North/Up
        return floatArrayOf(
            c, -s, 0f, 0f,
            0f, 0f, -1f, 0f,
            s, c, 0f, 0f,
            0f, 0f, 0f, 1f,
        )
    }

    private fun assertNorthWithEastOnTheRight(rotation: Int) {
        val (axisX, axisY) = DeviceOrientationProvider.axesForRotation(rotation)
        val remapped = FloatArray(16)
        SensorManager.remapCoordinateSystem(turnedDeviceMatrix(rotation), axisX, axisY, remapped).shouldBeTrue()

        val heading = atan2(-remapped[2].toDouble(), -remapped[6].toDouble()).toFloat()
        withClue("rotation=$rotation heading=$heading") {
            abs(heading) shouldBeLessThan 0.01f
        }

        // Screen right in East/North/Up is the remapped X column
        val rightE = remapped[0]
        val rightN = remapped[4]
        val rightU = remapped[8]
        withClue("rotation=$rotation screenRight=($rightE, $rightN, $rightU)") {
            abs(rightE - 1f) shouldBeLessThan 0.01f
            abs(rightN) shouldBeLessThan 0.01f
            abs(rightU) shouldBeLessThan 0.01f
        }
    }

    @Test
    fun `rotation 0 looks north with east on the right`() = assertNorthWithEastOnTheRight(Surface.ROTATION_0)

    @Test
    fun `rotation 90 looks north with east on the right`() = assertNorthWithEastOnTheRight(Surface.ROTATION_90)

    @Test
    fun `rotation 180 looks north with east on the right`() = assertNorthWithEastOnTheRight(Surface.ROTATION_180)

    @Test
    fun `rotation 270 looks north with east on the right`() = assertNorthWithEastOnTheRight(Surface.ROTATION_270)
}
