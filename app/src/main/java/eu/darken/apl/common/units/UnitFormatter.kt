package eu.darken.apl.common.units

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

enum class UnitSymbol {
    FEET,
    METERS,
    KNOTS,
    KILOMETERS_PER_HOUR,
    MILES_PER_HOUR,
    FEET_PER_MINUTE,
    FEET_PER_MINUTE_SHORT,
    METERS_PER_SECOND,
    CELSIUS,
    FAHRENHEIT,
    KILOMETERS,
    NAUTICAL_MILES,
    MILES,
}

/** A value rounded and formatted for display, without its unit. */
data class Quantity(
    val number: String,
    val unit: UnitSymbol,
    /** The real value is below [number]. */
    val lessThan: Boolean = false,
)

/** Takes the app's canonical units (feet, knots, ft/min, °C, meters) and converts them for display. */
class UnitFormatter(
    val aircraftUnits: AircraftUnits,
    val distanceUnit: DistanceUnit,
    private val locale: Locale,
) {

    fun altitude(feet: Int): Quantity = when (aircraftUnits) {
        AircraftUnits.METRIC -> Quantity(format(roundTo(feet * METERS_PER_FOOT, 10.0)), UnitSymbol.METERS)
        AircraftUnits.AVIATION, AircraftUnits.IMPERIAL -> Quantity(format(feet.toDouble()), UnitSymbol.FEET)
    }

    /** For a bound that has to stay at or below [feet]: 24,000 ft is 7,315.2 m and shows as 7,300 m. */
    fun altitudeAtMost(feet: Int): Quantity = when (aircraftUnits) {
        AircraftUnits.METRIC -> Quantity(format(floor(feet * METERS_PER_FOOT / 100.0) * 100.0), UnitSymbol.METERS)
        AircraftUnits.AVIATION, AircraftUnits.IMPERIAL -> Quantity(format(feet.toDouble()), UnitSymbol.FEET)
    }

    fun speed(knots: Double): Quantity = when (aircraftUnits) {
        AircraftUnits.AVIATION -> Quantity(format(knots), UnitSymbol.KNOTS)
        AircraftUnits.METRIC -> Quantity(format(knots * KMH_PER_KNOT), UnitSymbol.KILOMETERS_PER_HOUR)
        AircraftUnits.IMPERIAL -> Quantity(format(knots * MPH_PER_KNOT), UnitSymbol.MILES_PER_HOUR)
    }

    fun verticalRate(feetPerMinute: Int): Quantity = when (aircraftUnits) {
        AircraftUnits.METRIC -> Quantity(
            format(feetPerMinute * METERS_PER_SECOND_PER_FPM, fractionDigits = 1, signed = true),
            UnitSymbol.METERS_PER_SECOND,
        )

        AircraftUnits.AVIATION, AircraftUnits.IMPERIAL -> Quantity(
            format(feetPerMinute.toDouble(), signed = true),
            UnitSymbol.FEET_PER_MINUTE,
        )
    }

    /** -1,234 ft/min becomes -1.2k fpm, 640 stays +640 fpm. */
    fun verticalRateShort(feetPerMinute: Int): Quantity = when (aircraftUnits) {
        AircraftUnits.METRIC -> verticalRate(feetPerMinute)
        AircraftUnits.AVIATION, AircraftUnits.IMPERIAL -> {
            val number = if (abs(feetPerMinute) < 1000) {
                format(feetPerMinute.toDouble(), signed = true)
            } else {
                format(feetPerMinute / 1000.0, fractionDigits = 1, signed = true) + "k"
            }
            Quantity(number, UnitSymbol.FEET_PER_MINUTE_SHORT)
        }
    }

    fun temperature(celsius: Int): Quantity = when (aircraftUnits) {
        AircraftUnits.IMPERIAL -> Quantity(format(celsius * 9.0 / 5.0 + 32.0), UnitSymbol.FAHRENHEIT)
        AircraftUnits.AVIATION, AircraftUnits.METRIC -> Quantity(format(celsius.toDouble()), UnitSymbol.CELSIUS)
    }

    /** One decimal below 10, whole numbers above, and "< 0.1" for anything closer. */
    fun distance(meters: Double): Quantity {
        val value = meters / distanceUnit.meters
        return when {
            value < 0.1 -> Quantity(format(0.1, fractionDigits = 1), distanceSymbol, lessThan = true)
            value < 10 -> Quantity(format(value, fractionDigits = 1, minFractionDigits = 0), distanceSymbol)
            else -> Quantity(format(value), distanceSymbol)
        }
    }

    /** With [fractionDigits] regardless of size, for values that must not look rounder than they are. */
    fun distance(meters: Double, fractionDigits: Int): Quantity = Quantity(
        format(meters / distanceUnit.meters, fractionDigits = fractionDigits, minFractionDigits = 0),
        distanceSymbol,
    )

    val distanceSymbol: UnitSymbol
        get() = when (distanceUnit) {
            DistanceUnit.KILOMETERS -> UnitSymbol.KILOMETERS
            DistanceUnit.NAUTICAL_MILES -> UnitSymbol.NAUTICAL_MILES
            DistanceUnit.MILES -> UnitSymbol.MILES
        }

    private fun format(
        value: Double,
        fractionDigits: Int = 0,
        minFractionDigits: Int = fractionDigits,
        signed: Boolean = false,
    ): String {
        val scale = Math.pow(10.0, fractionDigits.toDouble())
        // -0.04 must not show as "-0.0"
        val rounded = (value * scale).roundToLong().let { if (it == 0L) 0.0 else it / scale }
        val text = NumberFormat.getNumberInstance(locale).apply {
            maximumFractionDigits = fractionDigits
            minimumFractionDigits = minFractionDigits
        }.format(rounded)
        return if (signed && rounded > 0) "+$text" else text
    }

    private fun roundTo(value: Double, step: Double): Double = (value / step).roundToLong() * step

    companion object {
        const val METERS_PER_FOOT = 0.3048
        const val KMH_PER_KNOT = 1.852
        const val MPH_PER_KNOT = 1.852 / 1.609344
        const val METERS_PER_SECOND_PER_FPM = METERS_PER_FOOT / 60.0
    }
}
