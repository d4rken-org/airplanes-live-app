package eu.darken.apl.common.flight.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "airports")
data class AirportEntity(
    @PrimaryKey @ColumnInfo(name = "icao_code") val icaoCode: String,
    @ColumnInfo(name = "iata_code") val iataCode: String?,
    @ColumnInfo(name = "name") val name: String?,
    @ColumnInfo(name = "country") val country: String?,
    @ColumnInfo(name = "latitude") val latitude: Double?,
    @ColumnInfo(name = "longitude") val longitude: Double?,
    @ColumnInfo(name = "catalogue_id") val catalogueId: String? = null,
    @ColumnInfo(name = "elevation_ft") val elevationFt: Int? = null,
    @ColumnInfo(name = "time_zone") val timeZone: String? = null,
)
