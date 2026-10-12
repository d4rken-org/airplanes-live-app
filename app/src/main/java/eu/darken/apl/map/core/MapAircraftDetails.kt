package eu.darken.apl.map.core

import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.aircraft.altitudeLabel
import eu.darken.apl.main.core.aircraft.messageTypeLabel
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class MapAircraftDetails(
    val hex: String,
    val callsign: String?,
    val registration: String?,
    val country: String?,
    val icaoType: String?,
    val typeLong: String?,
    val operator: String?,
    val altitude: String?,
    val altitudeGeom: String?,
    val climb: Climb?,
    val vertRate: String?,
    val speed: String?,
    val ias: String?,
    val trackDegrees: Float?,
    val track: String?,
    val heading: String?,
    val squawk: String?,
    val temp: String?,
    val position: String?,
    val source: String?,
    val dbFlags: String?,
) {
    enum class Climb { CLIMBING, LEVEL, DESCENDING }

    companion object {
        // Below this a vertical rate is noise around level flight
        private const val LEVEL_RATE_FPM = 250

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
            climb = ac.altitudeRate?.takeIf { ac.onGround != true }?.let {
                when {
                    abs(it) < LEVEL_RATE_FPM -> Climb.LEVEL
                    it > 0 -> Climb.CLIMBING
                    else -> Climb.DESCENDING
                }
            },
            vertRate = ac.altitudeRate?.let { "%+,d ft/min".format(it) },
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
    }
}
