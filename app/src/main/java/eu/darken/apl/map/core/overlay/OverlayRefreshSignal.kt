package eu.darken.apl.map.core.overlay

import eu.darken.apl.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Asks [OverlayRepo] to read the catalog again. Requests made while nothing reads the catalog are dropped. */
@Singleton
class OverlayRefreshSignal @Inject constructor() {

    private val _requests = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val requests: Flow<String> = _requests.asSharedFlow()

    fun request(reason: String) {
        log(TAG, VERBOSE) { "request($reason)" }
        _requests.tryEmit(reason)
    }

    companion object {
        private val TAG = logTag("Map", "Overlay", "RefreshSignal")
    }
}
