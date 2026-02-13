package eu.darken.apl.common.flight.db

import android.content.Context
import androidx.room.Room
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FlightDatabase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
) {

    private val database by lazy {
        Room.databaseBuilder(
            context,
            FlightRoomDb::class.java, "flights"
        ).build()
    }

    private val airportDao: AirportDao
        get() = database.airports()

    private val routeDao: FlightRouteDao
        get() = database.routes()

    fun byHex(hex: String): Flow<FlightRouteEntity?> {
        log(TAG, VERBOSE) { "byHex($hex)" }
        return routeDao.byHex(hex).flowOn(dispatcherProvider.IO).onEach {
            log(TAG, VERBOSE) { "byHex($hex) -> $it" }
        }
    }

    fun byAirport(icao: String): Flow<List<FlightRouteEntity>> {
        log(TAG, VERBOSE) { "byAirport($icao)" }
        return routeDao.byAirport(icao).flowOn(dispatcherProvider.IO).onEach {
            log(TAG, VERBOSE) { "byAirport($icao) -> ${it.size} routes" }
        }
    }

    fun byOrigin(icao: String): Flow<List<FlightRouteEntity>> {
        log(TAG, VERBOSE) { "byOrigin($icao)" }
        return routeDao.byOrigin(icao).flowOn(dispatcherProvider.IO).onEach {
            log(TAG, VERBOSE) { "byOrigin($icao) -> ${it.size} routes" }
        }
    }

    fun byDestination(icao: String): Flow<List<FlightRouteEntity>> {
        log(TAG, VERBOSE) { "byDestination($icao)" }
        return routeDao.byDestination(icao).flowOn(dispatcherProvider.IO).onEach {
            log(TAG, VERBOSE) { "byDestination($icao) -> ${it.size} routes" }
        }
    }

    fun airportByIcao(icao: String): Flow<AirportEntity?> {
        log(TAG, VERBOSE) { "airportByIcao($icao)" }
        return airportDao.byIcao(icao).flowOn(dispatcherProvider.IO).onEach {
            log(TAG, VERBOSE) { "airportByIcao($icao) -> $it" }
        }
    }

    suspend fun upsertAirport(
        icao: String,
        iata: String?,
        name: String?,
        country: String?,
    ) = withContext(dispatcherProvider.IO) {
        log(TAG, VERBOSE) { "upsertAirport($icao, $iata, $name, $country)" }
        airportDao.upsert(icao, iata, name, country)
    }

    suspend fun insertRoute(entity: FlightRouteEntity) = withContext(dispatcherProvider.IO) {
        log(TAG, VERBOSE) { "insertRoute($entity)" }
        routeDao.insert(entity)
    }

    suspend fun deleteOlderThan(instant: Instant) = withContext(dispatcherProvider.IO) {
        log(TAG, VERBOSE) { "deleteOlderThan($instant)" }
        routeDao.deleteOlderThan(instant)
    }

    suspend fun getLatestByHex(hex: String): FlightRouteEntity? = withContext(dispatcherProvider.IO) {
        log(TAG, VERBOSE) { "getLatestByHex($hex)" }
        routeDao.byHex(hex).first()
    }

    companion object {
        internal val TAG = logTag("Flight", "Database")
    }
}
