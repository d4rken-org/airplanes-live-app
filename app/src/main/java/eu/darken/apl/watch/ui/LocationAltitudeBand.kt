package eu.darken.apl.watch.ui

import eu.darken.apl.common.units.AircraftUnits
import eu.darken.apl.common.units.UnitFormatter
import eu.darken.apl.common.units.UnitSymbol
import eu.darken.apl.watch.core.types.LocationWatch
import kotlin.math.roundToInt

/** A location watch's altitude band in feet, null bounds are open. */
data class AltitudeBand(val minFt: Int?, val maxFt: Int?)

/** Whole units that altitude band bounds are shown and typed in. */
enum class AltitudeBandUnit(val symbol: UnitSymbol) {
    FEET(UnitSymbol.FEET),

    /** 1,000 ft shows as 305 m, 305 m typed back is 1,001 ft. */
    METERS(UnitSymbol.METERS),
    ;

    fun fromFeet(feet: Int): Int = when (this) {
        FEET -> feet
        METERS -> (feet * UnitFormatter.METERS_PER_FOOT).roundToInt()
    }

    fun toFeet(value: Int): Int = when (this) {
        FEET -> value
        METERS -> (value / UnitFormatter.METERS_PER_FOOT).roundToInt()
    }

    /** 60,000 ft, or 18,288 m. */
    val max: Int
        get() = fromFeet(LocationWatch.MAX_ALTITUDE_FT)

    companion object {
        fun of(units: AircraftUnits): AltitudeBandUnit = when (units) {
            AircraftUnits.METRIC -> METERS
            AircraftUnits.AVIATION, AircraftUnits.IMPERIAL -> FEET
        }
    }
}

enum class AltitudeBoundError {
    NOT_WHOLE_NUMBER,
    NEGATIVE,
    TOO_HIGH,

    /** Min and max are each fine, but the min is above the max. */
    MIN_ABOVE_MAX,
}

/** One bound being edited, [text] is in the input's [AltitudeBandUnit]. */
data class AltitudeBound(
    val storedFt: Int?,
    val text: String,
    val edit: Edit = Edit.UNTOUCHED,
) {
    enum class Edit {
        /** Shows the stored value, which saves as [storedFt] without a round trip through [text]. */
        UNTOUCHED,
        EDITED,
        CLEARED,
    }
}

/**
 * The min and max fields of a location watch editor. Untouched bounds keep their stored feet exactly, so
 * saving a band shown in meters does not change it.
 */
data class AltitudeBandInput(
    val unit: AltitudeBandUnit,
    val min: AltitudeBound,
    val max: AltitudeBound,
) {

    fun withMinText(text: String): AltitudeBandInput = copy(min = min.withText(text))

    fun withMaxText(text: String): AltitudeBandInput = copy(max = max.withText(text))

    private val minParsed: Parsed = parse(min)
    private val maxParsed: Parsed = parse(max)

    private val minAboveMax: Boolean =
        minParsed is Parsed.Value && maxParsed is Parsed.Value &&
                minParsed.feet != null && maxParsed.feet != null && minParsed.feet > maxParsed.feet

    val minError: AltitudeBoundError?
        get() = (minParsed as? Parsed.Error)?.error
            ?: AltitudeBoundError.MIN_ABOVE_MAX.takeIf { minAboveMax && minAboveMaxShownAtMin }

    val maxError: AltitudeBoundError?
        get() = (maxParsed as? Parsed.Error)?.error
            ?: AltitudeBoundError.MIN_ABOVE_MAX.takeIf { minAboveMax && !minAboveMaxShownAtMin }

    /** At the bound that was typed: the max, unless only the min changed. */
    private val minAboveMaxShownAtMin: Boolean
        get() = max.edit == AltitudeBound.Edit.UNTOUCHED && min.edit != AltitudeBound.Edit.UNTOUCHED

    /** Null while a field has an error. */
    val band: AltitudeBand?
        get() {
            if (minParsed !is Parsed.Value || maxParsed !is Parsed.Value || minAboveMax) return null
            return AltitudeBand(minParsed.feet, maxParsed.feet)
                .takeIf { LocationWatch.altitudeBandAllowed(it.minFt, it.maxFt) }
        }

    val isValid: Boolean
        get() = band != null

    val stored: AltitudeBand
        get() = AltitudeBand(min.storedFt, max.storedFt)

    /** Valid and not what is stored, saving it starts the watch's history over. */
    val isChanged: Boolean
        get() = band.let { it != null && it != stored }

    private fun AltitudeBound.withText(text: String): AltitudeBound = copy(
        text = text,
        edit = when {
            text == fieldText(storedFt, unit) -> AltitudeBound.Edit.UNTOUCHED
            text.isBlank() -> AltitudeBound.Edit.CLEARED
            else -> AltitudeBound.Edit.EDITED
        },
    )

    private fun parse(bound: AltitudeBound): Parsed = when (bound.edit) {
        AltitudeBound.Edit.UNTOUCHED -> Parsed.Value(bound.storedFt)
        AltitudeBound.Edit.CLEARED -> Parsed.Value(null)
        AltitudeBound.Edit.EDITED -> {
            val text = bound.text.trim()
            when {
                text.isEmpty() -> Parsed.Value(null)
                !WHOLE_NUMBER.matches(text) -> Parsed.Error(AltitudeBoundError.NOT_WHOLE_NUMBER)
                text.startsWith('-') && text.any { it in '1'..'9' } -> Parsed.Error(AltitudeBoundError.NEGATIVE)
                else -> {
                    // Too many digits for an Int is too high as well
                    val value = text.removePrefix("-").toIntOrNull()
                    if (value == null || value > unit.max) {
                        Parsed.Error(AltitudeBoundError.TOO_HIGH)
                    } else {
                        Parsed.Value(unit.toFeet(value))
                    }
                }
            }
        }
    }

    private sealed interface Parsed {
        data class Value(val feet: Int?) : Parsed
        data class Error(val error: AltitudeBoundError) : Parsed
    }

    companion object {
        private val WHOLE_NUMBER = Regex("-?\\d+")

        /** Prefilled with a stored band, every bound untouched. */
        fun of(band: AltitudeBand, unit: AltitudeBandUnit): AltitudeBandInput = AltitudeBandInput(
            unit = unit,
            min = AltitudeBound(band.minFt, fieldText(band.minFt, unit)),
            max = AltitudeBound(band.maxFt, fieldText(band.maxFt, unit)),
        )

        /** Plain digits, no grouping, so the text parses back. */
        private fun fieldText(feet: Int?, unit: AltitudeBandUnit): String = feet?.let { unit.fromFeet(it).toString() } ?: ""

        fun empty(unit: AltitudeBandUnit): AltitudeBandInput = of(AltitudeBand(null, null), unit)
    }
}
