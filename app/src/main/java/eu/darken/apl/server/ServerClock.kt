package eu.darken.apl.server

import eu.darken.apl.common.MonotonicClock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Server time projected onto the monotonic clock: observation ages must not change when the device
 * clock is wrong or a snapshot is delivered again.
 */
@Singleton
class ServerClock @Inject constructor(
    private val monotonicClock: MonotonicClock,
) {

    private data class Anchor(
        val serverTimeMillis: Long,
        val elapsed: Long,
        val precise: Boolean,
    )

    @Volatile private var anchor: Anchor? = null

    /** Milliseconds the server is ahead of the device wall clock. */
    val offsetMillis: Long
        get() = anchor?.let { it.serverTimeMillis + (monotonicClock.elapsed() - it.elapsed) - System.currentTimeMillis() } ?: 0L

    /** Timestamps taken from a response payload, millisecond accurate. */
    fun noteServerTime(serverTimeMillis: Long) {
        anchor = Anchor(serverTimeMillis, monotonicClock.elapsed(), precise = true)
    }

    /** The HTTP `Date` header only has second granularity, so it never displaces a payload timestamp. */
    fun noteDateHeader(dateMillis: Long) {
        val current = anchor
        val outdated = current == null ||
                !current.precise ||
                monotonicClock.elapsed() - current.elapsed > PRECISE_ANCHOR_MAX_AGE_MS
        if (outdated) anchor = Anchor(dateMillis, monotonicClock.elapsed(), precise = false)
    }

    fun now(): Instant {
        val current = anchor ?: return Instant.ofEpochMilli(System.currentTimeMillis())
        return Instant.ofEpochMilli(current.serverTimeMillis + (monotonicClock.elapsed() - current.elapsed))
    }

    fun nowSeconds(): Long = now().epochSecond

    fun elapsed(): Long = monotonicClock.elapsed()

    companion object {
        private const val PRECISE_ANCHOR_MAX_AGE_MS = 5 * 60 * 1000L
    }
}
