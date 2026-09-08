package eu.darken.apl.main.core.db

import android.location.Location
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.aircraft.AircraftHex
import eu.darken.apl.main.core.aircraft.Airframe
import eu.darken.apl.main.core.aircraft.Callsign
import eu.darken.apl.main.core.aircraft.Registration
import eu.darken.apl.main.core.aircraft.SquawkCode
import java.time.Instant

@Entity(
    tableName = "aircraft_cache",
)
data class CachedAircraftEntity(
    @PrimaryKey @ColumnInfo(name = "hex") val hex: AircraftHex,
    @ColumnInfo(name = "source") val source: String?,
    @ColumnInfo(name = "registration") val registration: Registration?,
    @ColumnInfo(name = "flight") val callsign: Callsign?,

    @ColumnInfo(name = "operator") val operator: String?,
    @ColumnInfo(name = "airframe") val airframe: Airframe?,
    @ColumnInfo(name = "description") val description: String?,

    @ColumnInfo(name = "squawk") val squawk: SquawkCode?,
    @ColumnInfo(name = "emergency") val emergency: String?,

    @ColumnInfo(name = "military") val military: Boolean,
    @ColumnInfo(name = "ladd") val ladd: Boolean,
    @ColumnInfo(name = "pia") val pia: Boolean,

    @ColumnInfo(name = "temperature_outside") val outsideTemp: Int?, // outer/static air temperature (C)
    @ColumnInfo(name = "altitude_ft") val altitudeFt: Int?,
    @ColumnInfo(name = "on_ground") val onGround: Boolean?,
    @ColumnInfo(name = "altitude_geom_ft") val geometricAltitudeFt: Int?,
    @ColumnInfo(name = "altitude_rate") val altitudeRate: Int?,
    @ColumnInfo(name = "speed_ground") val groundSpeed: Float?, // ground speed in knots
    @ColumnInfo(name = "speed_air") val indicatedAirSpeed: Int?, // indicated air speed in knots
    @ColumnInfo(name = "track") val trackheading: Double?,
    @ColumnInfo(name = "ground_track") val groundTrack: Float?,
    @ColumnInfo(name = "location") val location: Location?,

    @ColumnInfo(name = "message_seen_at") val messageSeenAt: Instant?,
    @ColumnInfo(name = "position_seen_at") val positionSeenAt: Instant?,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Instant,
)

internal fun Aircraft.toEntity() = CachedAircraftEntity(
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

internal fun CachedAircraftEntity.toAircraft() = Aircraft(
    hex = hex,
    registration = registration,
    callsign = callsign,
    operator = operator,
    airframe = airframe,
    description = description,
    squawk = squawk,
    emergency = emergency,
    source = source,
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
