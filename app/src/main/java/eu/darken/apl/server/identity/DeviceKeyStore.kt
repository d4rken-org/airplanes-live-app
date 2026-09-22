package eu.darken.apl.server.identity

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.Logging.Priority.INFO
import eu.darken.apl.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.spec.ECGenParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

interface DeviceKeyStore {
    suspend fun getOrCreate(): DeviceKey

    fun exists(): Boolean

    suspend fun reset()
}

@Singleton
class KeystoreDeviceKeyStore @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
) : DeviceKeyStore {

    private val lock = Mutex()

    private val keyStore: KeyStore
        get() = KeyStore.getInstance(PROVIDER).apply { load(null) }

    override suspend fun getOrCreate(): DeviceKey = lock.withLock {
        withContext(dispatcherProvider.IO) {
            load() ?: create()
        }
    }

    override fun exists(): Boolean = keyStore.containsAlias(ALIAS)

    override suspend fun reset() = lock.withLock {
        withContext(dispatcherProvider.IO) {
            log(TAG, INFO) { "reset()" }
            keyStore.deleteEntry(ALIAS)
        }
    }

    private fun load(): DeviceKey? {
        val store = keyStore
        val privateKey = store.getKey(ALIAS, null) as? PrivateKey ?: return null
        val publicKey = store.getCertificate(ALIAS)?.publicKey ?: return null
        log(TAG, VERBOSE) { "Existing device key loaded" }
        return KeystoreDeviceKey(privateKey, publicKey)
    }

    private fun create(): DeviceKey {
        log(TAG, INFO) { "Generating a new device key" }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(false)
            .build()
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, PROVIDER)
        generator.initialize(spec)
        val pair = generator.generateKeyPair()
        return KeystoreDeviceKey(pair.private, pair.public)
    }

    companion object {
        private const val PROVIDER = "AndroidKeyStore"
        private const val ALIAS = "apl.installation.key"
        private val TAG = logTag("Server", "DeviceKeyStore")
    }
}
