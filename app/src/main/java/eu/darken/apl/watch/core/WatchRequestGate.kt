package eu.darken.apl.watch.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Live checks spend the WATCH allowance one at a time. */
@Singleton
class WatchRequestGate @Inject constructor() {

    private val mutex = Mutex()

    suspend fun <T> live(block: suspend () -> T): T = mutex.withLock { block() }
}
