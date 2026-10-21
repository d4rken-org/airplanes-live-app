package eu.darken.apl.map.core

import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.aircraft.messageTypeLabel
import java.util.Locale
import kotlin.math.roundToInt

data class MapAircraftDetails(
    val hex: String,
    /** Only what the map itself knows, without the server's details. */
    val isPartial: Boolean = false,
    /** From the history at the time shown, not the aircraft now. */
    val isHistorical: Boolean = false,
    val callsign: String? = null,
    val registration: String? = null,
    val country: String? = null,
    val icaoType: String? = null,
    val typeLong: String? = null,
    val operator: String? = null,
    val onGround: Boolean = false,
    val altitudeFt: Int? = null,
    val altitudeGeomFt: Int? = null,
    val vertRateFpm: Int? = null,
    val groundSpeedKt: Float? = null,
    val iasKt: Int? = null,
    val trackDegrees: Float? = null,
    val track: String? = null,
    val heading: String? = null,
    val squawk: String? = null,
    val tempC: Int? = null,
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
            onGround = ac.onGround == true,
            altitudeFt = ac.altitudeFt,
            altitudeGeomFt = ac.geometricAltitudeFt,
            vertRateFpm = ac.altitudeRate,
            groundSpeedKt = ac.groundSpeed,
            iasKt = ac.indicatedAirSpeed,
            trackDegrees = ac.groundTrack,
            track = ac.groundTrack?.let { "${it.roundToInt()}°" },
            heading = ac.trackheading?.let { "${it.roundToInt()}°" },
            squawk = ac.squawk,
            tempC = ac.outsideTemp,
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
            onGround = plane.onGround,
            altitudeFt = plane.altitudeFt,
            groundSpeedKt = plane.groundSpeedKnots?.toFloat(),
            trackDegrees = plane.trackDegrees,
            track = plane.trackDegrees?.let { "${it.roundToInt()}°" },
            dbFlags = "military".takeIf { plane.military },
        )

        fun pending(hex: String, country: String?): MapAircraftDetails = MapAircraftDetails(
            hex = hex,
            isPartial = true,
            country = country,
        )
    }
}
