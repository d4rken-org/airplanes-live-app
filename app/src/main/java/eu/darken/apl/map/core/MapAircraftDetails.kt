package eu.darken.apl.map.core

import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.aircraft.altitudeLabel
import eu.darken.apl.main.core.aircraft.messageTypeLabel
import java.util.Locale
import kotlin.math.roundToInt

data class MapAircraftDetails(
    val hex: String,
    val callsign: String?,
    val registration: String?,
    val country: String?,
    val icaoType: String?,
    val typeLong: String?,
    val typeDesc: String?,
    val operator: String?,
    // Movement
    val altitude: String?,
    val altitudeGeom: String?,
    val speed: String?,
    val vertRate: String?,
    val track: String?,
    val position: String?,
    // Signal
    val source: String?,
    val rssi: String?,
    val messageRate: String?,
    val messageCount: String?,
    val seen: String?,
    val seenPos: String?,
    // Navigation
    val squawk: String?,
    val route: String?,
    val navAltitude: String?,
    val navHeading: String?,
    val navModes: String?,
    val navQnh: String?,
    // Speed detail
    val tas: String?,
    val ias: String?,
    val mach: String?,
    // Altitude detail
    val baroRate: String?,
    val geomRate: String?,
    // Direction
    val trueHeading: String?,
    val magHeading: String?,
    val roll: String?,
    // Wind
    val windSpeed: String?,
    val windDir: String?,
    val temp: String?,
    // Aircraft meta
    val dbFlags: String?,
    val adsVersion: String?,
    val category: String?,
    // Photo
    val photoUrl: String?,
    val photoCredit: String?,
) {
    companion object {
        fun from(ac: Aircraft, country: String?): MapAircraftDetails = MapAircraftDetails(
            hex = ac.hex,
            callsign = ac.callsign?.trim()?.takeIf { it.isNotEmpty() },
            registration = ac.registration,
            country = country,
            icaoType = ac.airframe,
            typeLong = ac.description,
            typeDesc = null,
            operator = ac.operator,
            altitude = when {
                ac.onGround == true -> ac.altitudeLabel
                else -> ac.altitudeFt?.let { "$it ft" }
            },
            altitudeGeom = ac.geometricAltitudeFt?.let { "$it ft" },
            speed = ac.groundSpeed?.let { "${it.roundToInt()} kts" },
            vertRate = ac.altitudeRate?.let { "$it ft/min" },
            track = ac.groundTrack?.let { "${it.roundToInt()}°" },
            position = ac.location?.let { "%.4f, %.4f".format(Locale.ROOT, it.latitude, it.longitude) },
            source = ac.source?.let { ac.messageTypeLabel },
            rssi = null,
            messageRate = null,
            messageCount = null,
            seen = null,
            seenPos = null,
            squawk = ac.squawk,
            route = null,
            navAltitude = null,
            navHeading = null,
            navModes = null,
            navQnh = null,
            tas = null,
            ias = ac.indicatedAirSpeed?.let { "$it kts" },
            mach = null,
            baroRate = null,
            geomRate = null,
            trueHeading = ac.trackheading?.let { "${it.roundToInt()}°" },
            magHeading = null,
            roll = null,
            windSpeed = null,
            windDir = null,
            temp = ac.outsideTemp?.let { "$it °C" },
            dbFlags = listOfNotNull(
                "military".takeIf { ac.military },
                "LADD".takeIf { ac.ladd },
                "PIA".takeIf { ac.pia },
            ).joinToString(", ").takeIf { it.isNotEmpty() },
            adsVersion = null,
            category = null,
            photoUrl = null,
            photoCredit = null,
        )
    }
}
