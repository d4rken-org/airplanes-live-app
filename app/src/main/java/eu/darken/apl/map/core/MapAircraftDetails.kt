package eu.darken.apl.map.core

import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.aircraft.altitudeLabel
import eu.darken.apl.main.core.aircraft.messageTypeLabel
import org.json.JSONObject
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
        /** The native map's details come from the selected aircraft's record, not from a page. */
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

        fun fromJson(json: String): MapAircraftDetails? {
            val obj = try {
                JSONObject(json)
            } catch (_: Exception) {
                return null
            }

            val rawHex = obj.optString("hex").takeIf { it.isNotBlank() } ?: return null

            // Parse hex from "Hex: ABCDEF" format, strip stray "Copy Link" text
            val hex = rawHex
                .replace(Regex("(?i)hex:\\s*"), "")
                .replace(Regex("(?i)\\s*copy\\s+link\\s*"), "")
                .trim()
                .uppercase()

            return MapAircraftDetails(
                hex = hex,
                callsign = obj.optNull("callsign"),
                registration = obj.optNull("registration"),
                country = obj.optNull("country"),
                icaoType = obj.optNull("icaoType"),
                typeLong = obj.optNull("typeLong"),
                typeDesc = obj.optNull("typeDesc"),
                operator = obj.optNull("operator"),
                altitude = obj.optNull("altitude"),
                altitudeGeom = obj.optNull("altitudeGeom"),
                speed = obj.optNull("speed"),
                vertRate = obj.optNull("vertRate"),
                track = obj.optNull("track"),
                position = obj.optNull("position"),
                source = obj.optNull("source"),
                rssi = obj.optNull("rssi"),
                messageRate = obj.optNull("messageRate"),
                messageCount = obj.optNull("messageCount"),
                seen = obj.optNull("seen"),
                seenPos = obj.optNull("seenPos"),
                squawk = obj.optNull("squawk"),
                route = obj.optNull("route"),
                navAltitude = obj.optNull("navAltitude"),
                navHeading = obj.optNull("navHeading"),
                navModes = obj.optNull("navModes"),
                navQnh = obj.optNull("navQnh"),
                tas = obj.optNull("tas"),
                ias = obj.optNull("ias"),
                mach = obj.optNull("mach"),
                baroRate = obj.optNull("baroRate"),
                geomRate = obj.optNull("geomRate"),
                trueHeading = obj.optNull("trueHeading"),
                magHeading = obj.optNull("magHeading"),
                roll = obj.optNull("roll"),
                windSpeed = obj.optNull("windSpeed"),
                windDir = obj.optNull("windDir"),
                temp = obj.optNull("temp"),
                dbFlags = obj.optNull("dbFlags"),
                adsVersion = obj.optNull("adsVersion"),
                category = obj.optNull("category"),
                photoUrl = obj.optNull("photoUrl"),
                photoCredit = obj.optNull("photoCredit"),
            )
        }

        private fun JSONObject.optNull(key: String): String? {
            val value = optString(key, "")
            return value.takeIf { it.isNotBlank() }
        }
    }
}
