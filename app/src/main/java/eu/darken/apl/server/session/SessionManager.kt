package eu.darken.apl.server.session

import eu.darken.apl.common.BuildConfigWrap
import eu.darken.apl.common.coroutine.AppScope
import eu.darken.apl.common.debug.logging.Logging.Priority.INFO
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.api.EnrollRequest
import eu.darken.apl.server.api.RefreshRequest
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.ServerCodes
import eu.darken.apl.server.api.ServerEndpoint
import eu.darken.apl.server.api.TokenResponse
import eu.darken.apl.server.identity.DeviceKey
import eu.darken.apl.server.identity.DeviceKeyStore
import eu.darken.apl.server.identity.enrollmentMessage
import eu.darken.apl.server.identity.newOperationId
import eu.darken.apl.server.identity.refreshProof
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(
    @param:AppScope private val appScope: CoroutineScope,
    private val store: SessionStore,
    private val endpoint: ServerEndpoint,
    private val deviceKeyStore: DeviceKeyStore,
    private val serverClock: ServerClock,
) {

    private val lock = Mutex()
    private val _state = MutableStateFlow<SessionState>(SessionState.Unknown)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private var deviceKeyChecked = false

    init {
        appScope.launch { publishStoredState() }
    }

    private suspend fun publishStoredState() {
        val session = store.current()
        _state.value = when {
            session.revoked -> SessionState.Revoked
            session.credentials != null -> SessionState.Active(session.credentials.installationId)
            else -> SessionState.NoSession
        }
    }

    suspend fun ensureSession() {
        lock.withLock {
            val session = requireNotRevoked()
            if (session.credentials == null) {
                enrollLocked()
            } else {
                _state.value = SessionState.Active(session.credentials.installationId)
            }
        }
    }

    suspend fun accessToken(): String = lock.withLock { accessTokenLocked() }

    suspend fun invalidateAccessToken(token: String) = lock.withLock {
        store.expireAccessToken(token)
    }

    /**
     * Runs [block] with a valid access token and covers the two outcomes the caller cannot handle:
     * a token that expired mid flight is refreshed once, a revoked installation ends the session.
     */
    suspend fun <T> authed(block: suspend (token: String) -> T): T {
        val token = accessToken()
        return try {
            block(token)
        } catch (e: ServerApiException) {
            when (e.code) {
                ServerCodes.EXPIRED, ServerCodes.INVALID_TOKEN -> {
                    invalidateAccessToken(token)
                    runCatchingRevocation(block)
                }

                ServerCodes.INSTALLATION_REVOKED -> throw markRevoked()
                else -> throw e
            }
        }
    }

    /** Wipes the installation identity, the app enrolls from scratch on the next request. */
    suspend fun resetIdentity() = lock.withLock {
        log(TAG, INFO) { "resetIdentity()" }
        deviceKeyStore.reset()
        store.clearAll()
        deviceKeyChecked = false
        _state.value = SessionState.NoSession
    }

    private suspend fun <T> runCatchingRevocation(block: suspend (token: String) -> T): T {
        val token = accessToken()
        return try {
            block(token)
        } catch (e: ServerApiException) {
            if (e.code == ServerCodes.INSTALLATION_REVOKED) throw markRevoked() else throw e
        }
    }

    private suspend fun accessTokenLocked(): String {
        val session = checkDeviceKeyLocked(requireNotRevoked())
        val credentials = session.credentials ?: return enrollLocked().accessToken
        val remaining = credentials.accessExpiresAt.toEpochMilli() - serverClock.now().toEpochMilli()
        if (remaining > ACCESS_TOKEN_MIN_VALIDITY_MS) {
            _state.value = SessionState.Active(credentials.installationId)
            return credentials.accessToken
        }
        return refreshLocked(session).accessToken
    }

    private suspend fun requireNotRevoked(): SessionStore.Session = store.current().also {
        if (it.revoked) {
            _state.value = SessionState.Revoked
            throw SessionRevokedException()
        }
    }

    private suspend fun checkDeviceKeyLocked(session: SessionStore.Session): SessionStore.Session {
        if (deviceKeyChecked) return session
        deviceKeyChecked = true

        val storedThumbprint = session.keyThumbprint
        if (session.credentials == null || storedThumbprint == null) return session

        val currentThumbprint = if (deviceKeyStore.exists()) deviceKeyStore.getOrCreate().thumbprint else null
        if (currentThumbprint == storedThumbprint) return session

        log(TAG, WARN) { "Device key does not match the stored session, enrolling as a new identity" }
        store.clearCredentials()
        _state.value = SessionState.KeyLost
        return store.current()
    }

    private suspend fun refreshLocked(session: SessionStore.Session): SessionStore.Credentials {
        val credentials = session.credentials!!
        val deviceKey = deviceKeyStore.getOrCreate()

        // Reusing the operation id makes a retried refresh idempotent instead of a second rotation
        val pending = session.pendingRefresh?.takeIf { it.refreshToken == credentials.refreshToken }
            ?: SessionStore.PendingRefresh(
                operationId = newOperationId(),
                refreshToken = credentials.refreshToken,
                startedAt = serverClock.now(),
            ).also { store.setPendingRefresh(it) }

        var proofRetried = false
        while (true) {
            val timestamp = serverClock.nowSeconds()
            val request = RefreshRequest(
                refreshToken = pending.refreshToken,
                operationId = pending.operationId,
                timestamp = timestamp,
                signature = deviceKey.signBase64(refreshProof(pending.refreshToken, pending.operationId, timestamp)),
            )
            val response = try {
                endpoint.refresh(request)
            } catch (e: ServerApiException) {
                when (e.code) {
                    ServerCodes.STALE_PROOF, ServerCodes.BAD_PROOF -> {
                        if (proofRetried) throw e
                        proofRetried = true
                        continue
                    }

                    ServerCodes.RECOVERY_SUPERSEDED -> {
                        store.clearPendingRefresh()
                        val stored = store.current().credentials
                        val remaining = stored?.accessExpiresAt?.toEpochMilli()?.minus(serverClock.now().toEpochMilli())
                        return if (stored != null && remaining!! > ACCESS_TOKEN_MIN_VALIDITY_MS) {
                            _state.value = SessionState.Active(stored.installationId)
                            stored
                        } else {
                            enrollLocked()
                        }
                    }

                    ServerCodes.INVALID_TOKEN,
                    ServerCodes.EXPIRED,
                    ServerCodes.REVOKED,
                    ServerCodes.REUSE_DETECTED,
                    ServerCodes.RECOVERY_EXPIRED,
                    ServerCodes.RECOVERY_UNAVAILABLE -> {
                        store.clearPendingRefresh()
                        return enrollLocked()
                    }

                    ServerCodes.INSTALLATION_REVOKED -> throw markRevoked()

                    else -> throw e
                }
            }
            return response.persist(deviceKey.thumbprint)
        }
    }

    private suspend fun enrollLocked(): SessionStore.Credentials {
        val deviceKey = deviceKeyStore.getOrCreate()
        var challengeRetried = false
        while (true) {
            val challenge = endpoint.challenge().challenge
            val request = EnrollRequest(
                challenge = challenge,
                publicKey = deviceKey.publicKeySpkiBase64,
                signature = deviceKey.signBase64(enrollmentMessage(challenge)),
                variant = VARIANT,
                appVersion = BuildConfigWrap.VERSION_CODE.toInt(),
            )
            val response = try {
                endpoint.enroll(request)
            } catch (e: ServerApiException) {
                when (e.code) {
                    ServerCodes.CHALLENGE_USED,
                    ServerCodes.EXPIRED_CHALLENGE,
                    ServerCodes.INVALID_CHALLENGE -> {
                        if (challengeRetried) throw e
                        challengeRetried = true
                        continue
                    }

                    ServerCodes.INSTALLATION_REVOKED -> throw markRevoked()

                    else -> throw e
                }
            }
            return response.persist(deviceKey.thumbprint)
        }
    }

    private suspend fun TokenResponse.persist(keyThumbprint: String): SessionStore.Credentials {
        val credentials = SessionStore.Credentials(
            installationId = installationId,
            accessToken = accessToken,
            accessExpiresAt = Instant.ofEpochMilli(accessExpiresAt),
            refreshToken = refreshToken,
            refreshExpiresAt = Instant.ofEpochMilli(refreshExpiresAt),
        )
        store.setCredentials(credentials, keyThumbprint)
        _state.value = SessionState.Active(installationId)
        return credentials
    }

    private suspend fun markRevoked(): SessionRevokedException {
        log(TAG, WARN) { "Installation was revoked" }
        store.setRevoked()
        _state.value = SessionState.Revoked
        return SessionRevokedException()
    }

    private fun DeviceKey.signBase64(message: ByteArray): String =
        Base64.getEncoder().encodeToString(sign(message))

    companion object {
        private const val ACCESS_TOKEN_MIN_VALIDITY_MS = 30_000L
        private val VARIANT = if (BuildConfigWrap.FLAVOR == BuildConfigWrap.Flavor.GPLAY) "play" else "foss"
        private val TAG = logTag("Server", "SessionManager")
    }
}
