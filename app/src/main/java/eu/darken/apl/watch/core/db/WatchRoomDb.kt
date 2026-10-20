package eu.darken.apl.watch.core.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import eu.darken.apl.common.room.InstantConverter
import eu.darken.apl.watch.core.db.history.WatchCheckDao
import eu.darken.apl.watch.core.db.history.WatchCheckEntity
import eu.darken.apl.watch.core.db.history.WatchHistoryAppliedEntity
import eu.darken.apl.watch.core.db.history.WatchHistoryDao
import eu.darken.apl.watch.core.db.history.WatchHistoryHourEntity
import eu.darken.apl.watch.core.db.history.WatchHistorySyncEntity
import eu.darken.apl.watch.core.db.server.WatchNotificationOutboxEntity
import eu.darken.apl.watch.core.db.server.WatchServerDao
import eu.darken.apl.watch.core.db.server.WatchServerEventEntity
import eu.darken.apl.watch.core.db.server.WatchServerRollupEntity
import eu.darken.apl.watch.core.db.server.WatchServerStateEntity
import eu.darken.apl.watch.core.db.server.WatchServerSyncEntity
import eu.darken.apl.watch.core.db.server.WatchServerTombstoneEntity
import eu.darken.apl.watch.core.db.types.AircraftWatchEntity
import eu.darken.apl.watch.core.db.types.BaseWatchEntity
import eu.darken.apl.watch.core.db.types.FlightWatchEntity
import eu.darken.apl.watch.core.db.types.LocationWatchEntity
import eu.darken.apl.watch.core.db.types.SquawkWatchEntity
import eu.darken.apl.watch.core.db.types.WatchDao

@Database(
    entities = [
        BaseWatchEntity::class,
        FlightWatchEntity::class,
        SquawkWatchEntity::class,
        AircraftWatchEntity::class,
        LocationWatchEntity::class,
        WatchCheckEntity::class,
        WatchHistoryHourEntity::class,
        WatchHistorySyncEntity::class,
        WatchHistoryAppliedEntity::class,
        WatchServerStateEntity::class,
        WatchServerTombstoneEntity::class,
        WatchServerSyncEntity::class,
        WatchServerEventEntity::class,
        WatchNotificationOutboxEntity::class,
        WatchServerRollupEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
@TypeConverters(InstantConverter::class)
abstract class WatchRoomDb : RoomDatabase() {
    abstract fun watches(): WatchDao
    abstract fun checks(): WatchCheckDao
    abstract fun history(): WatchHistoryDao
    abstract fun server(): WatchServerDao
}