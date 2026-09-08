package eu.darken.apl.server.identity

import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature

class KeystoreDeviceKey(
    private val privateKey: PrivateKey,
    publicKey: PublicKey,
) : DeviceKey {

    override val publicKeySpkiBase64: String = publicKey.encodeSpkiBase64()

    override val thumbprint: String = publicKey.jwkThumbprint()

    override fun sign(message: ByteArray): ByteArray = Signature.getInstance(JcaDeviceKey.SIGNATURE_ALGORITHM).run {
        initSign(privateKey)
        update(message)
        sign()
    }

    override fun toString(): String = "KeystoreDeviceKey($thumbprint)"
}
