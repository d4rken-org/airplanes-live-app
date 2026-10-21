package eu.darken.apl.map.ui

import android.graphics.Color
import kotlin.math.roundToInt

/** Stands in for the altitude of aircraft on the ground or without one, which are grey. */
internal const val GROUND_ALTITUDE = -1_000

/** The colour the map draws an aircraft at [altitudeFt] with, for drawing outside the map. */
internal fun altitudeColor(altitudeFt: Int): Int {
    val (firstAltitude, firstColor) = ALTITUDE_COLORS.first()
    if (altitudeFt <= firstAltitude) return firstColor
    ALTITUDE_COLORS.zipWithNext().forEach { (from, to) ->
        if (altitudeFt <= to.first) {
            val fraction = (altitudeFt - from.first).toFloat() / (to.first - from.first)
            fun channel(shift: Int): Int {
                val a = (from.second shr shift) and 0xFF
                val b = (to.second shr shift) and 0xFF
                return (a + (b - a) * fraction).roundToInt() shl shift
            }
            return channel(24) or channel(16) or channel(8) or channel(0)
        }
    }
    return ALTITUDE_COLORS.last().second
}

internal val ALTITUDE_COLORS = listOf(
    GROUND_ALTITUDE to Color.parseColor("#9E9E9E"),
    0 to Color.parseColor("#FF6D00"),
    5_000 to Color.parseColor("#FFC400"),
    10_000 to Color.parseColor("#64DD17"),
    20_000 to Color.parseColor("#00B8D4"),
    30_000 to Color.parseColor("#2962FF"),
    40_000 to Color.parseColor("#D500F9"),
)
