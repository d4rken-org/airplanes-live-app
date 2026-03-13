package eu.darken.apl.main.core.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import eu.darken.apl.common.room.InstantConverter
import eu.darken.apl.common.room.LocationConverter

@Database(
    entities = [
        CachedAircraftEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(InstantConverter::class, LocationConverter::class)
abstract class AircraftRoomDb : RoomDatabase() {
    abstract fun aircraft(): CachedAircraftDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE aircraft_cache ADD COLUMN ground_track REAL")
            }
        }
    }
}