package eu.darken.apl.main.core.aircraft

import android.location.Location
import java.time.Instant

/**
 * One observation of an aircraft as the server reported it.
 *
 * Timestamps are server time and stay unchanged when the same observation is delivered again,
 * so ages never rejuvenate. A null [messageSeenAt] or [positionSeenAt] means unknown, it is never
 * substituted with another timestamp.
 */
data class Aircraft(
    val hex: AircraftHex,
    val registration: Registration?,
    val callsign: Callsign?,
    val operator: String?,
    val airframe: Airframe?,
    val description: String?,
    val squawk: SquawkCode?,
    val emergency: String?,
    val source: String?,
    val military: Boolean,
    val ladd: Boolean,
    val pia: Boolean,
    val outsideTemp: Int?,
    val altitudeFt: Int?,
    val onGround: Boolean?,
    val geometricAltitudeFt: Int?,
    val altitudeRate: Int?,
    val groundSpeed: Float?,
    val indicatedAirSpeed: Int?,
    val trackheading: Double?,
    val groundTrack: Float?,
    val location: Location?,
    val messageSeenAt: Instant?,
    val positionSeenAt: Instant?,
    val fetchedAt: Instant,
) {
    override fun toString(): String = "Aircraft($hex, $registration, $airframe)"
}
