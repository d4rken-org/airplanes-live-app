package eu.darken.apl.airport.core

import eu.darken.apl.map.core.MapAirport
import eu.darken.apl.map.core.MapOptions

/** In tar1090 zoom, the unit [MapOptions.Camera.zoom] uses. Provisional, to be tuned on a device. */
internal object AirportMapZoom {
    const val LARGE = 11.0
    const val MEDIUM = 12.0
    const val SMALL = 13.0
    const val OTHER = 12.0
}

/** The chip shows the same code whether the airport was found by code or by name, so [Airport.matchedCode] is left out. */
fun Airport.toMapOptions(): MapOptions = MapOptions(
    camera = MapOptions.Camera(
        lat = latitude,
        lon = longitude,
        zoom = when (size) {
            Airport.Size.LARGE -> AirportMapZoom.LARGE
            Airport.Size.MEDIUM -> AirportMapZoom.MEDIUM
            Airport.Size.SMALL -> AirportMapZoom.SMALL
            Airport.Size.OTHER -> AirportMapZoom.OTHER
        },
    ),
    airport = MapAirport(
        id = id,
        name = name,
        code = iata ?: icao ?: ident,
        lat = latitude,
        lon = longitude,
        type = type,
    ),
)
