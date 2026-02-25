package eu.darken.apl.main.core.api

import dagger.Reusable
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.datastore.value
import eu.darken.apl.common.datastore.valueBlocking
import eu.darken.apl.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.main.core.GeneralSettings
import eu.darken.apl.main.core.aircraft.AircraftHex
import eu.darken.apl.main.core.aircraft.Airframe
import eu.darken.apl.main.core.aircraft.Callsign
import eu.darken.apl.main.core.aircraft.Registration
import eu.darken.apl.main.core.aircraft.SquawkCode
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Converter
import retrofit2.HttpException
import retrofit2.Retrofit
import javax.inject.Inject


@Reusable
class AirplanesLiveEndpoint @Inject constructor(
    private val baseClient: OkHttpClient,
    private val jsonConverterFactory: Converter.Factory,
    private val dispatcherProvider: DispatcherProvider,
    private val generalSettings: GeneralSettings,
) {
    internal var baseUrl: String = "https://api.airplanes.live/v2/"
    internal var restBaseUrl: String = "https://rest.api.airplanes.live/"

    private suspend fun hasApiKey(): Boolean =
        !generalSettings.airplanesLiveApiKey.value().isNullOrBlank()
                && generalSettings.apiKeyValid.value != false

    private val v2Api: AirplanesLiveApi by lazy {
        Retrofit.Builder()
            .client(baseClient)
            .baseUrl(baseUrl)
            .addConverterFactory(jsonConverterFactory)
            .build()
            .create(AirplanesLiveApi::class.java)
    }

    private val restApi: AirplanesLiveRestApi by lazy {
        Retrofit.Builder()
            .client(baseClient.newBuilder().apply {
                addInterceptor { chain ->
                    // Strip trailing "=" from empty query params so "all=" becomes "all"
                    // The REST API requires presence-only flags, not key=value format
                    val request = chain.request()
                    val query = request.url.encodedQuery
                    val fixedRequest = if (query != null) {
                        val fixedQuery = query.split("&").joinToString("&") { param ->
                            if (param.endsWith("=")) param.dropLast(1) else param
                        }
                        val fixedUrl = request.url.newBuilder().encodedQuery(fixedQuery).build()
                        request.newBuilder().url(fixedUrl).build()
                    } else {
                        request
                    }
                    chain.proceed(fixedRequest)
                }
                addInterceptor { chain ->
                    val key = generalSettings.airplanesLiveApiKey.valueBlocking
                    if (key.isNullOrBlank()) {
                        return@addInterceptor chain.proceed(chain.request())
                    }
                    val authedRequest = chain.request().newBuilder().header("auth", key).build()
                    chain.proceed(authedRequest)
                }
            }.build())
            .baseUrl(restBaseUrl)
            .addConverterFactory(jsonConverterFactory)
            .build()
            .create(AirplanesLiveRestApi::class.java)
    }

    private suspend fun <T> withRestFallback(restCall: suspend () -> T, v2Call: suspend () -> T): T {
        if (!hasApiKey()) {
            log(TAG, VERBOSE) { "Routing: v2 (no API key)" }
            return v2Call()
        }
        log(TAG, VERBOSE) { "Routing: REST" }
        return try {
            restCall()
        } catch (e: HttpException) {
            if (e.code() == 403) {
                log(TAG, WARN) { "REST API rejected key (403), falling back to v2" }
                generalSettings.apiKeyValid.value = false
                v2Call()
            } else {
                throw e
            }
        }
    }

    suspend fun getByHex(
        hexes: Set<AircraftHex>,
    ): Collection<AirplanesLiveApi.Aircraft> = withContext(dispatcherProvider.IO) {
        log(TAG) { "getByHexes(hexes=$hexes)" }
        if (hexes.isEmpty()) return@withContext emptySet()
        withRestFallback(
            restCall = {
                hexes
                    .chunkToCommaArgs(limit = 1000)
                    .map { restApi.findByHex(it).throwForErrors() }
                    .flatMap { it.ac }
            },
            v2Call = {
                hexes
                    .chunkToCommaArgs(limit = 1000)
                    .map { v2Api.getAircraftByHex(it).throwForErrors() }
                    .flatMap { it.ac }
            }
        )
    }

    suspend fun getBySquawk(
        squawks: Set<SquawkCode>
    ): Collection<AirplanesLiveApi.Aircraft> = withContext(dispatcherProvider.IO) {
        log(TAG) { "getBySquawks(squawks=$squawks)" }
        if (squawks.isEmpty()) return@withContext emptySet()
        withRestFallback(
            restCall = {
                squawks
                    .map { restApi.findBySquawk(squawk = it).throwForErrors() }
                    .flatMap { it.ac }
            },
            v2Call = {
                squawks
                    .map { v2Api.getAircraftBySquawk(it).throwForErrors() }
                    .flatMap { it.ac }
            }
        )
    }

    suspend fun getByCallsign(
        callsigns: Set<Callsign>
    ): Collection<AirplanesLiveApi.Aircraft> = withContext(dispatcherProvider.IO) {
        log(TAG) { "getByCallsigns(callsigns=$callsigns)" }
        if (callsigns.isEmpty()) return@withContext emptySet()
        withRestFallback(
            restCall = {
                callsigns
                    .chunkToCommaArgs(limit = 1000)
                    .map { restApi.findByCallsign(it).throwForErrors() }
                    .flatMap { it.ac }
            },
            v2Call = {
                callsigns
                    .chunkToCommaArgs(limit = 1000)
                    .map { v2Api.getAircraftByCallsign(it).throwForErrors() }
                    .flatMap { it.ac }
            }
        )
    }

    suspend fun getByRegistration(
        registrations: Set<Registration>
    ): Collection<AirplanesLiveApi.Aircraft> = withContext(dispatcherProvider.IO) {
        log(TAG) { "getByRegistration(registrations=$registrations)" }
        if (registrations.isEmpty()) return@withContext emptySet()
        withRestFallback(
            restCall = {
                registrations
                    .chunkToCommaArgs(limit = 1000)
                    .map { restApi.findByRegistration(it).throwForErrors() }
                    .flatMap { it.ac }
            },
            v2Call = {
                registrations
                    .chunkToCommaArgs(limit = 1000)
                    .map { v2Api.getAircraftByRegistration(it).throwForErrors() }
                    .flatMap { it.ac }
            }
        )
    }

    suspend fun getByAirframe(
        airframes: Set<Airframe>
    ): Collection<AirplanesLiveApi.Aircraft> = withContext(dispatcherProvider.IO) {
        log(TAG) { "getByAirframe(airframes=$airframes)" }
        if (airframes.isEmpty()) return@withContext emptySet()
        withRestFallback(
            restCall = {
                airframes
                    .chunkToCommaArgs(limit = 1000)
                    .map { restApi.findByAirframe(it).throwForErrors() }
                    .flatMap { it.ac }
            },
            v2Call = {
                airframes
                    .chunkToCommaArgs(limit = 1000)
                    .map { v2Api.getAircraftByAirframe(it).throwForErrors() }
                    .flatMap { it.ac }
            }
        )
    }

    suspend fun getMilitary(): Collection<AirplanesLiveApi.Aircraft> = withContext(dispatcherProvider.IO) {
        log(TAG) { "getMilitary()" }
        withRestFallback(
            restCall = { restApi.getMilitary().throwForErrors().ac },
            v2Call = { v2Api.getMilitary().throwForErrors().ac }
        )
    }

    suspend fun getLADD(): Collection<AirplanesLiveApi.Aircraft> = withContext(dispatcherProvider.IO) {
        log(TAG) { "getLADD()" }
        withRestFallback(
            restCall = { restApi.getLADD().throwForErrors().ac },
            v2Call = { v2Api.getLADD().throwForErrors().ac }
        )
    }

    suspend fun getPIA(): Collection<AirplanesLiveApi.Aircraft> = withContext(dispatcherProvider.IO) {
        log(TAG) { "getPIA()" }
        withRestFallback(
            restCall = { restApi.getPIA().throwForErrors().ac },
            v2Call = { v2Api.getPIA().throwForErrors().ac }
        )
    }

    suspend fun getByLocation(
        latitude: Double,
        longitude: Double,
        radiusInMeter: Long,
    ): Collection<AirplanesLiveApi.Aircraft> = withContext(dispatcherProvider.IO) {
        log(TAG) { "getByLocation($latitude,$longitude,$radiusInMeter)" }
        val radiusNmi = maxOf(1, (radiusInMeter / NAUTICAL_MILE_METER).toInt())
        withRestFallback(
            restCall = {
                restApi.findByCircle("$latitude,$longitude,$radiusNmi").throwForErrors().ac
            },
            v2Call = {
                v2Api.getAircraftsByLocation(
                    latitude = latitude,
                    longitude = longitude,
                    radius = radiusNmi
                ).throwForErrors().ac
            }
        )
    }

    private fun Collection<String>.chunkToCommaArgs(limit: Int = 30) = this
        .chunked(limit)
        .map { it.joinToString(",") }

    private fun <T : AirplanesLiveApi.BaseResponse> T.throwForErrors(): T = this.also {
        if (it.message != "No error") throw AirplanesLiveApiException(it.message)
    }

    companion object {
        private const val NAUTICAL_MILE_METER = 1852L
        private val TAG = logTag("Core", "Endpoint")
    }
}
