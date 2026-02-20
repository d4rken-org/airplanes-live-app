package eu.darken.apl.common.compose.preview

import android.location.Location
import eu.darken.apl.common.flight.Airport
import eu.darken.apl.common.flight.FlightRoute
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.aircraft.Airframe
import eu.darken.apl.main.core.aircraft.AircraftHex
import eu.darken.apl.main.core.aircraft.Callsign
import eu.darken.apl.main.core.aircraft.Registration
import eu.darken.apl.main.core.aircraft.SquawkCode
import java.time.Instant

data class FakeAircraft(
    override val hex: AircraftHex = "ABC123",
    override val messageType: String = "adsb_icao",
    override val dbFlags: Int? = null,
    override val registration: Registration? = "D-ABCD",
    override val callsign: Callsign? = "DLH123",
    override val operator: String? = "Lufthansa",
    override val airframe: Airframe? = "A320",
    override val description: String? = "Airbus A320neo",
    override val squawk: SquawkCode? = "1000",
    override val emergency: String? = null,
    override val outsideTemp: Int? = null,
    override val altitude: String? = "35000",
    override val altitudeRate: Int? = 0,
    override val groundSpeed: Float? = 450f,
    override val indicatedAirSpeed: Int? = 280,
    override val trackheading: Double? = 45.0,
    override val location: Location? = null,
    override val messages: Int = 100,
    override val seenAt: Instant = Instant.now(),
    override val rssi: Double = -10.0,
) : Aircraft

fun mockFlightRoute() = FlightRoute(
    callsign = "DLH123",
    origin = Airport(icaoCode = "EDDF", iataCode = "FRA", name = "Frankfurt Airport", countryName = "Germany"),
    destination = Airport(icaoCode = "EGLL", iataCode = "LHR", name = "London Heathrow", countryName = "United Kingdom"),
    seenAt = Instant.now(),
)
