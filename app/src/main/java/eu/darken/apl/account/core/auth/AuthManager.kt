package eu.darken.apl.account.core.auth

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.apl.account.core.AccountSettings
import eu.darken.apl.account.core.LoginCancelledException
import eu.darken.apl.account.core.NotLoggedInException
import eu.darken.apl.account.core.OAuthRateLimitedException
import eu.darken.apl.account.core.SessionExpiredException
import eu.darken.apl.common.BuildConfigWrap
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.datastore.value
import eu.darken.apl.common.debug.logging.Logging.Priority.ERROR
import eu.darken.apl.common.debug.logging.Logging.Priority.INFO
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.NoClientAuthentication
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenResponse
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Owns the OAuth session (AppAuth [AuthState]). A single [Mutex] serializes every read-modify-write
 * of the stored state so the rotating refresh token is persisted atomically — presenting a stale
 * refresh token would revoke the whole token family.
 */
@Singleton
class AuthManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: AccountSettings,
    private val baseClient: OkHttpClient,
    private val dispatcherProvider: DispatcherProvider,
) {

    private val host = BuildConfigWrap.OAUTH_HOST
    private val clientId = BuildConfigWrap.OAUTH_CLIENT_ID
    private val redirectUri = BuildConfigWrap.OAUTH_REDIRECT_URI
    private val signature = "$host|$clientId"

    private val stateLock = Mutex()
    private val authService by lazy { AuthorizationService(context) }

    private val serviceConfig = AuthorizationServiceConfiguration(
        "$host/o/authorize/".toUri(),
        "$host/o/token/".toUri(),
    )

    val isLoggedIn: Flow<Boolean> = combine(
        settings.authStateRaw.flow,
        settings.authStateSignature.flow,
    ) { raw, sig -> raw != null && sig == signature }

    /** Build the Custom Tab intent for the authorize step (PKCE S256 is AppAuth's default). */
    fun createAuthRequestIntent(): Intent {
        val request = AuthorizationRequest.Builder(
            serviceConfig,
            clientId,
            ResponseTypeValues.CODE,
            redirectUri.toUri(),
        ).setScope(SCOPE).build()
        return authService.getAuthorizationRequestIntent(request)
    }

    /** Exchange the authorization-code redirect (delivered as an Activity result) for tokens. */
    suspend fun completeAuth(data: Intent) {
        val response = AuthorizationResponse.fromIntent(data)
        val exception = AuthorizationException.fromIntent(data)
        if (response == null) {
            when (exception) {
                AuthorizationException.GeneralErrors.USER_CANCELED_AUTH_FLOW,
                AuthorizationException.GeneralErrors.PROGRAM_CANCELED_AUTH_FLOW -> {
                    log(TAG, INFO) { "Authorization cancelled by user" }
                    throw LoginCancelledException()
                }
                else -> {
                    log(TAG, WARN) { "Authorization failed: ${exception?.asLog()}" }
                    throw exception ?: NotLoggedInException()
                }
            }
        }

        // Serialize the exchange with refresh/logout and make it non-cancellable so a returned
        // (rotated) token is always persisted, never lost to a cancelled login coroutine.
        stateLock.withLock {
            withContext(NonCancellable) {
                val (tokenResponse, tokenException) = performTokenRequest(response)
                if (tokenResponse == null) {
                    log(TAG, WARN) { "Token exchange failed: ${tokenException?.asLog()}" }
                    throw tokenException ?: NotLoggedInException()
                }
                val state = AuthState(response, exception)
                state.update(tokenResponse, tokenException)
                persistLocked(state)
            }
        }
        log(TAG, INFO) { "Authentication completed" }
    }

    /**
     * Returns a fresh access token, refreshing (and persisting the rotated refresh token) if needed.
     * Throws [NotLoggedInException] / [SessionExpiredException] when no usable session exists.
     */
    suspend fun getValidAccessToken(): String = stateLock.withLock {
        // NonCancellable: the refresh token rotates server-side during performActionWithFreshTokens.
        // If the caller is cancelled after rotation but before we persist, the new token would be
        // lost and the next use would replay the stale one, revoking the whole token family.
        withContext(NonCancellable) {
            val state = loadStateLocked() ?: throw NotLoggedInException()
            val token = try {
                freshToken(state)
            } catch (e: SessionExpiredException) {
                clearLocked()
                throw e
            }
            persistLocked(state)
            token ?: throw NotLoggedInException()
        }
    }

    suspend fun logout() {
        log(TAG) { "logout()" }
        // Capture the latest refresh token and clear local state atomically under the lock
        // (waits behind any in-flight refresh), then revoke best-effort outside the lock.
        val refreshToken = stateLock.withLock {
            withContext(NonCancellable) {
                val token = loadStateLocked()?.refreshToken
                clearLocked()
                token
            }
        }
        if (refreshToken != null) runCatching { revoke(refreshToken) }
            .onFailure { log(TAG, WARN) { "Revoke failed (ignored): ${it.asLog()}" } }
    }

    private suspend fun loadStateLocked(): AuthState? {
        val raw = settings.authStateRaw.value() ?: return null
        if (settings.authStateSignature.value() != signature) {
            log(TAG, WARN) { "Stored AuthState belongs to a different host/client, clearing." }
            clearLocked()
            return null
        }
        return try {
            AuthState.jsonDeserialize(raw)
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to deserialize AuthState, clearing: ${e.asLog()}" }
            clearLocked()
            null
        }
    }

    private suspend fun persistLocked(state: AuthState) {
        settings.authStateRaw.value(state.jsonSerializeString())
        settings.authStateSignature.value(signature)
    }

    private suspend fun clearLocked() {
        settings.authStateRaw.value(null)
        settings.authStateSignature.value(null)
    }

    private suspend fun performTokenRequest(
        response: AuthorizationResponse,
    ): Pair<TokenResponse?, AuthorizationException?> = suspendCancellableCoroutine { cont ->
        authService.performTokenRequest(
            response.createTokenExchangeRequest(),
            NoClientAuthentication.INSTANCE,
        ) { tokenResponse, ex -> cont.resume(tokenResponse to ex) }
    }

    private suspend fun freshToken(state: AuthState): String? = suspendCancellableCoroutine { cont ->
        state.performActionWithFreshTokens(
            authService,
            NoClientAuthentication.INSTANCE,
        ) { accessToken, _, ex ->
            if (ex != null) cont.resumeWithException(mapTokenException(ex))
            else cont.resume(accessToken)
        }
    }

    private fun mapTokenException(ex: AuthorizationException): Exception = when {
        ex == AuthorizationException.TokenRequestErrors.INVALID_GRANT -> SessionExpiredException()
        ex.error == "rate_limited" -> OAuthRateLimitedException(null)
        else -> ex
    }

    private suspend fun revoke(token: String) = withContext(dispatcherProvider.IO) {
        val body = FormBody.Builder()
            .add("token", token)
            .add("client_id", clientId)
            .build()
        val request = Request.Builder().url("$host/o/revoke_token/").post(body).build()
        baseClient.newCall(request).execute().use { resp ->
            log(TAG, INFO) { "Revoke responded ${resp.code}" }
        }
    }

    companion object {
        private const val SCOPE = "me"
        private val TAG = logTag("Account", "AuthManager")
    }
}
