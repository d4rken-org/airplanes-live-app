package eu.darken.apl.common.flight.api

import dagger.Reusable
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.OkHttpClient
import retrofit2.Converter
import retrofit2.Retrofit
import javax.inject.Inject

@Reusable
class AdsbdbEndpoint @Inject constructor(
    private val baseClient: OkHttpClient,
    private val jsonConverterFactory: Converter.Factory,
    private val dispatcherProvider: DispatcherProvider,
    private val json: Json,
) {

    internal var baseUrl: String = "https://api.adsbdb.com/"

    private val api: AdsbdbApi by lazy {
        Retrofit.Builder()
            .client(baseClient.newBuilder().build())
            .baseUrl(baseUrl)
            .addConverterFactory(jsonConverterFactory)
            .build()
            .create(AdsbdbApi::class.java)
    }

    suspend fun getByCallsign(callsign: String): AdsbdbApi.FlightRouteData? = withContext(dispatcherProvider.IO) {
        log(TAG) { "getByCallsign(callsign=$callsign)" }

        val response = api.getByCallsign(callsign)
        when (val flightrouteElement = response.response?.flightroute) {
            is JsonObject -> {
                json.decodeFromJsonElement<AdsbdbApi.FlightRouteData>(flightrouteElement)
                    .also { log(TAG, VERBOSE) { "getByCallsign($callsign) -> $it" } }
            }

            is JsonPrimitive -> {
                log(TAG, VERBOSE) { "getByCallsign($callsign) -> ${flightrouteElement.content}" }
                null
            }

            else -> {
                log(TAG, WARN) { "getByCallsign($callsign) -> unexpected payload: $flightrouteElement" }
                null
            }
        }
    }

    companion object {
        private val TAG = logTag("Flight", "Adsbdb", "Endpoint")
    }
}
