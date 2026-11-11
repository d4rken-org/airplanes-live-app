package eu.darken.apl.map.core

import eu.darken.apl.airport.core.AirportRef

/**
 * An airport as the base map's vector tiles carry it (OpenMapTiles `aerodrome_label`), null without a code.
 * The name is the first given of [nameLatin], [nameEn] and [name].
 */
fun tileAirportRef(
    icao: String?,
    iata: String?,
    nameLatin: String?,
    nameEn: String?,
    name: String?,
    latitude: Double,
    longitude: Double,
): AirportRef? = AirportRef.create(
    icao = icao,
    iata = iata,
    name = listOf(nameLatin, nameEn, name).firstOrNull { !it.isNullOrBlank() },
    latitude = latitude,
    // A tap in a world copy past ±180 reports the longitude there, which would not count as a position
    longitude = MapViewport.normalizeLongitude(longitude),
)
