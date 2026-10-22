package eu.darken.apl.watch.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import eu.darken.apl.R
import eu.darken.apl.common.units.DistanceUnit
import eu.darken.apl.common.units.LocalUnitFormatter
import eu.darken.apl.common.units.text
import eu.darken.apl.watch.core.types.LocationWatch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The radii a location watch offers, in meters: both limits exactly and every whole [DistanceUnit] between them.
 * 2 to 25 km in miles gives 2000, 3218.7 (2 mi), 4828.0 (3 mi) … 24140.2 (15 mi), 25000.
 */
object RadiusStops {

    fun build(minMeters: Float, maxMeters: Float, unit: DistanceUnit): List<Float> {
        if (maxMeters < minMeters) return emptyList()
        if (maxMeters == minMeters) return listOf(minMeters)
        val first = ceil(minMeters / unit.meters).toInt()
        val last = floor(maxMeters / unit.meters).toInt()
        val interior = (first..last)
            .map { (it * unit.meters).toFloat() }
            .filter { it > minMeters + EPSILON_METERS && it < maxMeters - EPSILON_METERS }
        return listOf(minMeters) + interior + maxMeters
    }

    fun forLimit(maxRadiusKm: Int, unit: DistanceUnit): List<Float> =
        build(LocationWatch.MIN_RADIUS_KM * 1000f, maxRadiusKm * 1000f, unit)

    fun nearestIndex(stops: List<Float>, meters: Float): Int =
        stops.indices.minByOrNull { abs(stops[it] - meters) } ?: 0

    /** A radius picked before the limit dropped goes down to the new limit. */
    fun clamp(meters: Float, stops: List<Float>): Float? =
        if (stops.isEmpty()) null else meters.coerceIn(stops.first(), stops.last())

    /** What a new watch's editor shows: the [picked] radius, or the stop nearest the default before any pick. */
    fun shown(picked: Float?, stops: List<Float>): Float? =
        (picked ?: stops.getOrNull(nearestIndex(stops, LocationWatch.DEFAULT_RADIUS_METERS)))?.let { clamp(it, stops) }

    fun isWhole(meters: Float, unit: DistanceUnit): Boolean {
        val value = meters / unit.meters
        return abs(value - value.roundToInt()) * unit.meters < EPSILON_METERS
    }

    /** The stop a slider move lands on, null while it stays on the stop [currentMeters] shows at. */
    fun pickOnMove(stops: List<Float>, currentMeters: Float, position: Float): Float? {
        if (stops.isEmpty()) return null
        val index = position.roundToInt().coerceIn(stops.indices)
        return stops[index].takeIf { index != nearestIndex(stops, currentMeters) }
    }

    fun saveAllowed(meters: Float, storedMeters: Float?, maxRadiusKm: Int): Boolean =
        LocationWatch.radiusSaveAllowed(meters, storedMeters, maxRadiusKm)

    fun allows(meters: Float, maxRadiusKm: Int): Boolean = LocationWatch.radiusAllowed(meters, maxRadiusKm)

    private const val EPSILON_METERS = 0.5f
}

/** "25 km", or "≈15.5 mi" where the radius is no whole unit. */
@Composable
@ReadOnlyComposable
fun radiusText(meters: Float): String {
    val units = LocalUnitFormatter.current
    return if (RadiusStops.isWhole(meters, units.distanceUnit)) {
        units.distance(meters.toDouble(), fractionDigits = 0).text()
    } else {
        stringResource(R.string.units_approximately_x, units.distance(meters.toDouble(), fractionDigits = 1).text())
    }
}

/** Moves between [stops] by index, so every position is a radius [RadiusStops] offers. */
@Composable
fun LocationRadiusPicker(
    stops: List<Float>,
    radiusInMeters: Float,
    onRadiusChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val radius = radiusText(radiusInMeters)
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.watch_details_location_radius_x, radius),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (stops.size > 1) {
            Slider(
                value = RadiusStops.nearestIndex(stops, radiusInMeters).toFloat(),
                // A radius between stops shows at its nearest one, touching the thumb there keeps it
                onValueChange = { position -> RadiusStops.pickOnMove(stops, radiusInMeters, position)?.let(onRadiusChange) },
                valueRange = 0f..stops.lastIndex.toFloat(),
                steps = stops.size - 2,
                enabled = enabled,
                // Hundreds of stops would draw the track as dots
                colors = SliderDefaults.colors(
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { stateDescription = radius },
            )
        }
    }
}
