package eu.darken.apl.server.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import eu.darken.apl.common.BuildConfigWrap
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.datastore.valueBlocking
import eu.darken.apl.common.debug.autoreport.DebugSettings
import eu.darken.apl.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.ServerJson
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import java.net.ConnectException
import java.net.NoRouteToHostException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerEndpoint @Inject constructor(
    private val baseClient: OkHttpClient,
    @param:ServerJson private val json: Json,
    private val dispatcherProvider: DispatcherProvider,
    private val serverClock: ServerClock,
    private val debugSettings: DebugSettings? = null,
) {

    internal var baseUrl: String = BuildConfigWrap.SERVER_BASE_URL

    private val api: ServerApi by lazy { createApi(createClient()) }

    /**
     * Registration is the one call whose source address the server compares against the address its
     * upstream saw the feeder on. That comparison is by raw bytes, so a device reaching us over IPv6
     * can never match a feeder seen over IPv4, and the registration is refused. Pinning this call to
     * IPv4 removes that mismatch; it cannot make the two addresses agree on a multi-WAN network.
     */
    private val ipv4Api: ServerApi by lazy { createApi(createClient(dns = Ipv4OnlyDns())) }

    private fun createApi(client: OkHttpClient): ServerApi = Retrofit.Builder()
        .client(client)
        .baseUrl(baseUrl)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(ServerApi::class.java)

    /** Built from [baseClient] either way, so both clients share its connection pool and dispatcher. */
    private fun createClient(dns: Dns? = null): OkHttpClient = baseClient.newBuilder().apply {
        // The shared logger has no redaction, credentials must never reach it
        interceptors().removeAll { it is HttpLoggingInterceptor }
        addInterceptor(loggingInterceptor())
        addInterceptor(serverClockInterceptor())
        dns?.let { dns(it) }
    }.build()

    private fun loggingInterceptor(): Interceptor {
        val logger = HttpLoggingInterceptor.Logger { log(TAG, VERBOSE) { it } }
        val credentialLogger = HttpLoggingInterceptor(logger).apply {
            level = HttpLoggingInterceptor.Level.HEADERS
            redactHeader("Authorization")
        }
        val defaultLogger = HttpLoggingInterceptor(logger).apply {
            level = when {
                debugSettings?.isDebugMode?.valueBlocking == true -> HttpLoggingInterceptor.Level.BODY
                else -> HttpLoggingInterceptor.Level.BASIC
            }
            redactHeader("Authorization")
        }
        return Interceptor { chain ->
            val path = chain.request().url.encodedPath.trimStart('/')
            val delegate = if (CREDENTIAL_PATHS.contains(path)) credentialLogger else defaultLogger
            delegate.intercept(chain)
        }
    }

    private fun serverClockInterceptor() = Interceptor { chain ->
        chain.proceed(chain.request()).also { response ->
            response.headers.getDate("Date")?.let { serverClock.noteDateHeader(it.time) }
        }
    }

    private suspend fun <T> call(block: suspend () -> T): T = withContext(dispatcherProvider.IO) {
        try {
            block()
        } catch (e: HttpException) {
            throw e.toApiException()
        }
    }

    private fun HttpException.toApiException(): ServerApiException {
        val response = response()
        val status = code()
        val problem = response?.errorBody()
            ?.takeIf { it.contentType()?.subtype?.contains("problem") == true }
            ?.let {
                try {
                    json.decodeFromString<ApiProblem>(it.string())
                } catch (_: Exception) {
                    null
                }
            }
        val headerRetryAfter = response?.headers()?.get("Retry-After")?.toLongOrNull()
        return ServerApiException(
            code = problem?.code ?: "http_$status",
            status = status,
            retryAfterSeconds = problem?.retryAfterSeconds ?: headerRetryAfter,
            detail = problem?.detail,
        )
    }

    private fun bearer(token: String) = "Bearer $token"

    suspend fun challenge(): ChallengeResponse = call { api.challenge() }

    suspend fun enroll(request: EnrollRequest): TokenResponse = call { api.enroll(request) }

    suspend fun refresh(request: RefreshRequest): TokenResponse = call { api.refresh(request) }

    suspend fun access(token: String): AccessResponse = call { api.access(bearer(token)) }

    suspend fun registerFeeder(token: String, feederId: String): FeederStatusResponse = call {
        try {
            ipv4Api.registerFeeder(bearer(token), RegisterFeederRequest(feederId))
        } catch (e: NoIpv4AddressException) {
            throw Ipv4UnreachableException(e)
        } catch (e: ConnectException) {
            throw Ipv4UnreachableException(e)
        } catch (e: NoRouteToHostException) {
            throw Ipv4UnreachableException(e)
        }
    }

    suspend fun feederStatus(token: String): FeederStatusResponse = call { api.feederStatus(bearer(token)) }

    suspend fun unlinkFeeder(token: String): FeederStatusResponse = call { api.unlinkFeeder(bearer(token)) }

    suspend fun map(token: String, request: MapRequest): ViewingResponse = call { api.map(bearer(token), request) }

    suspend fun ar(token: String, request: ArRequest): ViewingResponse = call { api.ar(bearer(token), request) }

    suspend fun search(token: String, request: SearchBatchRequest): BatchResponse = call {
        api.search(bearer(token), request)
    }

    suspend fun checkWatches(token: String, request: WatchBatchRequest): BatchResponse = call {
        api.checkWatches(bearer(token), request)
    }

    companion object {
        private val CREDENTIAL_PATHS = setOf(
            "api/v1/enrollment/challenge",
            "api/v1/installations",
            "api/v1/sessions/refresh",
        )
        private val TAG = logTag("Server", "Endpoint")
    }
}
