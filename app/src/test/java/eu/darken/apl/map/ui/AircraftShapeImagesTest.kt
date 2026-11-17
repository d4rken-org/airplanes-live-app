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
        val box = PixelBox(left = 2, top = 2, right = 8, bottom = 8)
        centredPlacement(centre = box, fit = box, width = 10, height = 10) shouldBe
            Placement(offsetX = 0, offsetY = 0, width = 10, height = 10)
    }

    @Test
    fun `an off-centre box that fits moves onto the centre`() {
        // 0..4 and 1..5 become 3..7 both ways, centred on 5
        val box = PixelBox(left = 0, top = 1, right = 4, bottom = 5)
        centredPlacement(centre = box, fit = box, width = 10, height = 10) shouldBe
            Placement(offsetX = 3, offsetY = 2, width = 10, height = 10)
    }

    @Test
    fun `a box wider than the image grows it on both sides`() {
        // 12 wide becomes 0..12 in a 12 wide image
        val wide = PixelBox(left = -3, top = 0, right = 9, bottom = 10)
        centredPlacement(centre = wide, fit = wide, width = 10, height = 10) shouldBe
            Placement(offsetX = 3, offsetY = 0, width = 12, height = 10)
        // 11 wide becomes 1..12, centre 6.5 against 6
        val odd = PixelBox(left = -2, top = 0, right = 9, bottom = 10)
        centredPlacement(centre = odd, fit = odd, width = 10, height = 10) shouldBe
            Placement(offsetX = 3, offsetY = 0, width = 12, height = 10)
    }

    @Test
    fun `faint pixels that would not fit after the move grow the image`() {
        // The opaque 0..4 alone moves to 3..7, its faint pixel at 7 would land at 10, outside;
        // grown to 12 the opaque box moves to 4..8, centred on 6, and the faint pixel to 11
        centredPlacement(
            centre = PixelBox(left = 0, top = 0, right = 4, bottom = 10),
            fit = PixelBox(left = 0, top = 0, right = 8, bottom = 10),
            width = 10,
            height = 10,
        ) shouldBe Placement(offsetX = 4, offsetY = 0, width = 12, height = 10)
    }

    @Test
    fun `faint pixels that fit after the move change nothing`() {
        centredPlacement(
            centre = PixelBox(left = 0, top = 1, right = 4, bottom = 5),
            fit = PixelBox(left = 0, top = 0, right = 6, bottom = 5),
            width = 10,
            height = 10,
        ) shouldBe Placement(offsetX = 3, offsetY = 2, width = 10, height = 10)
    }

    @Test
    fun `one opaque pixel`() {
        val pixels = IntArray(8 * 4)
        pixels[1 * 8 + 3] = OPAQUE
        val extent = opaqueExtent(pixels, width = 8, height = 4)
        extent.opaque shouldBe PixelBox(left = 3, top = 1, right = 4, bottom = 2)
        extent.drawn shouldBe extent.opaque
        // Its corner at 3, 1 is one pixel left of and above the centre at 4, 2
        extent.farthest shouldBe (sqrt(2f) plusOrMinus 1e-5f)
    }

    @Test
    fun `a filled centred square`() {
        val pixels = IntArray(8 * 8)
        for (y in 2 until 6) for (x in 2 until 6) pixels[y * 8 + x] = OPAQUE
        val extent = opaqueExtent(pixels, width = 8, height = 8)
        extent.opaque shouldBe PixelBox(left = 2, top = 2, right = 6, bottom = 6)
        extent.farthest shouldBe (sqrt(8f) plusOrMinus 1e-5f)
    }

    @Test
    fun `half alpha counts as opaque, less does not`() {
        val pixels = IntArray(4 * 4)
        pixels[0] = 0x7FFFFFFF
        pixels[2 * 4 + 2] = 0x80FFFFFF.toInt()
        opaqueExtent(pixels, width = 4, height = 4).opaque shouldBe PixelBox(left = 2, top = 2, right = 3, bottom = 3)
    }

    @Test
    fun `the drawn box covers every pixel with any alpha`() {
        val pixels = IntArray(6 * 5)
        pixels[1 * 6 + 2] = OPAQUE
        pixels[4 * 6 + 0] = 0x01FFFFFF
        pixels[0 * 6 + 4] = 0x7FFFFFFF
        // Colour without alpha is not drawn
        pixels[4 * 6 + 5] = 0x00FFFFFF
        val extent = opaqueExtent(pixels, width = 6, height = 5)
        extent.opaque shouldBe PixelBox(left = 2, top = 1, right = 3, bottom = 2)
        extent.drawn shouldBe PixelBox(left = 0, top = 0, right = 5, bottom = 5)
    }

    @Test
    fun `only faint pixels leave the opaque box empty`() {
        val pixels = IntArray(4 * 4)
        pixels[1 * 4 + 1] = 0x40FFFFFF
        val extent = opaqueExtent(pixels, width = 4, height = 4)
        extent shouldBe OpaqueExtent(PixelBox.EMPTY, PixelBox(left = 1, top = 1, right = 2, bottom = 2), 0f)
        extent.opaque.isEmpty.shouldBeTrue()
    }

    @Test
    fun `a transparent image is empty`() {
        val extent = opaqueExtent(IntArray(6 * 5), width = 6, height = 5)
        extent shouldBe OpaqueExtent(PixelBox.EMPTY, PixelBox.EMPTY, 0f)
        extent.opaque.isEmpty.shouldBeTrue()
    }

    companion object {
        private const val OPAQUE = 0xFFFFFFFF.toInt()
    }
}
