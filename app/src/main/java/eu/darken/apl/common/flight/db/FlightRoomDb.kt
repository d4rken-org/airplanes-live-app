package eu.darken.apl.common.flight.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import eu.darken.apl.common.room.InstantConverter

@Database(
    entities = [
        AirportEntity::class,
        FlightRouteEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(InstantConverter::class)
abstract class FlightRoomDb : RoomDatabase() {
    abstract fun airports(): AirportDao
    abstract fun routes(): FlightRouteDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `airports` ADD COLUMN `latitude` REAL")
                db.execSQL("ALTER TABLE `airports` ADD COLUMN `longitude` REAL")
            }
        }
    }
}
