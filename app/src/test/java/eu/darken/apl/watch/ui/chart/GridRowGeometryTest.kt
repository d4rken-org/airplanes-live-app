package eu.darken.apl.watch.ui.chart

import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class GridRowGeometryTest : BaseTest() {

    // 320 px canvas: cells of (320 - 23) / 24 = 12.375 px, one every 13.375 px
    private fun geometry(rtl: Boolean) = GridRowGeometry(
        rowWidth = 360f,
        labelWidth = 40f,
        gap = 1f,
        slots = 24,
        rtl = rtl,
    )

    @Test
    fun `cells follow the label in LTR`() {
        val geometry = geometry(rtl = false)

        geometry.cellCenter(0) shouldBe (46.1875f plusOrMinus 0.001f)
        geometry.cellCenter(12) shouldBe (206.6875f plusOrMinus 0.001f)
        geometry.cellCenter(23) shouldBe (353.8125f plusOrMinus 0.001f)
    }

    @Test
    fun `cells start at the left edge in RTL, hour 0 first`() {
        val geometry = geometry(rtl = true)

        geometry.cellCenter(0) shouldBe (6.1875f plusOrMinus 0.001f)
        geometry.cellCenter(12) shouldBe (166.6875f plusOrMinus 0.001f)
        geometry.cellCenter(23) shouldBe (313.8125f plusOrMinus 0.001f)
    }

    @Test
    fun `LTR taps on the label or past the row pick nothing`() {
        val geometry = geometry(rtl = false)

        geometry.slotAt(0f) shouldBe null
        geometry.slotAt(39.9f) shouldBe null
        geometry.slotAt(40f) shouldBe 0
        geometry.slotAt(359.9f) shouldBe 23
        geometry.slotAt(360f) shouldBe null
    }

    @Test
    fun `RTL taps on the label on the right pick nothing`() {
        val geometry = geometry(rtl = true)

        geometry.slotAt(-1f) shouldBe null
        geometry.slotAt(0f) shouldBe 0
        geometry.slotAt(319.9f) shouldBe 23
        geometry.slotAt(320f) shouldBe null
        geometry.slotAt(359f) shouldBe null
    }

    @Test
    fun `a tap in a gap picks the cell left of it`() {
        // Cell 0 ends at 52.375, cell 1 starts at 53.375
        geometry(rtl = false).slotAt(53f) shouldBe 0
        geometry(rtl = true).slotAt(13f) shouldBe 0
    }

    @Test
    fun `each cell's center picks that cell`() {
        listOf(false, true).forEach { rtl ->
            val geometry = geometry(rtl)
            (0 until 24).forEach { index -> geometry.slotAt(geometry.cellCenter(index)) shouldBe index }
        }
    }
}
