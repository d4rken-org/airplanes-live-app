package eu.darken.apl.common

import android.os.SystemClock

/** Milliseconds since boot, unaffected by wall clock changes. */
interface MonotonicClock {
    fun elapsed(): Long
}

object SystemMonotonicClock : MonotonicClock {
    override fun elapsed(): Long = SystemClock.elapsedRealtime()
}
