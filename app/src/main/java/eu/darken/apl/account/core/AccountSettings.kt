package eu.darken.apl.account.core

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.apl.account.core.api.AccountApi
import eu.darken.apl.common.datastore.createValue
import eu.darken.apl.common.debug.logging.logTag
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import eu.darken.apl.common.datastore.createValue as createJsonValue

@Singleton
class AccountSettings @Inject constructor(
    @param:ApplicationContext private val context: Context,
    json: Json,
) {

    private val Context.dataStore by preferencesDataStore(name = "settings_account")

    /** AppAuth [net.openid.appauth.AuthState.jsonSerializeString] blob (tokens + config). */
    val authStateRaw = context.dataStore.createValue<String?>("account.oauth.authstate", null)

    /** Signature ("host|clientId") the stored AuthState belongs to; guards dev<->prod reuse. */
    val authStateSignature = context.dataStore.createValue<String?>("account.oauth.authstate.signature", null)

    /** Last-known profile, for instant UI before a refresh completes. */
    val identityCache = context.dataStore.createJsonValue<AccountApi.Identity?>(
        "account.identity",
        null,
        json,
        onErrorFallbackToDefault = true,
    )

    companion object {
        internal val TAG = logTag("Account", "Settings")
    }
}
