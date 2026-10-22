package eu.darken.apl.map.core

import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.aircraft.AircraftSourceType
import eu.darken.apl.main.core.aircraft.sourceType
import java.util.Locale

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
    val headingDegrees: Double? = null,
    val squawk: String? = null,
    val tempC: Int? = null,
    val position: String? = null,
    val sourceType: AircraftSourceType? = null,
    val military: Boolean = false,
    val ladd: Boolean = false,
    val pia: Boolean = false,
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
            headingDegrees = ac.trackheading,
            squawk = ac.squawk,
            tempC = ac.outsideTemp,
            position = ac.location?.let { "%.4f, %.4f".format(Locale.ROOT, it.latitude, it.longitude) },
            sourceType = ac.source?.let { ac.sourceType },
            military = ac.military,
            ladd = ac.ladd,
            pia = ac.pia,
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
            military = plane.military,
        )

        fun pending(hex: String, country: String?): MapAircraftDetails = MapAircraftDetails(
            hex = hex,
            isPartial = true,
            country = country,
        )
    }
}
