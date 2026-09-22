package eu.darken.apl.server.identity

import java.util.UUID

/** [timestampSeconds] is Unix epoch seconds. */
fun refreshProof(refreshToken: String, operationId: String, timestampSeconds: Long): ByteArray =
    "apl-refresh-v2\n$refreshToken\n$operationId\n$timestampSeconds".toByteArray(Charsets.UTF_8)

/** The whole opaque challenge string is signed, not its decoded payload. */
fun enrollmentMessage(challenge: String): ByteArray = challenge.toByteArray(Charsets.UTF_8)

fun newOperationId(): String = UUID.randomUUID().toString().lowercase()
