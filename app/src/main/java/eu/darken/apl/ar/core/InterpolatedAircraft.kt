package eu.darken.apl.ar.core

import eu.darken.apl.main.core.aircraft.Aircraft

data class InterpolatedAircraft(
    val source: Aircraft,
    val interpolatedLat: Double,
    val interpolatedLon: Double,
    val altitudeFt: Int?,
    val distanceM: Double,
    val ageSec: Float,
)
