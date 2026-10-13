package eu.darken.apl.map.core

import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.aircraft.altitudeLabel
import eu.darken.apl.main.core.aircraft.messageTypeLabel
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class MapAircraftDetails(
    val hex: String,
    /** Only what the map itself knows, without the server's details. */
    val isPartial: Boolean = false,
    val callsign: String? = null,
    val registration: String? = null,
    val country: String? = null,
    val icaoType: String? = null,
    val typeLong: String? = null,
    val operator: String? = null,
    val altitude: String? = null,
    val altitudeGeom: String? = null,
    val vertRate: String? = null,
    val vertRateShort: String? = null,
    val speed: String? = null,
    val ias: String? = null,
    val trackDegrees: Float? = null,
    val track: String? = null,
    val heading: String? = null,
    val squawk: String? = null,
    val temp: String? = null,
    val position: String? = null,
    val source: String? = null,
    val dbFlags: String? = null,
) {
    companion object {
        fun from(ac: Aircraft, country: String?): MapAircraftDetails = MapAircraftDetails(
            hex = ac.hex,
            callsign = ac.callsign?.trim()?.takeIf { it.isNotEmpty() },
            registration = ac.registration,
            country = country,
            icaoType = ac.airframe,
            typeLong = ac.description,
            operator = ac.operator,
            altitude = when {
                ac.onGround == true -> ac.altitudeLabel
                else -> ac.altitudeFt?.let { "%,d ft".format(it) }
            },
            altitudeGeom = ac.geometricAltitudeFt?.let { "%,d ft".format(it) },
            vertRate = ac.altitudeRate?.let { if (it == 0) "0 ft/min" else "%+,d ft/min".format(it) },
            vertRateShort = ac.altitudeRate?.takeIf { ac.onGround != true }?.let { shortRate(it) },
            speed = ac.groundSpeed?.let { "${it.roundToInt()} kts" },
            ias = ac.indicatedAirSpeed?.let { "$it kts" },
            trackDegrees = ac.groundTrack,
            track = ac.groundTrack?.let { "${it.roundToInt()}°" },
            heading = ac.trackheading?.let { "${it.roundToInt()}°" },
            squawk = ac.squawk,
            temp = ac.outsideTemp?.let { "$it °C" },
            position = ac.location?.let { "%.4f, %.4f".format(Locale.ROOT, it.latitude, it.longitude) },
            source = ac.source?.let { ac.messageTypeLabel },
            dbFlags = listOfNotNull(
                "military".takeIf { ac.military },
                "LADD".takeIf { ac.ladd },
                "PIA".takeIf { ac.pia },
            ).joinToString(", ").takeIf { it.isNotEmpty() },
        )

        fun from(plane: MapPlane, country: String?): MapAircraftDetails = MapAircraftDetails(
            hex = plane.hex,
            isPartial = true,
            callsign = plane.callsign?.trim()?.takeIf { it.isNotEmpty() },
            country = country,
            icaoType = plane.aircraftType,
            altitude = when {
                plane.onGround -> "ground"
                else -> plane.altitudeFt?.let { "%,d ft".format(it) }
            },
            speed = plane.groundSpeedKnots?.let { "$it kts" },
            trackDegrees = plane.trackDegrees,
            track = plane.trackDegrees?.let { "${it.roundToInt()}°" },
            dbFlags = "military".takeIf { plane.military },
        )

        fun pending(hex: String, country: String?): MapAircraftDetails = MapAircraftDetails(
            hex = hex,
            isPartial = true,
            country = country,
        )

        // -1,234 ft/min becomes -1.2k fpm, 640 stays +640 fpm
        private fun shortRate(fpm: Int): String = when {
            fpm == 0 -> "0 fpm"
            abs(fpm) < 1000 -> "%+d fpm".format(fpm)
            else -> "%+.1fk fpm".format(fpm / 1000f)
        }
    }
}
