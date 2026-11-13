package eu.darken.apl.map.ui

import eu.darken.apl.airport.core.AirportTarget

internal sealed interface MapTapTarget {
    data class Aircraft(val hex: String) : MapTapTarget
    data class Airport(val target: AirportTarget) : MapTapTarget
    data object Map : MapTapTarget
}

/** [distance] in px from the finger to where the feature is anchored. */
internal data class TapCandidate<out T>(val value: T, val distance: Float)

/**
 * What a tap on the map opens, from what was found under the finger ([aircraftAtPoint], [airportLabelsAtPoint])
 * and within the slop box around it (the rest).
 *
 * Close up an airport's name label sits above the aircraft parked there, so a label under the finger beats aircraft
 * that are only near it. An aircraft under the finger still beats the label. Without a label under the finger this
 * is the nearest aircraft, then the selected airport, then the nearest airport.
 */
internal fun mapTapTarget(
    aircraftAtPoint: List<TapCandidate<String>>,
    airportLabelsAtPoint: List<TapCandidate<AirportTarget>>,
    aircraftNearby: List<TapCandidate<String>>,
    selectedAirportNearby: AirportTarget?,
    airportsNearby: List<TapCandidate<AirportTarget>>,
): MapTapTarget {
    // A neighbour's anchor can be nearer than that of the aircraft under the finger, the nearest one wins
    if (aircraftAtPoint.isNotEmpty()) {
        return MapTapTarget.Aircraft((aircraftAtPoint + aircraftNearby).minBy { it.distance }.value)
    }
    airportLabelsAtPoint.minByOrNull { it.distance }?.let { return MapTapTarget.Airport(it.value) }
    aircraftNearby.minByOrNull { it.distance }?.let { return MapTapTarget.Aircraft(it.value) }
    selectedAirportNearby?.let { return MapTapTarget.Airport(it) }
    airportsNearby.minByOrNull { it.distance }?.let { return MapTapTarget.Airport(it.value) }
    return MapTapTarget.Map
}
