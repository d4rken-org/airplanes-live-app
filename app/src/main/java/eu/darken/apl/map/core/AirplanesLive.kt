package eu.darken.apl.map.core

object AirplanesLive {
    const val URL_START_FEEDING = "https://airplanes.live/get-started/"
    const val URL_GLOBE = "https://globe.airplanes.live/"

    fun aircraftUrl(hex: String): String = "$URL_GLOBE?icao=${hex.lowercase()}"
}