package eu.darken.apl.watch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import eu.darken.apl.R
import eu.darken.apl.common.units.LocalUnitFormatter
import eu.darken.apl.common.units.Quantity
import eu.darken.apl.common.units.text

/** The user's aircraft altitude unit. */
@Composable
@ReadOnlyComposable
fun altitudeBandUnit(): AltitudeBandUnit = AltitudeBandUnit.of(LocalUnitFormatter.current.aircraftUnits)

/** "1,000–10,000 ft", "above 1,000 ft" or "below 10,000 ft"; null without a band. */
@Composable
@ReadOnlyComposable
fun altitudeBandText(minFt: Int?, maxFt: Int?): String? {
    if (minFt == null && maxFt == null) return null
    val units = LocalUnitFormatter.current
    val unit = altitudeBandUnit()
    val minNumber = minFt?.let { units.number(unit.fromFeet(it)) }
    val maxNumber = maxFt?.let { units.number(unit.fromFeet(it)) }
    return when {
        minNumber != null && maxNumber != null -> stringResource(
            R.string.watch_location_altitude_band_range_x_y,
            minNumber,
            Quantity(maxNumber, unit.symbol).text(),
        )

        minNumber != null -> stringResource(R.string.watch_location_altitude_band_above_x, Quantity(minNumber, unit.symbol).text())
        else -> stringResource(R.string.watch_location_altitude_band_below_x, Quantity(maxNumber!!, unit.symbol).text())
    }
}

/** Min and max altitude of a location watch, blank for no limit. */
@Composable
fun LocationAltitudeBandFields(
    input: AltitudeBandInput,
    onInputChange: (AltitudeBandInput) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AltitudeBoundField(
                label = stringResource(R.string.watch_location_altitude_min_label),
                text = input.min.text,
                error = input.minError?.let { altitudeBoundErrorText(it, input.unit, isMin = true) },
                unit = input.unit,
                enabled = enabled,
                onTextChange = { onInputChange(input.withMinText(it)) },
                modifier = Modifier.weight(1f),
            )
            AltitudeBoundField(
                label = stringResource(R.string.watch_location_altitude_max_label),
                text = input.max.text,
                error = input.maxError?.let { altitudeBoundErrorText(it, input.unit, isMin = false) },
                unit = input.unit,
                enabled = enabled,
                onTextChange = { onInputChange(input.withMaxText(it)) },
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = stringResource(R.string.watch_location_altitude_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AltitudeBoundField(
    label: String,
    text: String,
    error: String?,
    unit: AltitudeBandUnit,
    enabled: Boolean,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        label = { Text(label) },
        suffix = { Text(stringResource(unit.symbolRes)) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        enabled = enabled,
        singleLine = true,
        modifier = modifier,
    )
}

@Composable
@ReadOnlyComposable
private fun altitudeBoundErrorText(error: AltitudeBoundError, unit: AltitudeBandUnit, isMin: Boolean): String = when (error) {
    AltitudeBoundError.NOT_WHOLE_NUMBER -> stringResource(R.string.watch_location_altitude_error_not_whole)
    AltitudeBoundError.NEGATIVE -> stringResource(R.string.watch_location_altitude_error_negative)
    AltitudeBoundError.TOO_HIGH -> stringResource(
        R.string.watch_location_altitude_error_too_high_x,
        Quantity(LocalUnitFormatter.current.number(unit.max), unit.symbol).text(),
    )

    AltitudeBoundError.MIN_ABOVE_MAX -> stringResource(
        if (isMin) R.string.watch_location_altitude_error_above_max else R.string.watch_location_altitude_error_below_min
    )
}

private val AltitudeBandUnit.symbolRes: Int
    get() = when (this) {
        AltitudeBandUnit.FEET -> R.string.units_feet_symbol
        AltitudeBandUnit.METERS -> R.string.units_meters_symbol
    }

/** For `rememberSaveable`, the editor's bounds survive recreation with their edit state. */
val AltitudeBandInputSaver: Saver<AltitudeBandInput, Any> = listSaver(
    save = { input ->
        listOf(
            input.unit.name,
            input.min.storedFt, input.min.text, input.min.edit.name,
            input.max.storedFt, input.max.text, input.max.edit.name,
        )
    },
    restore = { saved ->
        AltitudeBandInput(
            unit = AltitudeBandUnit.valueOf(saved[0] as String),
            min = AltitudeBound(saved[1] as Int?, saved[2] as String, AltitudeBound.Edit.valueOf(saved[3] as String)),
            max = AltitudeBound(saved[4] as Int?, saved[5] as String, AltitudeBound.Edit.valueOf(saved[6] as String)),
        )
    },
)
