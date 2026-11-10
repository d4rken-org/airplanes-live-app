package eu.darken.apl.watch.ui.thumbnail

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Runs one render at a time. Each render gets the full [timeout] once it holds the lock, so waiting in line
 * doesn't eat into it.
 */
@Singleton
class LocationThumbnailRenderQueue(private val timeout: Duration) {

    @Inject constructor() : this(DEFAULT_TIMEOUT)

    private val mutex = Mutex()

    // A timeout is thrown as a plain exception, not a CancellationException, so Coil treats it as an error
    suspend fun <T : Any> runExclusive(block: suspend () -> T): T = mutex.withLock {
        withTimeoutOrNull(timeout) { block() } ?: throw LocationThumbnailTimeoutException(timeout)
    }

    companion object {
        val DEFAULT_TIMEOUT = 30.seconds
    }
}

class LocationThumbnailTimeoutException(timeout: Duration) : Exception("Rendering took longer than $timeout")
