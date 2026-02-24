package eu.darken.apl.watch.ui.preview

import eu.darken.apl.common.compose.preview.FakeAircraft
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.aircraft.AircraftHex
import eu.darken.apl.main.core.aircraft.Callsign
import eu.darken.apl.main.core.aircraft.SquawkCode
import eu.darken.apl.watch.core.db.types.AircraftWatchEntity
import eu.darken.apl.watch.core.db.types.BaseWatchEntity
import eu.darken.apl.watch.core.db.types.FlightWatchEntity
import eu.darken.apl.watch.core.db.types.LocationWatchEntity
import eu.darken.apl.watch.core.db.types.SquawkWatchEntity
import eu.darken.apl.watch.core.makeWatchId
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.FlightWatch
import eu.darken.apl.watch.core.types.LocationWatch
import eu.darken.apl.watch.core.types.SquawkWatch

fun mockAircraftWatch(hex: AircraftHex = "ABC123", note: String = ""): AircraftWatch {
    val id = makeWatchId()
    return AircraftWatch(
        base = BaseWatchEntity(id = id, watchType = AircraftWatchEntity.TYPE_KEY, userNote = note),
        specific = AircraftWatchEntity(id = id, hexCode = hex),
    )
}

fun mockAircraftWatchStatus(aircraft: Aircraft? = FakeAircraft()) = AircraftWatch.Status(
    watch = mockAircraftWatch(hex = aircraft?.hex ?: "ABC123"),
    lastCheck = null,
    lastHit = null,
    tracked = if (aircraft != null) setOf(aircraft) else emptySet(),
)

fun mockFlightWatch(callsign: Callsign = "BAW123"): FlightWatch {
    val id = makeWatchId()
    return FlightWatch(
        base = BaseWatchEntity(id = id, watchType = FlightWatchEntity.TYPE_KEY),
        specific = FlightWatchEntity(id = id, callsign = callsign),
    )
}

fun mockFlightWatchStatus(callsign: Callsign = "BAW123") = FlightWatch.Status(
    watch = mockFlightWatch(callsign),
    lastCheck = null,
    lastHit = null,
)

fun mockSquawkWatch(code: SquawkCode = "7700"): SquawkWatch {
    val id = makeWatchId()
    return SquawkWatch(
        base = BaseWatchEntity(id = id, watchType = SquawkWatchEntity.TYPE_KEY),
        specific = SquawkWatchEntity(id = id, code = code),
    )
}

fun mockSquawkWatchStatus(aircraft: Set<Aircraft> = emptySet()) = SquawkWatch.Status(
    watch = mockSquawkWatch(),
    lastCheck = null,
    lastHit = null,
    tracked = aircraft,
)

fun mockLocationWatch(
    label: String = "London Heathrow",
    latitude: Double = 51.47,
    longitude: Double = -0.46,
    radiusInMeters: Float = 25000f,
): LocationWatch {
    val id = makeWatchId()
    return LocationWatch(
        base = BaseWatchEntity(
            id = id,
            watchType = LocationWatchEntity.TYPE_KEY,
            latitude = latitude,
            longitude = longitude,
            radius = radiusInMeters,
        ),
        specific = LocationWatchEntity(id = id, label = label),
    )
}

fun mockLocationWatchStatus(aircraft: Set<Aircraft> = emptySet()) = LocationWatch.Status(
    watch = mockLocationWatch(),
    lastCheck = null,
    lastHit = null,
    tracked = aircraft,
)
