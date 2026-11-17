package eu.darken.apl.map.ui

import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import kotlin.math.sqrt

class AircraftShapeImagesTest : BaseTest() {

    @Test
    fun `a centred box stays where it is`() {
        centredPlacement(left = 2, top = 2, right = 8, bottom = 8, width = 10, height = 10) shouldBe
            Placement(offsetX = 0, offsetY = 0, width = 10, height = 10)
    }

    @Test
    fun `an off-centre box that fits moves onto the centre`() {
        // 0..4 and 1..5 become 3..7 both ways, centred on 5
        centredPlacement(left = 0, top = 1, right = 4, bottom = 5, width = 10, height = 10) shouldBe
            Placement(offsetX = 3, offsetY = 2, width = 10, height = 10)
    }

    @Test
    fun `a box wider than the image grows it on both sides`() {
        // 12 wide becomes 0..12 in a 12 wide image
        centredPlacement(left = -3, top = 0, right = 9, bottom = 10, width = 10, height = 10) shouldBe
            Placement(offsetX = 3, offsetY = 0, width = 12, height = 10)
        // 11 wide becomes 1..12, centre 6.5 against 6
        centredPlacement(left = -2, top = 0, right = 9, bottom = 10, width = 10, height = 10) shouldBe
            Placement(offsetX = 3, offsetY = 0, width = 12, height = 10)
    }

    @Test
    fun `one opaque pixel`() {
        val pixels = IntArray(8 * 4)
        pixels[1 * 8 + 3] = OPAQUE
        val extent = opaqueExtent(pixels, width = 8, height = 4)
        extent.copy(farthest = 0f) shouldBe OpaqueExtent(left = 3, top = 1, right = 4, bottom = 2, farthest = 0f)
        // Its corner at 3, 1 is one pixel left of and above the centre at 4, 2
        extent.farthest shouldBe (sqrt(2f) plusOrMinus 1e-5f)
    }

    @Test
    fun `a filled centred square`() {
        val pixels = IntArray(8 * 8)
        for (y in 2 until 6) for (x in 2 until 6) pixels[y * 8 + x] = OPAQUE
        val extent = opaqueExtent(pixels, width = 8, height = 8)
        extent.copy(farthest = 0f) shouldBe OpaqueExtent(left = 2, top = 2, right = 6, bottom = 6, farthest = 0f)
        extent.farthest shouldBe (sqrt(8f) plusOrMinus 1e-5f)
    }

    @Test
    fun `half alpha counts as opaque, less does not`() {
        val pixels = IntArray(4 * 4)
        pixels[0] = 0x7FFFFFFF
        pixels[2 * 4 + 2] = 0x80FFFFFF.toInt()
        opaqueExtent(pixels, width = 4, height = 4).copy(farthest = 0f) shouldBe
            OpaqueExtent(left = 2, top = 2, right = 3, bottom = 3, farthest = 0f)
    }

    @Test
    fun `a transparent image is empty`() {
        val extent = opaqueExtent(IntArray(6 * 5), width = 6, height = 5)
        extent shouldBe OpaqueExtent(0, 0, 0, 0, 0f)
        extent.isEmpty.shouldBeTrue()
    }

    companion object {
        private const val OPAQUE = 0xFFFFFFFF.toInt()
    }
}
