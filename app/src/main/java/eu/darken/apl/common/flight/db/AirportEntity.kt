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
)
