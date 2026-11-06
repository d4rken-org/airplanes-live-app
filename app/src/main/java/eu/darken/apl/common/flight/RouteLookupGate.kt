package eu.darken.apl.common.flight

import eu.darken.apl.common.MonotonicClock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * When route lookups may be sent again. Deadlines only ever grow: a later, shorter retry hint
 * never shortens a hold, and a success never clears one.
 */
@Singleton
class RouteLookupGate @Inject constructor(
    private val clock: MonotonicClock,
) {

    data class Key(val hex: String, val callsign: String)

    private val lock = Any()
    private var appHoldUntil: Long? = null
    private val keyHolds = mutableMapOf<Key, Long>()
    private val rejected = mutableSetOf<Key>()
    private val noneUntil = mutableMapOf<Key, Long>()

    fun now(): Long = clock.elapsed()

    /** The app-wide hold's deadline while it is active. */
    fun appHold(): Long? = synchronized(lock) {
        appHoldUntil?.takeIf { it > clock.elapsed() }
    }

    fun keyHold(key: Key): Long? = synchronized(lock) {
        val until = keyHolds[key] ?: return null
        if (until > clock.elapsed()) {
            until
        } else {
            keyHolds.remove(key)
            null
        }
    }

    fun isRejected(key: Key): Boolean = synchronized(lock) { key in rejected }

    /** Returns the hold's deadline after composing. */
    fun holdApp(retryAfterSeconds: Long?): Long = synchronized(lock) {
        val until = deadline(retryAfterSeconds)
        maxOf(appHoldUntil ?: until, until).also { appHoldUntil = it }
    }

    /** Returns the hold's deadline after composing. */
    fun holdKey(key: Key, retryAfterSeconds: Long?): Long = synchronized(lock) {
        val now = clock.elapsed()
        keyHolds.entries.removeAll { it.value <= now }
        val until = deadline(retryAfterSeconds)
        maxOf(keyHolds[key] ?: until, until).also { keyHolds[key] = it }
    }

    fun reject(key: Key) {
        synchronized(lock) { rejected.add(key) }
    }

    fun rememberNone(key: Key) {
        synchronized(lock) { noneUntil[key] = clock.elapsed() + NONE_TTL_MS }
    }

    fun isRememberedNone(key: Key): Boolean = synchronized(lock) {
        val now = clock.elapsed()
        noneUntil.entries.removeAll { it.value <= now }
        key in noneUntil
    }

    private fun deadline(retryAfterSeconds: Long?): Long {
        val seconds = retryAfterSeconds?.takeIf { it >= 1 } ?: FALLBACK_HOLD_SECONDS
        return clock.elapsed() + seconds * 1000
    }

    companion object {
        const val FALLBACK_HOLD_SECONDS = 5L
        const val NONE_TTL_MS = 600_000L
    }
}
