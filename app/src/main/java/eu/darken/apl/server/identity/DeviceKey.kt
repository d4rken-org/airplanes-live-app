package eu.darken.apl.server.identity

import java.math.BigInteger
import java.security.MessageDigest
import java.security.PublicKey
import java.security.interfaces.ECPublicKey
import java.util.Base64

interface DeviceKey {
    /** Standard base64 (padded) of the X.509 SPKI encoding, the wire format for enrollment. */
    val publicKeySpkiBase64: String

    /** RFC 7638 JWK thumbprint, unpadded base64url. This is the server side installation identity. */
    val thumbprint: String

    /** DER encoded ECDSA signature over [message], hashed with SHA-256. */
    fun sign(message: ByteArray): ByteArray
}

internal fun PublicKey.encodeSpkiBase64(): String = Base64.getEncoder().encodeToString(encoded)

internal fun PublicKey.jwkThumbprint(): String {
    val ec = this as ECPublicKey
    val x = ec.w.affineX.toFixedWidth(EC_P256_COORDINATE_BYTES)
    val y = ec.w.affineY.toFixedWidth(EC_P256_COORDINATE_BYTES)
    val encoder = Base64.getUrlEncoder().withoutPadding()
    val jwk = """{"crv":"P-256","kty":"EC","x":"${encoder.encodeToString(x)}","y":"${encoder.encodeToString(y)}"}"""
    val digest = MessageDigest.getInstance("SHA-256").digest(jwk.toByteArray(Charsets.UTF_8))
    return encoder.encodeToString(digest)
}

private const val EC_P256_COORDINATE_BYTES = 32

private fun BigInteger.toFixedWidth(width: Int): ByteArray {
    val raw = toByteArray()
    return when {
        raw.size == width -> raw
        // BigInteger prepends a sign byte for values with the high bit set
        raw.size > width -> raw.copyOfRange(raw.size - width, raw.size)
        else -> ByteArray(width).also { raw.copyInto(it, width - raw.size) }
    }
}
