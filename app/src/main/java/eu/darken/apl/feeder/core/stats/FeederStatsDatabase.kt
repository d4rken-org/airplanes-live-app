package eu.darken.apl.feeder.core.stats

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeederStatsDatabase @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val database by lazy {
        Room.databaseBuilder(
            context,
            FeederStatsRoomDb::class.java, "feeder-stats"
        )
            .addMigrations(MIGRATION_1_2)
            .build()
    }

    val beastStats: BeastStatsDao
        get() = database.beastStats()

    val mlatStats: MlatStatsDao
        get() = database.mlatStats()

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_stats_beast_receiver_id_received_at` ON `stats_beast` (`receiver_id`, `received_at`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_stats_mlat_receiver_id_received_at` ON `stats_mlat` (`receiver_id`, `received_at`)")
            }
        }
    }
}