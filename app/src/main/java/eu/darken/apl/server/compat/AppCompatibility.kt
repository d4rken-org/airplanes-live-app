package eu.darken.apl.server.compat

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import eu.darken.apl.common.BuildConfigWrap
import eu.darken.apl.common.coroutine.AppScope
import eu.darken.apl.common.datastore.createValue
import eu.darken.apl.common.datastore.value
import eu.darken.apl.common.debug.logging.Logging.Priority.ERROR
import eu.darken.apl.common.debug.logging.Logging.Priority.INFO
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.server.ServerCompatDataStore
import eu.darken.apl.server.api.MetaResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the server still serves this build. Fed by the server's version requirements and by
 * refusals for an outdated app; only a newer reading of the requirements lifts a refusal.
 */
@Singleton
class AppCompatibility internal constructor(
    appScope: CoroutineScope,
    dataStore: DataStore<Preferences>,
    json: Json,
    private val versionCode: Long,
) {

    @Inject constructor(
        @AppScope appScope: CoroutineScope,
        @ServerCompatDataStore dataStore: DataStore<Preferences>,
        json: Json,
    ) : this(appScope, dataStore, json, BuildConfigWrap.VERSION_CODE)

    private val snapshot = dataStore.createValue<CompatSnapshot?>(
        key = KEY_SNAPSHOT,
        defaultValue = null,
        json = json,
        onErrorFallbackToDefault = true,
    )

    private val dismissed = dataStore.createValue<Int?>(
        key = KEY_DISMISSED,
        defaultValue = null,
        json = json,
        onErrorFallbackToDefault = true,
    )

    private val _state = MutableStateFlow<CompatState>(CompatState.Unknown)
    val state: StateFlow<CompatState> = _state.asStateFlow()

    private val lock = Mutex()
    private val loaded = CompletableDeferred<Unit>()

    /** Guarded by [lock]. Moves on every refusal, so a check that started before one cannot undo it. */
    private var generation = 0L

    /** Written by the load before [loaded] completes, guarded by [lock] after that. */
    private var checkedAt: Instant? = null

    init {
        appScope.launch { load() }.invokeOnCompletion {
            // A load that failed or never ran fails open
            _state.compareAndSet(CompatState.Unknown, CompatState.Ok)
            loaded.complete(Unit)
        }
    }

    private suspend fun load() {
        val stored = try {
            snapshot.value()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, ERROR) { "Reading the stored compatibility failed: ${e.asLog()}" }
            _state.value = CompatState.Ok
            return
        }

        if (stored != null && stored.appVersionCode != versionCode) {
            log(TAG, INFO) { "Dropping the compatibility stored for build ${stored.appVersionCode}" }
            try {
                snapshot.value(null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(TAG, WARN) { "Clearing the stored compatibility failed: ${e.asLog()}" }
            }
            _state.value = CompatState.Ok
            return
        }

        checkedAt = stored?.checkedAt
        _state.value = when (stored?.result) {
            null, CompatSnapshot.Result.OK -> CompatState.Ok
            CompatSnapshot.Result.RECOMMENDED -> stored.recommendedVersion
                ?.let { CompatState.UpdateRecommended(it) }
                ?: CompatState.Ok

            CompatSnapshot.Result.REQUIRED -> CompatState.UpdateRequired
        }
        log(TAG) { "Loaded: ${_state.value}, checkedAt=$checkedAt" }
    }

    /** Once this returns, [state] holds the stored answer or [CompatState.Ok], never [CompatState.Unknown]. */
    suspend fun awaitLoaded() = loaded.await()

    /** When the server's version requirements were last applied, null if they never were for this build. */
    suspend fun lastCheckedAt(): Instant? {
        awaitLoaded()
        return lock.withLock { checkedAt }
    }

    /** Call before asking the server, and hand the result to [applyMeta] with the generation this returned. */
    suspend fun beginCheck(): Long {
        awaitLoaded()
        return lock.withLock { generation }
    }

    /** Returns false and changes nothing when a refusal arrived after [beginCheck] returned [generation]. */
    suspend fun applyMeta(generation: Long, meta: MetaResponse, checkedAt: Instant): Boolean {
        awaitLoaded()
        return lock.withLock {
            if (generation != this.generation) {
                log(TAG, INFO) { "Ignoring requirements from generation $generation, now ${this.generation}" }
                return@withLock false
            }
            val newState = when {
                versionCode < meta.minSupportedAppVersion -> CompatState.UpdateRequired
                versionCode < meta.recommendedAppVersion -> CompatState.UpdateRecommended(meta.recommendedAppVersion)
                else -> CompatState.Ok
            }
            log(TAG) { "applyMeta($meta): $newState" }
            _state.value = newState
            this.checkedAt = checkedAt
            persist(newState)
            true
        }
    }

    /** The server refused a request because this build is too old. */
    suspend fun noteRefusal() {
        awaitLoaded()
        lock.withLock {
            generation++
            if (_state.value != CompatState.UpdateRequired) log(TAG, WARN) { "The server requires a newer app" }
            _state.value = CompatState.UpdateRequired
            persist(CompatState.UpdateRequired)
        }
    }

    /** The newest recommended version the user put off, null if none. */
    val dismissedRecommendation: Flow<Int?> = dismissed.flow.catch { e ->
        log(TAG, ERROR) { "Reading the dismissed recommendation failed: ${e.asLog()}" }
        emit(null)
    }

    suspend fun dismissRecommendation(version: Int) {
        try {
            dismissed.value(version)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, ERROR) { "Storing the dismissed recommendation failed: ${e.asLog()}" }
        }
    }

    /** Caller holds [lock]. The state in memory is already current, a failed write only costs the restart. */
    private suspend fun persist(judged: CompatState) {
        val stored = CompatSnapshot(
            appVersionCode = versionCode,
            result = when (judged) {
                CompatState.UpdateRequired -> CompatSnapshot.Result.REQUIRED
                is CompatState.UpdateRecommended -> CompatSnapshot.Result.RECOMMENDED
                else -> CompatSnapshot.Result.OK
            },
            recommendedVersion = (judged as? CompatState.UpdateRecommended)?.recommendedVersion,
            checkedAt = checkedAt,
        )
        try {
            snapshot.value(stored)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, ERROR) { "Storing the compatibility failed: ${e.asLog()}" }
        }
    }

    companion object {
        private const val KEY_SNAPSHOT = "compat.snapshot"
        private const val KEY_DISMISSED = "compat.recommendation.dismissed"
        private val TAG = logTag("Server", "Compat")
    }
}
