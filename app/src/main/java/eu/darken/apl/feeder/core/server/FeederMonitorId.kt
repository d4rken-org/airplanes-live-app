package eu.darken.apl.feeder.core.server

import eu.darken.apl.feeder.core.ReceiverId
import java.util.UUID

// UUID.fromString alone also takes short groups: "1-2-3-4-5" reads as 00000001-0002-0003-0004-000000000005
private val HYPHENATED_UUID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

/**
 * The id the server's feeder monitoring knows this receiver by, e.g. " 0A1B2C3D-…" becomes "0a1b2c3d-…".
 * Null for an id the server would refuse: not a hyphenated UUID, or the all-zero one.
 */
fun ReceiverId.toFeederMonitorId(): String? {
    val trimmed = trim()
    if (!HYPHENATED_UUID.matches(trimmed)) return null
    val uuid = UUID.fromString(trimmed)
    if (uuid.mostSignificantBits == 0L && uuid.leastSignificantBits == 0L) return null
    return uuid.toString()
}
