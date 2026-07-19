package eu.darken.apl.account.core

import android.content.Intent
import eu.darken.apl.account.core.api.AccountApi
import eu.darken.apl.account.core.api.AccountEndpoint
import eu.darken.apl.account.core.auth.AuthManager
import eu.darken.apl.common.datastore.value
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-facing account state: login state + cached identity, and the login/logout/refresh actions.
 * Owned feeders are consumed separately by the feeder module via [AccountEndpoint].
 */
@Singleton
class AccountRepo @Inject constructor(
    private val authManager: AuthManager,
    private val accountEndpoint: AccountEndpoint,
    private val accountSettings: AccountSettings,
) {

    val isLoggedIn: Flow<Boolean> = authManager.isLoggedIn

    val identity: Flow<AccountApi.Identity?> = accountSettings.identityCache.flow

    suspend fun refreshIdentity() {
        log(TAG) { "refreshIdentity()" }
        // The fetch is a slow network call during which the session can be replaced (logout +
        // re-login as another account). Committing the result unguarded would overwrite the new
        // account's identity with the old one's — only cache it if the session is unchanged.
        val epoch = authManager.sessionEpoch
        val identity = accountEndpoint.getIdentity()
        if (authManager.sessionEpoch == epoch) {
            accountSettings.identityCache.value(identity)
        } else {
            log(TAG, WARN) { "Session changed while fetching identity, dropping stale result" }
        }
    }

    suspend fun completeLogin(data: Intent) {
        log(TAG) { "completeLogin()" }
        // Drop any identity cached from a previous/revoked session *before* the new auth state is
        // persisted, so a stale profile from a different account can't flash the instant isLoggedIn
        // flips true (remote revocation clears auth but leaves the identity cache behind).
        accountSettings.identityCache.value(null)
        authManager.completeAuth(data)
        refreshIdentity()
    }

    suspend fun logout() {
        log(TAG) { "logout()" }
        // Clear the identity before the (network-slow) token revocation, so this clear can't land
        // *after* a quick re-login has already cached the next account's identity.
        accountSettings.identityCache.value(null)
        authManager.logout()
    }

    companion object {
        private val TAG = logTag("Account", "Repo")
    }
}
