package eu.darken.apl.common.units

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.util.Locale

class UnitFormatterTest : BaseTest() {

    private fun formatter(
        aircraft: AircraftUnits = AircraftUnits.AVIATION,
        distance: DistanceUnit = DistanceUnit.KILOMETERS,
        locale: Locale = Locale.US,
    ) = UnitFormatter(aircraft, distance, locale)

    @Nested
    inner class Altitude {

        @Test
        fun `aviation keeps feet with grouping`() {
            formatter().altitude(35_000) shouldBe Quantity("35,000", UnitSymbol.FEET)
        }

        @Test
        fun `metric rounds to 10 m`() {
            // 35,000 ft is 10,668 m
            formatter(AircraftUnits.METRIC).altitude(35_000) shouldBe Quantity("10,670", UnitSymbol.METERS)
        }

        @Test
        fun `imperial keeps feet`() {
            formatter(AircraftUnits.IMPERIAL).altitude(1_200).unit shouldBe UnitSymbol.FEET
        }

        @Test
        fun `grouping follows the locale`() {
            formatter(locale = Locale.GERMANY).altitude(35_000).number shouldBe "35.000"
        }

        @Test
        fun `zero is zero, not unknown`() {
            formatter(AircraftUnits.METRIC).altitude(0).number shouldBe "0"
        }
    }

    @Nested
    inner class AltitudeAtMost {

        @Test
        fun `metric rounds down, never above the bound`() {
            // 24,000 ft is 7,315.2 m, rounding to nearest 10 m would give 7,320 m
            formatter(AircraftUnits.METRIC).altitudeAtMost(24_000).number shouldBe "7,300"
        }

        @Test
        fun `the shown bound stays below the feet it came from for every thousand`() {
            val metric = formatter(AircraftUnits.METRIC)
            for (feet in 1_000..60_000 step 1_000) {
                val meters = metric.altitudeAtMost(feet).number.replace(",", "").toDouble()
                (meters <= feet * UnitFormatter.METERS_PER_FOOT) shouldBe true
            }
        }

        @Test
        fun `feet stay exact`() {
            formatter().altitudeAtMost(24_000) shouldBe Quantity("24,000", UnitSymbol.FEET)
        }
    }

    @Nested
    inner class Speed {

        @Test
        fun `knots round to whole`() {
            formatter().speed(450.6) shouldBe Quantity("451", UnitSymbol.KNOTS)
        }

        @Test
        fun `metric is km per hour`() {
            formatter(AircraftUnits.METRIC).speed(450.0) shouldBe Quantity("833", UnitSymbol.KILOMETERS_PER_HOUR)
        }

        @Test
        fun `imperial is mph`() {
            formatter(AircraftUnits.IMPERIAL).speed(450.0) shouldBe Quantity("518", UnitSymbol.MILES_PER_HOUR)
        }
    }

    @Nested
    inner class VerticalRate {

        @Test
        fun `climb has a plus sign`() {
            formatter().verticalRate(1_200) shouldBe Quantity("+1,200", UnitSymbol.FEET_PER_MINUTE)
        }

        @Test
        fun `descent keeps its minus sign`() {
            formatter().verticalRate(-640).number shouldBe "-640"
        }

        @Test
        fun `level has no sign`() {
            formatter().verticalRate(0).number shouldBe "0"
        }

        @Test
        fun `metric is meters per second with one decimal`() {
            // 1,200 ft/min is 6.096 m/s
            formatter(AircraftUnits.METRIC).verticalRate(1_200) shouldBe Quantity("+6.1", UnitSymbol.METERS_PER_SECOND)
        }

        @Test
        fun `a rate that rounds to zero shows no sign`() {
            // 5 ft/min is 0.025 m/s
            formatter(AircraftUnits.METRIC).verticalRate(-5).number shouldBe "0.0"
        }

        @Test
        fun `short form abbreviates thousands`() {
            formatter().verticalRateShort(-1_234) shouldBe Quantity("-1.2k", UnitSymbol.FEET_PER_MINUTE_SHORT)
            formatter().verticalRateShort(640) shouldBe Quantity("+640", UnitSymbol.FEET_PER_MINUTE_SHORT)
        }

        @Test
        fun `metric short form is the full form`() {
            formatter(AircraftUnits.METRIC).verticalRateShort(-1_234).unit shouldBe UnitSymbol.METERS_PER_SECOND
        }
    }

    @Nested
    inner class Temperature {

        @Test
        fun `aviation and metric show celsius`() {
            formatter().temperature(-54) shouldBe Quantity("-54", UnitSymbol.CELSIUS)
            formatter(AircraftUnits.METRIC).temperature(-54) shouldBe Quantity("-54", UnitSymbol.CELSIUS)
        }

        @Test
        fun `imperial shows fahrenheit`() {
            formatter(AircraftUnits.IMPERIAL).temperature(-54) shouldBe Quantity("-65", UnitSymbol.FAHRENHEIT)
        }
    }

    @Nested
    inner class Distance {

        @Test
        fun `one decimal below 10`() {
            formatter().distance(3_456.0) shouldBe Quantity("3.5", UnitSymbol.KILOMETERS)
        }

        @Test
        fun `whole numbers from 10`() {
            formatter().distance(12_345.0) shouldBe Quantity("12", UnitSymbol.KILOMETERS)
        }

        @Test
        fun `a whole value below 10 drops the decimal`() {
            formatter().distance(2_000.0).number shouldBe "2"
        }

        @Test
        fun `very close is less than 0_1`() {
            formatter().distance(40.0) shouldBe Quantity("0.1", UnitSymbol.KILOMETERS, lessThan = true)
        }

        @Test
        fun `nautical miles and miles`() {
            formatter(distance = DistanceUnit.NAUTICAL_MILES).distance(25_000.0) shouldBe Quantity("13", UnitSymbol.NAUTICAL_MILES)
            formatter(distance = DistanceUnit.MILES).distance(25_000.0) shouldBe Quantity("16", UnitSymbol.MILES)
        }

        @Test
        fun `fixed fraction digits keep a value from looking rounder`() {
            formatter(distance = DistanceUnit.MILES).distance(25_000.0, fractionDigits = 1).number shouldBe "15.5"
        }

        @Test
        fun `the decimal separator follows the locale`() {
            formatter(locale = Locale.GERMANY).distance(3_456.0).number shouldBe "3,5"
        }
    }
}
