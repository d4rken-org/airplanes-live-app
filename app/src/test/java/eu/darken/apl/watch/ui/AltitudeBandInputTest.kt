package eu.darken.apl.watch.ui

import eu.darken.apl.common.units.AircraftUnits
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class AltitudeBandInputTest : BaseTest() {

    private val feet = AltitudeBandInput.empty(AltitudeBandUnit.FEET)
    private val meters = AltitudeBandInput.empty(AltitudeBandUnit.METERS)

    @Test
    fun `metric units edit in meters, the others in feet`() {
        AltitudeBandUnit.of(AircraftUnits.METRIC) shouldBe AltitudeBandUnit.METERS
        AltitudeBandUnit.of(AircraftUnits.AVIATION) shouldBe AltitudeBandUnit.FEET
        AltitudeBandUnit.of(AircraftUnits.IMPERIAL) shouldBe AltitudeBandUnit.FEET
    }

    @Test
    fun `feet are taken as typed`() {
        feet.withMinText("1000").withMaxText("10000").band shouldBe AltitudeBand(1_000, 10_000)
    }

    @Test
    fun `meters convert to the nearest foot and show back the same`() {
        val input = meters.withMinText("1505")

        input.band shouldBe AltitudeBand(4_938, null)
        AltitudeBandInput.of(AltitudeBand(4_938, null), AltitudeBandUnit.METERS).min.text shouldBe "1505"
    }

    @Test
    fun `blank fields are open bounds`() {
        feet.band shouldBe AltitudeBand(null, null)
        feet.withMinText("  ").band shouldBe AltitudeBand(null, null)
        feet.withMinText("1000").withMinText("").band shouldBe AltitudeBand(null, null)
    }

    @Test
    fun `text that is no whole number is an error`() {
        listOf("abc", "1.5", "1,000", "10 000").forEach { text ->
            val input = feet.withMinText(text)
            input.minError shouldBe AltitudeBoundError.NOT_WHOLE_NUMBER
            input.band shouldBe null
        }
    }

    @Test
    fun `negative altitudes are an error`() {
        val input = feet.withMaxText("-5")

        input.maxError shouldBe AltitudeBoundError.NEGATIVE
        input.isValid shouldBe false
        feet.withMinText("0").band shouldBe AltitudeBand(0, null)
    }

    @Test
    fun `altitudes above the limit are an error`() {
        feet.withMaxText("60000").band shouldBe AltitudeBand(null, 60_000)
        feet.withMaxText("60001").maxError shouldBe AltitudeBoundError.TOO_HIGH
        feet.withMaxText("99999999999").maxError shouldBe AltitudeBoundError.TOO_HIGH

        meters.withMaxText("18288").band shouldBe AltitudeBand(null, 60_000)
        meters.withMaxText("18289").maxError shouldBe AltitudeBoundError.TOO_HIGH
    }

    @Test
    fun `a min above the max is an error at the max`() {
        val input = feet.withMinText("10000").withMaxText("1000")

        input.minError shouldBe null
        input.maxError shouldBe AltitudeBoundError.MIN_ABOVE_MAX
        input.band shouldBe null
        feet.withMinText("5000").withMaxText("5000").band shouldBe AltitudeBand(5_000, 5_000)
    }

    @Test
    fun `a min raised above a stored max is an error at the min`() {
        val input = AltitudeBandInput.of(AltitudeBand(1_000, 10_000), AltitudeBandUnit.FEET).withMinText("20000")

        input.minError shouldBe AltitudeBoundError.MIN_ABOVE_MAX
        input.maxError shouldBe null
        input.band shouldBe null
    }

    @Test
    fun `an untouched bound saves its stored feet, not its meters converted back`() {
        val input = AltitudeBandInput.of(AltitudeBand(1_000, null), AltitudeBandUnit.METERS)

        input.min.text shouldBe "305"
        input.band shouldBe AltitudeBand(1_000, null)
        input.isChanged shouldBe false
        // Typed back to what it showed, still the stored value
        input.withMinText("30").withMinText("305").band shouldBe AltitudeBand(1_000, null)
        // 305 m alone is 1,001 ft
        meters.withMinText("305").band shouldBe AltitudeBand(1_001, null)
    }

    @Test
    fun `an untouched bound near the limit stays valid and exact`() {
        val input = AltitudeBandInput.of(AltitudeBand(null, 59_999), AltitudeBandUnit.METERS)

        input.max.text shouldBe "18288"
        input.band shouldBe AltitudeBand(null, 59_999)
    }

    @Test
    fun `an edited bound counts as a change`() {
        val stored = AltitudeBandInput.of(AltitudeBand(1_000, 10_000), AltitudeBandUnit.METERS)

        val edited = stored.withMaxText("3100")

        edited.max.edit shouldBe AltitudeBound.Edit.EDITED
        edited.band shouldBe AltitudeBand(1_000, 10_171)
        edited.isChanged shouldBe true
    }

    @Test
    fun `a cleared bound is open`() {
        val input = AltitudeBandInput.of(AltitudeBand(1_000, 10_000), AltitudeBandUnit.FEET).withMinText("")

        input.min.edit shouldBe AltitudeBound.Edit.CLEARED
        input.band shouldBe AltitudeBand(null, 10_000)
        input.isChanged shouldBe true
    }
}
