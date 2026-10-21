package eu.darken.apl.server.compat

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import eu.darken.apl.common.coroutine.AppScope
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.api.ServerEndpoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/** Asks the server for its version requirements and hands them to [AppCompatibility], one request at a time. */
@Singleton
class AppCompatibilityCheck @Inject constructor(
    @param:AppScope private val appScope: CoroutineScope,
    private val endpoint: ServerEndpoint,
    private val compatibility: AppCompatibility,
    private val serverClock: ServerClock,
) {

    private class Fetch(val force: Boolean, val generation: Long, val done: CompletableDeferred<Unit>)

    private val lock = Mutex()

    /** Guarded by [lock]. */
    private var inFlight: Fetch? = null

    /** Guarded by [lock]. The forced fetch that starts once [inFlight] ends, for triggers it could not answer. */
    private var queued: CompletableDeferred<Unit>? = null

    private val _checking = MutableStateFlow(false)

    /** True while a request for the requirements is out or queued. */
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    private var isInit = false

    fun setup() {
        log(TAG) { "setup()" }
        require(!isInit)
        isInit = true

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                onForeground()
            }
        })
    }

    /** While the app is refused, every return to the foreground asks again; otherwise once per [TTL]. */
    fun onForeground(): Job = appScope.launch {
        compatibility.awaitLoaded()
        if (compatibility.state.value is CompatState.UpdateRequired) {
            check(force = true)
            return@launch
        }
        val last = compatibility.lastCheckedAt()
        // Off by more than the TTL in either direction: the clock changed, the stored time says nothing
        val age = last?.let { Duration.between(it, serverClock.now()) }
        if (age == null || age >= TTL || age <= TTL.negated()) check(force = false)
    }

    /**
     * Returns once a request that started after this call, or one that can still answer it, has
     * finished. [force] skips the HTTP cache. A failed request changes nothing.
     */
    suspend fun check(force: Boolean) {
        val answer: Deferred<Unit> = lock.withLock {
            val running = inFlight
            when {
                running == null -> start(force, CompletableDeferred())
                // Joinable only while its generation is current, see AppCompatibility.applyMeta
                (running.force || !force) && running.generation == compatibility.beginCheck() -> running.done
                else -> queued ?: CompletableDeferred<Unit>().also { queued = it }
            }
        }
        answer.await()
    }

    /** Caller holds [lock]. */
    private suspend fun start(force: Boolean, done: CompletableDeferred<Unit>): Deferred<Unit> {
        val generation = compatibility.beginCheck()
        inFlight = Fetch(force, generation, done)
        _checking.value = true
        // Atomic, so a cancelled scope still runs the cleanup and nobody waits forever
        appScope.launch(start = CoroutineStart.ATOMIC) {
            try {
                fetch(force, generation)
            } finally {
                withContext(NonCancellable) {
                    lock.withLock {
                        done.complete(Unit)
                        val next = queued
                        queued = null
                        if (next != null) {
                            start(force = true, done = next)
                        } else {
                            inFlight = null
                            _checking.value = false
                        }
                    }
                }
            }
        }
        return done
    }

    private suspend fun fetch(force: Boolean, generation: Long) {
        try {
            val meta = endpoint.meta(forceNetwork = force)
            val applied = compatibility.applyMeta(generation, meta, serverClock.now())
            log(TAG) { "check(force=$force): $meta, applied=$applied, now ${compatibility.state.value}" }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, WARN) { "check(force=$force) failed: ${e.asLog()}" }
        }
    }

    companion object {
        internal val TTL: Duration = Duration.ofHours(4)
        private val TAG = logTag("Server", "Compat", "Check")
    }
}
