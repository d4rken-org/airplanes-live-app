package eu.darken.apl.main.core.api

import dagger.Reusable
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.main.core.aircraft.AircraftHex
import eu.darken.apl.main.core.aircraft.Airframe
import eu.darken.apl.main.core.aircraft.Callsign
import eu.darken.apl.main.core.aircraft.Registration
import eu.darken.apl.main.core.aircraft.SquawkCode
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Converter
import retrofit2.Retrofit
import java.util.stream.Collectors
import javax.inject.Inject


@Reusable
class AirplanesLiveEndpoint @Inject constructor(
    private val baseClient: OkHttpClient,
    private val jsonConverterFactory: Converter.Factory,
    private val dispatcherProvider: DispatcherProvider,
) {

    private val api: AirplanesLiveApi by lazy {
        val configHttpClient = baseClient.newBuilder().apply {

        }.build()

        Retrofit.Builder()
            .client(configHttpClient)
            .baseUrl("https://api.airplanes.live/v2/")
            .addConverterFactory(jsonConverterFactory)
            .build()
            .create(AirplanesLiveApi::class.java)
    }

    suspend fun getByHex(
        hexes: Set<AircraftHex>,
    ): Collection<AirplanesLiveApi.Aircraft> = withContext(dispatcherProvider.IO) {
        log(TAG) { "getByHexes(hexes=$hexes)" }
        if (hexes.isEmpty()) return@withContext emptySet()
        hexes
            .chunkToCommaArgs(limit = 1000)
            .map { api.getAircraftByHex(it).throwForErrors() }
            .toList()
            .flatMap { it.ac }
    }

    suspend fun getBySquawk(
        squawks: Set<SquawkCode>
    ): Collection<AirplanesLiveApi.Aircraft> = withContext(dispatcherProvider.IO) {
        log(TAG) { "getBySquawks(squawks=$squawks)" }
        if (squawks.isEmpty()) return@withContext emptySet()
        squawks
            .map { api.getAircraftBySquawk(it).throwForErrors() }
            .toList()
            .map { it.ac }
            .flatten()
    }

    suspend fun getByCallsign(
        callsigns: Set<Callsign>
    ): Collection<AirplanesLiveApi.Aircraft> = withContext(dispatcherProvider.IO) {
        log(TAG) { "getByCallsigns(callsigns=$callsigns)" }
        if (callsigns.isEmpty()) return@withContext emptySet()
        callsigns
            .chunkToCommaArgs(limit = 1000)
            .map { api.getAircraftByCallsign(it).throwForErrors() }
            .toList()
            .flatMap { it.ac }
    }

    suspend fun getByRegistration(
        registrations: Set<Registration>
    ): Collection<AirplanesLiveApi.Aircraft> = withContext(dispatcherProvider.IO) {
        log(TAG) { "getByRegistration(registrations=$registrations)" }
        if (registrations.isEmpty()) return@withContext emptySet()
        registrations
            .chunkToCommaArgs(limit = 1000)
            .map { api.getAircraftByRegistration(it).throwForErrors() }
            .toList()
            .flatMap { it.ac }
    }

    suspend fun getByAirframe(
        airframes: Set<Airframe>
    ): Collection<AirplanesLiveApi.Aircraft> = withContext(dispatcherProvider.IO) {
        log(TAG) { "getByAirframe(airframes=$airframes)" }
        if (airframes.isEmpty()) return@withContext emptySet()
        airframes
            .chunkToCommaArgs(limit = 1000)
            .map { api.getAircraftByAirframe(it).throwForErrors() }
            .toList()
            .flatMap { it.ac }
    }

    suspend fun getMilitary(): Collection<AirplanesLiveApi.Aircraft> = withContext(dispatcherProvider.IO) {
        log(TAG) { "getMilitary()" }
        api.getMilitary().throwForErrors().ac
    }

    suspend fun getLADD(): Collection<AirplanesLiveApi.Aircraft> = withContext(dispatcherProvider.IO) {
        log(TAG) { "getLADD()" }
        api.getLADD().throwForErrors().ac
    }

    suspend fun getPIA(): Collection<AirplanesLiveApi.Aircraft> = withContext(dispatcherProvider.IO) {
        log(TAG) { "getPIA()" }
        api.getPIA().throwForErrors().ac
    }

    suspend fun getByLocation(
        latitude: Double,
        longitude: Double,
        radiusInMeter: Long,
    ): Collection<AirplanesLiveApi.Aircraft> = withContext(dispatcherProvider.IO) {
        log(TAG) { "getByLocation($latitude,$longitude,$radiusInMeter)" }

        api.getAircraftsByLocation(
            latitude = latitude,
            longitude = longitude,
            radius = (radiusInMeter / NAUTICAL_MILE_METER).toInt()
        ).ac
    }

    private fun Collection<String>.chunkToCommaArgs(limit: Int = 30) = this
        .chunked(limit)
        .map { chunk ->
            chunk.stream()
                .map { it.toString() }
                .collect(Collectors.joining(","))
        }

    private fun <T : AirplanesLiveApi.BaseResponse> T.throwForErrors(): T = this.also {
        if (it.message != "No error") throw AirplanesLiveApiException(it.message)
    }

    companion object {
        private const val NAUTICAL_MILE_METER = 1852L
        private val TAG = logTag("Core", "Endpoint")
    }
}
