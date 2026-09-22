package eu.darken.apl.common.compose.preview

import android.location.Location
import eu.darken.apl.common.flight.Airport
import eu.darken.apl.common.flight.FlightRoute
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.aircraft.AircraftHex
import eu.darken.apl.main.core.aircraft.Airframe
import eu.darken.apl.main.core.aircraft.Callsign
import eu.darken.apl.main.core.aircraft.Registration
import eu.darken.apl.main.core.aircraft.SquawkCode
import java.time.Instant

fun FakeAircraft(
    hex: AircraftHex = "ABC123",
    source: String? = "adsb_icao",
    registration: Registration? = "D-ABCD",
    callsign: Callsign? = "DLH123",
    operator: String? = "Lufthansa",
    airframe: Airframe? = "A320",
    description: String? = "Airbus A320neo",
    squawk: SquawkCode? = "1000",
    emergency: String? = null,
    military: Boolean = false,
    ladd: Boolean = false,
    pia: Boolean = false,
    outsideTemp: Int? = null,
    altitudeFt: Int? = 35000,
    onGround: Boolean? = false,
    geometricAltitudeFt: Int? = null,
    altitudeRate: Int? = 0,
    groundSpeed: Float? = 450f,
    indicatedAirSpeed: Int? = 280,
    trackheading: Double? = 45.0,
    groundTrack: Float? = 45f,
    location: Location? = null,
    messageSeenAt: Instant? = Instant.now(),
    positionSeenAt: Instant? = null,
    fetchedAt: Instant = Instant.now(),
) = Aircraft(
    hex = hex,
    source = source,
    registration = registration,
    callsign = callsign,
    operator = operator,
    airframe = airframe,
    description = description,
    squawk = squawk,
    emergency = emergency,
    military = military,
    ladd = ladd,
    pia = pia,
    outsideTemp = outsideTemp,
    altitudeFt = altitudeFt,
    onGround = onGround,
    geometricAltitudeFt = geometricAltitudeFt,
    altitudeRate = altitudeRate,
    groundSpeed = groundSpeed,
    indicatedAirSpeed = indicatedAirSpeed,
    trackheading = trackheading,
    groundTrack = groundTrack,
    location = location,
    messageSeenAt = messageSeenAt,
    positionSeenAt = positionSeenAt,
    fetchedAt = fetchedAt,
)

fun mockFlightRoute() = FlightRoute(
    callsign = "DLH123",
    origin = Airport(icaoCode = "EDDF", iataCode = "FRA", name = "Frankfurt Airport", countryName = "Germany"),
    destination = Airport(icaoCode = "EGLL", iataCode = "LHR", name = "London Heathrow", countryName = "United Kingdom"),
    seenAt = Instant.now(),
)
