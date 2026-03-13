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
    @PrimaryKey @ColumnInfo(name = "hex") override val hex: AircraftHex,
    @ColumnInfo(name = "message_type") override val messageType: String,
    @ColumnInfo(name = "db_flags") override val dbFlags: Int?,
    @ColumnInfo(name = "registration") override val registration: Registration?,
    @ColumnInfo(name = "flight") override val callsign: Callsign?,

    @ColumnInfo(name = "operator") override val operator: String?,
    @ColumnInfo(name = "airframe") override val airframe: Airframe?,
    @ColumnInfo(name = "description") override val description: String?,

    @ColumnInfo(name = "squawk") override val squawk: SquawkCode?,
    @ColumnInfo(name = "emergency") override val emergency: String?,

    @ColumnInfo(name = "temperature_outside") override val outsideTemp: Int?, // outer/static air temperature (C)
    @ColumnInfo(name = "altitude") override val altitude: String?, // Altitude in feet or "ground"
    @ColumnInfo(name = "altitude_rate") override val altitudeRate: Int?,
    @ColumnInfo(name = "speed_ground") override val groundSpeed: Float?, // ground speed in knots
    @ColumnInfo(name = "speed_air") override val indicatedAirSpeed: Int?, // indicated air speed in knots
    @ColumnInfo(name = "track") override val trackheading: Double?,
    @ColumnInfo(name = "ground_track") override val groundTrack: Float?,
    @ColumnInfo(name = "location") override val location: Location?,

    @ColumnInfo(name = "messages") override val messages: Int,
    @ColumnInfo(name = "seen_at") override val seenAt: Instant,
    @ColumnInfo(name = "rssi") override val rssi: Double,
) : Aircraft


internal fun Aircraft.toEntity() = CachedAircraftEntity(
    hex = this.hex,
    messageType = this.messageType,
    dbFlags = this.dbFlags,
    registration = this.registration,
    callsign = this.callsign,
    operator = this.operator,
    airframe = this.airframe,
    description = this.description,
    squawk = this.squawk,
    emergency = this.emergency,
    outsideTemp = this.outsideTemp,
    altitude = this.altitude,
    altitudeRate = this.altitudeRate,
    groundSpeed = this.groundSpeed,
    indicatedAirSpeed = this.indicatedAirSpeed,
    trackheading = this.trackheading,
    groundTrack = this.groundTrack,
    location = this.location,
    messages = this.messages,
    seenAt = this.seenAt,
    rssi = this.rssi
)