package eu.darken.apl.account.core

import android.content.Intent
import eu.darken.apl.account.core.api.AccountApi
import eu.darken.apl.account.core.api.AccountEndpoint
import eu.darken.apl.account.core.auth.AuthManager
import eu.darken.apl.common.datastore.value
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
        val identity = accountEndpoint.getIdentity()
        accountSettings.identityCache.value(identity)
    }

    suspend fun completeLogin(data: Intent) {
        log(TAG) { "completeLogin()" }
        authManager.completeAuth(data)
        refreshIdentity()
    }

    suspend fun logout() {
        log(TAG) { "logout()" }
        authManager.logout()
        accountSettings.identityCache.value(null)
    }

    companion object {
        private val TAG = logTag("Account", "Repo")
    }
}
