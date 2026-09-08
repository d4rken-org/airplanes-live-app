package eu.darken.apl.server.identity

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

class DeviceKeyTest : BaseTest() {

    private fun newKeyPair() = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    private fun pinnedPublicKey() = KeyFactory.getInstance("EC")
        .generatePublic(X509EncodedKeySpec(PINNED_SPKI_HEX.hexToByteArray()))

    @Test
    fun `thumbprint matches the pinned vector`() {
        pinnedPublicKey().jwkThumbprint() shouldBe PINNED_THUMBPRINT
    }

    @Test
    fun `public key is standard base64 of the spki encoding`() {
        val keyPair = newKeyPair()
        val key = JcaDeviceKey(keyPair)

        key.publicKeySpkiBase64 shouldMatch Regex("^[A-Za-z0-9+/]+={0,2}$")
        Base64.getDecoder().decode(key.publicKeySpkiBase64) shouldBe keyPair.public.encoded
    }

    @Test
    fun `enrollment signs the whole challenge string`() {
        val keyPair = newKeyPair()
        val key = JcaDeviceKey(keyPair)
        val signature = key.sign(enrollmentMessage(PINNED_CHALLENGE))

        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(keyPair.public)
        verifier.update(PINNED_CHALLENGE.toByteArray(Charsets.UTF_8))
        verifier.verify(signature) shouldBe true

        val decodedPayload = Base64.getUrlDecoder().decode(PINNED_CHALLENGE.substringBefore('.'))
        val payloadVerifier = Signature.getInstance("SHA256withECDSA")
        payloadVerifier.initVerify(keyPair.public)
        payloadVerifier.update(decodedPayload)
        payloadVerifier.verify(signature) shouldBe false
    }

    @Test
    fun `pinned signature verifies against the pinned key`() {
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(pinnedPublicKey())
        verifier.update(PINNED_CHALLENGE.toByteArray(Charsets.UTF_8))
        verifier.verify(PINNED_SIGNATURE_HEX.hexToByteArray()) shouldBe true
    }

    @Test
    fun `refresh proof bytes are exact`() {
        val proof = refreshProof(
            refreshToken = "REFRESH",
            operationId = "5a5c6b2e-3b0a-4a2e-9a0f-000000000001",
            timestampSeconds = 1710000000L,
        )
        proof.toString(Charsets.UTF_8) shouldBe
                "apl-refresh-v2\nREFRESH\n5a5c6b2e-3b0a-4a2e-9a0f-000000000001\n1710000000"
    }

    @Test
    fun `operation ids are lowercase canonical uuids`() {
        repeat(16) {
            newOperationId() shouldMatch Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
        }
    }

    companion object {
        private const val PINNED_SPKI_HEX =
            "3059301306072a8648ce3d020106082a8648ce3d03010703420004" +
                    "25eac36c0655b64faf83598db8832a1d2cde041c8443e5e4ad45d0f40a232a42" +
                    "cc2d222fa74ef3da5e45c4efa9396697e0dedfd1c27e4f4fb9c289a9d6bbabf7"

        private const val PINNED_CHALLENGE =
            "eyJ2IjoxLCJub25jZSI6IkFBQUFBQUFBQUFBQUFBQUFBQSJ9.c2lnbmF0dXJl"

        private const val PINNED_SIGNATURE_HEX =
            "304402201e7ad23a84b4d30780d49f8d8b5c4c47e7b59548094006d85e3769b2b9ed537a" +
                    "0220470f08349ca810aed4ebe23a424e676aa3f03103b72586e02069476379b53d54"

        private const val PINNED_THUMBPRINT = "devZe8lnhrEDfvf3dNVTZrqGp6dByEb6EtngxaK4YqI"
    }
}
