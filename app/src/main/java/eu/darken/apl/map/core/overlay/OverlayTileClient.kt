package eu.darken.apl.map.core.overlay

import eu.darken.apl.common.coroutine.AppScope
import eu.darken.apl.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.server.ServerJson
import eu.darken.apl.server.api.ApiProblem
import eu.darken.apl.server.api.ServerCodes
import eu.darken.apl.server.api.ServerEndpoint
import eu.darken.apl.server.api.appHeaders
import eu.darken.apl.server.compat.AppCompatibility
import eu.darken.apl.server.compat.CompatState
import eu.darken.apl.server.session.SessionManager
import eu.darken.apl.server.session.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Dispatcher
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import okio.GzipSource
import okio.buffer
import org.maplibre.android.module.http.HttpRequestUtil
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The HTTP client MapLibre loads everything with, base map included. Only requests to the server's
 * origin get the session token and app headers, checked on every hop so a redirect can't carry them away.
 */
@Singleton
class OverlayTileClient @Inject constructor(
    baseClient: OkHttpClient,
    @param:ServerJson private val json: Json,
    private val endpoint: ServerEndpoint,
    private val sessionManager: SessionManager,
    private val compatibility: AppCompatibility,
    @param:AppScope private val appScope: CoroutineScope,
    private val refreshSignal: OverlayRefreshSignal,
) {

    internal val client: OkHttpClient = baseClient.newBuilder().apply {
        // The shared logger has no redaction, credentials must never reach it
        interceptors().removeAll { it is HttpLoggingInterceptor }
        // MapLibre Native (13.6) caches tiles in its own database
        cache(null)
        // A map full of tiles must not hold up the API calls and token refreshes on the shared dispatcher
        dispatcher(Dispatcher().apply { maxRequestsPerHost = MAX_REQUESTS_PER_HOST })
        addNetworkInterceptor(Interceptor { chain -> intercept(chain) })
        authenticator(object : Authenticator {
            override fun authenticate(route: Route?, response: Response): Request? = refreshToken(response)
        })
    }.build()

    private val installed = AtomicBoolean(false)

    /** Call before the first map view exists. */
    fun install() {
        if (!installed.compareAndSet(false, true)) return
        log(TAG) { "install()" }
        HttpRequestUtil.setOkHttpClient(client)
    }

    private fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.isServerOrigin()) return chain.proceed(request)

        if (compatibility.state.value is CompatState.UpdateRequired) {
            throw IOException("The server requires a newer app")
        }
        if (sessionManager.state.value is SessionState.Revoked) {
            throw IOException("This installation was revoked by the server")
        }

        val authorized = request.newBuilder().appHeaders().apply {
            // A retry from the authenticator already carries the refreshed token
            if (request.header(AUTHORIZATION) == null) {
                sessionManager.currentAccessToken.value?.let { header(AUTHORIZATION, bearer(it)) }
            }
        }.build()

        val response = chain.proceed(authorized)
        log(TAG, VERBOSE) { "${request.url.encodedPath}: ${response.code}" }
        if (response.code in 400..499) noteRefusal(response)
        return response
    }

    private fun noteRefusal(response: Response) {
        val code = response.problemCode() ?: return
        val status = response.code
        when {
            status == 403 && code == ServerCodes.APP_UPDATE_REQUIRED -> {
                appScope.launch { compatibility.noteRefusal() }
            }

            status == 403 && code == ServerCodes.INSTALLATION_REVOKED -> {
                appScope.launch { sessionManager.noteRevokedByServer() }
            }

            status == 403 && code in CATALOG_CODES_403 -> refreshSignal.request(code)
            status == 404 && code in CATALOG_CODES_404 -> refreshSignal.request(code)
        }
    }

    private fun refreshToken(response: Response): Request? {
        val request = response.request
        if (!request.url.isServerOrigin()) return null
        if (response.priorResponse != null) return null

        val code = response.problemCode()
        if (code != ServerCodes.INVALID_TOKEN && code != ServerCodes.EXPIRED) return null

        // The token is added below the authenticator, only the request that went out carries it
        val sentToken = (response.networkResponse?.request ?: request).header(AUTHORIZATION)?.removePrefix(BEARER_PREFIX)
        return try {
            val token = runBlocking {
                // A token another request already replaced is left alone, so concurrent refusals refresh once
                sentToken?.let { sessionManager.invalidateAccessToken(it) }
                sessionManager.accessToken()
            }
            request.newBuilder().header(AUTHORIZATION, bearer(token)).build()
        } catch (e: Exception) {
            log(TAG, WARN) { "Refreshing the token for ${request.url.encodedPath} failed: $e" }
            null
        }
    }

    private fun HttpUrl.isServerOrigin(): Boolean {
        val server = endpoint.baseUrl.toHttpUrlOrNull() ?: return false
        return scheme == server.scheme && host == server.host && port == server.port
    }

    /** Problem answers are tiny, a body that isn't one just yields no code. */
    private fun Response.problemCode(): String? = try {
        val peeked = peekBody(PROBLEM_PEEK_BYTES)
        val text = if (header("Content-Encoding").equals("gzip", ignoreCase = true)) {
            GzipSource(peeked.source()).buffer().use { it.readUtf8() }
        } else {
            peeked.string()
        }
        json.decodeFromString<ApiProblem>(text).code
    } catch (e: Exception) {
        null
    }

    private fun bearer(token: String) = "$BEARER_PREFIX$token"

    companion object {
        private const val AUTHORIZATION = "Authorization"
        private const val BEARER_PREFIX = "Bearer "
        private const val MAX_REQUESTS_PER_HOST = 20
        private const val PROBLEM_PEEK_BYTES = 4096L
        private val TAG = logTag("Map", "Overlay", "TileClient")

        /** Refusals the catalog answers: what the installation may load, or which frames exist now. */
        private val CATALOG_CODES_403 = setOf(
            ServerCodes.INSTALLATION_RESTRICTED,
            ServerCodes.OVERLAY_UPGRADE_REQUIRED,
        )
        private val CATALOG_CODES_404 = setOf(
            ServerCodes.OVERLAY_NOT_FOUND,
            ServerCodes.OVERLAY_FRAME_UNKNOWN,
        )
    }
}
