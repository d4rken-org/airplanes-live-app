package eu.darken.apl.account.core.api

import dagger.Reusable
import eu.darken.apl.account.core.SessionExpiredException
import eu.darken.apl.account.core.auth.AuthManager
import eu.darken.apl.common.BuildConfigWrap
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Converter
import retrofit2.HttpException
import retrofit2.Retrofit
import javax.inject.Inject

/**
 * Authenticated client for the airplanes.live account API. A fresh bearer token is fetched
 * per call from [AuthManager] and passed via the Authorization header (no OkHttp interceptor,
 * so rotation / rate-limit / re-auth handling stays explicit and testable).
 */
@Reusable
class AccountEndpoint @Inject constructor(
    private val baseClient: OkHttpClient,
    private val jsonConverterFactory: Converter.Factory,
    private val dispatcherProvider: DispatcherProvider,
    private val authManager: AuthManager,
) {
    internal var baseUrl: String = "${BuildConfigWrap.OAUTH_HOST}/"

    private val api: AccountApi by lazy {
        Retrofit.Builder()
            .client(baseClient)
            .baseUrl(baseUrl)
            .addConverterFactory(jsonConverterFactory)
            .build()
            .create(AccountApi::class.java)
    }

    suspend fun getIdentity(): AccountApi.Identity = withContext(dispatcherProvider.IO) {
        log(TAG) { "getIdentity()" }
        mapErrors { api.getIdentity(bearer()) }
    }

    suspend fun getOwnedFeeders(): List<AccountApi.OwnedFeeder> = withContext(dispatcherProvider.IO) {
        log(TAG) { "getOwnedFeeders()" }
        mapErrors { api.getFeeders(bearer()).feeders }
    }

    private suspend fun bearer(): String = "Bearer ${authManager.getValidAccessToken()}"

    private inline fun <T> mapErrors(block: () -> T): T = try {
        block()
    } catch (e: HttpException) {
        // A 401 even with a freshly-minted token means the session was revoked server-side.
        if (e.code() == 401) throw SessionExpiredException() else throw e
    }

    companion object {
        private val TAG = logTag("Account", "Endpoint")
    }
}
