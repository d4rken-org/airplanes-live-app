package eu.darken.apl.server.identity

import java.security.KeyPair
import java.security.Signature

class JcaDeviceKey(private val keyPair: KeyPair) : DeviceKey {

    override val publicKeySpkiBase64: String by lazy { keyPair.public.encodeSpkiBase64() }

    override val thumbprint: String by lazy { keyPair.public.jwkThumbprint() }

    override fun sign(message: ByteArray): ByteArray = Signature.getInstance(SIGNATURE_ALGORITHM).run {
        initSign(keyPair.private)
        update(message)
        sign()
    }

    override fun toString(): String = "JcaDeviceKey($thumbprint)"

    companion object {
        const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    }
}
