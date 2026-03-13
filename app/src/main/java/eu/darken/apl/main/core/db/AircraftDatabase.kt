package eu.darken.apl.main.core.db

import android.content.Context
import androidx.room.Room
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.aircraft.AircraftHex
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AircraftDatabase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
) {

    private val database by lazy {
        Room.databaseBuilder(
            context,
            AircraftRoomDb::class.java, "aircraft"
        )
            .addMigrations(AircraftRoomDb.MIGRATION_1_2)
            .build()
    }

    private val aircraftDao: CachedAircraftDao
        get() = database.aircraft()

    fun byHex(hex: AircraftHex): Flow<CachedAircraftEntity?> {
        log(TAG, VERBOSE) { "byHex($hex)" }
        return aircraftDao.byHex(hex).flowOn(dispatcherProvider.IO).onEach {
            log(TAG, VERBOSE) { "byHex($hex) -> $it" }
        }
    }

    fun current(): Flow<List<CachedAircraftEntity>> {
        log(TAG, VERBOSE) { "current()" }
        return aircraftDao.current().flowOn(dispatcherProvider.IO).onEach {
            log(TAG, VERBOSE) { "current() -> ${it.size} aircraft" }
        }
    }

    suspend fun update(toUpdate: Collection<Aircraft>) = withContext(dispatcherProvider.IO) {
        log(TAG, VERBOSE) { "update(size=${toUpdate.size})" }
        if (toUpdate.isEmpty()) return@withContext
        aircraftDao.insertOrUpdateUsers(toUpdate.map { it.toEntity() })
    }

    suspend fun count(): Int = withContext(dispatcherProvider.IO) {
        aircraftDao.count()
    }

    suspend fun delete(hex: AircraftHex): Int = withContext(dispatcherProvider.IO) {
        log(TAG, VERBOSE) { "delete($hex)" }
        aircraftDao.delete(hex)
    }

    companion object {
        internal val TAG = logTag("Aircraft", "Database")
    }
}