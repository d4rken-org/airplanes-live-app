package eu.darken.apl.feeder.core.stats

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import eu.darken.apl.common.room.InstantConverter

@Database(
    entities = [
        BeastStatsEntity::class,
        MlatStatsEntity::class,
    ],
    version = 2,
    autoMigrations = [
//        AutoMigration(1, 2)
    ],
    exportSchema = true,
)
@TypeConverters(InstantConverter::class)
abstract class FeederStatsRoomDb : RoomDatabase() {
    abstract fun beastStats(): BeastStatsDao
    abstract fun mlatStats(): MlatStatsDao
}