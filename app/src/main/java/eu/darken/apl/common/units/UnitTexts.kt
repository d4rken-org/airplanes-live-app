package eu.darken.apl.common.units

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import eu.darken.apl.R
import java.util.Locale

/** The default serves previews and tests. */
val LocalUnitFormatter = staticCompositionLocalOf {
    UnitFormatter(AircraftUnits.AVIATION, DistanceUnit.KILOMETERS, Locale.getDefault())
}

@get:StringRes
private val UnitSymbol.formatRes: Int
    get() = when (this) {
        UnitSymbol.FEET -> R.string.units_feet_x
        UnitSymbol.METERS -> R.string.units_meters_x
        UnitSymbol.KNOTS -> R.string.units_knots_x
        UnitSymbol.KILOMETERS_PER_HOUR -> R.string.units_kilometers_per_hour_x
        UnitSymbol.MILES_PER_HOUR -> R.string.units_miles_per_hour_x
        UnitSymbol.FEET_PER_MINUTE -> R.string.units_feet_per_minute_x
        UnitSymbol.FEET_PER_MINUTE_SHORT -> R.string.units_feet_per_minute_short_x
        UnitSymbol.METERS_PER_SECOND -> R.string.units_meters_per_second_x
        UnitSymbol.CELSIUS -> R.string.units_celsius_x
        UnitSymbol.FAHRENHEIT -> R.string.units_fahrenheit_x
        UnitSymbol.KILOMETERS -> R.string.units_kilometers_x
        UnitSymbol.NAUTICAL_MILES -> R.string.units_nautical_miles_x
        UnitSymbol.MILES -> R.string.units_miles_x
        UnitSymbol.DEGREES -> R.string.units_degrees_x
    }

@get:StringRes
val DistanceUnit.symbolRes: Int
    get() = when (this) {
        DistanceUnit.KILOMETERS -> R.string.units_kilometers_symbol
        DistanceUnit.NAUTICAL_MILES -> R.string.units_nautical_miles_symbol
        DistanceUnit.MILES -> R.string.units_miles_symbol
    }

@Composable
@ReadOnlyComposable
fun Quantity.text(): String {
    val numberText = if (thousands) stringResource(R.string.units_thousands_x, number) else number
    val text = stringResource(unit.formatRes, numberText)
    return if (lessThan) stringResource(R.string.units_less_than_x, text) else text
}

@Composable
@ReadOnlyComposable
fun altitudeText(feet: Int?, onGround: Boolean?): String? = when {
    onGround == true -> stringResource(R.string.units_altitude_ground_label)
    feet != null -> LocalUnitFormatter.current.altitude(feet).text()
    else -> null
}

@Composable
@ReadOnlyComposable
fun speedText(knots: Number?): String? = knots?.let { LocalUnitFormatter.current.speed(it.toDouble()).text() }

@Composable
@ReadOnlyComposable
fun distanceText(meters: Number?): String? = meters?.let { LocalUnitFormatter.current.distance(it.toDouble()).text() }
