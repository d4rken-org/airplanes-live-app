package eu.darken.apl.server.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import eu.darken.apl.server.ServerSessionDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionStore @Inject constructor(
    @param:ServerSessionDataStore private val dataStore: DataStore<Preferences>,
) {

    data class Credentials(
        val installationId: String,
        val accessToken: String,
        val accessExpiresAt: Instant,
        val refreshToken: String,
        val refreshExpiresAt: Instant,
    )

    data class PendingRefresh(
        val operationId: String,
        val refreshToken: String,
        val startedAt: Instant,
    )

    data class Session(
        val credentials: Credentials? = null,
        val keyThumbprint: String? = null,
        val pendingRefresh: PendingRefresh? = null,
        val revoked: Boolean = false,
    )

    val session: Flow<Session> = dataStore.data.map { it.toSession() }

    suspend fun current(): Session = session.first()

    /** Storing new credentials always retires the pending refresh they came from. */
    suspend fun setCredentials(credentials: Credentials, keyThumbprint: String) {
        dataStore.edit {
            it[INSTALLATION_ID] = credentials.installationId
            it[ACCESS_TOKEN] = credentials.accessToken
            it[ACCESS_EXPIRES_AT] = credentials.accessExpiresAt.toEpochMilli()
            it[REFRESH_TOKEN] = credentials.refreshToken
            it[REFRESH_EXPIRES_AT] = credentials.refreshExpiresAt.toEpochMilli()
            it[KEY_THUMBPRINT] = keyThumbprint
            it.remove(PENDING_OPERATION_ID)
            it.remove(PENDING_REFRESH_TOKEN)
            it.remove(PENDING_STARTED_AT)
        }
    }

    suspend fun setPendingRefresh(pending: PendingRefresh) {
        dataStore.edit {
            it[PENDING_OPERATION_ID] = pending.operationId
            it[PENDING_REFRESH_TOKEN] = pending.refreshToken
            it[PENDING_STARTED_AT] = pending.startedAt.toEpochMilli()
        }
    }

    suspend fun clearPendingRefresh() {
        dataStore.edit {
            it.remove(PENDING_OPERATION_ID)
            it.remove(PENDING_REFRESH_TOKEN)
            it.remove(PENDING_STARTED_AT)
        }
    }

    /** Forces the next token request to refresh, without discarding the refresh credentials. */
    suspend fun expireAccessToken(token: String) {
        dataStore.edit {
            if (it[ACCESS_TOKEN] == token) it[ACCESS_EXPIRES_AT] = 0L
        }
    }

    suspend fun clearCredentials() {
        dataStore.edit {
            it.remove(INSTALLATION_ID)
            it.remove(ACCESS_TOKEN)
            it.remove(ACCESS_EXPIRES_AT)
            it.remove(REFRESH_TOKEN)
            it.remove(REFRESH_EXPIRES_AT)
            it.remove(PENDING_OPERATION_ID)
            it.remove(PENDING_REFRESH_TOKEN)
            it.remove(PENDING_STARTED_AT)
        }
    }

    suspend fun setRevoked() {
        dataStore.edit {
            it[REVOKED] = true
            it.remove(INSTALLATION_ID)
            it.remove(ACCESS_TOKEN)
            it.remove(ACCESS_EXPIRES_AT)
            it.remove(REFRESH_TOKEN)
            it.remove(REFRESH_EXPIRES_AT)
            it.remove(PENDING_OPERATION_ID)
            it.remove(PENDING_REFRESH_TOKEN)
            it.remove(PENDING_STARTED_AT)
        }
    }

    /** Only the debug identity reset clears a revocation, it is not recoverable by retrying. */
    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }

    private fun Preferences.toSession(): Session {
        val installationId = this[INSTALLATION_ID]
        val accessToken = this[ACCESS_TOKEN]
        val accessExpiresAt = this[ACCESS_EXPIRES_AT]
        val refreshToken = this[REFRESH_TOKEN]
        val refreshExpiresAt = this[REFRESH_EXPIRES_AT]
        val credentials = if (
            installationId != null && accessToken != null && accessExpiresAt != null &&
            refreshToken != null && refreshExpiresAt != null
        ) {
            Credentials(
                installationId = installationId,
                accessToken = accessToken,
                accessExpiresAt = Instant.ofEpochMilli(accessExpiresAt),
                refreshToken = refreshToken,
                refreshExpiresAt = Instant.ofEpochMilli(refreshExpiresAt),
            )
        } else {
            null
        }

        val pendingOperationId = this[PENDING_OPERATION_ID]
        val pendingToken = this[PENDING_REFRESH_TOKEN]
        val pendingStartedAt = this[PENDING_STARTED_AT]
        val pending = if (pendingOperationId != null && pendingToken != null && pendingStartedAt != null) {
            PendingRefresh(pendingOperationId, pendingToken, Instant.ofEpochMilli(pendingStartedAt))
        } else {
            null
        }

        return Session(
            credentials = credentials,
            keyThumbprint = this[KEY_THUMBPRINT],
            pendingRefresh = pending,
            revoked = this[REVOKED] ?: false,
        )
    }

    companion object {
        private val INSTALLATION_ID = stringPreferencesKey("session.installation.id")
        private val ACCESS_TOKEN = stringPreferencesKey("session.access.token")
        private val ACCESS_EXPIRES_AT = longPreferencesKey("session.access.expires.at")
        private val REFRESH_TOKEN = stringPreferencesKey("session.refresh.token")
        private val REFRESH_EXPIRES_AT = longPreferencesKey("session.refresh.expires.at")
        private val KEY_THUMBPRINT = stringPreferencesKey("session.key.thumbprint")
        private val PENDING_OPERATION_ID = stringPreferencesKey("session.pending.refresh.operation.id")
        private val PENDING_REFRESH_TOKEN = stringPreferencesKey("session.pending.refresh.token")
        private val PENDING_STARTED_AT = longPreferencesKey("session.pending.refresh.started.at")
        private val REVOKED = booleanPreferencesKey("session.revoked")
    }
}
