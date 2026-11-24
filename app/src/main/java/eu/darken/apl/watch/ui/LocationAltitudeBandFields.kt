package eu.darken.apl.watch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
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

/** [altitudeBandText] as a value on its own, "Above 1,000 ft"; null without a band. */
@Composable
@ReadOnlyComposable
fun altitudeBandValueText(minFt: Int?, maxFt: Int?): String? {
    val locale = LocalConfiguration.current.locales[0]
    return altitudeBandText(minFt, maxFt)?.replaceFirstChar { it.titlecase(locale) }
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
        val band = input.band
        LocationValueRow(
            label = stringResource(R.string.common_altitude_label),
            value = band?.let {
                altitudeBandValueText(it.minFt, it.maxFt) ?: stringResource(R.string.watch_location_altitude_any)
            },
            modifier = Modifier.padding(bottom = 4.dp),
        )
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
    val state = rememberTextFieldState(text)
    val currentOnTextChange by rememberUpdatedState(onTextChange)
    // The text both sides last agreed on: an input text equal to it is the echo of a reported edit, not a reset
    val synced = remember { SyncedText(text) }
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.collect {
            if (it != synced.value) {
                synced.value = it
                currentOnTextChange(it)
            }
        }
    }
    LaunchedEffect(text) {
        if (text != synced.value) {
            synced.value = text
            state.setTextAndPlaceCursorAtEnd(text)
        }
    }
    OutlinedTextField(
        state = state,
        // Keeps the label up so an empty field shows its "No limit" placeholder, focused or not
        labelPosition = TextFieldLabelPosition.Attached(alwaysMinimize = true),
        label = { Text(label) },
        placeholder = { Text(stringResource(R.string.watch_location_altitude_no_limit)) },
        suffix = { Text(stringResource(unit.symbolRes)) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        lineLimits = TextFieldLineLimits.SingleLine,
        enabled = enabled,
        modifier = modifier,
    )
}

private class SyncedText(var value: String)

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
