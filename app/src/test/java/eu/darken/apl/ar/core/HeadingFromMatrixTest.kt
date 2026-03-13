package eu.darken.apl.ar.core

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.floats.shouldBeLessThan
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Tests heading/pitch/roll extraction directly from rotation matrices,
 * bypassing SensorManager.getOrientation() which has gimbal lock at pitch = ±90°.
 *
 * Matrix layout (4x4, column-major as returned by SensorManager):
 *   [0]  [1]  [2]  [3]
 *   [4]  [5]  [6]  [7]
 *   [8]  [9]  [10] [11]
 *   [12] [13] [14] [15]
 *
 * Look direction = device -Z in world = (-R[2], -R[6], -R[10])
 *   heading = atan2(-R[2], -R[6])     (East, North components)
 *   pitch   = atan2(-R[10], sqrt(R[2]² + R[6]²))
 *   roll    = atan2(R[8], R[9])
 */
class HeadingFromMatrixTest : BaseTest() {

    // Stateless version (matches production code when horizontal magnitude is not near-zero)
    private fun headingFromMatrix(m: FloatArray): Float {
        val e = -m[2]; val n = -m[6]
        return atan2(e.toDouble(), n.toDouble()).toFloat()
    }

    // Stateful version matching production code's epsilon guard
    private var lastValidHeading = 0f

    private fun headingFromMatrixGuarded(m: FloatArray): Float {
        val e = -m[2]; val n = -m[6]
        if (e * e + n * n < 1e-4f) return lastValidHeading
        val h = atan2(e.toDouble(), n.toDouble()).toFloat()
        lastValidHeading = h
        return h
    }

    private fun pitchFromMatrix(m: FloatArray): Float {
        return atan2((-m[10]).toDouble(), sqrt((m[2] * m[2] + m[6] * m[6]).toDouble())).toFloat()
    }

    private fun rollFromMatrix(m: FloatArray): Float {
        return atan2(m[8].toDouble(), m[9].toDouble()).toFloat()
    }

    @BeforeEach
    fun resetState() {
        lastValidHeading = 0f
    }

    /** Identity-like matrix for phone upright, facing North, after remapCoordinateSystem(X, Y) */
    private fun facingNorthMatrix() = floatArrayOf(
        // Phone upright portrait facing North:
        // device-X = world-East, device-Y = world-Up, device-Z = world-South (into screen)
        // Row 0 (world-X/East):   deviceX=1, deviceY=0, deviceZ=0
        // Row 1 (world-Y/North):  deviceX=0, deviceY=0, deviceZ=-1
        // Row 2 (world-Z/Up):     deviceX=0, deviceY=1, deviceZ=0
        1f, 0f, 0f, 0f,
        0f, 0f, -1f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f,
    )

    /** Phone upright portrait facing East */
    private fun facingEastMatrix() = floatArrayOf(
        // device-X = world-North, device-Y = world-Up, device-Z = world-West
        // Row 0 (East):   deviceX=0, deviceY=0, deviceZ=-1  (West = -East)
        // Row 1 (North):  deviceX=1, deviceY=0, deviceZ=0
        // Row 2 (Up):     deviceX=0, deviceY=1, deviceZ=0
        0f, 0f, -1f, 0f,
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f,
    )

    /** Phone upright portrait facing South */
    private fun facingSouthMatrix() = floatArrayOf(
        // device-X = world-West, device-Y = world-Up, device-Z = world-North
        // Row 0 (East):   deviceX=-1, deviceY=0, deviceZ=0
        // Row 1 (North):  deviceX=0,  deviceY=0, deviceZ=1
        // Row 2 (Up):     deviceX=0,  deviceY=1, deviceZ=0
        -1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f,
    )

    /** Phone upright portrait facing West */
    private fun facingWestMatrix() = floatArrayOf(
        // device-X = world-South, device-Y = world-Up, device-Z = world-East
        // Row 0 (East):   deviceX=0,  deviceY=0, deviceZ=1
        // Row 1 (North):  deviceX=-1, deviceY=0, deviceZ=0
        // Row 2 (Up):     deviceX=0,  deviceY=1, deviceZ=0
        0f, 0f, 1f, 0f,
        -1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f,
    )

    @Test
    fun `facing North gives heading near 0`() {
        val heading = headingFromMatrix(facingNorthMatrix())
        abs(heading) shouldBeLessThan 0.01f
    }

    @Test
    fun `facing East gives heading near pi div 2`() {
        val heading = headingFromMatrix(facingEastMatrix())
        abs(heading - (PI / 2).toFloat()) shouldBeLessThan 0.01f
    }

    @Test
    fun `facing South gives heading near pi`() {
        val heading = headingFromMatrix(facingSouthMatrix())
        abs(abs(heading) - PI.toFloat()) shouldBeLessThan 0.01f
    }

    @Test
    fun `facing West gives heading near negative pi div 2`() {
        val heading = headingFromMatrix(facingWestMatrix())
        abs(heading - (-PI / 2).toFloat()) shouldBeLessThan 0.01f
    }

    @Test
    fun `upright portrait facing horizon gives pitch near 0`() {
        val pitch = pitchFromMatrix(facingNorthMatrix())
        abs(pitch) shouldBeLessThan 0.01f
    }

    @Test
    fun `upright portrait gives roll near 0`() {
        val roll = rollFromMatrix(facingNorthMatrix())
        abs(roll) shouldBeLessThan 0.01f
    }

    // --- Non-cardinal heading ---

    /** Phone upright portrait facing NE (45° from North toward East) */
    private fun facingNEMatrix(): FloatArray {
        val s = sin(PI / 4).toFloat() // 0.707
        val c = cos(PI / 4).toFloat()
        return floatArrayOf(
            // Row 0 (East):  devX=cos45, devY=0, devZ=-sin45
            // Row 1 (North): devX=-sin45, devY=0, devZ=-cos45
            // Row 2 (Up):    devX=0, devY=1, devZ=0
            c, 0f, -s, 0f,
            -s, 0f, -c, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f,
        )
    }

    @Test
    fun `facing NE gives heading near pi div 4`() {
        val heading = headingFromMatrix(facingNEMatrix())
        abs(heading - (PI / 4).toFloat()) shouldBeLessThan 0.01f
    }

    // --- Pitched orientations ---

    /**
     * Phone facing North, pitched up 45° (camera looking 45° above horizon).
     * devZ = (0, -cos45, -sin45), devY = (0, -sin45, cos45), devX = (1, 0, 0)
     */
    private fun pitchedUp45Matrix(): FloatArray {
        val s = sin(PI / 4).toFloat()
        val c = cos(PI / 4).toFloat()
        return floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, -s, -c, 0f,
            0f, c, -s, 0f,
            0f, 0f, 0f, 1f,
        )
    }

    /**
     * Phone facing North, pitched down 30° (camera looking 30° below horizon).
     * devZ = (0, -cos30, sin30), devY = (0, sin30, cos30), devX = (1, 0, 0)
     */
    private fun pitchedDown30Matrix(): FloatArray {
        val s30 = sin(PI / 6).toFloat() // 0.5
        val c30 = cos(PI / 6).toFloat() // 0.866
        return floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, s30, -c30, 0f,
            0f, c30, s30, 0f,
            0f, 0f, 0f, 1f,
        )
    }

    @Test
    fun `pitched up 45 gives pitch near pi div 4`() {
        val pitch = pitchFromMatrix(pitchedUp45Matrix())
        abs(pitch - (PI / 4).toFloat()) shouldBeLessThan 0.01f
    }

    @Test
    fun `pitched up 45 heading stays North`() {
        val heading = headingFromMatrix(pitchedUp45Matrix())
        abs(heading) shouldBeLessThan 0.01f
    }

    @Test
    fun `pitched up 45 roll stays 0`() {
        val roll = rollFromMatrix(pitchedUp45Matrix())
        abs(roll) shouldBeLessThan 0.01f
    }

    @Test
    fun `pitched down 30 gives negative pitch`() {
        val pitch = pitchFromMatrix(pitchedDown30Matrix())
        abs(pitch - (-PI / 6).toFloat()) shouldBeLessThan 0.01f
    }

    @Test
    fun `pitched down 30 heading stays North`() {
        val heading = headingFromMatrix(pitchedDown30Matrix())
        abs(heading) shouldBeLessThan 0.01f
    }

    // --- Near-vertical degenerate case (epsilon guard) ---

    /** Look direction is straight up — heading is undefined, R[2] and R[6] are both ~0 */
    private fun lookingStraightUpMatrix() = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, -1f, 0f, 0f,
        0f, 0f, -1f, 0f,
        0f, 0f, 0f, 1f,
    )

    @Test
    fun `near-vertical returns last valid heading`() {
        // Prime with a known heading (East = pi/2)
        headingFromMatrixGuarded(facingEastMatrix())
        abs(lastValidHeading - (PI / 2).toFloat()) shouldBeLessThan 0.01f

        // Now feed a near-vertical matrix — should return the East heading, not atan2(0,0)
        val heading = headingFromMatrixGuarded(lookingStraightUpMatrix())
        abs(heading - (PI / 2).toFloat()) shouldBeLessThan 0.01f
    }

    @Test
    fun `near-vertical does not update lastValidHeading`() {
        headingFromMatrixGuarded(facingNorthMatrix())
        abs(lastValidHeading) shouldBeLessThan 0.01f

        headingFromMatrixGuarded(lookingStraightUpMatrix())
        // lastValidHeading should still be ~0 (North), not overwritten
        abs(lastValidHeading) shouldBeLessThan 0.01f
    }

    @Test
    fun `straight up pitch is pi div 2`() {
        val pitch = pitchFromMatrix(lookingStraightUpMatrix())
        abs(pitch - (PI / 2).toFloat()) shouldBeLessThan 0.01f
    }

    // --- VERTICAL_THRESHOLD semantics ---

    /** Build a facing-North matrix pitched up by [degrees]. */
    private fun pitchedMatrix(degrees: Double): FloatArray {
        val theta = Math.toRadians(degrees)
        val s = sin(theta).toFloat()
        val c = cos(theta).toFloat()
        return floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, -s, -c, 0f,
            0f, c, -s, 0f,
            0f, 0f, 0f, 1f,
        )
    }

    @Test
    fun `vertical threshold passes at 70 degrees pitch`() {
        val threshold = cos(Math.toRadians(15.0)).toFloat()
        val m = pitchedMatrix(70.0)
        // abs(R[10]) should be less than threshold → magnetometer updates allowed
        (abs(m[10]) < threshold).shouldBeTrue()
    }

    @Test
    fun `vertical threshold blocks at 80 degrees pitch`() {
        val threshold = cos(Math.toRadians(15.0)).toFloat()
        val m = pitchedMatrix(80.0)
        // abs(R[10]) should exceed threshold → magnetometer updates blocked
        (abs(m[10]) < threshold).shouldBeFalse()
    }

    @Test
    fun `vertical threshold boundary at 75 degrees pitch`() {
        val threshold = cos(Math.toRadians(15.0)).toFloat()
        val m = pitchedMatrix(75.0)
        // sin(75°) = cos(15°), so abs(R[10]) ≈ threshold (at boundary)
        abs(abs(m[10]) - threshold) shouldBeLessThan 0.001f
    }
}
