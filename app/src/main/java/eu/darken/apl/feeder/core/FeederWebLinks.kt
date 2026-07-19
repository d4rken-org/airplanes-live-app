package eu.darken.apl.feeder.core

private val CANONICAL_UUID = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

/**
 * URL-facing feeder identifier, mirroring the website's `derive_short_id`: the last 12 hex chars
 * of the canonical UUID (its final group). Anything that isn't a canonical UUID is returned as-is —
 * the website's feeder route accepts full UUIDs too and 404s cleanly on garbage, which beats
 * slicing a malformed id into something that looks valid but isn't.
 */
fun ReceiverId.toShortFeederId(): String = if (CANONICAL_UUID.matches(this)) takeLast(12) else this

/** The feeder's detail page on the airplanes.live account dashboard. */
fun feederWebsiteUrl(host: String, feederId: ReceiverId): String =
    "${host.trimEnd('/')}/account/feeders/${feederId.toShortFeederId()}/"
