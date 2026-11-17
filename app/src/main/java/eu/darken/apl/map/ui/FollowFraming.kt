package eu.darken.apl.map.ui

import eu.darken.apl.map.core.MapViewport
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan

internal data class MapSpot(val latitude: Double, val longitude: Double)

/**
 * [target] moved by the step from [from] to [to] on the Web Mercator plane, on which a step on an untilted map's screen
 * is the same wherever it is taken. [zoomIn] levels deeper the same screen step is that much shorter.
 * E.g. a step a degree south at the equator moves 60°N about half a degree: (60, 10) becomes (59.496, 10).
 */
internal fun shiftedOnMap(target: MapSpot, from: MapSpot, to: MapSpot, zoomIn: Double = 0.0): MapSpot {
    val scale = 2.0.pow(-zoomIn)
    var dx = mercatorX(to.longitude) - mercatorX(from.longitude)
    // A step across the antimeridian is short, not most of the way around
    if (dx > 0.5) dx -= 1.0 else if (dx < -0.5) dx += 1.0
    val dy = mercatorY(to.latitude) - mercatorY(from.latitude)
    return MapSpot(
        latitude = latitudeOf(mercatorY(target.latitude) + dy * scale),
        longitude = MapViewport.normalizeLongitude((mercatorX(target.longitude) + dx * scale - 0.5) * 360.0),
    )
}

private fun mercatorX(longitude: Double): Double = longitude / 360.0 + 0.5

private fun mercatorY(latitude: Double): Double = 0.5 - ln(tan(PI / 4 + Math.toRadians(latitude) / 2)) / (2 * PI)

private fun latitudeOf(y: Double): Double = Math.toDegrees(atan(sinh((0.5 - y) * 2 * PI)))
