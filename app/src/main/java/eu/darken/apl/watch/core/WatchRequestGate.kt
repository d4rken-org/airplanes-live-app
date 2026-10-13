package eu.darken.apl.watch.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Live checks and history pages spend the same WATCH allowance, one at a time. A live check that is
 * waiting makes the history side stop before its next page, so backfill never delays an alert.
 */
@Singleton
class WatchRequestGate @Inject constructor() {

    private val mutex = Mutex()
    private val liveWaiting = AtomicInteger(0)

    val isLiveWaiting: Boolean
        get() = liveWaiting.get() > 0

    suspend fun <T> live(block: suspend () -> T): T {
        liveWaiting.incrementAndGet()
        try {
            return mutex.withLock { block() }
        } finally {
            liveWaiting.decrementAndGet()
        }
    }

    /** Null when a live check is waiting, the page was not run. */
    suspend fun <T> history(block: suspend () -> T): T? {
        if (isLiveWaiting) return null
        return mutex.withLock {
            if (isLiveWaiting) null else block()
        }
    }
}
