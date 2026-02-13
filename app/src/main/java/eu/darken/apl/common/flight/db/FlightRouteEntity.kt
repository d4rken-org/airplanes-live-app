package eu.darken.apl.common.flight.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "route_cache")
data class FlightRouteEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "aircraft_hex", index = true) val aircraftHex: String,
    @ColumnInfo(name = "callsign") val callsign: String,
    @ColumnInfo(name = "origin_icao", index = true) val originIcao: String?,
    @ColumnInfo(name = "destination_icao", index = true) val destinationIcao: String?,
    @ColumnInfo(name = "seen_at") val seenAt: Instant,
    @ColumnInfo(name = "fetched_at", index = true) val fetchedAt: Instant,
)
