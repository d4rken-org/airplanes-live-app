package eu.darken.apl.main.core.aircraft

import android.location.Location
import java.time.Instant

interface Aircraft {
    val hex: AircraftHex

    val messageType: String
    val dbFlags: Int?

    val registration: Registration?
    val callsign: Callsign?

    val operator: String?
    val airframe: Airframe?
    val description: String?

    val squawk: SquawkCode?
    val emergency: String?

    val outsideTemp: Int?
    val altitude: String?
    val altitudeRate: Int?
    val groundSpeed: Float?
    val indicatedAirSpeed: Int?
    val trackheading: Double?
    val groundTrack: Float?

    val location: Location?

    val messages: Int
    val seenAt: Instant
    val rssi: Double
}
