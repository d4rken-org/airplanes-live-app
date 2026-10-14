package eu.darken.apl.watch.core.db.types

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import eu.darken.apl.main.core.aircraft.AircraftHex
import eu.darken.apl.main.core.aircraft.Registration
import eu.darken.apl.watch.core.WatchId

@Entity(
    tableName = "watch_aircraft",
    foreignKeys = [
        ForeignKey(
            entity = BaseWatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class AircraftWatchEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: WatchId,
    @ColumnInfo(name = "hex_code") val hexCode: AircraftHex,
    @ColumnInfo(name = "last_registration") val lastRegistration: Registration? = null,
) : WatchType {

    companion object {
        const val TYPE_KEY = "aircraft"
    }
}