package eu.darken.apl.common.flight.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import eu.darken.apl.common.room.InstantConverter

@Database(
    entities = [
        AirportEntity::class,
        FlightRouteEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(InstantConverter::class)
abstract class FlightRoomDb : RoomDatabase() {
    abstract fun airports(): AirportDao
    abstract fun routes(): FlightRouteDao
}
