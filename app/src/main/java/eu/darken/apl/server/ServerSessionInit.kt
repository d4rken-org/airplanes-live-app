package eu.darken.apl.server

import eu.darken.apl.common.coroutine.AppScope
import eu.darken.apl.common.debug.logging.Logging.Priority.ERROR
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.session.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerSessionInit @Inject constructor(
    @param:AppScope private val appScope: CoroutineScope,
    private val sessionManager: SessionManager,
    private val accessRepo: AccessRepo,
    private val serverClock: ServerClock,
) {

    fun setup() {
        log(TAG) { "setup()" }
        appScope.launch {
            try {
                sessionManager.ensureSession()
            } catch (e: Exception) {
                log(TAG, ERROR) { "Could not establish a server session: ${e.asLog()}" }
                return@launch
            }

            val current = accessRepo.state.value
            val age = current?.let { Duration.between(it.fetchedAt, serverClock.now()) }
            if (age == null || age > MAX_ACCESS_AGE) accessRepo.refresh("app-start")
        }
    }

    companion object {
        private val MAX_ACCESS_AGE = Duration.ofMinutes(15)
        private val TAG = logTag("Server", "SessionInit")
    }
}
