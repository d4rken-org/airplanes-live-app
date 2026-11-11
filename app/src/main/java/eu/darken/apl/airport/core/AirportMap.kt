package eu.darken.apl.airport.core

import eu.darken.apl.map.core.MapOptions

/** In tar1090 zoom, the unit [MapOptions.Camera.zoom] uses. Provisional, to be tuned on a device. */
internal object AirportMapZoom {
    const val LARGE = 11.0
    const val MEDIUM = 12.0
    const val SMALL = 13.0
    const val OTHER = 12.0
}

/** Null when the position of the airport is unknown. */
fun AirportTarget.toMapOptions(): MapOptions? {
    val lat = latitude ?: return null
    val lon = longitude ?: return null
    return MapOptions(
        camera = MapOptions.Camera(
            lat = lat,
            lon = lon,
            zoom = when (size) {
                Airport.Size.LARGE -> AirportMapZoom.LARGE
                Airport.Size.MEDIUM -> AirportMapZoom.MEDIUM
                Airport.Size.SMALL -> AirportMapZoom.SMALL
                Airport.Size.OTHER -> AirportMapZoom.OTHER
            },
        ),
        airport = this,
    )
}
